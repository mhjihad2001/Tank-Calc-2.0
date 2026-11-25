package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.engine.ExportUtils
import com.example.ui.components.AppScreen
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

enum class CalcMode { BY_NEAT, BY_TOTAL, BY_LEVEL }
enum class NeatUnit { DRUMS, LITRES, KG }
enum class LevelUnit { CM, MM }
enum class StrengthBasis { VOLUME, WEIGHT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolutionScreen(
  repository: TankRepository,
  onBack: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val dosingTanks by repository.dosingTanks.collectAsState()
  val chemicals by repository.chemicals.collectAsState()
  val presets by repository.solutionPresets.collectAsState()
  val batches by repository.solutionBatches.collectAsState()

  var draftSavedIndicator by remember { mutableStateOf(false) }
  var showClearConfirm by remember { mutableStateOf(false) }
  var showOverflowMenu by remember { mutableStateOf(false) }

  // Selected Preset
  var selectedPresetId by remember(presets) {
    mutableStateOf(presets.firstOrNull()?.id ?: "")
  }
  val activePreset = presets.find { it.id == selectedPresetId } ?: presets.firstOrNull()

  // Calculation mode
  var calcMode by remember { mutableStateOf(CalcMode.BY_NEAT) }
  var neatUnit by remember { mutableStateOf(NeatUnit.DRUMS) }
  var levelUnit by remember { mutableStateOf(LevelUnit.CM) }
  var strengthBasis by remember { mutableStateOf(StrengthBasis.VOLUME) }

  // Editable parameters
  var neatChemName by remember(activePreset) { mutableStateOf(activePreset?.neatChemicalName ?: "DMDS") }
  var neatDensityStr by remember(activePreset) { mutableStateOf((activePreset?.neatDensityKgL ?: 1.06).toString()) }
  var drumSizeKgStr by remember(activePreset) { mutableStateOf((activePreset?.drumSizeKg ?: 200.0).toString()) }
  var solventName by remember(activePreset) { mutableStateOf(activePreset?.solventName ?: "HN+LN") }
  var solventDensityStr by remember { mutableStateOf("0.74") }
  var strengthPctStr by remember(activePreset) { mutableStateOf((activePreset?.strengthPercent ?: 16.5).toString()) }

  // User input values per mode
  var inputNeatValStr by remember { mutableStateOf("1.0") } // e.g. 1 drum
  var inputTargetTotalValStr by remember { mutableStateOf("1000.0") } // e.g. 1000 L
  var inputTargetLevelRiseStr by remember { mutableStateOf("50.0") } // e.g. 50 cm
  var initialLevelStr by remember { mutableStateOf("30.0") } // initial level in tank

  // Selected Prep Tank
  var selectedTankName by remember(activePreset, dosingTanks) {
    mutableStateOf(
      activePreset?.defaultTankName?.takeIf { dt -> dosingTanks.any { it.name.equals(dt, ignoreCase = true) } }
        ?: dosingTanks.firstOrNull()?.name ?: "Preparation Tank"
    )
  }
  val prepTank = dosingTanks.find { it.name.equals(selectedTankName, ignoreCase = true) }
    ?: dosingTanks.firstOrNull()
    ?: DosingTank(name = "Preparation Tank", diameterMm = 1600.0, heightMm = 2000.0, overrideLitresPerMm = 2.0106, linkedChemicalName = "DMDS")

  // Actual measurement inputs
  var actualFinalLevelStr by remember { mutableStateOf("") }
  var operatorRemarks by remember { mutableStateOf("") }
  var setActiveBatchForChem by remember { mutableStateOf(true) }

  // Dialogs
  var showHistoryDialog by remember { mutableStateOf(false) }
  var showPresetEditorDialog by remember { mutableStateOf(false) }
  var editingPreset by remember { mutableStateOf<SolutionPreset?>(null) }
  var selectedBatchForView by remember { mutableStateOf<SolutionBatchRecord?>(null) }

  // Numeric parsing
  val neatDensity = neatDensityStr.toDoubleOrNull() ?: 1.0
  val drumSizeKg = drumSizeKgStr.toDoubleOrNull() ?: 200.0
  val solventDensity = solventDensityStr.toDoubleOrNull() ?: 0.74
  val strengthPct = (strengthPctStr.toDoubleOrNull() ?: 16.5).coerceIn(0.01, 100.0)
  val strengthFraction = strengthPct / 100.0

  // Tank geometric factors
  val litresPerMm = prepTank.calculatedLitresPerMm
  val litresPerCm = litresPerMm * 10.0
  val initialLevelInput = initialLevelStr.toDoubleOrNull() ?: 0.0
  val initialLevelMm = if (levelUnit == LevelUnit.CM) initialLevelInput * 10.0 else initialLevelInput
  val initialLevelCm = initialLevelMm / 10.0

  // Calculation resolution based on mode
  val neatLPerDrum = if (neatDensity > 0.0) drumSizeKg / neatDensity else 0.0

  val (calculatedNeatL, calculatedTotalL) = when (calcMode) {
    CalcMode.BY_NEAT -> {
      val inputVal = inputNeatValStr.toDoubleOrNull() ?: 0.0
      val neatL = when (neatUnit) {
        NeatUnit.DRUMS -> inputVal * neatLPerDrum
        NeatUnit.LITRES -> inputVal
        NeatUnit.KG -> if (neatDensity > 0.0) inputVal / neatDensity else 0.0
      }
      val totalL = if (strengthBasis == StrengthBasis.VOLUME) {
        if (strengthFraction > 0.0) neatL / strengthFraction else 0.0
      } else {
        // By weight: neatMass = neatL * neatDensity; totalMass = neatMass / strengthFraction
        // solventMass = totalMass - neatMass; solventL = solventMass / solventDensity
        val neatMass = neatL * neatDensity
        val totalMass = if (strengthFraction > 0.0) neatMass / strengthFraction else 0.0
        val solventMass = (totalMass - neatMass).coerceAtLeast(0.0)
        val solventL = if (solventDensity > 0.0) solventMass / solventDensity else 0.0
        neatL + solventL
      }
      Pair(neatL, totalL)
    }
    CalcMode.BY_TOTAL -> {
      val totalL = (inputTargetTotalValStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
      val neatL = if (strengthBasis == StrengthBasis.VOLUME) {
        totalL * strengthFraction
      } else {
        // % wt: totalMass ~ totalL * avgDensity, solve for neatL
        val neatFraction = strengthFraction
        val num = totalL * solventDensity * neatFraction
        val den = neatDensity * (1.0 - neatFraction) + solventDensity * neatFraction
        if (den > 0.0) (num / den) else 0.0
      }
      Pair(neatL, totalL)
    }
    CalcMode.BY_LEVEL -> {
      val levelRiseInput = (inputTargetLevelRiseStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
      val levelRiseCm = if (levelUnit == LevelUnit.CM) levelRiseInput else levelRiseInput / 10.0
      val totalL = levelRiseCm * litresPerCm
      val neatL = if (strengthBasis == StrengthBasis.VOLUME) {
        totalL * strengthFraction
      } else {
        val neatFraction = strengthFraction
        val num = totalL * solventDensity * neatFraction
        val den = neatDensity * (1.0 - neatFraction) + solventDensity * neatFraction
        if (den > 0.0) (num / den) else 0.0
      }
      Pair(neatL, totalL)
    }
  }

  val calculatedNeatKg = calculatedNeatL * neatDensity
  val calculatedSolventL = (calculatedTotalL - calculatedNeatL).coerceAtLeast(0.0)
  val calculatedSolventKg = calculatedSolventL * solventDensity

  // Tank Level Changes
  val levelRiseCm = if (litresPerCm > 0.0) calculatedTotalL / litresPerCm else 0.0
  val levelRiseMm = levelRiseCm * 10.0
  val expectedFinalLevelMm = initialLevelMm + levelRiseMm
  val expectedFinalLevelCm = expectedFinalLevelMm / 10.0
  val isOverflow = expectedFinalLevelMm > prepTank.heightMm
  val remainingCapacityL = (prepTank.capacityLiters - (expectedFinalLevelMm * litresPerMm)).coerceAtLeast(0.0)

  // Actual vs Expected calculation
  val actualFinalLevelInput = actualFinalLevelStr.toDoubleOrNull()
  val actualFinalLevelMm = if (actualFinalLevelInput != null) {
    if (levelUnit == LevelUnit.CM) actualFinalLevelInput * 10.0 else actualFinalLevelInput
  } else null

  val (actualVolumeAddedL, volumeDeviationL, volumeDeviationPct, actualStrengthPct) = if (actualFinalLevelMm != null) {
    val actVol = ((actualFinalLevelMm - initialLevelMm) * litresPerMm).coerceAtLeast(0.0)
    val devL = actVol - calculatedTotalL
    val devPct = if (calculatedTotalL > 0.0) (devL / calculatedTotalL) * 100.0 else 0.0
    val actStr = if (actVol > 0.0) (calculatedNeatL / actVol) * 100.0 else 0.0
    listOf(actVol, devL, devPct, actStr)
  } else {
    listOf(0.0, 0.0, 0.0, strengthPct)
  }

  // Restore draft
  LaunchedEffect(Unit) {
    val draft = repository.getDraft("solution")
    if (draft != null) {
      try {
        val json = JSONObject(draft.payloadJson)
        if (json.has("presetId")) selectedPresetId = json.getString("presetId")
        if (json.has("inputNeat")) inputNeatValStr = json.getString("inputNeat")
        if (json.has("inputTargetTotal")) inputTargetTotalValStr = json.getString("inputTargetTotal")
        if (json.has("inputTargetLevelRise")) inputTargetLevelRiseStr = json.getString("inputTargetLevelRise")
        if (json.has("initialLevel")) initialLevelStr = json.getString("initialLevel")
      } catch (_: Exception) {}
    }
  }

  // Auto-save draft (400ms debounce)
  LaunchedEffect(selectedPresetId, inputNeatValStr, inputTargetTotalValStr, inputTargetLevelRiseStr, initialLevelStr) {
    delay(400L)
    val payload = JSONObject().apply {
      put("presetId", selectedPresetId)
      put("inputNeat", inputNeatValStr)
      put("inputTargetTotal", inputTargetTotalValStr)
      put("inputTargetLevelRise", inputTargetLevelRiseStr)
      put("initialLevel", initialLevelStr)
    }.toString()
    repository.saveDraft("solution", "default", payload, "Solution: ${activePreset?.name ?: "Batch"}")
    draftSavedIndicator = true
    delay(1500L)
    draftSavedIndicator = false
  }

  AppScreen(
    title = "Solution Making",
    subtitle = if (draftSavedIndicator) "Draft saved" else null,
    onBack = onBack,
    actions = {
      IconButton(onClick = { showHistoryDialog = true }) {
        BadgedBox(
          badge = {
            if (batches.isNotEmpty()) {
              Badge { Text("${batches.size}") }
            }
          }
        ) {
          Icon(Icons.Default.History, contentDescription = "Batch History")
        }
      }
      Box {
        IconButton(onClick = { showOverflowMenu = true }) {
          Icon(Icons.Default.MoreVert, contentDescription = "More Actions")
        }
        DropdownMenu(
          expanded = showOverflowMenu,
          onDismissRequest = { showOverflowMenu = false }
        ) {
          DropdownMenuItem(
            text = { Text("Add New Preset") },
            leadingIcon = { Icon(Icons.Default.AddCircleOutline, contentDescription = null) },
            onClick = {
              showOverflowMenu = false
              editingPreset = null
              showPresetEditorDialog = true
            }
          )
          HorizontalDivider()
          DropdownMenuItem(
            text = { Text("Clear / New") },
            leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) },
            onClick = {
              showOverflowMenu = false
              showClearConfirm = true
            }
          )
        }
      }
    }
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item { Spacer(modifier = Modifier.height(4.dp)) }

      // PRESET SELECTOR
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Solution Presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(
              onClick = {
                editingPreset = activePreset
                showPresetEditorDialog = true
              }
            ) {
              Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Edit Selected")
            }
          }

          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            items(presets) { preset ->
              val isSelected = preset.id == selectedPresetId
              Card(
                colors = CardDefaults.cardColors(
                  containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (isSelected) {
                  androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else null,
                modifier = Modifier
                  .width(180.dp)
                  .clickable { selectedPresetId = preset.id }
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      preset.name,
                      fontWeight = FontWeight.Bold,
                      style = MaterialTheme.typography.titleSmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                      shape = RoundedCornerShape(4.dp)
                    ) {
                      Text(
                        "${String.format(Locale.US, "%.1f", preset.strengthPercent)}%",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    "Neat: ${preset.neatChemicalName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Text(
                    "Solvent: ${preset.solventName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Text(
                    "Tank: ${preset.defaultTankName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      }

      // CALCULATION MODE (Segmented)
      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Calculation Mode", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
              SegmentedButton(
                selected = calcMode == CalcMode.BY_NEAT,
                onClick = { calcMode = CalcMode.BY_NEAT },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
              ) {
                Text("By Neat Qty", style = MaterialTheme.typography.labelSmall)
              }
              SegmentedButton(
                selected = calcMode == CalcMode.BY_TOTAL,
                onClick = { calcMode = CalcMode.BY_TOTAL },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
              ) {
                Text("Target Total", style = MaterialTheme.typography.labelSmall)
              }
              SegmentedButton(
                selected = calcMode == CalcMode.BY_LEVEL,
                onClick = { calcMode = CalcMode.BY_LEVEL },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
              ) {
                Text("By Tank Level", style = MaterialTheme.typography.labelSmall)
              }
            }

            // Mode-specific input row
            when (calcMode) {
              CalcMode.BY_NEAT -> {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  OutlinedTextField(
                    value = inputNeatValStr,
                    onValueChange = { inputNeatValStr = it },
                    label = { Text("Neat Quantity (${neatUnit.name.lowercase().replaceFirstChar { it.uppercase() }})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                  )

                  Column {
                    Text("Unit", style = MaterialTheme.typography.labelSmall)
                    Row {
                      FilterChip(
                        selected = neatUnit == NeatUnit.DRUMS,
                        onClick = { neatUnit = NeatUnit.DRUMS },
                        label = { Text("Drums") }
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      FilterChip(
                        selected = neatUnit == NeatUnit.LITRES,
                        onClick = { neatUnit = NeatUnit.LITRES },
                        label = { Text("L") }
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      FilterChip(
                        selected = neatUnit == NeatUnit.KG,
                        onClick = { neatUnit = NeatUnit.KG },
                        label = { Text("kg") }
                      )
                    }
                  }
                }
              }
              CalcMode.BY_TOTAL -> {
                OutlinedTextField(
                  value = inputTargetTotalValStr,
                  onValueChange = { inputTargetTotalValStr = it },
                  label = { Text("Target Total Solution Volume (Litres)") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  trailingIcon = { Text("L", modifier = Modifier.padding(end = 12.dp)) },
                  modifier = Modifier.fillMaxWidth()
                )
              }
              CalcMode.BY_LEVEL -> {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  OutlinedTextField(
                    value = inputTargetLevelRiseStr,
                    onValueChange = { inputTargetLevelRiseStr = it },
                    label = { Text("Target Level Increase") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                  )
                  Row {
                    FilterChip(
                      selected = levelUnit == LevelUnit.CM,
                      onClick = { levelUnit = LevelUnit.CM },
                      label = { Text("cm") }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                      selected = levelUnit == LevelUnit.MM,
                      onClick = { levelUnit = LevelUnit.MM },
                      label = { Text("mm") }
                    )
                  }
                }
              }
            }
          }
        }
      }

      // CHEMICAL, SOLVENT & STRENGTH PARAMETERS
      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Recipe & Chemical Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = neatChemName,
                onValueChange = { neatChemName = it },
                label = { Text("Neat Chemical") },
                modifier = Modifier.weight(1.2f)
              )
              OutlinedTextField(
                value = neatDensityStr,
                onValueChange = { neatDensityStr = it },
                label = { Text("Density (kg/L)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
              )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = drumSizeKgStr,
                onValueChange = { drumSizeKgStr = it },
                label = { Text("Drum Size (kg/drum)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
              )
              OutlinedTextField(
                value = solventName,
                onValueChange = { solventName = it },
                label = { Text("Solvent Name") },
                modifier = Modifier.weight(1.2f)
              )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
              OutlinedTextField(
                value = strengthPctStr,
                onValueChange = { strengthPctStr = it },
                label = { Text("Target Strength (%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = { Text("%", modifier = Modifier.padding(end = 12.dp)) },
                modifier = Modifier.weight(1f)
              )
              Column(modifier = Modifier.weight(1f)) {
                Text("Basis", style = MaterialTheme.typography.labelSmall)
                Row {
                  FilterChip(
                    selected = strengthBasis == StrengthBasis.VOLUME,
                    onClick = { strengthBasis = StrengthBasis.VOLUME },
                    label = { Text("% Vol") }
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  FilterChip(
                    selected = strengthBasis == StrengthBasis.WEIGHT,
                    onClick = { strengthBasis = StrengthBasis.WEIGHT },
                    label = { Text("% Wt") }
                  )
                }
              }
            }
          }
        }
      }

      // PREPARATION TANK & LEVEL
      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Preparation Tank", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            // Tank Selector
            var expandedTankDropdown by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
              expanded = expandedTankDropdown,
              onExpandedChange = { expandedTankDropdown = !expandedTankDropdown }
            ) {
              OutlinedTextField(
                value = prepTank.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Dosing / Prep Tank") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTankDropdown) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
              )
              ExposedDropdownMenu(
                expanded = expandedTankDropdown,
                onDismissRequest = { expandedTankDropdown = false }
              ) {
                dosingTanks.forEach { tank ->
                  DropdownMenuItem(
                    text = {
                      Column {
                        Text(tank.name, fontWeight = FontWeight.Bold)
                        Text(
                          "D=${tank.diameterMm}mm, H=${tank.heightMm}mm | ${String.format(Locale.US, "%.2f", tank.calculatedLitresPerMm * 10)} L/cm",
                          style = MaterialTheme.typography.bodySmall
                        )
                      }
                    },
                    onClick = {
                      selectedTankName = tank.name
                      expandedTankDropdown = false
                    }
                  )
                }
              }
            }

            // Tank stats pill
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.SpaceAround
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text("Rate (L/cm)", style = MaterialTheme.typography.labelSmall)
                  Text(String.format(Locale.US, "%.3f", litresPerCm), fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text("Rate (L/mm)", style = MaterialTheme.typography.labelSmall)
                  Text(String.format(Locale.US, "%.4f", litresPerMm), fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text("Capacity", style = MaterialTheme.typography.labelSmall)
                  Text("${String.format(Locale.US, "%.0f", prepTank.capacityLiters)} L", fontWeight = FontWeight.Bold)
                }
              }
            }

            // Initial Level Input
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = initialLevelStr,
                onValueChange = { initialLevelStr = it },
                label = { Text("Initial Tank Level (${levelUnit.name.lowercase()})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
              )
              Row {
                FilterChip(
                  selected = levelUnit == LevelUnit.CM,
                  onClick = { levelUnit = LevelUnit.CM },
                  label = { Text("cm") }
                )
                Spacer(modifier = Modifier.width(4.dp))
                FilterChip(
                  selected = levelUnit == LevelUnit.MM,
                  onClick = { levelUnit = LevelUnit.MM },
                  label = { Text("mm") }
                )
              }
            }
          }
        }
      }

      // LIVE CALCULATION RESULTS
      item {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
          ),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Batch Quantities (Live)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
              ) {
                Text(
                  "Target: ${String.format(Locale.US, "%.2f", strengthPct)}% ($strengthBasis)",
                  color = MaterialTheme.colorScheme.onPrimary,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }

            // 4 Key Outputs Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              QuantityBox(
                title = "Total Solution",
                value = "${String.format(Locale.US, "%.1f", calculatedTotalL)} L",
                subtitle = "Total Volume",
                modifier = Modifier.weight(1f),
                highlight = true
              )
              QuantityBox(
                title = "Neat $neatChemName",
                value = "${String.format(Locale.US, "%.1f", calculatedNeatL)} L",
                subtitle = "${String.format(Locale.US, "%.1f", calculatedNeatKg)} kg (${String.format(Locale.US, "%.2f", if (neatLPerDrum > 0) calculatedNeatL / neatLPerDrum else 0.0)} drums)",
                modifier = Modifier.weight(1f)
              )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              QuantityBox(
                title = "Solvent ($solventName)",
                value = "${String.format(Locale.US, "%.1f", calculatedSolventL)} L",
                subtitle = "${String.format(Locale.US, "%.1f", calculatedSolventKg)} kg",
                modifier = Modifier.weight(1f)
              )
              QuantityBox(
                title = "Level Rise",
                value = "+${String.format(Locale.US, "%.1f", levelRiseCm)} cm",
                subtitle = "+${String.format(Locale.US, "%.0f", levelRiseMm)} mm",
                modifier = Modifier.weight(1f)
              )
            }

            HorizontalDivider()

            // Tank Level Progression
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("Tank Level Forecast", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text("Initial Level:", style = MaterialTheme.typography.bodySmall)
                Text("${String.format(Locale.US, "%.1f", initialLevelCm)} cm (${String.format(Locale.US, "%.0f", initialLevelMm)} mm)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text("Expected Final Level:", style = MaterialTheme.typography.bodySmall)
                Text("${String.format(Locale.US, "%.1f", expectedFinalLevelCm)} cm (${String.format(Locale.US, "%.0f", expectedFinalLevelMm)} mm)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text("Remaining Capacity:", style = MaterialTheme.typography.bodySmall)
                Text("${String.format(Locale.US, "%.0f", remainingCapacityL)} L (Max ${prepTank.heightMm / 10.0} cm)", style = MaterialTheme.typography.bodySmall)
              }

              if (isOverflow) {
                Surface(
                  color = MaterialTheme.colorScheme.errorContainer,
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      "OVERFLOW WARNING: Expected level (${expectedFinalLevelMm.toInt()} mm) exceeds tank height (${prepTank.heightMm.toInt()} mm)!",
                      color = MaterialTheme.colorScheme.onErrorContainer,
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }
              }
            }

            // Intermediate formula details
            Surface(
              color = MaterialTheme.colorScheme.surface,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Formulas & Calculation Breakdown", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("• Total Solution = Neat Volume / (${String.format(Locale.US, "%.4f", strengthFraction)}) = ${String.format(Locale.US, "%.1f", calculatedTotalL)} L", style = MaterialTheme.typography.bodySmall)
                Text("• Solvent Added = Total - Neat = ${String.format(Locale.US, "%.1f", calculatedTotalL)} - ${String.format(Locale.US, "%.1f", calculatedNeatL)} = ${String.format(Locale.US, "%.1f", calculatedSolventL)} L", style = MaterialTheme.typography.bodySmall)
                Text("• Level Change = Total / ${String.format(Locale.US, "%.2f", litresPerCm)} L/cm = ${String.format(Locale.US, "%.1f", levelRiseCm)} cm (${String.format(Locale.US, "%.0f", levelRiseMm)} mm)", style = MaterialTheme.typography.bodySmall)
                Text("• Final Level = ${String.format(Locale.US, "%.1f", initialLevelCm)} + ${String.format(Locale.US, "%.1f", levelRiseCm)} = ${String.format(Locale.US, "%.1f", expectedFinalLevelCm)} cm", style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }
      }

      // PREPARATION SHEET CARD (Batch Sheet Style)
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(12.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Batch Preparation Sheet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
              Button(
                onClick = {
                  val batchSheetText = buildString {
                    appendLine("=== CHEMICAL SOLUTION PREPARATION SHEET ===")
                    appendLine("Preset: $neatChemName ${String.format(Locale.US, "%.2f", strengthPct)}%")
                    appendLine("Solvent: $solventName")
                    appendLine("Target Tank: ${prepTank.name} (Rate: ${String.format(Locale.US, "%.2f", litresPerCm)} L/cm)")
                    appendLine("1. Add Neat Chemical: ${String.format(Locale.US, "%.1f", calculatedNeatL)} L (${String.format(Locale.US, "%.1f", calculatedNeatKg)} kg)")
                    appendLine("2. Add Solvent: ${String.format(Locale.US, "%.1f", calculatedSolventL)} L (${String.format(Locale.US, "%.1f", calculatedSolventKg)} kg)")
                    appendLine("3. Expected Total Volume: ${String.format(Locale.US, "%.1f", calculatedTotalL)} L")
                    appendLine("4. Tank Level: ${String.format(Locale.US, "%.1f", initialLevelCm)} cm -> ${String.format(Locale.US, "%.1f", expectedFinalLevelCm)} cm (+${String.format(Locale.US, "%.1f", levelRiseCm)} cm)")
                  }
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  clipboard.setPrimaryClip(ClipData.newPlainText("Batch Sheet", batchSheetText))
                  Toast.makeText(context, "Preparation sheet copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy Sheet", style = MaterialTheme.typography.labelSmall)
              }
            }

            Surface(
              color = MaterialTheme.colorScheme.surface,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Recipe Steps:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Text("1. Verify tank ${prepTank.name} initial dip at ${String.format(Locale.US, "%.1f", initialLevelCm)} cm (${String.format(Locale.US, "%.0f", initialLevelMm)} mm).", style = MaterialTheme.typography.bodySmall)
                Text("2. Charge ${String.format(Locale.US, "%.1f", calculatedNeatL)} L of neat $neatChemName (${String.format(Locale.US, "%.1f", calculatedNeatKg)} kg / ${String.format(Locale.US, "%.2f", if (neatLPerDrum > 0) calculatedNeatL / neatLPerDrum else 0.0)} drums).", style = MaterialTheme.typography.bodySmall)
                Text("3. Top up with $solventName solvent by ${String.format(Locale.US, "%.1f", calculatedSolventL)} L.", style = MaterialTheme.typography.bodySmall)
                Text("4. Expected final level is ${String.format(Locale.US, "%.1f", expectedFinalLevelCm)} cm (${String.format(Locale.US, "%.0f", expectedFinalLevelMm)} mm).", style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }
      }

      // ACTUAL VS EXPECTED RECONCILIATION
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Actual vs Expected Reconciliation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = actualFinalLevelStr,
                onValueChange = { actualFinalLevelStr = it },
                label = { Text("Actual Final Level (${levelUnit.name.lowercase()})") },
                placeholder = { Text("e.g. ${String.format(Locale.US, "%.1f", expectedFinalLevelCm)}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
              )
            }

            if (actualFinalLevelMm != null) {
              Surface(
                color = when {
                  Math.abs(volumeDeviationPct) <= 2.0 -> Color(0xFFE8F5E9)
                  Math.abs(volumeDeviationPct) <= 5.0 -> Color(0xFFFFF3E0)
                  else -> Color(0xFFFFEBEE)
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text("Actual Volume Added:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    Text("${String.format(Locale.US, "%.1f", actualVolumeAddedL)} L", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                  }
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text("Volume Deviation:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    Text(
                      "${if (volumeDeviationL >= 0) "+" else ""}${String.format(Locale.US, "%.1f", volumeDeviationL)} L (${String.format(Locale.US, "%+.1f", volumeDeviationPct)}%)",
                      fontWeight = FontWeight.Bold,
                      color = if (Math.abs(volumeDeviationPct) <= 2.0) Color(0xFF2E7D32) else Color(0xFFC62828),
                      style = MaterialTheme.typography.bodySmall
                    )
                  }
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text("Actual Prepared Strength:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    Text(
                      "${String.format(Locale.US, "%.2f", actualStrengthPct)}% (Target: ${String.format(Locale.US, "%.2f", strengthPct)}%)",
                      fontWeight = FontWeight.Bold,
                      color = if (Math.abs(actualStrengthPct - strengthPct) <= 0.1) Color(0xFF2E7D32) else Color(0xFFE65100),
                      style = MaterialTheme.typography.bodySmall
                    )
                  }
                }
              }
            }

            OutlinedTextField(
              value = operatorRemarks,
              onValueChange = { operatorRemarks = it },
              label = { Text("Batch Remarks / Lot Number") },
              placeholder = { Text("e.g. Drum #441, new solvent drum opened") },
              modifier = Modifier.fillMaxWidth()
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Checkbox(
                checked = setActiveBatchForChem,
                onCheckedChange = { setActiveBatchForChem = it }
              )
              Text(
                "Set as active batch for $neatChemName (shows on Daily Injection)",
                style = MaterialTheme.typography.bodySmall
              )
            }

            // Save Batch Button
            Button(
              onClick = {
                val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val newBatch = SolutionBatchRecord(
                  dateTime = nowStr,
                  presetName = activePreset?.name ?: "$neatChemName ${String.format(Locale.US, "%.1f", strengthPct)}%",
                  plannedStrengthPercent = strengthPct,
                  actualStrengthPercent = if (actualFinalLevelMm != null) actualStrengthPct else strengthPct,
                  neatVolumeL = calculatedNeatL,
                  solventVolumeL = calculatedSolventL,
                  totalVolumeL = if (actualFinalLevelMm != null && actualVolumeAddedL > 0) actualVolumeAddedL else calculatedTotalL,
                  tankName = prepTank.name,
                  initialLevelMm = initialLevelMm,
                  finalLevelMm = expectedFinalLevelMm,
                  actualFinalLevelMm = actualFinalLevelMm ?: expectedFinalLevelMm,
                  remarks = operatorRemarks,
                  chemicalName = neatChemName
                )

                val updatedBatches = listOf(newBatch) + batches
                repository.saveSolutionBatches(updatedBatches)

                if (setActiveBatchForChem) {
                  val chemIdx = chemicals.indexOfFirst {
                    it.name.contains(neatChemName, ignoreCase = true) ||
                      it.productHeaderType.contains(neatChemName, ignoreCase = true) ||
                      neatChemName.contains(it.name, ignoreCase = true)
                  }
                  if (chemIdx >= 0) {
                    val updatedChems = chemicals.toMutableList()
                    updatedChems[chemIdx] = updatedChems[chemIdx].copy(
                      activeBatchId = newBatch.id,
                      activeBatchDate = nowStr
                    )
                    repository.saveChemicals(updatedChems)
                  }
                }

                Toast.makeText(context, "Solution batch saved to history log!", Toast.LENGTH_SHORT).show()
                actualFinalLevelStr = ""
                operatorRemarks = ""
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(Icons.Default.Save, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Save Batch Record to History Log")
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(24.dp)) }
    }
  }

  // BATCH HISTORY DIALOG
  if (showHistoryDialog) {
    AlertDialog(
      onDismissRequest = { showHistoryDialog = false },
      title = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Preparation Batch History", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          if (batches.isNotEmpty()) {
            IconButton(
              onClick = {
                val intent = ExportUtils.exportSolutionBatchesCsvFile(context, batches)
                if (intent != null) {
                  context.startActivity(Intent.createChooser(intent, "Share Solution Batches CSV"))
                } else {
                  Toast.makeText(context, "Error exporting CSV", Toast.LENGTH_SHORT).show()
                }
              }
            ) {
              Icon(Icons.Default.Share, contentDescription = "Export CSV")
            }
          }
        }
      },
      text = {
        if (batches.isEmpty()) {
          Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            Text("No preparation batches saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(batches) { b ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { selectedBatchForView = b }
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(b.presetName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(b.dateTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    "Total: ${String.format(Locale.US, "%.1f", b.totalVolumeL)} L (Neat: ${String.format(Locale.US, "%.1f", b.neatVolumeL)} L, Solvent: ${String.format(Locale.US, "%.1f", b.solventVolumeL)} L)",
                    style = MaterialTheme.typography.bodySmall
                  )
                  Text(
                    "Tank: ${b.tankName} | Strength: ${String.format(Locale.US, "%.2f", b.actualStrengthPercent)}%",
                    style = MaterialTheme.typography.bodySmall
                  )
                  if (b.remarks.isNotBlank()) {
                    Text("Remarks: ${b.remarks}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                  ) {
                    TextButton(
                      onClick = {
                        val summary = ExportUtils.formatSolutionBatchSummary(b)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Batch Summary", summary))
                        Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
                      }
                    ) {
                      Text("Copy", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                      onClick = {
                        val updated = batches.filter { it.id != b.id }
                        repository.saveSolutionBatches(updated)
                        Toast.makeText(context, "Batch deleted", Toast.LENGTH_SHORT).show()
                      }
                    ) {
                      Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showHistoryDialog = false }) {
          Text("Close")
        }
      }
    )
  }

  // PRESET EDITOR / CREATOR DIALOG
  if (showPresetEditorDialog) {
    val isEditing = editingPreset != null
    var pName by remember { mutableStateOf(editingPreset?.name ?: "Custom Solution") }
    var pChem by remember { mutableStateOf(editingPreset?.neatChemicalName ?: "DMDS") }
    var pDensity by remember { mutableStateOf((editingPreset?.neatDensityKgL ?: 1.06).toString()) }
    var pSolvent by remember { mutableStateOf(editingPreset?.solventName ?: "HN+LN") }
    var pStrength by remember { mutableStateOf((editingPreset?.strengthPercent ?: 10.0).toString()) }
    var pTank by remember { mutableStateOf(editingPreset?.defaultTankName ?: (dosingTanks.firstOrNull()?.name ?: "Water Tank")) }
    var pDrumKg by remember { mutableStateOf((editingPreset?.drumSizeKg ?: 200.0).toString()) }

    AlertDialog(
      onDismissRequest = { showPresetEditorDialog = false },
      title = { Text(if (isEditing) "Edit Preset" else "Add Solution Preset", fontWeight = FontWeight.Bold) },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = pName,
            onValueChange = { pName = it },
            label = { Text("Preset Display Name") },
            modifier = Modifier.fillMaxWidth()
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = pChem,
              onValueChange = { pChem = it },
              label = { Text("Neat Chemical") },
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = pDensity,
              onValueChange = { pDensity = it },
              label = { Text("Density (kg/L)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f)
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = pSolvent,
              onValueChange = { pSolvent = it },
              label = { Text("Solvent") },
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = pStrength,
              onValueChange = { pStrength = it },
              label = { Text("Strength (%)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f)
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = pDrumKg,
              onValueChange = { pDrumKg = it },
              label = { Text("Drum Size (kg)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = pTank,
              onValueChange = { pTank = it },
              label = { Text("Default Tank") },
              modifier = Modifier.weight(1f)
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val sVal = pStrength.toDoubleOrNull() ?: 10.0
            val dVal = pDensity.toDoubleOrNull() ?: 1.0
            val drumVal = pDrumKg.toDoubleOrNull() ?: 200.0
            val currentList = presets.toMutableList()

            if (isEditing) {
              val idx = currentList.indexOfFirst { it.id == editingPreset!!.id }
              if (idx >= 0) {
                currentList[idx] = editingPreset!!.copy(
                  name = pName,
                  neatChemicalName = pChem,
                  neatDensityKgL = dVal,
                  solventName = pSolvent,
                  strengthPercent = sVal,
                  defaultTankName = pTank,
                  drumSizeKg = drumVal,
                  isDefault = false
                )
              }
            } else {
              val newP = SolutionPreset(
                name = pName,
                neatChemicalName = pChem,
                neatDensityKgL = dVal,
                solventName = pSolvent,
                strengthPercent = sVal,
                basisString = "CRU feed (kg/hr)",
                defaultTankName = pTank,
                drumSizeKg = drumVal,
                isDefault = false
              )
              currentList.add(newP)
              selectedPresetId = newP.id
            }

            repository.saveSolutionPresets(currentList)
            showPresetEditorDialog = false
            Toast.makeText(context, "Preset saved!", Toast.LENGTH_SHORT).show()
          }
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showPresetEditorDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  if (showClearConfirm) {
    AlertDialog(
      onDismissRequest = { showClearConfirm = false },
      title = { Text("Clear Draft / Reset?") },
      text = { Text("Reset inputs and discard the current draft for Solution Making?") },
      confirmButton = {
        Button(onClick = {
          repository.deleteDraft("solution")
          inputNeatValStr = "1.0"
          inputTargetTotalValStr = "1000.0"
          inputTargetLevelRiseStr = "50.0"
          initialLevelStr = "30.0"
          showClearConfirm = false
        }) {
          Text("Reset")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showClearConfirm = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
fun QuantityBox(
  title: String,
  value: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  highlight: Boolean = false
) {
  Surface(
    color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(8.dp),
    tonalElevation = 2.dp,
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
      )
      Text(
        subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
