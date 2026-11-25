package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.engine.*
import com.example.ui.components.AppScreen
import com.example.ui.components.ProductBadge
import com.example.ui.components.TankPickerSheet
import com.example.ui.components.parseHexColor
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LossScreen(repository: TankRepository) {
  val context = LocalContext.current
  val tanks by repository.tanks.collectAsState()
  val products by repository.products.collectAsState()
  val settings by repository.settings.collectAsState()
  val savedReports by repository.lossReports.collectAsState()
  val blendRecords by repository.blendRecords.collectAsState()
  val blendPrograms by repository.blendPrograms.collectAsState()

  // Mode: Natural vs 30°C
  var mode by remember { mutableStateOf(CalculationMode.NORMAL) }

  // Date and Time state
  val now = Calendar.getInstance()
  val defaultEnd = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(now.time)
  val calStart = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -24) }
  val defaultStart = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(calStart.time)

  var startTimeStr by remember { mutableStateOf(defaultStart) }
  var endTimeStr by remember { mutableStateOf(defaultEnd) }

  // Tank selection
  val feedTanks = tanks.filter { it.type == TankType.FEED }
  val productTanks = tanks.filter { it.type == TankType.PRODUCT || it.type == TankType.BLENDING }
  val blendingTanks = tanks.filter { it.type == TankType.BLENDING }

  var selectedTankNames by remember(tanks) {
    mutableStateOf(tanks.filter { it.type != TankType.N2_STORAGE }.map { it.name }.toSet())
  }
  var showTankPicker by remember { mutableStateOf(false) }
  var showUnlinkedProductsExpanded by remember { mutableStateOf(false) }

  // Inputs per tank:
  // Feed: [initTov, finalTov, receiving, density, initTemp, finalTemp]
  // Prod: [initTov, finalTov, delivery, density, initTemp, finalTemp]
  val tankInputs = remember { mutableStateOf(mutableMapOf<String, Map<String, String>>()) }

  // Blending lines per product tank: tankName -> List<BlendingLineInput>
  val blendingLinesState = remember { mutableStateOf(mutableMapOf<String, List<BlendingLineInput>>()) }

  // Sync tracking indicators: tankName -> Pair(isInitialSynced, isFinalSynced)
  val syncedStatus = remember { mutableStateOf(mutableMapOf<String, Pair<Boolean, Boolean>>()) }

  // Current editing report ID (null = new report)
  var editingReportId by remember { mutableStateOf<String?>(null) }
  var showSavedReportsSheet by remember { mutableStateOf(false) }
  var showBlendRecordsHelper by remember { mutableStateOf(false) }
  var draftSavedIndicator by remember { mutableStateOf(false) }
  var showClearConfirm by remember { mutableStateOf(false) }

  // Dip helper state
  var dipHelperTankName by remember { mutableStateOf<String?>(null) }
  var dipHelperIsInitial by remember { mutableStateOf(true) }
  var dipHelperInput by remember { mutableStateOf("100.0") }

  // Restore draft
  LaunchedEffect(Unit) {
    val draft = repository.getDraft("loss_calc")
    if (draft != null) {
      try {
        val json = JSONObject(draft.payloadJson)
        if (json.has("startTime")) startTimeStr = json.getString("startTime")
        if (json.has("endTime")) endTimeStr = json.getString("endTime")
      } catch (_: Exception) {}
    }
  }

  // Auto-save draft (400ms debounce)
  LaunchedEffect(startTimeStr, endTimeStr, mode) {
    delay(400L)
    val payload = JSONObject().apply {
      put("startTime", startTimeStr)
      put("endTime", endTimeStr)
      put("mode", mode.name)
    }.toString()
    repository.saveDraft("loss_calc", "default", payload, "Loss: $startTimeStr -> $endTimeStr")
    draftSavedIndicator = true
    delay(1500L)
    draftSavedIndicator = false
  }

  fun getDisplayStr(rawVal: String?): String {
    val lit = rawVal?.toDoubleOrNull() ?: return ""
    if (lit == 0.0 && rawVal.isNullOrBlank()) return ""
    val disp = UnitUtils.toDisplay(lit, settings.unit)
    val dec = UnitUtils.defaultDecimals(settings.unit, settings)
    return if (dec <= 0) String.format(Locale.US, "%.0f", disp) else String.format(Locale.US, "%.${dec}f", disp)
  }

  fun parseInput(newVal: String): String {
    val typed = newVal.toDoubleOrNull() ?: return newVal
    val lit = UnitUtils.toLitres(typed, settings.unit)
    return lit.toString()
  }

  // Duration calculation & validation
  val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
  val durationInfo = remember(startTimeStr, endTimeStr) {
    try {
      val d1 = dateFormat.parse(startTimeStr.trim())
      val d2 = dateFormat.parse(endTimeStr.trim())
      if (d1 != null && d2 != null) {
        val diffMs = d2.time - d1.time
        if (diffMs > 0) {
          val totalMinutes = diffMs / (1000 * 60)
          val hours = totalMinutes / 60
          val mins = totalMinutes % 60
          val durationHours = totalMinutes / 60.0
          Triple(durationHours, "${hours}h ${mins}m", true)
        } else {
          Triple(0.0, "End time must be after start time", false)
        }
      } else {
        Triple(24.0, "24h 0m", true)
      }
    } catch (_: Exception) {
      Triple(24.0, "24h 0m", true)
    }
  }

  val durationHours = durationInfo.first
  val durationLabel = durationInfo.second
  val isTimeValid = durationInfo.third

  // Time-based auto-sync & chaining logic
  fun checkAndSyncTanks() {
    val updatedInputs = tankInputs.value.toMutableMap()
    val updatedSync = syncedStatus.value.toMutableMap()

    for (tName in selectedTankNames) {
      val currentMap = updatedInputs[tName]?.toMutableMap() ?: mutableMapOf()
      var initSynced = false
      var finalSynced = false

      // 1. Chaining: check if previous report ended at startTimeStr
      val chainedReading = repository.findChainedFinalReading(tName, startTimeStr)
      if (chainedReading != null && currentMap["initTov"].isNullOrBlank()) {
        val disp = UnitUtils.toDisplay(chainedReading.tov, settings.unit)
        currentMap["initTov"] = String.format(Locale.US, "%.${UnitUtils.defaultDecimals(settings.unit, settings)}f", disp)
        if (chainedReading.density15 != null) currentMap["density"] = chainedReading.density15.toString()
        if (chainedReading.temp != null) currentMap["initTemp"] = chainedReading.temp.toString()
        initSynced = true
      } else {
        val startReading = repository.findReading(tName, startTimeStr)
        if (startReading != null && currentMap["initTov"].isNullOrBlank()) {
          val disp = UnitUtils.toDisplay(startReading.tov, settings.unit)
          currentMap["initTov"] = String.format(Locale.US, "%.${UnitUtils.defaultDecimals(settings.unit, settings)}f", disp)
          if (startReading.density15 != null) currentMap["density"] = startReading.density15.toString()
          if (startReading.temp != null) currentMap["initTemp"] = startReading.temp.toString()
          initSynced = true
        }
      }

      // Check end reading
      val endReading = repository.findReading(tName, endTimeStr)
      if (endReading != null && currentMap["finalTov"].isNullOrBlank()) {
        val disp = UnitUtils.toDisplay(endReading.tov, settings.unit)
        currentMap["finalTov"] = String.format(Locale.US, "%.${UnitUtils.defaultDecimals(settings.unit, settings)}f", disp)
        if (endReading.temp != null) currentMap["finalTemp"] = endReading.temp.toString()
        finalSynced = true
      }

      // Pre-fill last density if density is empty
      if (currentMap["density"].isNullOrBlank()) {
        val lastDensity = repository.getTankDensity(tName)
        currentMap["density"] = lastDensity.toString()
      }

      updatedInputs[tName] = currentMap
      updatedSync[tName] = Pair(initSynced, finalSynced)
    }
    tankInputs.value = updatedInputs
    syncedStatus.value = updatedSync
  }

  // Trigger sync check when period or selected tanks change
  LaunchedEffect(startTimeStr, endTimeStr, selectedTankNames) {
    checkAndSyncTanks()
  }

  // Check if an exact matching saved report exists
  val matchingSavedReport = remember(startTimeStr, endTimeStr, savedReports) {
    repository.findExactReport(startTimeStr, endTimeStr)
  }

  // Active selected tanks
  val activeFeedTanks = feedTanks.filter { selectedTankNames.contains(it.name) }
  val activeProdTanks = productTanks.filter { selectedTankNames.contains(it.name) }

  // Build inputs for calculation engine
  val feedCalcInputs = activeFeedTanks.map { tank ->
    val map = tankInputs.value[tank.name] ?: emptyMap()
    FeedTankCalcInput(
      tankName = tank.name,
      initialTov = map["initTov"]?.toDoubleOrNull() ?: 0.0,
      finalTov = map["finalTov"]?.toDoubleOrNull() ?: 0.0,
      receivingAmount = map["receiving"]?.toDoubleOrNull() ?: 0.0,
      density15 = map["density"]?.toDoubleOrNull() ?: repository.getTankDensity(tank.name),
      initialTemp = map["initTemp"]?.toDoubleOrNull() ?: 25.0,
      finalTemp = map["finalTemp"]?.toDoubleOrNull() ?: 25.0,
      isInitialSynced = syncedStatus.value[tank.name]?.first ?: false,
      isFinalSynced = syncedStatus.value[tank.name]?.second ?: false
    )
  }

  val prodCalcInputs = activeProdTanks.map { tank ->
    val map = tankInputs.value[tank.name] ?: emptyMap()
    val lines = blendingLinesState.value[tank.name] ?: emptyList()
    // Backward compatibility if older single "blending" exists in map and lines empty
    val effectiveLines = if (lines.isEmpty() && (map["blending"]?.toDoubleOrNull() ?: 0.0) > 0.0) {
      listOf(BlendingLineInput(amount = map["blending"]!!.toDouble()))
    } else {
      lines
    }
    ProductTankCalcInput(
      tankName = tank.name,
      initialTov = map["initTov"]?.toDoubleOrNull() ?: 0.0,
      finalTov = map["finalTov"]?.toDoubleOrNull() ?: 0.0,
      blendingLines = effectiveLines,
      deliveryAmount = map["delivery"]?.toDoubleOrNull() ?: 0.0,
      density15 = map["density"]?.toDoubleOrNull() ?: repository.getTankDensity(tank.name),
      initialTemp = map["initTemp"]?.toDoubleOrNull() ?: 25.0,
      finalTemp = map["finalTemp"]?.toDoubleOrNull() ?: 25.0,
      productId = tank.productId,
      isInitialSynced = syncedStatus.value[tank.name]?.first ?: false,
      isFinalSynced = syncedStatus.value[tank.name]?.second ?: false
    )
  }

  // Pure Calculation Engine call
  val computationResult = remember(
    mode, startTimeStr, endTimeStr, durationHours,
    feedCalcInputs, prodCalcInputs, products, settings
  ) {
    LossEngine.computeLoss(
      LossComputationInput(
        mode = mode,
        startTime = startTimeStr,
        endTime = endTimeStr,
        durationHours = durationHours,
        feedTanks = feedCalcInputs,
        productTanks = prodCalcInputs,
        products = products,
        vcfTableType = settings.vcfTableType,
        roundVcfTo4Decimals = settings.roundVcfTo4Decimals,
        roundTempTo025 = settings.roundTempTo025,
        lossThresholdPercent = settings.lossThresholdPercent
      )
    )
  }

  val feedCalculations = computationResult.feedTanks
  val prodCalculations = computationResult.productTanks
  val totalFeed = computationResult.totalFeed
  val totalProduct = computationResult.totalProduct
  val loss = computationResult.loss
  val lossPercent = computationResult.lossPercent
  val yieldPercent = computationResult.yieldPercent
  val flag = computationResult.flag
  val feedRatePerHour = computationResult.feedRatePerHour
  val productRatePerHour = computationResult.productRatePerHour
  val lossRatePerHour = computationResult.lossRatePerHour

  // Date/Time picker helper
  fun showDateTimePicker(currentVal: String, onSelect: (String) -> Unit) {
    try {
      val parsed = dateFormat.parse(currentVal) ?: Date()
      val c = Calendar.getInstance().apply { time = parsed }
      DatePickerDialog(
        context,
        { _, y, m, d ->
          TimePickerDialog(
            context,
            { _, hour, min ->
              val newCal = Calendar.getInstance().apply {
                set(y, m, d, hour, min)
              }
              onSelect(dateFormat.format(newCal.time))
            },
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE),
            true
          ).show()
        },
        c.get(Calendar.YEAR),
        c.get(Calendar.MONTH),
        c.get(Calendar.DAY_OF_MONTH)
      ).show()
    } catch (_: Exception) {
      Toast.makeText(context, "Error opening date picker", Toast.LENGTH_SHORT).show()
    }
  }

  var showLossMenu by remember { mutableStateOf(false) }

  AppScreen(
    title = "Process Loss",
    subtitle = if (draftSavedIndicator) "Draft saved" else null,
    actions = {
      UnitQuickSwitchChip(
        currentUnit = settings.unit,
        onUnitChange = { repository.updateSettings(settings.copy(unit = it)) },
        modifier = Modifier.padding(end = 2.dp)
      )
      IconButton(onClick = {
        if (activeFeedTanks.isEmpty() && activeProdTanks.isEmpty()) {
          Toast.makeText(context, "Please select tanks first", Toast.LENGTH_SHORT).show()
          return@IconButton
        }
        val report = computationResult.toLossReport(editingReportId ?: UUID.randomUUID().toString())
        repository.addOrUpdateLossReport(report)
        repository.deleteDraft("loss_calc")
        editingReportId = report.id
        Toast.makeText(context, "Report saved and readings synced!", Toast.LENGTH_SHORT).show()
      }) {
        Icon(Icons.Default.Save, contentDescription = "Save Report")
      }
      Box {
        IconButton(onClick = { showLossMenu = true }) {
          Icon(Icons.Default.MoreVert, contentDescription = "More Actions")
        }
        DropdownMenu(
          expanded = showLossMenu,
          onDismissRequest = { showLossMenu = false }
        ) {
          DropdownMenuItem(
            text = { Text("Saved Reports (${savedReports.size})") },
            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
            onClick = {
              showLossMenu = false
              showSavedReportsSheet = true
            }
          )
          DropdownMenuItem(
            text = { Text("Clear / New Report") },
            leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) },
            onClick = {
              showLossMenu = false
              showClearConfirm = true
            }
          )
        }
      }
    }
  ) {
    Column(
      modifier = Modifier.fillMaxSize()
    ) {
      // 1. Compact Sticky Top Bar with Header details & Live Loss / Loss % / Yield & Product Yield summary
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              "Condensate Fractionation Plant",
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              if (mode == CalculationMode.TEMP_30C) "Standard (30 °C, ASTM D1250-80)" else "Natural (Ambient)",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onPrimaryContainer
            )
          }
          Text(
            "Period: $startTimeStr to $endTimeStr ($durationLabel)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
          )
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                if (loss < 0) "Process Gain (+)" else "Process Loss (-)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                UnitUtils.formatVolume(abs(loss), settings.unit, settings = settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (loss < 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
              )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Loss %", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
              Text(
                "${String.format(Locale.US, "%.2f", lossPercent)}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (loss < 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
              )
            }

            Column(horizontalAlignment = Alignment.End) {
              Text("Yield %", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
              Text(
                "${String.format(Locale.US, "%.1f", yieldPercent)}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }

          // Sticky summary of products if available
          if (computationResult.productYields.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              computationResult.productYields.take(4).forEach { py ->
                val pctStr = if (py.percentOfFeed != null) String.format(Locale.US, "%.1f%%", py.percentOfFeed) else "-"
                Text(
                  "${py.shortCode}: $pctStr",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = parseHexColor(py.colorHex, MaterialTheme.colorScheme.onPrimaryContainer)
                )
              }
            }
          }
        }
      }

      // Single Scrollable Page Content
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
      ) {
        // Prominent Header Card
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Refinery / Plant: Condensate Fractionation Plant", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                  Text("CONDENSATE FRACTIONATION PROCESS LOSS REPORT", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
                }
                AssistChip(
                  onClick = {},
                  label = { Text(if (mode == CalculationMode.TEMP_30C) "30 °C Mode" else "Natural Mode") }
                )
              }
              HorizontalDivider()
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Mode: ${if (mode == CalculationMode.TEMP_30C) "Standard (30 °C, ASTM D1250-80)" else "Natural (Ambient)"}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text("Prepared by: Process Engineer", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
              }
              Text("Period: $startTimeStr to $endTimeStr (Duration: $durationLabel)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
              val curReportId = remember(editingReportId, startTimeStr) {
                editingReportId?.let { "LR-" + it.replace("-", "").take(12).uppercase() }
                  ?: ("LR-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date()))
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Report ID: $curReportId", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text("Generated on: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}", style = MaterialTheme.typography.bodySmall)
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Status Flag: $flag", fontWeight = FontWeight.Bold, color = if (flag.contains("GREEN")) Color(0xFF10B981) else if (flag.contains("AMBER")) Color(0xFFF59E0B) else Color(0xFFEF4444), style = MaterialTheme.typography.bodySmall)
                Text("Display Unit: ${UnitUtils.unitLabel(settings.unit)}", style = MaterialTheme.typography.bodySmall)
              }
              Text("Generated by Tank Calc | Developed by Meherab Hossain Jihad", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
          }
        }

        // Warnings Banner (Amber)
        if (computationResult.warnings.isNotEmpty()) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFF856404), modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Blending & Period Warnings", fontWeight = FontWeight.Bold, color = Color(0xFF856404), style = MaterialTheme.typography.titleSmall)
                }
                computationResult.warnings.forEach { w ->
                  Text("• $w", color = Color(0xFF856404), style = MaterialTheme.typography.bodySmall)
                }
              }
            }
          }
        }

        // Banner if matching saved report exists
        if (matchingSavedReport != null && editingReportId != matchingSavedReport.id) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Saved report found for this period", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                  Text("Report from ${matchingSavedReport.timestamp}", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                  onClick = {
                    editingReportId = matchingSavedReport.id
                    mode = matchingSavedReport.mode
                    val repTanks = (matchingSavedReport.feedTanks + matchingSavedReport.productTanks).map { it.tankName }.toSet()
                    selectedTankNames = repTanks

                    val newMap = tankInputs.value.toMutableMap()
                    val newBlendLines = mutableMapOf<String, List<BlendingLineInput>>()

                    for (f in matchingSavedReport.feedTanks) {
                      newMap[f.tankName] = mapOf(
                        "initTov" to f.initialTov.toString(),
                        "finalTov" to f.finalTov.toString(),
                        "receiving" to f.receivingAmount.toString(),
                        "density" to (f.density15?.toString() ?: "750.0"),
                        "initTemp" to (f.initialTemp?.toString() ?: "25.0"),
                        "finalTemp" to (f.finalTemp?.toString() ?: "25.0")
                      )
                    }
                    for (p in matchingSavedReport.productTanks) {
                      newMap[p.tankName] = mapOf(
                        "initTov" to p.initialTov.toString(),
                        "finalTov" to p.finalTov.toString(),
                        "delivery" to p.deliveryAmount.toString(),
                        "density" to (p.density15?.toString() ?: "750.0"),
                        "initTemp" to (p.initialTemp?.toString() ?: "25.0"),
                        "finalTemp" to (p.finalTemp?.toString() ?: "25.0")
                      )
                      if (p.blendingLines.isNotEmpty()) {
                        newBlendLines[p.tankName] = p.blendingLines.map {
                          BlendingLineInput(id = it.id, amount = it.amount, destinationTankName = it.destinationTankName, time = it.time)
                        }
                      } else if (p.blendingAmount > 0) {
                        newBlendLines[p.tankName] = listOf(BlendingLineInput(amount = p.blendingAmount))
                      }
                    }
                    tankInputs.value = newMap
                    blendingLinesState.value = newBlendLines
                    Toast.makeText(context, "Loaded saved report", Toast.LENGTH_SHORT).show()
                  },
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                  Text("Load Report")
                }
              }
            }
          }
        }

        // Section 1: Mode Toggle
        item {
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
              horizontalArrangement = Arrangement.Center
            ) {
              SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                  selected = mode == CalculationMode.NORMAL,
                  onClick = { mode = CalculationMode.NORMAL },
                  shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                  Text("Natural Mode")
                }
                SegmentedButton(
                  selected = mode == CalculationMode.TEMP_30C,
                  onClick = { mode = CalculationMode.TEMP_30C },
                  shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                  Text("30 °C Mode")
                }
              }
            }
          }
        }

        // Section 2: Period Range
        item {
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text("Period Range", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedCard(
                  onClick = { showDateTimePicker(startTimeStr) { startTimeStr = it } },
                  modifier = Modifier.weight(1f)
                ) {
                  Column(modifier = Modifier.padding(10.dp)) {
                    Text("Start Date/Time", style = MaterialTheme.typography.labelSmall)
                    Text(startTimeStr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                  }
                }

                OutlinedCard(
                  onClick = { showDateTimePicker(endTimeStr) { endTimeStr = it } },
                  modifier = Modifier.weight(1f)
                ) {
                  Column(modifier = Modifier.padding(10.dp)) {
                    Text("End Date/Time", style = MaterialTheme.typography.labelSmall)
                    Text(endTimeStr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                  }
                }
              }

              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Calculated Duration: $durationLabel", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (!isTimeValid) {
                  Text("Invalid range", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }
        }

        // Section 3: Tank Selection Button
        item {
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Active Period Tanks", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(
                  "Selected: ${selectedTankNames.size} tanks (${activeFeedTanks.size} Feed, ${activeProdTanks.size} Product)",
                  style = MaterialTheme.typography.bodySmall
                )
              }
              OutlinedButton(onClick = { showTankPicker = true }) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Select Tanks")
              }
            }
          }
        }

        // Section 4: Feed Tank Cards
        if (activeFeedTanks.isNotEmpty()) {
          item {
            Text("Feed Tanks (${activeFeedTanks.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
          }

          items(activeFeedTanks) { tank ->
            val map = tankInputs.value[tank.name] ?: emptyMap()
            val initTov = getDisplayStr(map["initTov"])
            val finalTov = getDisplayStr(map["finalTov"])
            val rec = getDisplayStr(map["receiving"])
            val density = map["density"] ?: repository.getTankDensity(tank.name).toString()
            val initTemp = map["initTemp"] ?: "25.0"
            val finalTemp = map["finalTemp"] ?: "25.0"

            val isInitSynced = syncedStatus.value[tank.name]?.first == true
            val isFinalSynced = syncedStatus.value[tank.name]?.second == true

            val calcEntry = feedCalculations.find { it.tankName == tank.name }

            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(tank.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                  }
                  Text(
                    "Consumed: ${UnitUtils.formatVolume(calcEntry?.netQuantity ?: 0.0, settings.unit, settings = settings)}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                  )
                }
                HorizontalDivider()

                // Initial TOV
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                  OutlinedTextField(
                    value = initTov,
                    onValueChange = { newVal ->
                      val updated = map.toMutableMap().apply { put("initTov", parseInput(newVal)) }
                      tankInputs.value = tankInputs.value.toMutableMap().apply { put(tank.name, updated) }
                      syncedStatus.value = syncedStatus.value.toMutableMap().apply { put(tank.name, Pair(false, isFinalSynced)) }
                    },
                    label = { Text("Initial TOV (${UnitUtils.unitLabel(settings.unit)})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                      if (isInitSynced) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                          Text("SYNCED", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                      }
                    }
                  )
                  Button(onClick = {
                    dipHelperTankName = tank.name
                    dipHelperIsInitial = true
                  }) {
                    Text("Use Dip")
                  }
                }

                // Final TOV
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                  OutlinedTextField(
                    value = finalTov,
                    onValueChange = { newVal ->
                      val updated = map.toMutableMap().apply { put("finalTov", parseInput(newVal)) }
                      tankInputs.value = tankInputs.value.toMutableMap().apply { put(tank.name, updated) }
                      syncedStatus.value = syncedStatus.value.toMutableMap().apply { put(tank.name, Pair(isInitSynced, false)) }
                    },
                    label = { Text("Final TOV (${UnitUtils.unitLabel(settings.unit)})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                      if (isFinalSynced) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                          Text("SYNCED", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                      }
                    }
                  )
                  Button(onClick = {
                    dipHelperTankName = tank.name
                    dipHelperIsInitial = false
                  }) {
                    Text("Use Dip")
                  }
                }

                // Receiving
                OutlinedTextField(
                  value = rec,
                  onValueChange = { newVal ->
                    val updated = map.toMutableMap().apply { put("receiving", parseInput(newVal)) }
                    tankInputs.value = tankInputs.value.toMutableMap().apply { put(tank.name, updated) }
                  },
                  label = { Text("Receiving Amount (${UnitUtils.unitLabel(settings.unit)})") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  modifier = Modifier.fillMaxWidth()
                )

                // 30°C mode fields
                if (mode == CalculationMode.TEMP_30C) {
                  HorizontalDivider()
                  Text("Temperature & Density (@15°C)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                      value = density,
                      onValueChange = { newVal ->
                        val updated = map.toMutableMap().apply { put("density", newVal) }
                        tankInputs.value = tankInputs.value.toMutableMap().apply { put(tank.name, updated) }
                        newVal.toDoubleOrNull()?.let { repository.setTankDensity(tank.name, it) }
                      },
                      label = { Text("Density @15°C") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                      modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                      value = initTemp,
                      onValueChange = { newVal ->
                        val updated = map.toMutableMap().apply { put("initTemp", newVal) }
                        tankInputs.value = tankInputs.value.toMutableMap().apply { put(tank.name, updated) }
                      },
                      label = { Text("Init Temp °C") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                      modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                      value = finalTemp,
                      onValueChange = { newVal ->
                        val updated = map.toMutableMap().apply { put("finalTemp", newVal) }
                        tankInputs.value = tankInputs.value.toMutableMap().apply { put(tank.name, updated) }
                      },
                      label = { Text("Final Temp °C") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                      modifier = Modifier.weight(1f)
                    )
                  }

                  if (calcEntry != null && calcEntry.initialVcf != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                      Text("VCF Init: ${calcEntry.initialVcf} | V@30: ${UnitUtils.formatVolume(calcEntry.initialTov30 ?: 0.0, settings.unit, settings = settings)}", style = MaterialTheme.typography.bodySmall)
                      Text("VCF Final: ${calcEntry.finalVcf} | V@30: ${UnitUtils.formatVolume(calcEntry.finalTov30 ?: 0.0, settings.unit, settings = settings)}", style = MaterialTheme.typography.bodySmall)
                    }
                  }
                }
              }
            }
          }
        }

        // Section 5: Helper Button for Blends from Saved Records
        item {
          OutlinedButton(
            onClick = { showBlendRecordsHelper = true },
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.CallMerge, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add blends from saved blend records in this period")
          }
        }

        // Section 6: Product Tanks Grouped by Product
        if (activeProdTanks.isNotEmpty()) {
          item {
            Text(
              "Product Tanks (${activeProdTanks.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.secondary
            )
          }

          // Group by product
          val groupedProdTanks = activeProdTanks.groupBy { tank ->
            products.find { it.id == tank.productId || it.tankIds.contains(tank.id) }
          }

          // Products that have running tanks
          for ((prod, pTanks) in groupedProdTanks) {
            if (prod != null) {
              item {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductBadge(name = prod.name, shortCode = prod.shortCode, colorHex = prod.colorHex)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      "(${pTanks.size} running tanks)",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Text(
                    if (prod.category == ProductCategory.PRODUCT) "Product" else "Intermediate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            } else {
              item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductBadge(name = "Unassigned", shortCode = "UNASGN", colorHex = "#757575")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      "(${pTanks.size} tanks)",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Text(
                    "Hint: Link these tanks to products in Setup > Tanks / Products for automated yield grouping.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                  )
                }
              }
            }

            items(pTanks) { tank ->
              val map = tankInputs.value[tank.name] ?: emptyMap()
              val initTov = getDisplayStr(map["initTov"])
              val finalTov = getDisplayStr(map["finalTov"])
              val del = getDisplayStr(map["delivery"])
              val density = map["density"] ?: repository.getTankDensity(tank.name).toString()
              val initTemp = map["initTemp"] ?: "25.0"
              val finalTemp = map["finalTemp"] ?: "25.0"

              val isInitSynced = syncedStatus.value[tank.name]?.first == true
              val isFinalSynced = syncedStatus.value[tank.name]?.second == true

              val calcEntry = prodCalculations.find { it.tankName == tank.name }
              val currentLines = blendingLinesState.value[tank.name] ?: emptyList()
              val otherProdTanks = activeProdTanks.filter { it.name != tank.name }

              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                      Text(tank.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                      if (prod != null) {
                        ProductBadge(name = prod.name, shortCode = prod.shortCode, colorHex = prod.colorHex)
                      }
                    }
                    Text(
                      "Produced: ${UnitUtils.formatVolume(calcEntry?.netQuantity ?: 0.0, settings.unit, settings = settings)}",
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.secondary
                    )
                  }

                  // Transparent effective initial adjustment box
                  if (calcEntry != null && calcEntry.blendedInAmount > 0) {
                    Surface(
                      color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Icon(
                          Icons.Default.CallMerge,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.secondary,
                          modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                          calcEntry.effectiveInitialNote
                            ?: "Initial TOV ${String.format(Locale.US, "%,.0f", calcEntry.initialTov)} + blended in ${String.format(Locale.US, "%,.0f", calcEntry.blendedInAmount)} = effective initial ${String.format(Locale.US, "%,.0f", calcEntry.effectiveInitialTov ?: (calcEntry.initialTov + calcEntry.blendedInAmount))}",
                          style = MaterialTheme.typography.bodySmall,
                          fontWeight = FontWeight.SemiBold,
                          color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                      }
                    }
                  }

                  HorizontalDivider()

                  // Initial TOV
                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                      value = initTov,
                      onValueChange = { newVal ->
                        val updated = map.toMutableMap().apply { put("initTov", parseInput(newVal)) }
                        tankInputs.value = tankInputs.value.toMutableMap().apply { put(tank.name, updated) }
                        syncedStatus.value = syncedStatus.value.toMutableMap().apply { put(tank.name, Pair(false, isFinalSynced)) }
                      },
                      label = { Text("Initial TOV (${UnitUtils.unitLabel(settings.unit)})") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                      modifier = Modifier.weight(1f),
                      trailingIcon = {
                        if (isInitSynced) {
                          Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                            Text("SYNCED", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                          }
                        }
                      }
                    )
                    Button(onClick = {
                      dipHelperTankName = tank.name
                      dipHelperIsInitial = true
                    }) {
                      Text("Use Dip")
                    }
                  }

                  // Final TOV
                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                      value = finalTov,
                      onValueChange = { newVal ->
                        val updated = map.toMutableMap().apply { put("finalTov", parseInput(newVal)) }
                        tankInputs.value = tankInputs.value.toMutableMap().apply { put(tank.name, updated) }
                        syncedStatus.value = syncedStatus.value.toMutableMap().apply { put(tank.name, Pair(isInitSynced, false)) }
                      },
                      label = { Text("Final TOV (${UnitUtils.unitLabel(settings.unit)})") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                      modifier = Modifier.weight(1f),
                      trailingIcon = {
                        if (isFinalSynced) {
                          Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                            Text("SYNCED", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                          }
                        }
                      }
                    )
                    Button(onClick = {
                      dipHelperTankName = tank.name
                      dipHelperIsInitial = false
                    }) {
                      Text("Use Dip")
                    }
                  }

                  // Delivery input
                  OutlinedTextField(
                    value = del,
                    onValueChange = { newVal ->
                      val updated = map.toMutableMap().apply { put("delivery", parseInput(newVal)) }
                      tankInputs.value = tankInputs.value.toMutableMap().apply { put(tank.name, updated) }
                    },
                    label = { Text("Delivery Amount (${UnitUtils.unitLabel(settings.unit)})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                  )

                  // Blending Lines Section
                  HorizontalDivider()
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      "Blending Lines (${currentLines.size})",
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.secondary
                    )
                    TextButton(onClick = {
                      val newLine = BlendingLineInput(amount = 0.0)
                      val updated = currentLines + newLine
                      blendingLinesState.value = blendingLinesState.value.toMutableMap().apply { put(tank.name, updated) }
                    }) {
                      Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Add Blend Line")
                    }
                  }

                  if (currentLines.isEmpty()) {
                    Text("No blending transfers recorded for this tank.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                  } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      currentLines.forEachIndexed { idx, bLine ->
                        BlendingLineEditor(
                          line = bLine,
                          activeUnit = settings.unit,
                          availableDestinations = otherProdTanks.map { it.name },
                          onUpdate = { updatedLine ->
                            val updatedList = currentLines.toMutableList().apply { set(idx, updatedLine) }
                            blendingLinesState.value = blendingLinesState.value.toMutableMap().apply { put(tank.name, updatedList) }
                          },
                          onDelete = {
                            val updatedList = currentLines.toMutableList().apply { removeAt(idx) }
                            blendingLinesState.value = blendingLinesState.value.toMutableMap().apply { put(tank.name, updatedList) }
                          }
                        )
                      }
                    }
                  }

                  // 30°C mode fields
                  if (mode == CalculationMode.TEMP_30C) {
                    HorizontalDivider()
                    Text("Temperature & Density (@15°C)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      OutlinedTextField(
                        value = density,
                        onValueChange = { newVal ->
                          val updated = map.toMutableMap().apply { put("density", newVal) }
                          tankInputs.value = tankInputs.value.toMutableMap().apply { put(tank.name, updated) }
                          newVal.toDoubleOrNull()?.let { repository.setTankDensity(tank.name, it) }
                        },
                        label = { Text("Density @15°C") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                      )
                      OutlinedTextField(
                        value = initTemp,
                        onValueChange = { newVal ->
                          val updated = map.toMutableMap().apply { put("initTemp", newVal) }
                          tankInputs.value = tankInputs.value.toMutableMap().apply { put(tank.name, updated) }
                        },
                        label = { Text("Init Temp °C") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                      )
                      OutlinedTextField(
                        value = finalTemp,
                        onValueChange = { newVal ->
                          val updated = map.toMutableMap().apply { put("finalTemp", newVal) }
                          tankInputs.value = tankInputs.value.toMutableMap().apply { put(tank.name, updated) }
                        },
                        label = { Text("Final Temp °C") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                      )
                    }

                    if (calcEntry != null && calcEntry.initialVcf != null) {
                      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("VCF Init: ${calcEntry.initialVcf} | V@30: ${UnitUtils.formatVolume(calcEntry.initialTov30 ?: 0.0, settings.unit, settings = settings)}", style = MaterialTheme.typography.bodySmall)
                        Text("VCF Final: ${calcEntry.finalVcf} | V@30: ${UnitUtils.formatVolume(calcEntry.finalTov30 ?: 0.0, settings.unit, settings = settings)}", style = MaterialTheme.typography.bodySmall)
                      }
                    }
                  }
                }
              }
            }
          }
        }

        // Section 7: Product Yield Card (Feed consumption basis)
        item {
          ProductYieldCard(
            computationResult = computationResult,
            unit = settings.unit,
            settings = settings
          )
        }

        // Section 8: Product Tank Ratios (share of total product)
        if (prodCalculations.isNotEmpty() && totalProduct > 0) {
          item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Product Tank Production Ratios (Share of Total Product)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                for (entry in prodCalculations) {
                  val ratio = (entry.netQuantity / totalProduct) * 100.0
                  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.tankName, fontWeight = FontWeight.SemiBold)
                        if (entry.productName != null) {
                          Spacer(modifier = Modifier.width(6.dp))
                          ProductBadge(name = entry.productName, shortCode = entry.productShortCode, colorHex = entry.productColorHex)
                        }
                      }
                      Text("${String.format(Locale.US, "%.1f", ratio)}% (${UnitUtils.formatVolume(entry.netQuantity, settings.unit, settings = settings)})")
                    }
                    LinearProgressIndicator(
                      progress = { (ratio / 100.0).toFloat().coerceIn(0f, 1f) },
                      color = MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.fillMaxWidth()
                    )
                  }
                }
              }
            }
          }
        }

        // Section 9: Share, Save & Export Buttons
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = {
                  val report = computationResult.toLossReport(editingReportId ?: UUID.randomUUID().toString())
                  val periodTitle = ExportUtils.getPeriodReportTitle(report.startTime, report.endTime)
                  val text = ExportUtils.formatLossReport(report, unit = settings.unit, settings = settings)
                  ExportUtils.shareText(context, text, periodTitle)
                },
                modifier = Modifier.weight(1f)
              ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Text")
              }

              Button(
                onClick = {
                  val report = computationResult.toLossReport(editingReportId ?: UUID.randomUUID().toString())
                  repository.addOrUpdateLossReport(report)
                  editingReportId = report.id
                  val text = ExportUtils.formatLossReport(report, unit = settings.unit, settings = settings)
                  ExportUtils.copyToClipboard(context, text, "Loss Report")
                  Toast.makeText(context, "Saved & Copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
              ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save & Copy")
              }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = {
                  val report = computationResult.toLossReport(editingReportId ?: UUID.randomUUID().toString())
                  val intent = ExportUtils.exportLossReportCsvFile(context, report, unit = settings.unit, settings = settings)
                  if (intent != null) {
                    context.startActivity(Intent.createChooser(intent, "Export Process Loss Excel / CSV"))
                  } else {
                    Toast.makeText(context, "Error generating CSV export", Toast.LENGTH_SHORT).show()
                  }
                },
                modifier = Modifier.weight(1f)
              ) {
                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export Excel")
              }

              OutlinedButton(
                onClick = {
                  val report = computationResult.toLossReport(editingReportId ?: UUID.randomUUID().toString())
                  val intent = ExportUtils.exportLossReportPdfFile(context, report, unit = settings.unit, settings = settings)
                  if (intent != null) {
                    context.startActivity(Intent.createChooser(intent, "Export Process Loss PDF"))
                  } else {
                    Toast.makeText(context, "Error generating PDF export", Toast.LENGTH_SHORT).show()
                  }
                },
                modifier = Modifier.weight(1f)
              ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export PDF")
              }
            }

            Button(
              onClick = {
                val report = computationResult.toLossReport(editingReportId ?: UUID.randomUUID().toString())
                val waText = ExportUtils.formatLossReportWhatsApp(report, unit = settings.unit, settings = settings)
                ExportUtils.shareText(context, waText, "WhatsApp / Telegram Shift Summary")
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Quick WhatsApp / Telegram Summary", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    // Modal Tank Picker
    if (showTankPicker) {
      TankPickerSheet(
        tanks = tanks.filter { it.type != TankType.N2_STORAGE },
        selectedTankNames = selectedTankNames,
        products = products,
        isMultiSelect = true,
        onDismiss = { showTankPicker = false },
        onMultiSelected = { newSet ->
          selectedTankNames = newSet
        }
      )
    }

    // Helper: Add blends from saved records dialog
    if (showBlendRecordsHelper) {
      AddBlendsFromRecordsDialog(
        startTimeStr = startTimeStr,
        endTimeStr = endTimeStr,
        blendRecords = blendRecords,
        products = products,
        allProdTanks = activeProdTanks,
        mode = mode,
        onDismiss = { showBlendRecordsHelper = false },
        onApply = { newLinesByTank ->
          val updated = blendingLinesState.value.toMutableMap()
          for ((tName, lines) in newLinesByTank) {
            val existing = updated[tName] ?: emptyList()
            updated[tName] = existing + lines
          }
          blendingLinesState.value = updated
          showBlendRecordsHelper = false
          Toast.makeText(context, "Added blend lines from records!", Toast.LENGTH_SHORT).show()
        }
      )
    }

    // Dip Helper Dialog
    if (dipHelperTankName != null) {
      val tName = dipHelperTankName!!
      val tank = tanks.find { it.name == tName }
      AlertDialog(
        onDismissRequest = { dipHelperTankName = null },
        title = { Text("Calculate Volume from Dip ($tName)") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = dipHelperInput,
              onValueChange = { dipHelperInput = it },
              label = { Text("Dip (mm)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth()
            )
            val d = dipHelperInput.toDoubleOrNull() ?: 0.0
            val res = tank?.let { Calculators.calculateDipVolume(it, d, settings.invalidInputThreshold) }
            if (res != null) {
              Text("Calculated TOV: ${UnitUtils.formatVolume(res.totalVolume, settings.unit, settings = settings)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
          }
        },
        confirmButton = {
          Button(onClick = {
            val d = dipHelperInput.toDoubleOrNull() ?: 0.0
            val res = tank?.let { Calculators.calculateDipVolume(it, d, settings.invalidInputThreshold) }
            if (res != null) {
              val current = tankInputs.value[tName]?.toMutableMap() ?: mutableMapOf()
              if (dipHelperIsInitial) {
                current["initTov"] = String.format(Locale.US, "%.1f", res.totalVolume)
              } else {
                current["finalTov"] = String.format(Locale.US, "%.1f", res.totalVolume)
              }
              tankInputs.value = tankInputs.value.toMutableMap().apply { put(tName, current) }
            }
            dipHelperTankName = null
          }) {
            Text("Apply TOV")
          }
        },
        dismissButton = {
          TextButton(onClick = { dipHelperTankName = null }) { Text("Cancel") }
        }
      )
    }

    // Saved Reports Bottom Sheet
    if (showSavedReportsSheet) {
      ModalBottomSheet(onDismissRequest = { showSavedReportsSheet = false }) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Saved Loss Reports (${savedReports.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showSavedReportsSheet = false }) {
              Icon(Icons.Default.Close, contentDescription = "Close")
            }
          }

          if (savedReports.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text("No saved loss reports yet.")
            }
          } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              items(savedReports.sortedByDescending { it.timestamp }) { r ->
                Card(
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val periodTitle = ExportUtils.getPeriodReportTitle(r.startTime, r.endTime)
                    val shortReportId = "LR-" + r.id.replace("-", "").take(10).uppercase()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                      Text(periodTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                      AssistChip(
                        onClick = {},
                        label = { Text(r.flag, style = MaterialTheme.typography.labelSmall) }
                      )
                    }
                    Text("Report ID: $shortReportId | Mode: ${if (r.mode == CalculationMode.TEMP_30C) "30 °C Mode" else "Natural Mode"}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text("Period: ${r.startTime} to ${r.endTime} (${String.format(Locale.US, "%.1f", r.durationHours)} hrs)", style = MaterialTheme.typography.bodySmall)
                    Text("Loss: ${UnitUtils.formatVolume(abs(r.loss), settings.unit, settings = settings)} (${String.format(Locale.US, "%.2f", r.lossPercent)}%) | Yield: ${String.format(Locale.US, "%.1f", r.yieldPercent)}%", fontWeight = FontWeight.SemiBold, color = if (r.loss < 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)

                    if (r.productYields.isNotEmpty()) {
                      val pySummary = r.productYields.joinToString(" • ") { py ->
                        val pStr = py.percentOfFeed?.let { String.format(Locale.US, "%.1f%%", it) } ?: "-"
                        "${py.shortCode}: $pStr"
                      }
                      Text("Product Yields: $pySummary", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }

                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.End,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      // Open/Load report
                      TextButton(onClick = {
                        editingReportId = r.id
                        mode = r.mode
                        startTimeStr = r.startTime
                        endTimeStr = r.endTime
                        val repTanks = (r.feedTanks + r.productTanks).map { it.tankName }.toSet()
                        selectedTankNames = repTanks

                        val newMap = mutableMapOf<String, Map<String, String>>()
                        val newBlendLines = mutableMapOf<String, List<BlendingLineInput>>()

                        for (f in r.feedTanks) {
                          newMap[f.tankName] = mapOf(
                            "initTov" to f.initialTov.toString(),
                            "finalTov" to f.finalTov.toString(),
                            "receiving" to f.receivingAmount.toString(),
                            "density" to (f.density15?.toString() ?: "750.0"),
                            "initTemp" to (f.initialTemp?.toString() ?: "25.0"),
                            "finalTemp" to (f.finalTemp?.toString() ?: "25.0")
                          )
                        }
                        for (p in r.productTanks) {
                          newMap[p.tankName] = mapOf(
                            "initTov" to p.initialTov.toString(),
                            "finalTov" to p.finalTov.toString(),
                            "delivery" to p.deliveryAmount.toString(),
                            "density" to (p.density15?.toString() ?: "750.0"),
                            "initTemp" to (p.initialTemp?.toString() ?: "25.0"),
                            "finalTemp" to (p.finalTemp?.toString() ?: "25.0")
                          )
                          if (p.blendingLines.isNotEmpty()) {
                            newBlendLines[p.tankName] = p.blendingLines.map {
                              BlendingLineInput(id = it.id, amount = it.amount, destinationTankName = it.destinationTankName, time = it.time)
                            }
                          } else if (p.blendingAmount > 0) {
                            newBlendLines[p.tankName] = listOf(BlendingLineInput(amount = p.blendingAmount))
                          }
                        }
                        tankInputs.value = newMap
                        blendingLinesState.value = newBlendLines
                        showSavedReportsSheet = false
                        Toast.makeText(context, "Loaded report for editing", Toast.LENGTH_SHORT).show()
                      }) {
                        Text("Open")
                      }

                      // Duplicate report
                      TextButton(onClick = {
                        val dup = r.copy(id = UUID.randomUUID().toString(), timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                        repository.addOrUpdateLossReport(dup)
                        Toast.makeText(context, "Report duplicated", Toast.LENGTH_SHORT).show()
                      }) {
                        Text("Duplicate")
                      }

                      // WhatsApp / Telegram Quick Summary
                      TextButton(onClick = {
                        val waText = ExportUtils.formatLossReportWhatsApp(r, unit = settings.unit, settings = settings)
                        ExportUtils.shareText(context, waText, "WhatsApp Shift Summary - $periodTitle")
                      }) {
                        Text("WhatsApp")
                      }

                      // Share report
                      IconButton(onClick = {
                        val text = ExportUtils.formatLossReport(r, unit = settings.unit, settings = settings)
                        ExportUtils.shareText(context, text, periodTitle)
                      }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(20.dp))
                      }

                      // Delete report
                      IconButton(onClick = {
                        repository.deleteLossReport(r.id)
                        Toast.makeText(context, "Deleted report", Toast.LENGTH_SHORT).show()
                      }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Editor row for a single blending transfer line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlendingLineEditor(
  line: BlendingLineInput,
  activeUnit: UnitType,
  availableDestinations: List<String>,
  onUpdate: (BlendingLineInput) -> Unit,
  onDelete: () -> Unit
) {
  var amountInput by remember(line.amount) {
    val disp = UnitUtils.toDisplay(line.amount, activeUnit)
    mutableStateOf(if (line.amount == 0.0) "" else String.format(Locale.US, "%.1f", disp))
  }
  var timeInput by remember(line.time) { mutableStateOf(line.time ?: "") }
  var expandedDestPicker by remember { mutableStateOf(false) }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedTextField(
          value = amountInput,
          onValueChange = { newVal ->
            amountInput = newVal
            val typed = newVal.toDoubleOrNull() ?: 0.0
            val lit = UnitUtils.toLitres(typed, activeUnit)
            onUpdate(line.copy(amount = lit))
          },
          label = { Text("Amount (${UnitUtils.unitLabel(activeUnit)}) *") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.weight(1f),
          singleLine = true
        )

        OutlinedTextField(
          value = timeInput,
          onValueChange = { newVal ->
            timeInput = newVal
            onUpdate(line.copy(time = newVal.ifBlank { null }))
          },
          label = { Text("Time (HH:mm)") },
          placeholder = { Text("e.g. 14:30") },
          modifier = Modifier.width(115.dp),
          singleLine = true
        )

        IconButton(onClick = onDelete) {
          Icon(Icons.Default.Close, contentDescription = "Remove line", tint = MaterialTheme.colorScheme.error)
        }
      }

      // Destination Tank Selector
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        val destLabel = line.destinationTankName?.let { "Destination: $it" } ?: "Destination: None / External"
        Text(
          destLabel,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          color = if (line.destinationTankName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box {
          TextButton(onClick = { expandedDestPicker = true }) {
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Select Tank")
          }

          DropdownMenu(
            expanded = expandedDestPicker,
            onDismissRequest = { expandedDestPicker = false }
          ) {
            DropdownMenuItem(
              text = { Text("None / External Destination") },
              onClick = {
                onUpdate(line.copy(destinationTankName = null))
                expandedDestPicker = false
              }
            )
            availableDestinations.forEach { destName ->
              DropdownMenuItem(
                text = { Text(destName, fontWeight = FontWeight.Bold) },
                onClick = {
                  onUpdate(line.copy(destinationTankName = destName))
                  expandedDestPicker = false
                }
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Product Yield Card with horizontal progress bars and feed consumption basis results.
 */
@Composable
fun ProductYieldCard(
  computationResult: LossComputationResult,
  unit: UnitType,
  settings: AppSettings?
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          "Product Yield (Feed Consumption Basis)",
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary
        )
        if (computationResult.totalProductPercent != null) {
          Text(
            "Total Yield: ${String.format(Locale.US, "%.2f", computationResult.totalProductPercent)}%",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }

      HorizontalDivider()

      if (computationResult.productYields.isEmpty()) {
        Text("No product yield data available.", style = MaterialTheme.typography.bodySmall)
      } else {
        computationResult.productYields.forEach { py ->
          val pctOfFeed = py.percentOfFeed
          val pctStr = if (pctOfFeed != null) String.format(Locale.US, "%.2f%%", pctOfFeed) else "-"
          val barProgress = if (pctOfFeed != null) (pctOfFeed / 100.0).toFloat().coerceIn(0f, 1f) else 0f
          val color = parseHexColor(py.colorHex, MaterialTheme.colorScheme.primary)

          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                ProductBadge(name = py.productName, shortCode = py.shortCode, colorHex = py.colorHex)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  "(${py.tankNames.joinToString(", ")})",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  UnitUtils.formatVolume(py.productMade, unit, settings = settings),
                  fontWeight = FontWeight.Bold,
                  style = MaterialTheme.typography.bodyMedium
                )
                Text(
                  "$pctStr of feed",
                  fontWeight = FontWeight.SemiBold,
                  style = MaterialTheme.typography.labelSmall,
                  color = color
                )
              }
            }

            LinearProgressIndicator(
              progress = { barProgress },
              color = color,
              trackColor = color.copy(alpha = 0.2f),
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
            )

            // Tank breakdown within product
            if (py.tankBreakdown.size > 1) {
              val breakdownText = py.tankBreakdown.entries.joinToString(" • ") { (tName, pct) ->
                "$tName: ${String.format(Locale.US, "%.1f%%", pct)}"
              }
              Text(
                "Tank Share within ${py.productName}: $breakdownText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
              )
            }
          }
          Spacer(modifier = Modifier.height(2.dp))
        }

        HorizontalDivider()

        // Totals & Loss %
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column {
            Text("Total Product %", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            val totStr = if (computationResult.totalProductPercent != null) {
              "${String.format(Locale.US, "%.2f", computationResult.totalProductPercent)}%"
            } else "-"
            Text(totStr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
          }

          Column(horizontalAlignment = Alignment.End) {
            Text("Process Loss % (100% - Product %)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            val lossPctStr = if (computationResult.lossPercentOnFeed != null) {
              "${String.format(Locale.US, "%.2f", computationResult.lossPercentOnFeed)}%"
            } else "-"
            Text(
              lossPctStr,
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.bodyLarge,
              color = if ((computationResult.lossPercentOnFeed ?: 0.0) < 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            )
          }
        }
      }
    }
  }
}

/**
 * Dialog proposing blending lines from saved Transfer > Blend records in the period.
 */
data class ProposedBlendLine(
  val id: String = UUID.randomUUID().toString(),
  var isAccepted: Boolean = true,
  var sourceTankName: String,
  val destinationTankName: String,
  val amount: Double,
  val time: String,
  val componentName: String,
  val candidateTanks: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBlendsFromRecordsDialog(
  startTimeStr: String,
  endTimeStr: String,
  blendRecords: List<BlendRecord>,
  products: List<Product>,
  allProdTanks: List<Tank>,
  mode: CalculationMode,
  onDismiss: () -> Unit,
  onApply: (Map<String, List<BlendingLineInput>>) -> Unit
) {
  val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
  val periodStart = try { dateFormat.parse(startTimeStr.trim()) } catch (_: Exception) { null }
  val periodEnd = try { dateFormat.parse(endTimeStr.trim()) } catch (_: Exception) { null }

  // Filter records within period
  val recordsInPeriod = blendRecords.filter { br ->
    try {
      val d = dateFormat.parse(br.dateTime.trim())
      if (d != null && periodStart != null && periodEnd != null) {
        !d.before(periodStart) && !d.after(periodEnd)
      } else {
        true
      }
    } catch (_: Exception) {
      true
    }
  }

  // Build proposed lines
  val initialProposed = remember(recordsInPeriod, products, allProdTanks) {
    val list = mutableListOf<ProposedBlendLine>()
    for (rec in recordsInPeriod) {
      for (comp in rec.componentTransfers) {
        val amt = if (mode == CalculationMode.TEMP_30C && comp.transferred30L != null) comp.transferred30L else comp.transferredL
        if (amt > 0) {
          // Match product by componentName
          val matchedProd = products.find {
            it.name.equals(comp.componentName, ignoreCase = true) ||
            it.shortCode.equals(comp.componentName, ignoreCase = true) ||
            comp.stageName.contains(it.name, ignoreCase = true)
          }

          val candidateTanks = allProdTanks.filter {
            it.name != rec.tankName &&
            (it.productId == matchedProd?.id || matchedProd?.tankIds?.contains(it.id) == true)
          }.map { it.name }.ifEmpty {
            allProdTanks.filter { it.name != rec.tankName }.map { it.name }
          }

          val defaultSource = candidateTanks.firstOrNull() ?: ""
          if (defaultSource.isNotBlank()) {
            list.add(
              ProposedBlendLine(
                isAccepted = true,
                sourceTankName = defaultSource,
                destinationTankName = rec.tankName,
                amount = amt,
                time = rec.dateTime,
                componentName = comp.componentName.ifBlank { comp.stageName },
                candidateTanks = candidateTanks
              )
            )
          }
        }
      }
    }
    list
  }

  var proposedLines by remember { mutableStateOf<List<ProposedBlendLine>>(initialProposed) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column {
        Text("Add Blends from Saved Records", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(
          "Period: $startTimeStr to $endTimeStr (${recordsInPeriod.size} records found)",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    text = {
      if (proposedLines.isEmpty()) {
        Text("No blending transfers found in saved records for this period.", style = MaterialTheme.typography.bodyMedium)
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(proposedLines) { pLine ->
            var expandedPicker by remember { mutableStateOf(false) }

            Card(
              colors = CardDefaults.cardColors(
                containerColor = if (pLine.isAccepted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Checkbox(
                      checked = pLine.isAccepted,
                      onCheckedChange = { checked ->
                        proposedLines = proposedLines.map {
                          if (it.id == pLine.id) it.copy(isAccepted = checked) else it
                        }
                      }
                    )
                    Column {
                      Text(
                        "${pLine.componentName} -> ${pLine.destinationTankName}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                      )
                      Text(
                        "${String.format(Locale.US, "%,.0f", pLine.amount)} L (${pLine.time})",
                        style = MaterialTheme.typography.bodySmall
                      )
                    }
                  }
                }

                // Source Tank Selection
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Source Tank: ${pLine.sourceTankName}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                  if (pLine.candidateTanks.size > 1) {
                    Box {
                      TextButton(onClick = { expandedPicker = true }) {
                        Text("Change Tank")
                      }
                      DropdownMenu(
                        expanded = expandedPicker,
                        onDismissRequest = { expandedPicker = false }
                      ) {
                        pLine.candidateTanks.forEach { cand ->
                          DropdownMenuItem(
                            text = { Text(cand) },
                            onClick = {
                              proposedLines = proposedLines.map {
                                if (it.id == pLine.id) it.copy(sourceTankName = cand) else it
                              }
                              expandedPicker = false
                            }
                          )
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val result = mutableMapOf<String, MutableList<BlendingLineInput>>()
          for (p in proposedLines) {
            if (p.isAccepted && p.sourceTankName.isNotBlank()) {
              result.getOrPut(p.sourceTankName) { mutableListOf() }.add(
                BlendingLineInput(
                  amount = p.amount,
                  destinationTankName = p.destinationTankName,
                  time = p.time
                )
              )
            }
          }
          onApply(result)
        },
        enabled = proposedLines.any { it.isAccepted }
      ) {
        Text("Apply Accepted Blends")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
