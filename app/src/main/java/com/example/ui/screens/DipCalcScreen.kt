package com.example.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.engine.*
import com.example.ui.components.AppScreen
import com.example.ui.components.IndustrialKeypadDock
import com.example.ui.components.NumberInput
import com.example.ui.components.ProductBadge
import com.example.ui.components.SunlightModeToggleAction
import com.example.ui.components.TankPickerSheet
import com.example.ui.theme.LocalSunlightMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DipCalcScreen(
  repository: TankRepository,
  requestedSubMode: Int? = null,
  onSubModeHandled: () -> Unit = {}
) {
  val context = LocalContext.current
  val rawTanks by repository.tanks.collectAsState()
  val tanks = remember(rawTanks) { rawTanks.filter { it.type != com.example.data.TankType.N2_STORAGE } }
  val products by repository.products.collectAsState()
  val settings by repository.settings.collectAsState()

  // 0 = Dip to Volume, 1 = Volume to Dip, 2 = Bulk Dip Sheet
  var selectedSubMode by remember { mutableIntStateOf(0) }
  val modeTitles = listOf("Dip → Vol", "Vol → Dip", "Bulk Sheet")

  LaunchedEffect(requestedSubMode) {
    if (requestedSubMode != null && requestedSubMode in 0..2) {
      selectedSubMode = requestedSubMode
      onSubModeHandled()
    }
  }

  var showCalibrationEditor by remember { mutableStateOf(false) }
  var calibrationTankName by remember { mutableStateOf<String?>(null) }

  androidx.activity.compose.BackHandler(enabled = showCalibrationEditor) {
    showCalibrationEditor = false
  }

  if (showCalibrationEditor) {
    CalibrationInspectorScreen(
      repository = repository,
      initialTankName = calibrationTankName ?: tanks.firstOrNull()?.name,
      onBack = { showCalibrationEditor = false }
    )
    return
  }

  Scaffold(
    contentWindowInsets = WindowInsets.statusBars,
    topBar = {
      Column {
        TopAppBar(
          title = {
            Text(
              text = "Dip & Volume",
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
          },
          actions = {
            SunlightModeToggleAction(modifier = Modifier.padding(end = 6.dp))
            UnitQuickSwitchChip(
              currentUnit = settings.unit,
              onUnitChange = { repository.updateSettings(settings.copy(unit = it)) },
              modifier = Modifier.padding(end = 4.dp)
            )
            IconButton(onClick = {
              calibrationTankName = tanks.firstOrNull()?.name
              showCalibrationEditor = true
            }) {
              Icon(Icons.Default.TableChart, contentDescription = "Edit Calibration & Fractions")
            }
          }
        )
        HorizontalDivider(
          thickness = 0.5.dp,
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
      }
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
    ) {
      // Top Segmented Mode Selector
      SingleChoiceSegmentedButtonRow(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 6.dp)
      ) {
        modeTitles.forEachIndexed { index, title ->
          SegmentedButton(
            selected = selectedSubMode == index,
            onClick = { selectedSubMode = index },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = modeTitles.size)
          ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
          }
        }
      }

      when (selectedSubMode) {
        0 -> SingleDipToVolumeContent(
          tanks = tanks,
          products = products,
          settings = settings,
          repository = repository,
          onOpenCalibration = { tName ->
            calibrationTankName = tName
            showCalibrationEditor = true
          }
        )
        1 -> SingleVolumeToDipContent(
          tanks = tanks,
          products = products,
          settings = settings,
          onOpenCalibration = { tName ->
            calibrationTankName = tName
            showCalibrationEditor = true
          }
        )
        2 -> BulkDipSheetContent(
          tanks = tanks,
          products = products,
          settings = settings,
          repository = repository,
          onOpenCalibration = { tName ->
            calibrationTankName = tName
            showCalibrationEditor = true
          }
        )
      }
    }
  }
}

// ==========================================
// SUB-MODE 0: DIP TO VOLUME (Single Tank)
// ==========================================
@Composable
private fun SingleDipToVolumeContent(
  tanks: List<Tank>,
  products: List<Product>,
  settings: AppSettings,
  repository: TankRepository,
  onOpenCalibration: (String) -> Unit
) {
  val context = LocalContext.current
  var selectedTankName by remember(tanks) { mutableStateOf(tanks.firstOrNull()?.name ?: "") }
  var dipInput by remember { mutableStateOf("154.5") }
  var interpolateDecimal by remember { mutableStateOf(false) }
  var showPicker by remember { mutableStateOf(false) }
  var draftSavedIndicator by remember { mutableStateOf(false) }
  var showClearConfirm by remember { mutableStateOf(false) }

  val selectedTank = tanks.find { it.name == selectedTankName } ?: tanks.firstOrNull()
  val tankProduct = products.find { it.id == selectedTank?.productId }
  val dipVal = dipInput.toDoubleOrNull() ?: 0.0

  // Restore draft
  LaunchedEffect(Unit) {
    val draft = repository.getDraft("dip_calc")
    if (draft != null) {
      try {
        val json = JSONObject(draft.payloadJson)
        val tank = json.optString("tank", "")
        val dip = json.optString("dip", "")
        val interp = json.optBoolean("interpolate", false)
        if (tank.isNotBlank()) selectedTankName = tank
        if (dip.isNotBlank()) dipInput = dip
        interpolateDecimal = interp
      } catch (_: Exception) {}
    }
  }

  // Auto-save draft
  LaunchedEffect(selectedTankName, dipInput, interpolateDecimal) {
    delay(400L)
    val payload = JSONObject().apply {
      put("tank", selectedTankName)
      put("dip", dipInput)
      put("interpolate", interpolateDecimal)
    }.toString()
    repository.saveDraft("dip_calc", "default", payload, "$selectedTankName @ $dipInput mm")
    draftSavedIndicator = true
    delay(1500L)
    draftSavedIndicator = false
  }

  val calcResult = remember(selectedTank, dipVal, settings, interpolateDecimal) {
    if (selectedTank != null) {
      Calculators.calculateDipVolume(
        tank = selectedTank,
        inputDip = dipVal,
        threshold = settings.invalidInputThreshold,
        interpolateDecimal = interpolateDecimal
      )
    } else null
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Selected Tank", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (draftSavedIndicator) {
          AssistChip(
            onClick = {},
            label = { Text("Draft saved", style = MaterialTheme.typography.labelSmall) },
            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
          )
        }
      }
      Spacer(modifier = Modifier.height(4.dp))
      OutlinedCard(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (selectedTank != null) "${selectedTank.name} (${selectedTank.type})" else "Select Tank...", fontWeight = FontWeight.Bold)
            if (tankProduct != null) {
              ProductBadge(name = tankProduct.name, shortCode = tankProduct.shortCode)
            }
          }
          Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
      }
      if (selectedTank != null) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
          horizontalArrangement = Arrangement.End
        ) {
          TextButton(
            onClick = { onOpenCalibration(selectedTank.name) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
          ) {
            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Edit Calibration / Fractions", style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    }

    item {
      NumberInput(
        value = dipInput,
        onValueChange = { dipInput = it },
        label = "Enter Dip",
        suffix = "mm"
      )
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Linearly interpolate decimal mm?", style = MaterialTheme.typography.bodyMedium)
        Switch(
          checked = interpolateDecimal,
          onCheckedChange = { interpolateDecimal = it }
        )
      }
    }

    if (calcResult != null) {
      item {
        TankVisualCard(calcResult = calcResult, tankName = selectedTank?.name ?: "")
      }

      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Calculation Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()

            if (calcResult.invalidInput) {
              Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("INVALID INPUT: Base volume <= threshold (${settings.invalidInputThreshold} L)", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                }
              }
            }

            if (calcResult.warning != null) {
              Text(calcResult.warning!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (calcResult.interpolationNote != null) {
              Text(calcResult.interpolationNote!!, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            }

            if (calcResult.usedDirectMmLookup) {
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = "Direct 1 mm Calibration (No Fraction Applied)",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Calibrated Dip:")
                Text("${calcResult.baseDip} mm", fontWeight = FontWeight.SemiBold)
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Direct Table Volume:")
                Text(UnitUtils.formatVolume(calcResult.baseVolume, settings.unit, settings = settings), fontWeight = FontWeight.SemiBold)
              }
            } else {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Base Dip:")
                Text("${calcResult.baseDip} mm", fontWeight = FontWeight.SemiBold)
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Base Volume:")
                Text(UnitUtils.formatVolume(calcResult.baseVolume, settings.unit, settings = settings), fontWeight = FontWeight.SemiBold)
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Fraction mm:")
                Text("${calcResult.fracMm} mm", fontWeight = FontWeight.SemiBold)
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Fraction Volume:")
                Text(UnitUtils.formatVolume(calcResult.fracVolume, settings.unit, settings = settings), fontWeight = FontWeight.SemiBold)
              }
            }
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Total Volume:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
              Text(UnitUtils.formatVolume(calcResult.totalVolume, settings.unit, settings = settings), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Ullage (Empty Space):")
              Text(UnitUtils.formatVolume(calcResult.ullage, settings.unit, settings = settings))
            }
          }
        }
      }

      item {
        Button(
          onClick = {
            val summary = """
              Tank: $selectedTankName | Dip: $dipVal mm
              Total Volume: ${UnitUtils.formatVolume(calcResult.totalVolume, settings.unit, settings = settings)}
              Ullage: ${UnitUtils.formatVolume(calcResult.ullage, settings.unit, settings = settings)} (${String.format(Locale.US, "%.1f", calcResult.percentFull)}% Full)
              Generated by Tank Calc | Developed by Meherab Hossain Jihad
            """.trimIndent()
            ExportUtils.copyToClipboard(context, summary, "Dip Calculation Summary")
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Copy Summary for Report")
        }
      }
    }
  }

  if (showPicker) {
    TankPickerSheet(
      tanks = tanks,
      selectedTankNames = setOf(selectedTankName),
      isMultiSelect = false,
      onDismiss = { showPicker = false },
      onSingleSelected = { tank ->
        selectedTankName = tank.name
        showPicker = false
      }
    )
  }
}

// ==========================================
// SUB-MODE 1: VOLUME TO DIP (Reverse Lookup)
// ==========================================
@Composable
private fun SingleVolumeToDipContent(
  tanks: List<Tank>,
  products: List<Product>,
  settings: AppSettings,
  onOpenCalibration: (String) -> Unit
) {
  val context = LocalContext.current
  var selectedTankName by remember(tanks) { mutableStateOf(tanks.firstOrNull()?.name ?: "") }
  var volumeInputStr by remember { mutableStateOf("50000") }
  var showPicker by remember { mutableStateOf(false) }

  val selectedTank = tanks.find { it.name == selectedTankName } ?: tanks.firstOrNull()
  val tankProduct = products.find { it.id == selectedTank?.productId }
  val targetVolLitres = (volumeInputStr.toDoubleOrNull() ?: 0.0) * (if (settings.unit == UnitType.M3) 1000.0 else 1.0)

  val calculatedDipMm = remember(selectedTank, targetVolLitres, settings.invalidInputThreshold) {
    if (selectedTank != null && targetVolLitres > 0.0) {
      Calculators.calculateVolumeToDip(selectedTank, targetVolLitres, settings.invalidInputThreshold)
    } else null
  }

  val verifyCalcResult = remember(selectedTank, calculatedDipMm, settings.invalidInputThreshold) {
    if (selectedTank != null && calculatedDipMm != null) {
      Calculators.calculateDipVolume(selectedTank, calculatedDipMm, settings.invalidInputThreshold)
    } else null
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Text("Select Tank for Inverse Lookup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Spacer(modifier = Modifier.height(4.dp))
      OutlinedCard(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (selectedTank != null) "${selectedTank.name} (${selectedTank.type})" else "Select Tank...", fontWeight = FontWeight.Bold)
            if (tankProduct != null) {
              ProductBadge(name = tankProduct.name, shortCode = tankProduct.shortCode)
            }
          }
          Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
      }
      if (selectedTank != null) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
          horizontalArrangement = Arrangement.End
        ) {
          TextButton(
            onClick = { onOpenCalibration(selectedTank.name) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
          ) {
            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Edit Calibration / Fractions", style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    }

    item {
      NumberInput(
        value = volumeInputStr,
        onValueChange = { volumeInputStr = it },
        label = "Target Volume",
        suffix = if (settings.unit == UnitType.M3) "m³" else "L"
      )
    }

    if (calculatedDipMm != null && verifyCalcResult != null) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Calculated Dip Required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
              "${String.format(Locale.US, "%.0f", calculatedDipMm)} mm",
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary
            )
            Text("Achieves ${UnitUtils.formatVolume(verifyCalcResult.totalVolume, settings.unit, settings = settings)} (${String.format(Locale.US, "%.1f", verifyCalcResult.percentFull)}% full)", style = MaterialTheme.typography.bodyMedium)
          }
        }
      }

      item {
        TankVisualCard(calcResult = verifyCalcResult, tankName = selectedTank?.name ?: "")
      }

      item {
        Button(
          onClick = {
            val summary = """
              Tank: $selectedTankName | Target Vol: ${UnitUtils.formatVolume(targetVolLitres, settings.unit, settings = settings)}
              Calculated Dip: ${String.format(Locale.US, "%.0f", calculatedDipMm)} mm
              Achieved Volume: ${UnitUtils.formatVolume(verifyCalcResult.totalVolume, settings.unit, settings = settings)}
              Generated by Tank Calc | Developed by Meherab Hossain Jihad
            """.trimIndent()
            ExportUtils.copyToClipboard(context, summary, "Volume to Dip Summary")
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Copy Summary")
        }
      }
    }
  }

  if (showPicker) {
    TankPickerSheet(
      tanks = tanks,
      selectedTankNames = setOf(selectedTankName),
      isMultiSelect = false,
      onDismiss = { showPicker = false },
      onSingleSelected = { tank ->
        selectedTankName = tank.name
        showPicker = false
      }
    )
  }
}

// ==========================================
// SUB-MODE 2: BULK DIP SHEET (EXCEL MATRIX)
// ==========================================
private enum class BulkDipColumn(val title: String, val widthDp: Int) {
  RAW_DIP("Raw Dip (mm)", 110),
  WATER_DIP("Water Dip (mm)", 115),
  GROSS_VOL("Gross Vol", 125),
  WATER_VOL("Water Vol", 115),
  NET_VOL("Net Vol", 130),
  STATUS("Fill % / Status", 125)
}

private data class BulkDipCellCoord(
  val tankId: String,
  val column: BulkDipColumn // RAW_DIP or WATER_DIP
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkDipSheetContent(
  tanks: List<Tank>,
  products: List<Product>,
  settings: AppSettings,
  repository: TankRepository,
  onOpenCalibration: (String) -> Unit
) {
  val context = LocalContext.current
  val haptics = com.example.engine.rememberHapticController()
  val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
  var sheetDateStr by remember { mutableStateOf(sdf.format(Date())) }
  var displayUnit by remember(settings.unit) { mutableStateOf(settings.unit) }

  val bulkSheets by repository.bulkDipSheets.collectAsState()
  val prodDays by repository.productionReportDays.collectAsState()
  val transactions by repository.transactions.collectAsState()

  val prevDayDips = remember(sheetDateStr, bulkSheets, prodDays, tanks) {
    repository.getPreviousDayDipReadings(sheetDateStr)
  }
  val tanksWithTransferToday = remember(sheetDateStr, transactions) {
    transactions
      .filter { it.dateTime.startsWith(sheetDateStr) }
      .map { it.tankName.lowercase(Locale.US) }
      .toSet()
  }

  // Calibrated tanks (exclude N2 storage tanks)
  val calibratedTanks = remember(tanks) {
    tanks.filter { it.type != TankType.N2_STORAGE }.sortedBy { it.reportOrder }
  }
  val editableTanks = remember(calibratedTanks) {
    calibratedTanks.filter { it.dipTable.isNotEmpty() }
  }

  // Dips in current sheet: tankId -> raw dip string / water dip string
  val dipValues = remember { mutableStateMapOf<String, String>() }
  val waterDipValues = remember { mutableStateMapOf<String, String>() }

  // Load saved sheet on date change
  LaunchedEffect(sheetDateStr) {
    val savedSheet = repository.getBulkDipSheet(sheetDateStr)
    dipValues.clear()
    waterDipValues.clear()
    if (savedSheet != null) {
      savedSheet.entries.forEach { (tId, entry) ->
        if (entry.dipMm != null) {
          dipValues[tId] = if (entry.dipMm % 1.0 == 0.0) entry.dipMm.toInt().toString() else entry.dipMm.toString()
        }
        if (entry.waterDipMm != null && entry.waterDipMm > 0.0) {
          waterDipValues[tId] = if (entry.waterDipMm % 1.0 == 0.0) entry.waterDipMm.toInt().toString() else entry.waterDipMm.toString()
        }
      }
    } else {
      // Check draft
      val draft = repository.getDraft("bulk_dip_${sheetDateStr}")
      if (draft != null) {
        try {
          val json = JSONObject(draft.payloadJson)
          val rawObj = json.optJSONObject("raw") ?: json
          val waterObj = json.optJSONObject("water")
          val keys = rawObj.keys()
          while (keys.hasNext()) {
            val k = keys.next()
            if (k != "raw" && k != "water") {
              dipValues[k] = rawObj.optString(k, "")
            }
          }
          if (waterObj != null) {
            val wKeys = waterObj.keys()
            while (wKeys.hasNext()) {
              val wk = wKeys.next()
              waterDipValues[wk] = waterObj.optString(wk, "")
            }
          }
        } catch (_: Exception) {}
      }
    }
  }

  // Save current sheet helper
  val saveSheetAction: () -> Unit = {
    val entriesMap = mutableMapOf<String, BulkDipEntry>()
    calibratedTanks.forEach { tank ->
      val dipStr = dipValues[tank.id]
      val dipVal = dipStr?.toDoubleOrNull()
      val waterStr = waterDipValues[tank.id]
      val waterVal = waterStr?.toDoubleOrNull()
      if (dipVal != null) {
        val grossCalc = Calculators.calculateDipVolume(tank, dipVal, settings.invalidInputThreshold)
        val waterCalc = if (waterVal != null && waterVal > 0.0) {
          Calculators.calculateDipVolume(tank, waterVal, 0.0)
        } else null
        val grossVol = grossCalc?.totalVolume
        val waterVol = waterCalc?.totalVolume ?: 0.0
        val netVol = if (grossVol != null) max(0.0, grossVol - waterVol) else null
        val stamp = ProductionReportEngine.getTankCalibrationStamp(tank)
        entriesMap[tank.id] = BulkDipEntry(
          tankId = tank.id,
          tankName = tank.name,
          dipMm = dipVal,
          waterDipMm = waterVal,
          calculatedVolume = grossVol,
          waterVolume = waterVol,
          netVolume = netVol,
          isInvalid = grossCalc?.invalidInput == true || (waterVal != null && waterVal > dipVal),
          calibrationStamp = stamp
        )
      }
    }
    val sheet = BulkDipSheet(dateStr = sheetDateStr, entries = entriesMap)
    repository.saveBulkDipSheet(sheet)
  }

  // Auto-save draft on changes (400ms debounce)
  LaunchedEffect(dipValues.toMap(), waterDipValues.toMap(), sheetDateStr) {
    delay(400L)
    val root = JSONObject()
    val rawJson = JSONObject()
    val waterJson = JSONObject()
    dipValues.forEach { (k, v) -> rawJson.put(k, v) }
    waterDipValues.forEach { (k, v) -> waterJson.put(k, v) }
    root.put("raw", rawJson)
    root.put("water", waterJson)
    repository.saveDraft("bulk_dip_${sheetDateStr}", "bulk_dip", root.toString(), "Bulk Dip Sheet: $sheetDateStr")
    saveSheetAction()
  }

  // Check calibration stamp changes
  val savedSheet = repository.getBulkDipSheet(sheetDateStr)
  val hasCalibrationChanged = remember(savedSheet, calibratedTanks) {
    savedSheet?.entries?.any { (tId, entry) ->
      val tank = calibratedTanks.find { it.id == tId }
      val currentStamp = ProductionReportEngine.getTankCalibrationStamp(tank)
      entry.calibrationStamp != null && entry.calibrationStamp != currentStamp
    } == true
  }

  // Active spreadsheet cell state
  var activeCell by remember { mutableStateOf<BulkDipCellCoord?>(null) }
  val activeTank = calibratedTanks.find { it.id == activeCell?.tankId }
  val horizontalScrollState = rememberScrollState()

  // Dialogs
  var showUseInReportDialog by remember { mutableStateOf(false) }

  // Live Summary Totals
  var dippedCount = 0
  var totalGrossLitres = 0.0
  var totalWaterLitres = 0.0
  var totalNetLitres = 0.0

  calibratedTanks.forEach { tank ->
    val dipVal = dipValues[tank.id]?.toDoubleOrNull()
    val waterVal = waterDipValues[tank.id]?.toDoubleOrNull()
    if (dipVal != null && tank.dipTable.isNotEmpty()) {
      val gCalc = Calculators.calculateDipVolume(tank, dipVal, settings.invalidInputThreshold)
      if (gCalc != null && !gCalc.invalidInput) {
        dippedCount++
        val gVol = gCalc.totalVolume
        val wVol = if (waterVal != null && waterVal > 0.0) {
          Calculators.calculateDipVolume(tank, waterVal, 0.0)?.totalVolume ?: 0.0
        } else 0.0
        totalGrossLitres += gVol
        totalWaterLitres += wVol
        totalNetLitres += max(0.0, gVol - wVol)
      }
    }
  }

  val unitSuffix = if (displayUnit == UnitType.M3) "m³" else "L"
  fun formatVolCell(litres: Double): String {
    val v = if (displayUnit == UnitType.M3) litres / 1000.0 else litres
    return if (displayUnit == UnitType.M3) String.format(Locale.US, "%,.3f", v)
    else String.format(Locale.US, "%,.0f", v)
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Top Control Bar: Date Picker, Unit Toggle (L | m³), and Use in Production Report
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedCard(
              onClick = {
                val cal = Calendar.getInstance()
                try {
                  val p = sdf.parse(sheetDateStr)
                  if (p != null) cal.time = p
                } catch (_: Exception) {}
                DatePickerDialog(
                  context,
                  { _, y, m, d -> sheetDateStr = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d) },
                  cal.get(Calendar.YEAR),
                  cal.get(Calendar.MONTH),
                  cal.get(Calendar.DAY_OF_MONTH)
                ).show()
              }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(15.dp))
                Text(sheetDateStr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
              }
            }

            // Unit Toggle (L | m³)
            SingleChoiceSegmentedButtonRow {
              SegmentedButton(
                selected = displayUnit == UnitType.LITRES,
                onClick = { displayUnit = UnitType.LITRES },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
              ) {
                Text("L", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
              }
              SegmentedButton(
                selected = displayUnit == UnitType.M3,
                onClick = { displayUnit = UnitType.M3 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
              ) {
                Text("m³", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
              }
            }

            Button(
              onClick = { showUseInReportDialog = true },
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
              modifier = Modifier.testTag("use_in_production_report_button")
            ) {
              Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Use in Report", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // Calibration Changed Notice
      if (hasCalibrationChanged) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Tank Master Calibration Changed", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
              Text("Calibration stamps differ from when this sheet was saved.", style = MaterialTheme.typography.bodySmall)
            }
            Button(
              onClick = {
                saveSheetAction()
                Toast.makeText(context, "Recalculated volumes with latest calibrations", Toast.LENGTH_SHORT).show()
              },
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
              Text("Recalculate")
            }
          }
        }
      }

      // Continuous Spreadsheet Matrix Container
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          // 1. FREEZE-PANE HEADER ROW
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceContainerHigh)
          ) {
            // Frozen Left Header: Tank ID / Name
            Box(
              modifier = Modifier
                .width(135.dp)
                .height(46.dp)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                .padding(horizontal = 8.dp),
              contentAlignment = Alignment.CenterStart
            ) {
              Text("Tank ID / Role", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium)
            }

            // Scrollable Matrix Column Headers
            Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
              BulkDipColumn.entries.forEach { col ->
                val headerLabel = when (col) {
                  BulkDipColumn.GROSS_VOL -> "Gross Vol ($unitSuffix)"
                  BulkDipColumn.WATER_VOL -> "Water Vol ($unitSuffix)"
                  BulkDipColumn.NET_VOL -> "Net Vol ($unitSuffix)"
                  else -> col.title
                }
                Box(
                  modifier = Modifier
                    .width(col.widthDp.dp)
                    .height(46.dp)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .padding(horizontal = 6.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = headerLabel,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                  )
                }
              }
            }
          }

          // 2. MATRIX BODY ROWS
          val listState = rememberLazyListState()

          // Excel-style smart navigation: smoothly scroll active tank row into view when jumping via Enter/Next
          LaunchedEffect(activeCell) {
            val targetId = activeCell?.tankId ?: return@LaunchedEffect
            val idx = calibratedTanks.indexOfFirst { it.id == targetId }
            if (idx >= 0) {
              listState.animateScrollToItem(index = idx)
            }
          }

          LazyColumn(
            state = listState,
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
          ) {
            items(calibratedTanks, key = { it.id }) { tank ->
              val hasCalibration = tank.dipTable.isNotEmpty()
              val rawDipStr = dipValues[tank.id] ?: ""
              val waterDipStr = waterDipValues[tank.id] ?: ""
              val rawDipVal = rawDipStr.toDoubleOrNull()
              val waterDipVal = waterDipStr.toDoubleOrNull()

              val grossCalc = if (rawDipVal != null && hasCalibration) {
                Calculators.calculateDipVolume(tank, rawDipVal, settings.invalidInputThreshold)
              } else null

              val waterCalc = if (waterDipVal != null && waterDipVal > 0.0 && hasCalibration) {
                Calculators.calculateDipVolume(tank, waterDipVal, 0.0)
              } else null

              val grossVol = grossCalc?.totalVolume
              val waterVol = waterCalc?.totalVolume ?: 0.0
              val netVol = if (grossVol != null) max(0.0, grossVol - waterVol) else null

              val isWaterExceedingRaw = rawDipVal != null && waterDipVal != null && waterDipVal > rawDipVal
              val isInvalid = grossCalc?.invalidInput == true || isWaterExceedingRaw
              val hasExtrapolationWarning = grossCalc?.warning != null

              // Real-time Outlier / Anomaly Detection vs Previous Day's reading
              val prevDipMm = prevDayDips[tank.id] ?: prevDayDips[tank.name]
              val dipDeltaMm = if (rawDipVal != null && prevDipMm != null) rawDipVal - prevDipMm else null
              val hasTransferToday = tank.name.lowercase(Locale.US) in tanksWithTransferToday
              val isOutlierAnomaly = dipDeltaMm != null &&
                abs(dipDeltaMm) > settings.dipOutlierThresholdMm &&
                !hasTransferToday &&
                !isInvalid

              val isRowSelected = activeCell?.tankId == tank.id
              val rowBg = when {
                isInvalid -> Color(0xFFFFEBEE)
                isOutlierAnomaly -> Color(0xFFFFF8E1)
                hasExtrapolationWarning -> Color(0xFFFFF8E1)
                isRowSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                !hasCalibration -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surface
              }

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(rowBg)
              ) {
                // Frozen Left Column: Tank ID + Type Badge + Capacity
                Box(
                  modifier = Modifier
                    .width(135.dp)
                    .height(50.dp)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .background(
                      if (isRowSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                      else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                  contentAlignment = Alignment.CenterStart
                ) {
                  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                      Text(
                        text = tank.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasCalibration) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                      )
                      Surface(
                        color = when (tank.type) {
                          TankType.FEED -> MaterialTheme.colorScheme.primaryContainer
                          TankType.PRODUCT -> MaterialTheme.colorScheme.secondaryContainer
                          TankType.BLENDING -> MaterialTheme.colorScheme.tertiaryContainer
                          else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(3.dp)
                      ) {
                        Text(
                          text = tank.type.name.take(4),
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                      }
                    }
                    Text(
                      text = if (prevDipMm != null) "Prev: ${prevDipMm.toInt()} mm" else if (hasCalibration) "Max: ${formatVolCell(tank.maxVolumeLiters)} $unitSuffix" else "Uncalibrated",
                      style = MaterialTheme.typography.labelSmall,
                      color = if (hasCalibration) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                  }
                }

                // Scrollable Matrix Cells
                Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                  // Cell 1: Raw Dip (mm) with Outlier / Anomaly Amber Highlight
                  val isRawActive = activeCell?.tankId == tank.id && activeCell?.column == BulkDipColumn.RAW_DIP
                  Box(
                    modifier = Modifier
                      .width(BulkDipColumn.RAW_DIP.widthDp.dp)
                      .height(50.dp)
                      .border(
                        width = when {
                          isRawActive -> 2.dp
                          isInvalid -> 1.5.dp
                          isOutlierAnomaly -> 1.8.dp
                          else -> 0.5.dp
                        },
                        color = when {
                          isRawActive -> MaterialTheme.colorScheme.primary
                          isInvalid -> MaterialTheme.colorScheme.error
                          isOutlierAnomaly -> Color(0xFFF59E0B)
                          else -> MaterialTheme.colorScheme.outlineVariant
                        }
                      )
                      .background(
                        when {
                          isRawActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                          isOutlierAnomaly -> Color(0xFFFFF3CD)
                          rawDipStr.isNotBlank() -> MaterialTheme.colorScheme.surfaceContainerLowest
                          else -> Color.Transparent
                        }
                      )
                      .clickable(enabled = hasCalibration) {
                        haptics.trigger(com.example.engine.HapticEvent.CELL_SELECT)
                        activeCell = BulkDipCellCoord(tank.id, BulkDipColumn.RAW_DIP)
                      }
                      .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                  ) {
                    Column(horizontalAlignment = Alignment.End) {
                      Text(
                        text = rawDipStr.ifBlank { "-" },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (rawDipStr.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                          isInvalid -> MaterialTheme.colorScheme.error
                          isOutlierAnomaly -> Color(0xFF92400E)
                          rawDipStr.isNotBlank() -> MaterialTheme.colorScheme.primary
                          else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodyMedium
                      )
                      if (isOutlierAnomaly && dipDeltaMm != null) {
                        Text(
                          text = String.format(Locale.US, "⚠️ Δ%+.0f", dipDeltaMm),
                          fontSize = 9.sp,
                          fontWeight = FontWeight.ExtraBold,
                          color = Color(0xFFB45309)
                        )
                      }
                    }
                  }

                  // Cell 2: Water Dip (mm)
                  val isWaterActive = activeCell?.tankId == tank.id && activeCell?.column == BulkDipColumn.WATER_DIP
                  Box(
                    modifier = Modifier
                      .width(BulkDipColumn.WATER_DIP.widthDp.dp)
                      .height(50.dp)
                      .border(
                        width = if (isWaterActive) 2.dp else if (isWaterExceedingRaw) 1.5.dp else 0.5.dp,
                        color = if (isWaterActive) MaterialTheme.colorScheme.primary else if (isWaterExceedingRaw) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                      )
                      .background(
                        if (isWaterActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else if (waterDipStr.isNotBlank()) MaterialTheme.colorScheme.surfaceContainerLowest
                        else Color.Transparent
                      )
                      .clickable(enabled = hasCalibration) {
                        haptics.trigger(com.example.engine.HapticEvent.CELL_SELECT)
                        activeCell = BulkDipCellCoord(tank.id, BulkDipColumn.WATER_DIP)
                      }
                      .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                  ) {
                    Text(
                      text = waterDipStr.ifBlank { "0" },
                      fontFamily = FontFamily.Monospace,
                      fontWeight = if (waterDipStr.isNotBlank() && waterDipStr != "0") FontWeight.Bold else FontWeight.Normal,
                      color = if (isWaterExceedingRaw) MaterialTheme.colorScheme.error else if (waterDipStr.isNotBlank()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                      style = MaterialTheme.typography.bodyMedium
                    )
                  }

                  // Cell 3: Gross Volume
                  Box(
                    modifier = Modifier
                      .width(BulkDipColumn.GROSS_VOL.widthDp.dp)
                      .height(50.dp)
                      .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                      .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                  ) {
                    Text(
                      text = if (grossVol != null && !isInvalid) formatVolCell(grossVol) else "-",
                      fontFamily = FontFamily.Monospace,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  }

                  // Cell 4: Water Volume
                  Box(
                    modifier = Modifier
                      .width(BulkDipColumn.WATER_VOL.widthDp.dp)
                      .height(50.dp)
                      .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                      .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                  ) {
                    Text(
                      text = if (waterVol > 0.0) formatVolCell(waterVol) else "-",
                      fontFamily = FontFamily.Monospace,
                      style = MaterialTheme.typography.bodySmall,
                      color = if (waterVol > 0.0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }

                  // Cell 5: Net Volume
                  Box(
                    modifier = Modifier
                      .width(BulkDipColumn.NET_VOL.widthDp.dp)
                      .height(50.dp)
                      .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                      .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f))
                      .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                  ) {
                    if (isInvalid) {
                      Text("INVALID", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    } else {
                      Text(
                        text = if (netVol != null) formatVolCell(netVol) else "-",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (netVol != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }

                  // Cell 6: Fill % / Validation Status
                  Box(
                    modifier = Modifier
                      .width(BulkDipColumn.STATUS.widthDp.dp)
                      .height(50.dp)
                      .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                      .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    when {
                      isWaterExceedingRaw -> {
                        Surface(color = Color(0xFFFFCDD2), shape = RoundedCornerShape(4.dp)) {
                          Text("Water > Dip", color = Color(0xFFB71C1C), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                      }
                      grossCalc?.invalidInput == true -> {
                        Surface(color = Color(0xFFFFCDD2), shape = RoundedCornerShape(4.dp)) {
                          Text("<= Threshold", color = Color(0xFFB71C1C), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                      }
                      hasExtrapolationWarning -> {
                        Surface(color = Color(0xFFFFECB3), shape = RoundedCornerShape(4.dp)) {
                          Text("Out of Range", color = Color(0xFFE65100), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                      }
                      isOutlierAnomaly -> {
                        Surface(color = Color(0xFFFEF3C7), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)), shape = RoundedCornerShape(4.dp)) {
                          Text(
                            text = String.format(Locale.US, "Outlier %+.0fmm", dipDeltaMm ?: 0.0),
                            color = Color(0xFF92400E),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                          )
                        }
                      }
                      grossCalc != null -> {
                        val pctFull = if (tank.maxVolumeLiters > 0) (grossCalc.totalVolume / tank.maxVolumeLiters) * 100.0 else 0.0
                        Text(
                          text = String.format(Locale.US, "%.1f%% full", pctFull),
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = FontWeight.SemiBold,
                          color = if (pctFull >= 90.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                        )
                      }
                      else -> {
                        Text("-", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                    }
                  }
                }
              }
            }
          }

          // 3. PERMANENT ANCHORED SUMMARY ROW AT BOTTOM OF BULK DIP GRID
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.secondaryContainer)
              .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
          ) {
            // Frozen Left Summary Cell
            Box(
              modifier = Modifier
                .width(135.dp)
                .height(48.dp)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                .padding(horizontal = 8.dp),
              contentAlignment = Alignment.CenterStart
            ) {
              Column {
                Text("TOTALS", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("$dippedCount / ${editableTanks.size} tanks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
              }
            }

            // Scrollable Summary Cells synced with horizontalScrollState
            Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
              // Raw Dip summary placeholder
              Box(
                modifier = Modifier
                  .width(BulkDipColumn.RAW_DIP.widthDp.dp)
                  .height(48.dp)
                  .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                  .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
              ) {
                Text("Raw Dips", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
              }

              // Water Dip summary placeholder
              Box(
                modifier = Modifier
                  .width(BulkDipColumn.WATER_DIP.widthDp.dp)
                  .height(48.dp)
                  .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                  .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
              ) {
                Text("Water Dips", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
              }

              // Total Gross Volume
              Box(
                modifier = Modifier
                  .width(BulkDipColumn.GROSS_VOL.widthDp.dp)
                  .height(48.dp)
                  .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                  .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterEnd
              ) {
                Column(horizontalAlignment = Alignment.End) {
                  Text("Gross ($unitSuffix)", style = MaterialTheme.typography.labelSmall)
                  Text(formatVolCell(totalGrossLitres), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
              }

              // Total Water Volume
              Box(
                modifier = Modifier
                  .width(BulkDipColumn.WATER_VOL.widthDp.dp)
                  .height(48.dp)
                  .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                  .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterEnd
              ) {
                Column(horizontalAlignment = Alignment.End) {
                  Text("Water ($unitSuffix)", style = MaterialTheme.typography.labelSmall)
                  Text(formatVolCell(totalWaterLitres), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
              }

              // Total Net Volume
              Box(
                modifier = Modifier
                  .width(BulkDipColumn.NET_VOL.widthDp.dp)
                  .height(48.dp)
                  .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                  .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                  .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterEnd
              ) {
                Column(horizontalAlignment = Alignment.End) {
                  Text("Net Total ($unitSuffix)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                  Text(formatVolCell(totalNetLitres), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
              }

              // Status column summary
              Box(
                modifier = Modifier
                  .width(BulkDipColumn.STATUS.widthDp.dp)
                  .height(48.dp)
                  .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                  .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
              ) {
                Text("Live Sync Ready", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
              }
            }
          }
        }
      }

      // Reserve space when keypad dock is open
      if (activeCell != null) {
        Spacer(modifier = Modifier.height(230.dp))
      }
    }

    // 4. BOTTOM-DOCKED INDUSTRIAL KEYPAD & FORMULA BAR
    if (activeCell != null && activeTank != null) {
      val coord = activeCell!!
      val currentVal = if (coord.column == BulkDipColumn.RAW_DIP) {
        dipValues[activeTank.id] ?: ""
      } else {
        waterDipValues[activeTank.id] ?: ""
      }

      val rawVal = dipValues[activeTank.id]?.toDoubleOrNull()
      val waterVal = waterDipValues[activeTank.id]?.toDoubleOrNull()
      val grossCalc = if (rawVal != null) Calculators.calculateDipVolume(activeTank, rawVal, settings.invalidInputThreshold) else null
      val waterCalc = if (waterVal != null && waterVal > 0.0) Calculators.calculateDipVolume(activeTank, waterVal, 0.0) else null
      val netPreview = if (grossCalc != null && !grossCalc.invalidInput) {
        val netL = max(0.0, grossCalc.totalVolume - (waterCalc?.totalVolume ?: 0.0))
        "Net: ${formatVolCell(netL)} $unitSuffix"
      } else null

      val warningMsg = when {
        rawVal != null && waterVal != null && waterVal > rawVal -> "Water dip ($waterVal mm) exceeds raw dip ($rawVal mm)!"
        grossCalc?.invalidInput == true -> "Invalid dip: Base volume <= threshold (${settings.invalidInputThreshold} L)"
        grossCalc?.warning != null -> grossCalc.warning
        else -> null
      }

      val activePrevDip = prevDayDips[activeTank.id] ?: prevDayDips[activeTank.name]
      val activeDipDelta = if (rawVal != null && activePrevDip != null) rawVal - activePrevDip else null
      val activeHasTransfer = activeTank.name.lowercase(Locale.US) in tanksWithTransferToday
      val activeOutlierMsg = if (
        activeDipDelta != null &&
        abs(activeDipDelta) > settings.dipOutlierThresholdMm &&
        !activeHasTransfer
      ) {
        String.format(
          Locale.US,
          "Outlier Alert: Level changed %+.0f mm vs prev (%d mm) without transfer",
          activeDipDelta,
          activePrevDip?.toInt() ?: 0
        )
      } else null

      val moveDownSameCol: () -> Unit = {
        if (editableTanks.isNotEmpty()) {
          val idx = editableTanks.indexOfFirst { it.id == activeTank.id }.coerceAtLeast(0)
          val nextTank = editableTanks[(idx + 1) % editableTanks.size]
          activeCell = BulkDipCellCoord(nextTank.id, coord.column)
        }
      }

      val moveUpSameCol: () -> Unit = {
        if (editableTanks.isNotEmpty()) {
          val idx = editableTanks.indexOfFirst { it.id == activeTank.id }.coerceAtLeast(0)
          val prevIdx = if (idx - 1 < 0) editableTanks.size - 1 else idx - 1
          activeCell = BulkDipCellCoord(editableTanks[prevIdx].id, coord.column)
        }
      }

      val moveRightTab: () -> Unit = {
        if (coord.column == BulkDipColumn.RAW_DIP) {
          activeCell = BulkDipCellCoord(activeTank.id, BulkDipColumn.WATER_DIP)
        } else if (editableTanks.isNotEmpty()) {
          val idx = editableTanks.indexOfFirst { it.id == activeTank.id }.coerceAtLeast(0)
          val nextTank = editableTanks[(idx + 1) % editableTanks.size]
          activeCell = BulkDipCellCoord(nextTank.id, BulkDipColumn.RAW_DIP)
        }
      }

      val moveLeftShiftTab: () -> Unit = {
        if (coord.column == BulkDipColumn.WATER_DIP) {
          activeCell = BulkDipCellCoord(activeTank.id, BulkDipColumn.RAW_DIP)
        } else if (editableTanks.isNotEmpty()) {
          val idx = editableTanks.indexOfFirst { it.id == activeTank.id }.coerceAtLeast(0)
          val prevIdx = if (idx - 1 < 0) editableTanks.size - 1 else idx - 1
          activeCell = BulkDipCellCoord(editableTanks[prevIdx].id, BulkDipColumn.WATER_DIP)
        }
      }

      IndustrialKeypadDock(
        cellCoordinateLabel = activeTank.name,
        columnTitle = coord.column.title,
        unitBadge = "mm",
        valueText = currentVal,
        isTextMode = false,
        allowNegative = false,
        validationWarning = warningMsg,
        outlierWarning = activeOutlierMsg,
        livePreviewText = netPreview,
        onValueChange = { newText ->
          if (coord.column == BulkDipColumn.RAW_DIP) {
            dipValues[activeTank.id] = newText
          } else {
            waterDipValues[activeTank.id] = newText
          }
        },
        onEnterMoveDown = moveDownSameCol,
        onMoveUp = moveUpSameCol,
        onTabMoveRight = moveRightTab,
        onShiftTabMoveLeft = moveLeftShiftTab,
        onClose = { activeCell = null },
        modifier = Modifier.align(Alignment.BottomCenter)
      )
    }
  }

  // Use In Production Report Dialog
  if (showUseInReportDialog) {
    UseInProductionReportDialog(
      sheetDateStr = sheetDateStr,
      calibratedTanks = calibratedTanks,
      dipValues = dipValues.toMap(),
      waterDipValues = waterDipValues.toMap(),
      settings = settings,
      repository = repository,
      onDismiss = { showUseInReportDialog = false }
    )
  }
}

// Dialog for "Use in Production Report"
@Composable
private fun UseInProductionReportDialog(
  sheetDateStr: String,
  calibratedTanks: List<Tank>,
  dipValues: Map<String, String>,
  waterDipValues: Map<String, String>,
  settings: AppSettings,
  repository: TankRepository,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
  var targetPresentDateStr by remember { mutableStateOf(sheetDateStr) }
  var alsoFillPrevious by remember { mutableStateOf(false) }
  var showOverwriteChoiceDialog by remember { mutableStateOf(false) }

  // Target previous date calculated as targetPresentDateStr - 1 day
  val targetPrevDateStr = remember(targetPresentDateStr) {
    try {
      val cal = Calendar.getInstance().apply {
        time = sdf.parse(targetPresentDateStr) ?: Date()
        add(Calendar.DAY_OF_YEAR, -1)
      }
      sdf.format(cal.time)
    } catch (_: Exception) {
      sheetDateStr
    }
  }

  // Helper applying values to report snapshot
  fun applyToReport(overwriteAll: Boolean) {
    val existingSnapshot = repository.getProductionReportDay(targetPresentDateStr) ?: ProductionReportDaySnapshot(
      dateStr = targetPresentDateStr,
      reportTime = "10:00"
    )

    val skippedTanks = mutableListOf<String>()
    val updatedEntries = existingSnapshot.tankEntries.toMutableMap()

    calibratedTanks.forEach { tank ->
      val dipStr = dipValues[tank.id]
      val dipVal = dipStr?.toDoubleOrNull()
      val waterVal = waterDipValues[tank.id]?.toDoubleOrNull()
      if (dipVal != null) {
        val calc = Calculators.calculateDipVolume(tank, dipVal, settings.invalidInputThreshold)
        val waterCalc = if (waterVal != null && waterVal > 0.0) Calculators.calculateDipVolume(tank, waterVal, 0.0) else null
        if (calc == null || calc.invalidInput || (waterVal != null && waterVal > dipVal)) {
          skippedTanks.add("${tank.name} (invalid dip)")
        } else {
          val netVolume = max(0.0, calc.totalVolume - (waterCalc?.totalVolume ?: 0.0))
          val currentEntry = updatedEntries[tank.name] ?: ProductionReportTankEntry(tankName = tank.name)
          val hasExistingVol = currentEntry.volumeTodayInput != null && currentEntry.volumeTodayInput > 0
          if (!hasExistingVol || overwriteAll) {
            updatedEntries[tank.name] = currentEntry.copy(
              volumeTodayInput = netVolume,
              useVolumeModeToday = true,
              isFromDipSheetToday = true,
              dipSheetDipToday = dipVal
            )
          }
        }
      }
    }

    // If also fill previous
    if (alsoFillPrevious) {
      val prevSheet = repository.getBulkDipSheet(targetPrevDateStr)
      if (prevSheet != null) {
        calibratedTanks.forEach { tank ->
          val prevEntry = prevSheet.entries[tank.id]
          val volToUse = prevEntry?.netVolume ?: prevEntry?.calculatedVolume
          if (prevEntry != null && volToUse != null && !prevEntry.isInvalid) {
            val curr = updatedEntries[tank.name] ?: ProductionReportTankEntry(tankName = tank.name)
            val hasExistingPrev = curr.volumePrevInput != null && curr.volumePrevInput > 0
            if (!hasExistingPrev || overwriteAll) {
              updatedEntries[tank.name] = curr.copy(
                volumePrevInput = volToUse,
                useVolumeModePrev = true,
                isFromDipSheetPrev = true,
                dipSheetDipPrev = prevEntry.dipMm
              )
            }
          }
        }
      }
    }

    val finalSnapshot = existingSnapshot.copy(tankEntries = updatedEntries, lastModified = System.currentTimeMillis())
    repository.saveProductionReportDay(finalSnapshot)

    val msg = if (skippedTanks.isNotEmpty()) {
      "Updated Production Report for $targetPresentDateStr. Skipped: ${skippedTanks.joinToString(", ")}"
    } else {
      "Successfully synced Net Volumes to Production Report ($targetPresentDateStr)"
    }
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    onDismiss()
  }

  if (showOverwriteChoiceDialog) {
    AlertDialog(
      onDismissRequest = { showOverwriteChoiceDialog = false },
      title = { Text("Existing Volumes Found") },
      text = { Text("The Production Report for $targetPresentDateStr already contains volume values. How would you like to apply the dip sheet volumes?") },
      confirmButton = {
        Button(onClick = {
          showOverwriteChoiceDialog = false
          applyToReport(overwriteAll = true)
        }) {
          Text("Overwrite all")
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = {
            showOverwriteChoiceDialog = false
            onDismiss()
          }) {
            Text("Cancel")
          }
          OutlinedButton(onClick = {
            showOverwriteChoiceDialog = false
            applyToReport(overwriteAll = false)
          }) {
            Text("Fill empty only")
          }
        }
      }
    )
    return
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Use in Production Report") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Choose the Present Report Date to populate with net volumes from this dip sheet:")
        OutlinedCard(
          onClick = {
            val cal = Calendar.getInstance()
            try {
              val p = sdf.parse(targetPresentDateStr)
              if (p != null) cal.time = p
            } catch (_: Exception) {}
            DatePickerDialog(
              context,
              { _, y, m, d -> targetPresentDateStr = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d) },
              cal.get(Calendar.YEAR),
              cal.get(Calendar.MONTH),
              cal.get(Calendar.DAY_OF_MONTH)
            ).show()
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Present Date", style = MaterialTheme.typography.labelSmall)
              Text(targetPresentDateStr, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Default.CalendarToday, contentDescription = null)
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Checkbox(
            checked = alsoFillPrevious,
            onCheckedChange = { alsoFillPrevious = it }
          )
          Column {
            Text("Also fill Previous from previous date dip sheet", style = MaterialTheme.typography.bodyMedium)
            Text("Previous sheet date: $targetPrevDateStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = {
        val existing = repository.getProductionReportDay(targetPresentDateStr)
        val hasAnyVol = existing?.tankEntries?.values?.any { (it.volumeTodayInput ?: 0.0) > 0 } == true
        if (hasAnyVol) {
          showOverwriteChoiceDialog = true
        } else {
          applyToReport(overwriteAll = true)
        }
      }) {
        Text("Apply")
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

/**
 * Enhanced Industrial Tank Visualizer Card showing clear level markers for:
 * - Ullage (headspace)
 * - High-Level Alarm (HLA at 90%)
 * - Current Liquid Level
 * - Unpumpable / Heel Level (5% dead-stock)
 */
@Composable
fun TankVisualCard(calcResult: DipCalculationResult, tankName: String) {
  val isSunlight = LocalSunlightMode.current
  val pct = calcResult.percentFull.coerceIn(0.0, 100.0).toFloat()
  val fillFraction = (pct / 100f).coerceIn(0f, 1f)
  val isHlaTriggered = pct >= 90.0f
  val isBelowHeel = pct > 0f && pct <= 5.0f

  val animatedFill by animateFloatAsState(
    targetValue = fillFraction,
    animationSpec = tween(durationMillis = 550),
    label = "tank_level_anim"
  )

  val heelVolumeL = calcResult.capacity * 0.05
  val pumpableVolumeL = (calcResult.totalVolume - heelVolumeL).coerceAtLeast(0.0)
  val hlaVolumeL = calcResult.capacity * 0.90

  Card(
    colors = CardDefaults.cardColors(
      containerColor = if (isSunlight) Color.White else MaterialTheme.colorScheme.surfaceVariant
    ),
    border = if (isSunlight) androidx.compose.foundation.BorderStroke(2.dp, Color.Black) else null,
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Top Title & Status Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = tankName,
              fontWeight = FontWeight.ExtraBold,
              style = MaterialTheme.typography.titleMedium,
              color = if (isSunlight) Color.Black else MaterialTheme.colorScheme.onSurface
            )
            Surface(
              color = when {
                isHlaTriggered -> Color(0xFFB91C1C)
                isBelowHeel -> Color(0xFFD97706)
                else -> Color(0xFF0F766E)
              },
              shape = RoundedCornerShape(6.dp)
            ) {
              Text(
                text = when {
                  isHlaTriggered -> "HLA ALARM ≥90%"
                  isBelowHeel -> "NEAR HEEL ≤5%"
                  else -> "NORMAL OPERATING"
                },
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
          Text(
            text = "Current Dip: ${String.format(Locale.US, "%,.1f", calcResult.effectiveDip)} mm (${String.format(Locale.US, "%.1f", pct)}% full)",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSunlight) FontWeight.Bold else FontWeight.Medium,
            color = if (isSunlight) Color(0xFF0B2A5B) else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = "${String.format(Locale.US, "%,.0f", calcResult.totalVolume)} L",
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleLarge,
            color = if (isHlaTriggered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
          )
          Text(
            text = "Pumpable: ${String.format(Locale.US, "%,.0f", pumpableVolumeL)} L",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isSunlight) Color.Black else MaterialTheme.colorScheme.secondary
          )
        }
      }

      // Interactive Tank Schematic with Level Markers
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(175.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left: Tank Cylinder Canvas with HLA (90%), Current Level, Ullage, and Heel (5%)
        Box(
          modifier = Modifier
            .width(125.dp)
            .fillMaxHeight(),
          contentAlignment = Alignment.Center
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val tankLeft = 14f
            val tankRight = w - 14f
            val tankWidth = tankRight - tankLeft
            val tankTop = 12f
            val tankBottom = h - 10f
            val tankHeight = tankBottom - tankTop

            // 1. Tank Shell (Ullage / Headspace background)
            drawRoundRect(
              color = if (isSunlight) Color(0xFFF1F5F9) else Color(0xFFE2E8F0),
              topLeft = Offset(tankLeft, tankTop),
              size = Size(tankWidth, tankHeight),
              cornerRadius = CornerRadius(12f, 12f)
            )

            // 2. Unpumpable / Heel Zone shading (Bottom 5%)
            val heelFrac = 0.05f
            val heelHeight = tankHeight * heelFrac
            val heelTopY = tankBottom - heelHeight
            drawRoundRect(
              color = Color(0xFF94A3B8).copy(alpha = 0.35f),
              topLeft = Offset(tankLeft, heelTopY),
              size = Size(tankWidth, heelHeight),
              cornerRadius = CornerRadius(8f, 8f)
            )

            // 3. Liquid Fill
            val liquidHeight = (tankHeight * animatedFill).coerceAtLeast(0f)
            val liquidTopY = tankBottom - liquidHeight
            if (liquidHeight > 0f) {
              val fillBrush = Brush.verticalGradient(
                colors = if (isHlaTriggered) {
                  listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
                } else {
                  listOf(Color(0xFF14B8A6), Color(0xFF0E5AA7), Color(0xFF0B2A5B))
                },
                startY = liquidTopY,
                endY = tankBottom
              )
              drawRoundRect(
                brush = fillBrush,
                topLeft = Offset(tankLeft, liquidTopY),
                size = Size(tankWidth, liquidHeight),
                cornerRadius = CornerRadius(10f, 10f)
              )
            }

            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)

            // 4. High-Level Alarm (HLA at 90%) Marker Line
            val hlaY = tankBottom - (tankHeight * 0.90f)
            drawLine(
              color = Color(0xFFDC2626),
              start = Offset(4f, hlaY),
              end = Offset(w - 4f, hlaY),
              strokeWidth = if (isSunlight) 3.5f else 2.5f,
              pathEffect = dash
            )

            // 5. Current Level Solid Marker Line
            drawLine(
              color = if (isSunlight) Color.Black else Color(0xFF0F172A),
              start = Offset(6f, liquidTopY),
              end = Offset(w - 6f, liquidTopY),
              strokeWidth = 3f
            )

            // 6. Unpumpable / Heel Level (5%) Marker Line
            drawLine(
              color = Color(0xFFD97706),
              start = Offset(4f, heelTopY),
              end = Offset(w - 4f, heelTopY),
              strokeWidth = if (isSunlight) 3f else 2f,
              pathEffect = dash
            )

            // 7. Outer Tank Shell Border
            drawRoundRect(
              color = if (isSunlight) Color.Black else Color(0xFF0B2A5B),
              topLeft = Offset(tankLeft, tankTop),
              size = Size(tankWidth, tankHeight),
              cornerRadius = CornerRadius(12f, 12f),
              style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (isSunlight) 4f else 3f)
            )
          }
        }

        // Right: 4 Explicit Level Marker Callouts
        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.SpaceBetween
        ) {
          // Marker 1: Ullage (Available Headspace)
          LevelMarkerRow(
            badgeText = "ULLAGE",
            badgeColor = Color(0xFF475569),
            valueText = "${String.format(Locale.US, "%,.0f", calcResult.ullage)} L (${String.format(Locale.US, "%.1f%%", (100f - pct).coerceAtLeast(0f))})",
            isSunlight = isSunlight
          )

          // Marker 2: High-Level Alarm (HLA at 90%)
          LevelMarkerRow(
            badgeText = "HLA (90%)",
            badgeColor = Color(0xFFDC2626),
            valueText = "${String.format(Locale.US, "%,.0f", hlaVolumeL)} L${if (isHlaTriggered) " • ALARM!" else ""}",
            isSunlight = isSunlight
          )

          // Marker 3: Current Level
          LevelMarkerRow(
            badgeText = "CURRENT",
            badgeColor = Color(0xFF0E5AA7),
            valueText = "${String.format(Locale.US, "%,.1f", calcResult.effectiveDip)} mm • ${String.format(Locale.US, "%,.0f", calcResult.totalVolume)} L",
            isSunlight = isSunlight
          )

          // Marker 4: Unpumpable / Heel Level (5%)
          LevelMarkerRow(
            badgeText = "HEEL (5%)",
            badgeColor = Color(0xFFD97706),
            valueText = "Unpumpable: ~${String.format(Locale.US, "%,.0f", heelVolumeL)} L",
            isSunlight = isSunlight
          )
        }
      }

      // Bottom Capacity Bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "Ullage Headspace: ${String.format(Locale.US, "%,.0f", calcResult.ullage)} L",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "Max Capacity: ${String.format(Locale.US, "%,.0f", calcResult.capacity)} L",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
private fun LevelMarkerRow(
  badgeText: String,
  badgeColor: Color,
  valueText: String,
  isSunlight: Boolean
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Surface(
      color = badgeColor,
      shape = RoundedCornerShape(4.dp),
      modifier = Modifier.width(74.dp)
    ) {
      Text(
        text = badgeText,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
      )
    }
    Text(
      text = valueText,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = if (isSunlight) FontWeight.ExtraBold else FontWeight.SemiBold,
      color = if (isSunlight) Color.Black else MaterialTheme.colorScheme.onSurface,
      maxLines = 1
    )
  }
}


