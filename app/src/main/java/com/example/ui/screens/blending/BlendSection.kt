package com.example.ui.screens.blending

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.engine.Calculators
import com.example.engine.ExportUtils
import com.example.engine.UnitUtils
import com.example.engine.VcfEngine
import com.example.ui.components.AuthSession
import com.example.ui.components.PasswordDialog
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class StageInputState(
  val stageId: String,
  val stageName: String,
  val componentName: String,
  isVolumeInput: Boolean = false,
  dipStr: String = "",
  volumeStr: String = "",
  dateTimeStr: String = "",
  densityStr: String = "750.0",
  tempStr: String = "25.0",
  isSynced: Boolean = false,
  syncLabel: String = ""
) {
  var isVolumeInput by mutableStateOf(isVolumeInput)
  var dipStr by mutableStateOf(dipStr)
  var volumeStr by mutableStateOf(volumeStr)
  var dateTimeStr by mutableStateOf(dateTimeStr)
  var densityStr by mutableStateOf(densityStr)
  var tempStr by mutableStateOf(tempStr)
  var isSynced by mutableStateOf(isSynced)
  var syncLabel by mutableStateOf(syncLabel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlendSection(
  repository: TankRepository,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val rawTanks by repository.tanks.collectAsState()
  val tanks = remember(rawTanks) { rawTanks.filter { it.type != TankType.N2_STORAGE } }
  val settings by repository.settings.collectAsState()
  val programs by repository.blendPrograms.collectAsState()
  val blendRecords by repository.blendRecords.collectAsState()
  val editLogs by repository.editLogs.collectAsState()
  val haptics = com.example.engine.rememberHapticController()

  // Selected program
  var selectedProgramId by remember(programs) {
    mutableStateOf(programs.firstOrNull()?.id ?: "")
  }
  val selectedProgram = programs.find { it.id == selectedProgramId } ?: programs.firstOrNull()

  // General blend settings
  var calcMode by remember { mutableStateOf(CalculationMode.NORMAL) }
  var batchNumber by remember { mutableStateOf("") }
  var remarks by remember { mutableStateOf("") }
  var editingRecordId by remember { mutableStateOf<String?>(null) }

  // Dialogs
  var showProgramEditor by remember { mutableStateOf(false) }
  var programToEdit by remember { mutableStateOf<BlendProgram?>(null) }
  var showHistorySheet by remember { mutableStateOf(false) }
  var showReportDialog by remember { mutableStateOf(false) }
  var reportRecordToShow by remember { mutableStateOf<BlendRecord?>(null) }
  var showEditLogDialog by remember { mutableStateOf(false) }

  // Password guard
  var showPasswordDialog by remember { mutableStateOf(false) }
  var pendingAuthAction by remember { mutableStateOf<(() -> Unit)?>(null) }

  fun requireAuth(action: () -> Unit) {
    if (AuthSession.isUnlocked()) {
      action()
    } else {
      pendingAuthAction = action
      showPasswordDialog = true
    }
  }

  // Stage inputs state
  val stageStates = remember(selectedProgram?.id, editingRecordId) {
    mutableStateListOf<StageInputState>()
  }

  // Initialize stage states when program changes or upon fresh load
  LaunchedEffect(selectedProgram?.id, editingRecordId) {
    if (selectedProgram != null && editingRecordId == null) {
      stageStates.clear()
      val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
      val defaultDensity = repository.getTankDensity(selectedProgram.tankName)

      // Auto-fill stage 1 dip from last saved final dip
      val lastSaved = repository.getLastSavedFinalDip(selectedProgram.tankName)

      selectedProgram.stages.forEachIndexed { index, stage ->
        val isFirst = index == 0
        val prefillDip = if (isFirst && lastSaved != null) String.format(Locale.US, "%.1f", lastSaved.first) else if (isFirst) "120.0" else ""
        stageStates.add(
          StageInputState(
            stageId = stage.id,
            stageName = stage.stageName,
            componentName = stage.componentName,
            isVolumeInput = false,
            dipStr = prefillDip,
            volumeStr = "",
            dateTimeStr = now,
            densityStr = String.format(Locale.US, "%.1f", defaultDensity),
            tempStr = "25.0",
            isSynced = isFirst && lastSaved != null,
            syncLabel = if (isFirst && lastSaved != null) lastSaved.second else ""
          )
        )
      }
    }
  }

  val selectedTank = tanks.find { it.name.equals(selectedProgram?.tankName ?: "", ignoreCase = true) }
    ?: tanks.firstOrNull()

  // Evaluate volumes and differences live
  val stageEvaluations = stageStates.mapIndexed { index, sState ->
    val dipMm = sState.dipStr.toDoubleOrNull() ?: 0.0
    val dipRes = selectedTank?.let {
      Calculators.calculateDipVolume(it, dipMm, settings.invalidInputThreshold)
    }

    val naturalVolumeL = if (sState.isVolumeInput) {
      val volTyped = sState.volumeStr.toDoubleOrNull() ?: 0.0
      UnitUtils.toLitres(volTyped, settings.unit)
    } else {
      dipRes?.totalVolume ?: 0.0
    }

    // 30°C correction
    val rho = sState.densityStr.toDoubleOrNull() ?: 750.0
    val temp = sState.tempStr.toDoubleOrNull() ?: 25.0
    val vcfNat = VcfEngine.calculateVcf(rho, temp, settings.vcfTableType, settings.roundVcfTo4Decimals, settings.roundTempTo025)
    val vcf30 = VcfEngine.calculateVcf(rho, 30.0, settings.vcfTableType, settings.roundVcfTo4Decimals, settings.roundTempTo025)
    val vol30L = if (vcf30.vcf > 0) naturalVolumeL * vcfNat.vcf / vcf30.vcf else naturalVolumeL

    val effectiveVolL = if (calcMode == CalculationMode.TEMP_30C) vol30L else naturalVolumeL

    object {
      val naturalVol = naturalVolumeL
      val vol30 = vol30L
      val effectiveVol = effectiveVolL
      val warning = dipRes?.warning
      val isOutOfRange = dipRes?.warning?.contains("Extrapolating", ignoreCase = true) == true
      val vcfNatVal = vcfNat.vcf
      val vcf30Val = vcf30.vcf
    }
  }

  // Transferred amounts between stages
  val componentTransfers = stageStates.mapIndexedNotNull { index, stage ->
    if (index == 0) null
    else {
      val currentEval = stageEvaluations.getOrNull(index)
      val prevEval = stageEvaluations.getOrNull(index - 1)
      val currentVol = currentEval?.effectiveVol ?: 0.0
      val prevVol = prevEval?.effectiveVol ?: 0.0
      val naturalTransferred = (currentEval?.naturalVol ?: 0.0) - (prevEval?.naturalVol ?: 0.0)
      val transferred30 = (currentEval?.vol30 ?: 0.0) - (prevEval?.vol30 ?: 0.0)
      val transferred = currentVol - prevVol

      val warn = if (transferred < 0) {
        "Negative transfer: volume decreased by ${UnitUtils.formatVolume(abs(transferred), settings.unit, settings = settings)}"
      } else null

      BlendComponentResult(
        stageName = stage.stageName,
        componentName = stage.componentName,
        transferredL = transferred,
        naturalTransferredL = naturalTransferred,
        transferred30L = if (calcMode == CalculationMode.TEMP_30C) transferred30 else null,
        warning = warn
      )
    }
  }

  val finalVolumeL = stageEvaluations.lastOrNull()?.naturalVol ?: 0.0
  val finalVolume30L = stageEvaluations.lastOrNull()?.vol30 ?: 0.0
  val finalDisplayVol = if (calcMode == CalculationMode.TEMP_30C) finalVolume30L else finalVolumeL

  // Visual markers for tank visual
  val visualMarkers = stageStates.mapIndexed { index, sState ->
    val eval = stageEvaluations.getOrNull(index)
    val vol = if (calcMode == CalculationMode.TEMP_30C) eval?.vol30 ?: 0.0 else eval?.naturalVol ?: 0.0
    val color = when (index) {
      0 -> Color(0xFF64748B) // Initial gray
      1 -> Color(0xFF0284C7) // Blue
      2 -> Color(0xFF10B981) // Green
      3 -> Color(0xFFF59E0B) // Amber
      else -> Color(0xFF8B5CF6) // Purple
    }
    StageVisualMarker(
      stageName = sState.stageName,
      componentName = sState.componentName,
      volumeL = vol,
      dipMm = sState.dipStr.toDoubleOrNull() ?: 0.0,
      color = color
    )
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. Program Selector & Management Bar
    item {
      Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Blend Program", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row {
              IconButton(onClick = { showEditLogDialog = true }) {
                Icon(Icons.Default.HistoryEdu, contentDescription = "Edit Audit Log", tint = MaterialTheme.colorScheme.primary)
              }
              IconButton(onClick = { showHistorySheet = true }) {
                Icon(Icons.Default.History, contentDescription = "Blend History", tint = MaterialTheme.colorScheme.primary)
              }
              IconButton(onClick = {
                requireAuth {
                  programToEdit = null
                  showProgramEditor = true
                }
              }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Add, contentDescription = "Add Program")
                  Icon(Icons.Default.Lock, contentDescription = "Protected", modifier = Modifier.size(10.dp))
                }
              }
            }
          }

          // Program selector cards / chips
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            programs.forEach { prog ->
              val isSelected = prog.id == selectedProgramId
              FilterChip(
                selected = isSelected,
                onClick = {
                  haptics.trigger(com.example.engine.HapticEvent.MODE_SWITCH)
                  selectedProgramId = prog.id
                  editingRecordId = null
                },
                label = {
                  Text("${prog.name} (${prog.tankName})", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                },
                leadingIcon = if (isSelected) {
                  { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
              )
            }
          }

          if (selectedProgram != null) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Blending Tank: ${selectedProgram.tankName}",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
              )
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                  requireAuth {
                    programToEdit = selectedProgram
                    showProgramEditor = true
                  }
                }) {
                  Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Edit Program", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = {
                  requireAuth {
                    val copy = repository.duplicateBlendProgram(selectedProgram)
                    selectedProgramId = copy.id
                    repository.addEditLog("DUPLICATE_PROGRAM", copy.name, "Duplicated from ${selectedProgram.name}")
                    Toast.makeText(context, "Duplicated ${copy.name}", Toast.LENGTH_SHORT).show()
                  }
                }) {
                  Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Duplicate", style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }
        }
      }
    }

    // 1B. Compact Quick Dip Estimation (Initial Dip + Required Transfer Vol -> Estimated Dip)
    item {
      BlendDipEstimatorCard(
        tank = selectedTank,
        stage1DipStr = stageStates.firstOrNull()?.dipStr ?: "",
        settings = settings
      )
    }

    // 2. Mode, Batch No, and Remarks Row
    item {
      Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Calculation Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              FilterChip(
                selected = calcMode == CalculationMode.NORMAL,
                onClick = {
                  haptics.trigger(com.example.engine.HapticEvent.MODE_SWITCH)
                  calcMode = CalculationMode.NORMAL
                },
                label = { Text("Natural") }
              )
              FilterChip(
                selected = calcMode == CalculationMode.TEMP_30C,
                onClick = {
                  haptics.trigger(com.example.engine.HapticEvent.MODE_SWITCH)
                  calcMode = CalculationMode.TEMP_30C
                },
                label = { Text("30 °C") }
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedTextField(
              value = batchNumber,
              onValueChange = { batchNumber = it },
              label = { Text("Batch No (Optional)") },
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = remarks,
              onValueChange = { remarks = it },
              label = { Text("Remarks (Optional)") },
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
          }

          if (editingRecordId != null) {
            Surface(
              color = MaterialTheme.colorScheme.tertiaryContainer,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("Editing saved record (Re-save overwrites)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                  editingRecordId = null
                  Toast.makeText(context, "Switched to new record mode", Toast.LENGTH_SHORT).show()
                }) {
                  Text("New Blend")
                }
              }
            }
          }
        }
      }
    }

    // 3. Animated Tank Level Profile Visual
    item {
      BlendTankVisual(
        tank = selectedTank,
        markers = visualMarkers,
        currentUnit = settings.unit,
        settings = settings
      )
    }

    // 4. Stage Entry Cards (In Order)
    itemsIndexed(stageStates) { index, stageState ->
      val eval = stageEvaluations.getOrNull(index)
      val transferResult = if (index > 0) componentTransfers.getOrNull(index - 1) else null

      Card(
        colors = CardDefaults.cardColors(
          containerColor = if (index == 0) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Stage Title Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text(
                text = "Stage ${index + 1}: ${stageState.stageName}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
              )
              if (stageState.isSynced) {
                Surface(
                  color = MaterialTheme.colorScheme.secondaryContainer,
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                  ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(12.dp))
                    Text("Synced", style = MaterialTheme.typography.labelSmall)
                  }
                }
              }
            }

            // Dip vs Volume TOV toggle
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              FilterChip(
                selected = !stageState.isVolumeInput,
                onClick = { stageState.isVolumeInput = false },
                label = { Text("Dip", style = MaterialTheme.typography.labelSmall) }
              )
              FilterChip(
                selected = stageState.isVolumeInput,
                onClick = { stageState.isVolumeInput = true },
                label = { Text("Volume", style = MaterialTheme.typography.labelSmall) }
              )
            }
          }

          if (index > 0 && stageState.componentName.isNotBlank()) {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text(
                text = "Component Added: ${stageState.componentName}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
            }
          }

          // Input Row (Dip mm / Volume TOV + Date/Time)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            if (!stageState.isVolumeInput) {
              OutlinedTextField(
                value = stageState.dipStr,
                onValueChange = {
                  stageState.dipStr = it
                  stageState.isSynced = false
                },
                label = { Text("Dip (mm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
              )
            } else {
              OutlinedTextField(
                value = stageState.volumeStr,
                onValueChange = { stageState.volumeStr = it },
                label = { Text("Volume (${UnitUtils.unitLabel(settings.unit)})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
              )
            }

            OutlinedTextField(
              value = stageState.dateTimeStr,
              onValueChange = { stageState.dateTimeStr = it },
              label = { Text("Reading Date & Time") },
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
          }

          // In 30°C mode: Density & Temperature inputs
          if (calcMode == CalculationMode.TEMP_30C) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              OutlinedTextField(
                value = stageState.densityStr,
                onValueChange = { stageState.densityStr = it },
                label = { Text("Density @15°C (kg/m³)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
              )
              OutlinedTextField(
                value = stageState.tempStr,
                onValueChange = { stageState.tempStr = it },
                label = { Text("Temp (°C)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
              )
            }
          }

          // Live stage volume breakdown
          val stageVol = eval?.effectiveVol ?: 0.0
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Stage Volume: ${UnitUtils.formatVolume(stageVol, settings.unit, settings = settings)}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
              )
              if (calcMode == CalculationMode.TEMP_30C) {
                Text(
                  text = "Natural: ${UnitUtils.formatVolume(eval?.naturalVol ?: 0.0, settings.unit, settings = settings)} | VCF(t): ${eval?.vcfNatVal ?: 1.0} | VCF(30): ${eval?.vcf30Val ?: 1.0}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            // Transfer from previous stage (Reformate / Light Naphtha transferred)
            if (transferResult != null) {
              Column(horizontalAlignment = Alignment.End) {
                val label = if (transferResult.componentName.isNotBlank()) transferResult.componentName else transferResult.stageName
                Text(
                  text = "$label Transferred:",
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = UnitUtils.formatVolume(transferResult.transferredL, settings.unit, settings = settings),
                  fontWeight = FontWeight.Bold,
                  color = if (transferResult.transferredL >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.titleMedium
                )
              }
            }
          }

          // Warnings (Amber non-blocking)
          if (eval?.isOutOfRange == true) {
            Surface(
              color = Color(0xFFFEF3C7),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                Text(
                  text = eval.warning ?: "Dip is outside calibration table range.",
                  color = Color(0xFF92400E),
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }

          if (transferResult?.warning != null) {
            Surface(
              color = Color(0xFFFEE2E2),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Text(
                  text = transferResult.warning,
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      }
    }

    // 5. Cross-Check Summary Card
    item {
      Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Live Blending Summary & Cross-Check",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          HorizontalDivider()

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Final volume of tank:", fontWeight = FontWeight.Bold)
            Text(
              text = UnitUtils.formatVolume(finalDisplayVol, settings.unit, settings = settings),
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }

          componentTransfers.forEach { comp ->
            val label = if (comp.componentName.isNotBlank()) comp.componentName else comp.stageName
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("$label transferred amount:", fontWeight = FontWeight.Medium)
              Text(
                text = UnitUtils.formatVolume(comp.transferredL, settings.unit, settings = settings),
                fontWeight = FontWeight.SemiBold
              )
            }
          }

          HorizontalDivider()

          // Intermediate numbers cross-check
          Text("Intermediate Readings Breakdown:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
          stageStates.forEachIndexed { idx, s ->
            val eval = stageEvaluations.getOrNull(idx)
            val dipText = if (!s.isVolumeInput) "${s.dipStr} mm" else "Direct TOV"
            val vol = eval?.effectiveVol ?: 0.0
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("• ${s.stageName} ($dipText):", style = MaterialTheme.typography.bodySmall)
              Text(UnitUtils.formatVolume(vol, settings.unit, settings = settings), style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      }
    }

    // 6. Action Buttons: Save & View Report
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = {
            if (selectedProgram == null) {
              Toast.makeText(context, "Please select or create a blend program.", Toast.LENGTH_SHORT).show()
              return@Button
            }

            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val stageReadings = stageStates.mapIndexed { idx, s ->
              val eval = stageEvaluations.getOrNull(idx)
              BlendStageReading(
                stageId = s.stageId,
                stageName = s.stageName,
                componentName = s.componentName,
                isVolumeInput = s.isVolumeInput,
                dipMm = s.dipStr.toDoubleOrNull() ?: 0.0,
                volumeNaturalL = eval?.naturalVol ?: 0.0,
                dateTime = s.dateTimeStr.ifBlank { now },
                density15 = if (calcMode == CalculationMode.TEMP_30C) s.densityStr.toDoubleOrNull() else null,
                temp = if (calcMode == CalculationMode.TEMP_30C) s.tempStr.toDoubleOrNull() else null,
                vcfNatural = if (calcMode == CalculationMode.TEMP_30C) eval?.vcfNatVal else null,
                vcf30 = if (calcMode == CalculationMode.TEMP_30C) eval?.vcf30Val else null,
                volume30L = if (calcMode == CalculationMode.TEMP_30C) eval?.vol30 else null
              )
            }

            val record = BlendRecord(
              id = editingRecordId ?: UUID.randomUUID().toString(),
              programId = selectedProgram.id,
              programName = selectedProgram.name,
              tankName = selectedProgram.tankName,
              dateTime = stageStates.lastOrNull()?.dateTimeStr?.ifBlank { now } ?: now,
              mode = calcMode,
              stageReadings = stageReadings,
              finalVolumeL = finalVolumeL,
              finalVolume30L = if (calcMode == CalculationMode.TEMP_30C) finalVolume30L else null,
              componentTransfers = componentTransfers,
              batchNumber = batchNumber.trim(),
              remarks = remarks.trim()
            )

            repository.addOrUpdateBlendRecord(record)
            editingRecordId = record.id
            reportRecordToShow = record
            haptics.trigger(com.example.engine.HapticEvent.SUCCESS)
            Toast.makeText(context, "Blend operation saved to history log!", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.Save, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (editingRecordId != null) "Re-Save Blend" else "Save Blend Record")
        }

        OutlinedButton(
          onClick = {
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val stageReadings = stageStates.mapIndexed { idx, s ->
              val eval = stageEvaluations.getOrNull(idx)
              BlendStageReading(
                stageId = s.stageId,
                stageName = s.stageName,
                componentName = s.componentName,
                isVolumeInput = s.isVolumeInput,
                dipMm = s.dipStr.toDoubleOrNull() ?: 0.0,
                volumeNaturalL = eval?.naturalVol ?: 0.0,
                dateTime = s.dateTimeStr.ifBlank { now },
                density15 = if (calcMode == CalculationMode.TEMP_30C) s.densityStr.toDoubleOrNull() else null,
                temp = if (calcMode == CalculationMode.TEMP_30C) s.tempStr.toDoubleOrNull() else null,
                vcfNatural = if (calcMode == CalculationMode.TEMP_30C) eval?.vcfNatVal else null,
                vcf30 = if (calcMode == CalculationMode.TEMP_30C) eval?.vcf30Val else null,
                volume30L = if (calcMode == CalculationMode.TEMP_30C) eval?.vol30 else null
              )
            }
            reportRecordToShow = BlendRecord(
              id = editingRecordId ?: UUID.randomUUID().toString(),
              programId = selectedProgram?.id ?: "",
              programName = selectedProgram?.name ?: "Blend Program",
              tankName = selectedProgram?.tankName ?: "Tank",
              dateTime = stageStates.lastOrNull()?.dateTimeStr?.ifBlank { now } ?: now,
              mode = calcMode,
              stageReadings = stageReadings,
              finalVolumeL = finalVolumeL,
              finalVolume30L = if (calcMode == CalculationMode.TEMP_30C) finalVolume30L else null,
              componentTransfers = componentTransfers,
              batchNumber = batchNumber.trim(),
              remarks = remarks.trim()
            )
            showReportDialog = true
          },
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.Assessment, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("View Report")
        }
      }
    }
  }

  // Program Editor Dialog
  if (showProgramEditor) {
    BlendProgramEditorDialog(
      initialProgram = programToEdit,
      allTanks = tanks,
      onDismiss = { showProgramEditor = false },
      onSave = { updatedProgram ->
        val isNew = programToEdit == null
        repository.addOrUpdateBlendProgram(updatedProgram)
        selectedProgramId = updatedProgram.id
        showProgramEditor = false
        val action = if (isNew) "CREATE_PROGRAM" else "UPDATE_PROGRAM"
        val details = "${if (isNew) "Created" else "Updated"} program ${updatedProgram.name} for tank ${updatedProgram.tankName} with ${updatedProgram.stages.size} stages"
        repository.addEditLog(action, updatedProgram.name, details)
        Toast.makeText(context, "Saved program ${updatedProgram.name}", Toast.LENGTH_SHORT).show()
      },
      onDelete = { id ->
        val prog = programs.find { it.id == id }
        repository.deleteBlendProgram(id)
        showProgramEditor = false
        prog?.let {
          repository.addEditLog("DELETE_PROGRAM", it.name, "Deleted blend program ${it.name}")
        }
        Toast.makeText(context, "Deleted program", Toast.LENGTH_SHORT).show()
      }
    )
  }

  // Report Dialog
  if (showReportDialog && reportRecordToShow != null) {
    BlendReportDialog(
      record = reportRecordToShow!!,
      currentUnit = settings.unit,
      settings = settings,
      onDismiss = { showReportDialog = false }
    )
  }

  // History Sheet
  if (showHistorySheet) {
    BlendHistorySheet(
      records = blendRecords,
      allPrograms = programs,
      allTanks = tanks,
      currentUnit = settings.unit,
      settings = settings,
      onDismiss = { showHistorySheet = false },
      onOpenRecord = { rec ->
        editingRecordId = rec.id
        calcMode = rec.mode
        batchNumber = rec.batchNumber
        remarks = rec.remarks
        val prog = programs.find { it.id == rec.programId }
        if (prog != null) {
          selectedProgramId = prog.id
        }

        stageStates.clear()
        rec.stageReadings.forEachIndexed { idx, sr ->
          stageStates.add(
            StageInputState(
              stageId = sr.stageId,
              stageName = sr.stageName,
              componentName = sr.componentName,
              isVolumeInput = sr.isVolumeInput,
              dipStr = if (sr.dipMm > 0) String.format(Locale.US, "%.1f", sr.dipMm) else "",
              volumeStr = if (sr.volumeNaturalL > 0) String.format(Locale.US, "%.1f", UnitUtils.toDisplay(sr.volumeNaturalL, settings.unit)) else "",
              dateTimeStr = sr.dateTime,
              densityStr = String.format(Locale.US, "%.1f", sr.density15 ?: 750.0),
              tempStr = String.format(Locale.US, "%.1f", sr.temp ?: 25.0),
              isSynced = false,
              syncLabel = ""
            )
          )
        }
        showHistorySheet = false
        Toast.makeText(context, "Loaded ${rec.programName} (${rec.dateTime})", Toast.LENGTH_SHORT).show()
      },
      onDeleteRecord = { id ->
        repository.deleteBlendRecord(id)
        Toast.makeText(context, "Deleted blend record", Toast.LENGTH_SHORT).show()
      }
    )
  }

  // Edit Log Dialog
  if (showEditLogDialog) {
    EditLogDialog(
      logs = editLogs,
      onDismiss = { showEditLogDialog = false }
    )
  }

  // Password Dialog
  if (showPasswordDialog) {
    PasswordDialog(
      storedHash = settings.passwordHash,
      onDismiss = {
        showPasswordDialog = false
        pendingAuthAction = null
      },
      onSuccess = {
        showPasswordDialog = false
        pendingAuthAction?.invoke()
        pendingAuthAction = null
      }
    )
  }
}
