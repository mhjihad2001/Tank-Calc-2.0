package com.example.ui.screens

import android.app.DatePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.engine.*
import com.example.ui.components.DualProductionLossCard
import com.example.ui.components.IndustrialKeypadDock
import com.example.ui.components.PasswordDialog
import com.example.ui.components.ProductBadge
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionReportScreen(repository: TankRepository) {
  val context = LocalContext.current
  val tanks by repository.tanks.collectAsState()
  val settings by repository.settings.collectAsState()
  val setup by repository.productionReportSetup.collectAsState()
  val savedDays by repository.productionReportDays.collectAsState()
  val blendRecords by repository.blendRecords.collectAsState()
  val products by repository.products.collectAsState()

  // Selected tab: 0=Input, 1=Natural, 2=30°C, 3=ERP, 4=Product %, 5=Setup
  var selectedTab by remember { mutableIntStateOf(0) }
  val tabTitles = listOf("Input", "Natural", "30 °C", "ERP (30 °C)", "Product %", "Setup")

  // Date states
  val todayCal = Calendar.getInstance()
  val prevCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
  val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

  var todayDateStr by remember { mutableStateOf(sdfDate.format(todayCal.time)) }
  var prevDateStr by remember { mutableStateOf(sdfDate.format(prevCal.time)) }

  // Snapshots for Today and Previous day
  var todaySnapshot by remember {
    mutableStateOf(
      repository.getProductionReportDay(todayDateStr) ?: ProductionReportDaySnapshot(
        dateStr = todayDateStr,
        reportTime = setup.constants.reportTime,
        otherData = ProductionReportOtherData()
      )
    )
  }

  var prevSnapshot by remember {
    mutableStateOf(
      repository.getProductionReportDay(prevDateStr) ?: ProductionReportDaySnapshot(
        dateStr = prevDateStr,
        reportTime = setup.constants.reportTime,
        otherData = ProductionReportOtherData()
      )
    )
  }

  // Pre-fill carried densities / splits if empty on date change
  fun loadOrInitializeDay(dateStr: String): ProductionReportDaySnapshot {
    val existing = repository.getProductionReportDay(dateStr)
    if (existing != null) return existing

    val latest = repository.getLatestSavedDay()
    val carriedOther = if (latest != null) {
      ProductionReportOtherData(
        rawCategorySplits = latest.otherData.rawCategorySplits,
        operatingLoads = latest.otherData.operatingLoads,
        feedCompositionText = latest.otherData.feedCompositionText
      )
    } else {
      ProductionReportOtherData()
    }

    val carriedEntries = mutableMapOf<String, ProductionReportTankEntry>()
    tanks.filter { it.includeInProductionReport }.forEach { tank ->
      val prevEntry = latest?.tankEntries?.get(tank.name)
      carriedEntries[tank.name] = ProductionReportTankEntry(
        tankName = tank.name,
        density15 = prevEntry?.density15 ?: 0.736,
        density30 = prevEntry?.density30 ?: 0.736,
        temperatureC = prevEntry?.temperatureC ?: 30.0
      )
    }

    return ProductionReportDaySnapshot(
      dateStr = dateStr,
      reportTime = setup.constants.reportTime,
      tankEntries = carriedEntries,
      otherData = carriedOther
    )
  }

  LaunchedEffect(todayDateStr) {
    todaySnapshot = loadOrInitializeDay(todayDateStr)
  }

  LaunchedEffect(prevDateStr) {
    prevSnapshot = loadOrInitializeDay(prevDateStr)
  }

  // Calibration lookup provider
  val calibrationProvider: (String, Double) -> Double? = { tName, dip ->
    val t = tanks.find { it.name.equals(tName, ignoreCase = true) }
    t?.let { Calculators.calculateDipVolume(it, dip, settings.invalidInputThreshold)?.totalVolume }
  }

  // Calculation Results
  val naturalResult = remember(setup, todaySnapshot, prevSnapshot, tanks, settings.invalidInputThreshold, products) {
    ProductionReportEngine.computeReport(setup, todaySnapshot, prevSnapshot, CalculationBasis.NATURAL, calibrationProvider, tanks, settings.invalidInputThreshold, products)
  }

  val temp30Result = remember(setup, todaySnapshot, prevSnapshot, tanks, settings.invalidInputThreshold, products) {
    ProductionReportEngine.computeReport(setup, todaySnapshot, prevSnapshot, CalculationBasis.TEMP_30C, calibrationProvider, tanks, settings.invalidInputThreshold, products)
  }

  val erpRows = remember(setup, todaySnapshot, prevSnapshot, temp30Result) {
    ProductionReportEngine.computeErpRows(setup, todaySnapshot, prevSnapshot, temp30Result)
  }

  val saveSnapshot: (ProductionReportDaySnapshot) -> Unit = { snapshot ->
    val updatedEntries = snapshot.tankEntries.mapValues { (tName, entry) ->
      val linked = tanks.find { it.name.equals(tName, ignoreCase = true) }
      val stamp = ProductionReportEngine.getTankCalibrationStamp(linked)
      val calculatedNat = if (entry.dipMm != null && linked != null) {
        Calculators.calculateDipVolume(linked, entry.dipMm, settings.invalidInputThreshold)?.totalVolume
      } else {
        entry.volumeInput
      }
      entry.copy(
        savedNaturalVolume = entry.savedNaturalVolume ?: calculatedNat,
        calibrationVersionStamp = entry.calibrationVersionStamp ?: stamp
      )
    }
    val snapshotToSave = snapshot.copy(
      tankEntries = updatedEntries,
      tableVersionId = setup.tableVersionId,
      vccTableSnapshot = setup.vccTable,
      vcf30TableSnapshot = setup.vcf30Table,
      lastModified = System.currentTimeMillis()
    )
    repository.saveProductionReportDay(snapshotToSave)
  }

  // Dialogs
  var showSavedDaysDialog by remember { mutableStateOf(false) }
  var showSetupPasswordDialog by remember { mutableStateOf(false) }
  var isSetupAuthenticated by remember { mutableStateOf(false) }
  var showTableDiffDialog by remember { mutableStateOf(false) }
  var inspectTank by remember { mutableStateOf<Tank?>(null) }

  if (inspectTank != null) {
    BackHandler { inspectTank = null }
    CalibrationInspectorScreen(
      repository = repository,
      initialTankName = inspectTank?.name,
      onBack = { inspectTank = null }
    )
    return
  }

  Scaffold(
    contentWindowInsets = WindowInsets.statusBars,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              "Production Report",
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              "${prevDateStr} to ${todayDateStr} (${setup.constants.reportTime})",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        },
        actions = {
          com.example.ui.components.SunlightModeToggleAction()
          IconButton(onClick = { showSavedDaysDialog = true }) {
            Icon(Icons.Default.History, contentDescription = "Saved Days")
          }
          IconButton(onClick = {
            if (isSetupAuthenticated) {
              selectedTab = 5
            } else {
              showSetupPasswordDialog = true
            }
          }) {
            Icon(Icons.Default.Tune, contentDescription = "Setup")
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
    ) {
      // 1. Missing Tanks Blocking Alert
      if (naturalResult.isBlocked) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Column {
              Text("REPORT BLOCKED: MAPPED TANKS MISSING", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelMedium)
              Text(
                "Missing from Tank Master: ${naturalResult.missingTanks.joinToString(", ")}. Add them to Tank Master or re-link in Setup > Tank Map.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
              )
            }
          }
        }
      }

      // 2. Table Version Mismatch Amber Banner
      if (todaySnapshot.tableVersionId != null && todaySnapshot.tableVersionId != setup.tableVersionId && !todaySnapshot.useCurrentTablesOverride) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
          Row(
            modifier = Modifier
              .padding(10.dp)
              .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Calculated With Stored Table Version", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
              Text("Saved with table version ${todaySnapshot.tableVersionId?.take(8)}. Current tables differ.", style = MaterialTheme.typography.bodySmall)
            }
            Button(
              onClick = { showTableDiffDialog = true },
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
              Text("Recalculate with current tables", style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }

      // Top Tab Row
      ScrollableTabRow(
        selectedTabIndex = selectedTab,
        edgePadding = 8.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
      ) {
        tabTitles.forEachIndexed { index, title ->
          Tab(
            selected = selectedTab == index,
            onClick = {
              if (index == 5 && !isSetupAuthenticated) {
                showSetupPasswordDialog = true
              } else {
                selectedTab = index
              }
            },
            text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
          )
        }
      }

      // Content by Tab
      when (selectedTab) {
        0 -> ProductionReportInputTab(
          setup = setup,
          tanks = tanks,
          settings = settings,
          todayDateStr = todayDateStr,
          prevDateStr = prevDateStr,
          todaySnapshot = todaySnapshot,
          prevSnapshot = prevSnapshot,
          onTodayDateChange = { todayDateStr = it },
          onPrevDateChange = { prevDateStr = it },
          onUpdateTodaySnapshot = { todaySnapshot = it },
          onUpdatePrevSnapshot = { prevSnapshot = it },
          onSaveToday = {
            saveSnapshot(todaySnapshot)
            Toast.makeText(context, "Saved report for ${todaySnapshot.dateStr}", Toast.LENGTH_SHORT).show()
          },
          onSavePrev = {
            saveSnapshot(prevSnapshot)
            Toast.makeText(context, "Saved report for ${prevSnapshot.dateStr}", Toast.LENGTH_SHORT).show()
          },
          blendRecords = blendRecords,
          products = products,
          calibrationProvider = calibrationProvider,
          onInspectTank = { inspectTank = it },
          onUpdateTank = { repository.saveTank(it) },
          onLogEdit = { act, tgt, det -> repository.logEditAction(act, tgt, det) },
          repository = repository
        )
        1 -> ProductionReportViewTab(
          result = naturalResult,
          setup = setup,
          onShareText = {
            val text = ProductionReportExportUtils.formatReportText(naturalResult, setup)
            ExportUtils.shareText(context, text, "Production Report (Natural) - ${naturalResult.reportDate}")
          },
          onShareWhatsApp = {
            val waText = ProductionReportExportUtils.formatReportWhatsApp(naturalResult, setup, settings)
            ExportUtils.shareText(context, waText, "WhatsApp Shift Handover (Natural) - ${naturalResult.reportDate}")
          },
          onExportExcel = {
            try {
              val file = ProductionReportExcelUtils.exportReportExcel(context, setup, naturalResult, settings, CalculationBasis.NATURAL)
              val intent = ProductionReportExcelUtils.getExcelShareIntent(context, file)
              if (intent != null) context.startActivity(Intent.createChooser(intent, "Export Natural Excel"))
            } catch (e: Exception) {
              Toast.makeText(context, "Error exporting Excel: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          },
          onExportBothExcel = {
            try {
              ProductionReportExcelUtils.exportReportExcel(context, setup, naturalResult, settings, CalculationBasis.NATURAL)
              val file30 = ProductionReportExcelUtils.exportReportExcel(context, setup, temp30Result, settings, CalculationBasis.TEMP_30C)
              Toast.makeText(context, "Exported both .xlsx files successfully", Toast.LENGTH_SHORT).show()
              val intent = ProductionReportExcelUtils.getExcelShareIntent(context, file30)
              if (intent != null) context.startActivity(Intent.createChooser(intent, "Export Excel Reports"))
            } catch (e: Exception) {
              Toast.makeText(context, "Error exporting Excel: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          },
          onDownloadPdf = {
            try {
              val file = ProductionReportExportUtils.createReportPdfFile(context, naturalResult, setup, settings)
              Toast.makeText(context, "PDF saved to ${file.name}", Toast.LENGTH_SHORT).show()
              val viewIntent = ProductionReportExportUtils.getPdfViewIntent(context, file)
              context.startActivity(Intent.createChooser(viewIntent, "Open Production Report PDF"))
            } catch (e: Exception) {
              Toast.makeText(context, "Error creating PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          },
          onSharePdf = {
            try {
              val file = ProductionReportExportUtils.createReportPdfFile(context, naturalResult, setup, settings)
              val shareIntent = ProductionReportExportUtils.getPdfShareIntent(context, file, "Production Report (Natural) - ${naturalResult.reportDate}")
              context.startActivity(Intent.createChooser(shareIntent, "Share Production Report PDF"))
            } catch (e: Exception) {
              Toast.makeText(context, "Error sharing PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          }
        )
        2 -> ProductionReportViewTab(
          result = temp30Result,
          setup = setup,
          onShareText = {
            val text = ProductionReportExportUtils.formatReportText(temp30Result, setup)
            ExportUtils.shareText(context, text, "Production Report (30 °C) - ${temp30Result.reportDate}")
          },
          onShareWhatsApp = {
            val waText = ProductionReportExportUtils.formatReportWhatsApp(temp30Result, setup, settings)
            ExportUtils.shareText(context, waText, "WhatsApp Shift Handover (30 °C) - ${temp30Result.reportDate}")
          },
          onExportExcel = {
            try {
              val file = ProductionReportExcelUtils.exportReportExcel(context, setup, temp30Result, settings, CalculationBasis.TEMP_30C)
              val intent = ProductionReportExcelUtils.getExcelShareIntent(context, file)
              if (intent != null) context.startActivity(Intent.createChooser(intent, "Export 30C Excel"))
            } catch (e: Exception) {
              Toast.makeText(context, "Error exporting Excel: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          },
          onExportBothExcel = {
            try {
              ProductionReportExcelUtils.exportReportExcel(context, setup, naturalResult, settings, CalculationBasis.NATURAL)
              val file30 = ProductionReportExcelUtils.exportReportExcel(context, setup, temp30Result, settings, CalculationBasis.TEMP_30C)
              Toast.makeText(context, "Exported both .xlsx files successfully", Toast.LENGTH_SHORT).show()
              val intent = ProductionReportExcelUtils.getExcelShareIntent(context, file30)
              if (intent != null) context.startActivity(Intent.createChooser(intent, "Export Excel Reports"))
            } catch (e: Exception) {
              Toast.makeText(context, "Error exporting Excel: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          },
          onDownloadPdf = {
            try {
              val file = ProductionReportExportUtils.createReportPdfFile(context, temp30Result, setup, settings)
              Toast.makeText(context, "PDF saved to ${file.name}", Toast.LENGTH_SHORT).show()
              val viewIntent = ProductionReportExportUtils.getPdfViewIntent(context, file)
              context.startActivity(Intent.createChooser(viewIntent, "Open Production Report PDF"))
            } catch (e: Exception) {
              Toast.makeText(context, "Error creating PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          },
          onSharePdf = {
            try {
              val file = ProductionReportExportUtils.createReportPdfFile(context, temp30Result, setup, settings)
              val shareIntent = ProductionReportExportUtils.getPdfShareIntent(context, file, "Production Report (30 °C) - ${temp30Result.reportDate}")
              context.startActivity(Intent.createChooser(shareIntent, "Share Production Report PDF"))
            } catch (e: Exception) {
              Toast.makeText(context, "Error sharing PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          }
        )
        3 -> ProductionReportErpTab(
          erpRows = erpRows,
          dateStr = todaySnapshot.dateStr,
          prevDateStr = prevDateStr,
          calcResult = temp30Result,
          settings = settings,
          onExportCsv = {
            val csv = ProductionReportExportUtils.formatErpCsv(erpRows, todaySnapshot.dateStr)
            val intent = ProductionReportExportUtils.exportCsvFile(context, "ERP_Balance_${todaySnapshot.dateStr}.csv", csv)
            if (intent != null) context.startActivity(Intent.createChooser(intent, "Export ERP Report"))
          },
          onShareWhatsApp = {
            val waText = ProductionReportExportUtils.formatErpWhatsApp(erpRows, temp30Result, settings)
            ExportUtils.shareText(context, waText, "WhatsApp ERP Statement - ${todaySnapshot.dateStr}")
          },
          onDownloadPdf = {
            try {
              val file = ProductionReportExportUtils.createErpPdfFile(context, erpRows, temp30Result, settings)
              Toast.makeText(context, "ERP Statement PDF saved: ${file.name}", Toast.LENGTH_SHORT).show()
              val viewIntent = ProductionReportExportUtils.getPdfViewIntent(context, file)
              context.startActivity(Intent.createChooser(viewIntent, "Open ERP Statement PDF"))
            } catch (e: Exception) {
              Toast.makeText(context, "Error creating ERP PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          },
          onSharePdf = {
            try {
              val file = ProductionReportExportUtils.createErpPdfFile(context, erpRows, temp30Result, settings)
              val shareIntent = ProductionReportExportUtils.getPdfShareIntent(context, file, "Daily ERP Production & Stock Statement - ${todaySnapshot.dateStr}")
              context.startActivity(Intent.createChooser(shareIntent, "Share ERP Statement PDF"))
            } catch (e: Exception) {
              Toast.makeText(context, "Error sharing PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          }
        )
        4 -> ProductionReportProductYieldTab(
          result = temp30Result,
          setup = setup,
          onToggleJetSko = { enabled ->
            val updated = setup.copy(constants = setup.constants.copy(includeJetAndSkoInYield = enabled))
            repository.saveProductionReportSetup(updated)
          }
        )
        5 -> ProductionReportSetupTab(
          setup = setup,
          tanks = tanks,
          onSaveSetup = { updated ->
            repository.saveProductionReportSetup(updated)
            Toast.makeText(context, "Setup updated successfully", Toast.LENGTH_SHORT).show()
          },
          onLogEdit = { action, target, details ->
            repository.addEditLog(action, target, details)
          }
        )
      }
    }
  }


  // Table Difference Comparison Dialog
  if (showTableDiffDialog) {
    val resStored = temp30Result
    val resCurrent = ProductionReportEngine.computeReport(
      setup,
      todaySnapshot.copy(useCurrentTablesOverride = true),
      prevSnapshot.copy(useCurrentTablesOverride = true),
      CalculationBasis.TEMP_30C,
      calibrationProvider,
      tanks,
      settings.invalidInputThreshold
    )
    AlertDialog(
      onDismissRequest = { showTableDiffDialog = false },
      title = { Text("Recalculate with Current Tables", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Difference summary using current Setup tables vs Stored tables:", style = MaterialTheme.typography.bodyMedium)
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Feed:", fontWeight = FontWeight.SemiBold)
                Text(
                  "${String.format(Locale.US, "%,.0f", resStored.totalFeed)} → ${String.format(Locale.US, "%,.0f", resCurrent.totalFeed)} L (${String.format(Locale.US, "%+,.0f", resCurrent.totalFeed - resStored.totalFeed)})",
                  fontWeight = FontWeight.Bold
                )
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Produced:", fontWeight = FontWeight.SemiBold)
                Text(
                  "${String.format(Locale.US, "%,.0f", resStored.totalProduced)} → ${String.format(Locale.US, "%,.0f", resCurrent.totalProduced)} L (${String.format(Locale.US, "%+,.0f", resCurrent.totalProduced - resStored.totalProduced)})",
                  fontWeight = FontWeight.Bold
                )
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Process Loss:", fontWeight = FontWeight.SemiBold)
                Text(
                  "${String.format(Locale.US, "%,.0f", resStored.totalLoss)} → ${String.format(Locale.US, "%,.0f", resCurrent.totalLoss)} L (${String.format(Locale.US, "%+,.0f", resCurrent.totalLoss - resStored.totalLoss)})",
                  fontWeight = FontWeight.Bold,
                  color = if (resCurrent.totalLoss > resStored.totalLoss) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
              }
            }
          }
        }
      },
      confirmButton = {
        Button(onClick = {
          todaySnapshot = todaySnapshot.copy(useCurrentTablesOverride = true)
          prevSnapshot = prevSnapshot.copy(useCurrentTablesOverride = true)
          showTableDiffDialog = false
          Toast.makeText(context, "Applied current tables to this report", Toast.LENGTH_SHORT).show()
        }) {
          Text("Apply Current Tables")
        }
      },
      dismissButton = {
        TextButton(onClick = { showTableDiffDialog = false }) { Text("Keep Stored Tables") }
      }
    )
  }

  // Saved Days History Dialog
  if (showSavedDaysDialog) {
    AlertDialog(
      onDismissRequest = { showSavedDaysDialog = false },
      title = { Text("Saved Production Report Days (${savedDays.size})", fontWeight = FontWeight.Bold) },
      text = {
        if (savedDays.isEmpty()) {
          Text("No saved days yet. Select dates on the input tab and click 'Save Today'.")
        } else {
          LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(savedDays.sortedByDescending { it.dateStr }) { snap ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(snap.dateStr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("${snap.tankEntries.size} tanks recorded", style = MaterialTheme.typography.bodySmall)
                  }
                  Row {
                    TextButton(onClick = {
                      todayDateStr = snap.dateStr
                      todaySnapshot = snap
                      showSavedDaysDialog = false
                    }) {
                      Text("Load Today")
                    }
                    IconButton(onClick = { repository.deleteProductionReportDay(snap.dateStr) }) {
                      Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showSavedDaysDialog = false }) { Text("Close") }
      }
    )
  }

  // Password Dialog for Setup Tab
  if (showSetupPasswordDialog) {
    PasswordDialog(
      message = "Enter administrator password to edit production report setup:",
      storedHash = settings.passwordHash,
      onDismiss = { showSetupPasswordDialog = false },
      onSuccess = {
        isSetupAuthenticated = true
        showSetupPasswordDialog = false
        selectedTab = 5
      }
    )
  }
}

/**
 * Tab 0: Input Tab for Bulk Entry
 */
/**
 * Tab 0: Input Tab for Bulk Entry
 */
enum class ReportGridColumn(val title: String, val widthDp: Int) {
  PRESENT_VOL("Present Vol", 106),
  PRESENT_TEMP("Pres Temp", 90),
  PRESENT_DENSITY("Pres Dens", 102),
  PREV_VOL("Prev Vol", 106),
  PREV_TEMP("Prev Temp", 90),
  PREV_DENSITY("Prev Dens", 102),
  VARIANCE("Δ Stock", 106),
  RECEIVED("Received", 104),
  DELIVERED("Delivered", 104),
  ADJUSTMENT("Adj (±L)", 96),
  NOTE("Note", 130)
}

data class GridCellCoordinate(
  val tankName: String,
  val column: ReportGridColumn
)

enum class LedgerGridColumn(val title: String, val widthDp: Int) {
  VOLUME("Volume", 112),
  TEMP("Temp (°C)", 92),
  DENSITY("Density", 104),
  VOL_30C("Vol @30°C", 112),
  NOTE("Batch / Note", 135),
  ACTION("Act", 56)
}

data class LedgerRowItem(
  val tank: Tank,
  val isReceived: Boolean,
  val entryIndex: Int,
  val entry: MovementTransactionEntry
)

data class LedgerCellCoordinate(
  val tankName: String,
  val isReceived: Boolean,
  val entryId: String,
  val column: LedgerGridColumn
)

/**
 * Tab 0: Input Tab for Bulk Entry (Unified Excel-like Matrix + Live Movement Ledgers + Docked Keypad)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionReportInputTab(
  setup: ProductionReportSetup,
  tanks: List<Tank>,
  settings: AppSettings,
  todayDateStr: String,
  prevDateStr: String,
  todaySnapshot: ProductionReportDaySnapshot,
  prevSnapshot: ProductionReportDaySnapshot,
  onTodayDateChange: (String) -> Unit,
  onPrevDateChange: (String) -> Unit,
  onUpdateTodaySnapshot: (ProductionReportDaySnapshot) -> Unit,
  onUpdatePrevSnapshot: (ProductionReportDaySnapshot) -> Unit,
  onSaveToday: () -> Unit,
  onSavePrev: () -> Unit,
  blendRecords: List<BlendRecord>,
  products: List<Product>,
  calibrationProvider: (String, Double) -> Double?,
  onInspectTank: (Tank) -> Unit,
  onUpdateTank: (Tank) -> Unit,
  onLogEdit: (String, String, String) -> Unit,
  repository: TankRepository
) {
  val context = LocalContext.current
  var activeRoleFilter by remember { mutableStateOf<TankRole?>(null) }
  var showMoreColumns by remember { mutableStateOf(false) }
  var displayUnit by remember(settings.unit) { mutableStateOf(settings.unit) }
  var showOtherData by remember { mutableStateOf(false) }

  var activeCell by remember { mutableStateOf<GridCellCoordinate?>(null) }
  var activeLedgerCell by remember { mutableStateOf<LedgerCellCoordinate?>(null) }
  var tankForRoleSheet by remember { mutableStateOf<Tank?>(null) }
  var movementDialogInfo by remember { mutableStateOf<Pair<Tank, Boolean>?>(null) } // Optional detail popup if needed
  var tankToUpdatePrev by remember { mutableStateOf<Tank?>(null) }

  val horizontalScrollState = rememberScrollState()
  val ledgerHorizontalScrollState = rememberScrollState()

  val unitSuffix = if (displayUnit == UnitType.M3) "m³" else "L"
  fun formatVol(litres: Double, showSign: Boolean = false): String {
    val disp = if (displayUnit == UnitType.M3) litres / 1000.0 else litres
    return if (displayUnit == UnitType.M3) {
      if (showSign) String.format(Locale.US, "%+,.3f", disp) else String.format(Locale.US, "%,.3f", disp)
    } else {
      if (disp % 1.0 == 0.0) {
        if (showSign) String.format(Locale.US, "%+,.0f", disp) else String.format(Locale.US, "%,.0f", disp)
      } else {
        if (showSign) String.format(Locale.US, "%+,.2f", disp) else String.format(Locale.US, "%,.2f", disp)
      }
    }
  }

  fun showDatePicker(currentDateStr: String, onDateSelected: (String) -> Unit) {
    val cal = Calendar.getInstance()
    try {
      val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(currentDateStr)
      if (parsed != null) cal.time = parsed
    } catch (_: Exception) {}

    DatePickerDialog(
      context,
      { _, y, m, d ->
        val chosen = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
        onDateSelected(chosen)
      },
      cal.get(Calendar.YEAR),
      cal.get(Calendar.MONTH),
      cal.get(Calendar.DAY_OF_MONTH)
    ).show()
  }

  val activeTanks = tanks.filter { it.includeInProductionReport }.sortedBy { it.reportOrder }
  val filteredTanks = activeTanks.filter { tank ->
    val effRole = ProductionReportEngine.getTankEffectiveRole(tank, todaySnapshot.tankEntries[tank.name])
    activeRoleFilter == null || effRole == activeRoleFilter
  }

  // Group tanks for table sections
  val feedTanks = filteredTanks.filter { ProductionReportEngine.getTankEffectiveRole(it, todaySnapshot.tankEntries[it.name]) == TankRole.FEED }
  val productTanks = filteredTanks.filter { ProductionReportEngine.getTankEffectiveRole(it, todaySnapshot.tankEntries[it.name]) == TankRole.PRODUCT }
  val blendingTanks = filteredTanks.filter { ProductionReportEngine.getTankEffectiveRole(it, todaySnapshot.tankEntries[it.name]) == TankRole.BLENDING }
  val excludedTanks = filteredTanks.filter { ProductionReportEngine.getTankEffectiveRole(it, todaySnapshot.tankEntries[it.name]) == TankRole.EXCLUDED }

  // Compute Live Summary Row Totals for Main Tank Grid
  var sumPresentLitres = 0.0
  var sumPrevLitres = 0.0
  var sumVarianceLitres = 0.0
  var sumReceivedLitres = 0.0
  var sumDeliveredLitres = 0.0
  var sumAdjustmentLitres = 0.0
  var missing30Count = 0

  filteredTanks.forEach { tank ->
    val entry = todaySnapshot.tankEntries[tank.name] ?: ProductionReportTankEntry(tankName = tank.name)
    val prevEntry = prevSnapshot.tankEntries[tank.name] ?: ProductionReportTankEntry(tankName = tank.name)
    val effRole = ProductionReportEngine.getTankEffectiveRole(tank, entry)
    if (effRole != TankRole.EXCLUDED) {
      val presV = entry.volumeTodayInput ?: 0.0
      val prevV = entry.volumePrevOverride ?: entry.volumePrevInput ?: prevEntry.volumeTodayInput ?: 0.0
      sumPresentLitres += presV
      sumPrevLitres += prevV
      if (entry.volumeTodayInput != null || (entry.volumePrevOverride ?: entry.volumePrevInput ?: prevEntry.volumeTodayInput) != null) {
        sumVarianceLitres += (presV - prevV)
      }
      if (effRole == TankRole.FEED) {
        val rec = if (entry.receivedEntries.isNotEmpty()) entry.receivedEntries.sumOf { it.volumeNatural }
        else (todaySnapshot.otherData.ngcReceivedNatural[tank.name] ?: 0.0)
        sumReceivedLitres += rec
      }
      if (effRole == TankRole.PRODUCT || effRole == TankRole.BLENDING) {
        sumDeliveredLitres += entry.deliveredEntries.sumOf { it.volumeNatural }
      }
      sumAdjustmentLitres += entry.adjustmentL

      val hasCoeff = tank.coefficientSource != CoefficientSourceType.NONE
      if (presV > 0.0 && hasCoeff) {
        val missT = entry.tempTodayC == null && entry.temperatureC == null
        val missD = if (effRole == TankRole.FEED) (entry.densityToday15 == null && entry.density15 == null)
        else (entry.densityToday30 == null && entry.density30 == null)
        if (missT || missD) missing30Count++
      }
    }
  }

  // Build Flattened Live Movement Ledger Rows across all active Feed/Product/Blending tanks
  val ledgerRows = remember(filteredTanks, todaySnapshot.tankEntries) {
    val list = mutableListOf<LedgerRowItem>()
    filteredTanks.forEach { tank ->
      val entry = todaySnapshot.tankEntries[tank.name] ?: ProductionReportTankEntry(tankName = tank.name)
      val effRole = ProductionReportEngine.getTankEffectiveRole(tank, entry)
      if (effRole == TankRole.FEED) {
        entry.receivedEntries.forEachIndexed { idx, mov ->
          list.add(LedgerRowItem(tank = tank, isReceived = true, entryIndex = idx, entry = mov))
        }
      } else if (effRole == TankRole.PRODUCT || effRole == TankRole.BLENDING) {
        entry.deliveredEntries.forEachIndexed { idx, mov ->
          list.add(LedgerRowItem(tank = tank, isReceived = false, entryIndex = idx, entry = mov))
        }
      }
    }
    list
  }

  // Helper to add a new movement entry for a tank and immediately focus it in the docked keypad
  val addMovementEntryForTank: (Tank, Boolean) -> Unit = { tank, isReceived ->
    val map = todaySnapshot.tankEntries.toMutableMap()
    val curr = map[tank.name] ?: ProductionReportTankEntry(tankName = tank.name)
    val effRole = ProductionReportEngine.getTankEffectiveRole(tank, curr)
    val defaultTemp = curr.tempTodayC ?: curr.temperatureC ?: 30.0
    val defaultDens = if (effRole == TankRole.FEED) {
      curr.densityToday15 ?: curr.density15 ?: 0.7360
    } else {
      curr.densityToday30 ?: curr.density30 ?: 0.7500
    }
    val newEntry = MovementTransactionEntry(
      volumeNatural = 0.0,
      temperatureC = defaultTemp,
      density = defaultDens,
      note = if (isReceived) "Receipt #${curr.receivedEntries.size + 1}" else "Delivery #${curr.deliveredEntries.size + 1}"
    )
    map[tank.name] = if (isReceived) {
      curr.copy(receivedEntries = curr.receivedEntries + newEntry)
    } else {
      curr.copy(deliveredEntries = curr.deliveredEntries + newEntry)
    }
    onUpdateTodaySnapshot(todaySnapshot.copy(tankEntries = map))
    activeCell = null
    activeLedgerCell = LedgerCellCoordinate(
      tankName = tank.name,
      isReceived = isReceived,
      entryId = newEntry.id,
      column = LedgerGridColumn.VOLUME
    )
  }

  // Re-sync from dip sheet handler
  val onResyncFromDipSheet: () -> Unit = {
    val todaySheet = repository.getBulkDipSheet(todayDateStr)
    if (todaySheet == null) {
      Toast.makeText(context, "No saved dip sheet found for $todayDateStr", Toast.LENGTH_SHORT).show()
    } else {
      var count = 0
      val updatedMap = todaySnapshot.tankEntries.toMutableMap()
      todaySheet.entries.forEach { (tId, dipEntry) ->
        val volToUse = dipEntry.netVolume ?: dipEntry.calculatedVolume
        if (volToUse != null && !dipEntry.isInvalid) {
          val t = tanks.find { it.id == tId || it.name.equals(dipEntry.tankName, ignoreCase = true) }
          if (t != null) {
            val curr = updatedMap[t.name] ?: ProductionReportTankEntry(tankName = t.name)
            updatedMap[t.name] = curr.copy(
              volumeTodayInput = volToUse,
              useVolumeModeToday = true,
              isFromDipSheetToday = true,
              dipSheetDipToday = dipEntry.dipMm
            )
            count++
          }
        }
      }
      onUpdateTodaySnapshot(todaySnapshot.copy(tankEntries = updatedMap))
      Toast.makeText(context, "Re-synced $count tanks from dip sheet ($todayDateStr)", Toast.LENGTH_SHORT).show()
    }
  }

  val isAnyDockOpen = activeCell != null || activeLedgerCell != null

  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 10.dp)
        .padding(bottom = if (isAnyDockOpen) 250.dp else 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      contentPadding = PaddingValues(vertical = 8.dp)
    ) {
      // 1. Date selection & Save header
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                "Report Period Dates (10:00 AM Daily)",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall
              )
              if (missing30Count > 0) {
                Surface(
                  color = Color(0xFFFFF8E1),
                  shape = RoundedCornerShape(4.dp),
                  modifier = Modifier.border(1.dp, Color(0xFFFFB300), RoundedCornerShape(4.dp))
                ) {
                  Text(
                    "$missing30Count tank(s) missing Temp/Dens for 30°C",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE65100),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedCard(
                onClick = { showDatePicker(prevDateStr, onPrevDateChange) },
                modifier = Modifier.weight(1f)
              ) {
                Column(modifier = Modifier.padding(8.dp)) {
                  Text("Previous Day", style = MaterialTheme.typography.labelSmall)
                  Text(prevDateStr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
              }

              OutlinedCard(
                onClick = { showDatePicker(todayDateStr, onTodayDateChange) },
                modifier = Modifier.weight(1f)
              ) {
                Column(modifier = Modifier.padding(8.dp)) {
                  Text("Present Day (Report Date)", style = MaterialTheme.typography.labelSmall)
                  Text(todayDateStr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
              }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(onClick = onSavePrev, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save Prev")
              }
              Button(onClick = onSaveToday, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save Today")
              }
            }
          }
        }
      }

      // 2. Spreadsheet Matrix Toolbar: Unit Toggle (L | m³), Role Filters, More Columns, Dip Sheet Sync
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              // Unit Toggle (L | m³)
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Unit:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
              }

              // Re-sync from dip sheet
              OutlinedButton(
                onClick = onResyncFromDipSheet,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("resync_dip_sheet_button")
              ) {
                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sync Dip Sheet", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                  selected = activeRoleFilter == null,
                  onClick = { activeRoleFilter = null },
                  label = { Text("All", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                  selected = activeRoleFilter == TankRole.FEED,
                  onClick = { activeRoleFilter = if (activeRoleFilter == TankRole.FEED) null else TankRole.FEED },
                  label = { Text("Feed", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                  selected = activeRoleFilter == TankRole.PRODUCT,
                  onClick = { activeRoleFilter = if (activeRoleFilter == TankRole.PRODUCT) null else TankRole.PRODUCT },
                  label = { Text("Prod", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                  selected = activeRoleFilter == TankRole.BLENDING,
                  onClick = { activeRoleFilter = if (activeRoleFilter == TankRole.BLENDING) null else TankRole.BLENDING },
                  label = { Text("Blend", style = MaterialTheme.typography.labelSmall) }
                )
              }

              // More columns toggle
              FilterChip(
                selected = showMoreColumns,
                onClick = { showMoreColumns = !showMoreColumns },
                label = { Text("Adj / Note", style = MaterialTheme.typography.labelSmall) }
              )
            }
          }
        }
      }

      // 3. UNIFIED TANK SPREADSHEET MATRIX (Frozen Left Column + Freeze-Pane Header + Anchored Summary Row)
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            val headerCols = remember(showMoreColumns) {
              val cols = mutableListOf(
                ReportGridColumn.PRESENT_VOL,
                ReportGridColumn.PRESENT_TEMP,
                ReportGridColumn.PRESENT_DENSITY,
                ReportGridColumn.PREV_VOL,
                ReportGridColumn.PREV_TEMP,
                ReportGridColumn.PREV_DENSITY,
                ReportGridColumn.VARIANCE,
                ReportGridColumn.RECEIVED,
                ReportGridColumn.DELIVERED
              )
              if (showMoreColumns) {
                cols.add(ReportGridColumn.ADJUSTMENT)
                cols.add(ReportGridColumn.NOTE)
              }
              cols
            }

            // A) FREEZE-PANE HEADER ROW
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
              // Frozen Left Header: Tank ID / Role
              Box(
                modifier = Modifier
                  .width(142.dp)
                  .height(48.dp)
                  .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                  .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
              ) {
                Column {
                  Text("Tank ID / Role", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium)
                  Text("Enter↓  Tab→", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
              }

              // Scrollable Columns Header
              Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                headerCols.forEach { col ->
                  val colUnitLabel = when (col) {
                    ReportGridColumn.PRESENT_VOL, ReportGridColumn.PREV_VOL,
                    ReportGridColumn.VARIANCE, ReportGridColumn.RECEIVED, ReportGridColumn.DELIVERED -> " ($unitSuffix)"
                    ReportGridColumn.PRESENT_TEMP, ReportGridColumn.PREV_TEMP -> " (°C)"
                    else -> ""
                  }
                  Box(
                    modifier = Modifier
                      .width(col.widthDp.dp)
                      .height(48.dp)
                      .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                      .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = col.title + colUnitLabel,
                      fontWeight = FontWeight.Bold,
                      style = MaterialTheme.typography.labelSmall,
                      textAlign = TextAlign.Center
                    )
                  }
                }
              }
            }

            // B) TANK GROUP SECTIONS & MATRIX ROWS
            val renderSection: @Composable (String, List<Tank>) -> Unit = { sectionTitle, sectionTanks ->
              if (sectionTanks.isNotEmpty()) {
                // Section Title Banner
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    "$sectionTitle (${sectionTanks.size})",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                  )
                }

                // Section Tank Rows
                sectionTanks.forEach { tank ->
                  val entry = todaySnapshot.tankEntries[tank.name] ?: ProductionReportTankEntry(tankName = tank.name)
                  val prevEntry = prevSnapshot.tankEntries[tank.name] ?: ProductionReportTankEntry(tankName = tank.name)

                  val effRole = ProductionReportEngine.getTankEffectiveRole(tank, entry)
                  val hasCoeff = tank.coefficientSource != CoefficientSourceType.NONE && effRole != TankRole.EXCLUDED

                  // Validation check: Volume entered on Present or Prev but Temp/Density missing when needed for 30 °C
                  val presVol = entry.volumeTodayInput ?: 0.0
                  val isPresVolEntered = presVol > 0.0
                  val isMissingPresTemp = isPresVolEntered && hasCoeff && (entry.tempTodayC == null && entry.temperatureC == null)
                  val isMissingPresDens = isPresVolEntered && hasCoeff && if (effRole == TankRole.FEED) {
                    (entry.densityToday15 == null && entry.density15 == null)
                  } else {
                    (entry.densityToday30 == null && entry.density30 == null)
                  }

                  val prevVolResolved = entry.volumePrevOverride ?: entry.volumePrevInput ?: prevEntry.volumeTodayInput
                  val isPrevVolEntered = (prevVolResolved ?: 0.0) > 0.0
                  val isMissingPrevTemp = isPrevVolEntered && hasCoeff && (entry.tempPrevOverride == null && entry.tempPrevC == null && prevEntry.tempTodayC == null && prevEntry.temperatureC == null)
                  val isMissingPrevDens = isPrevVolEntered && hasCoeff && if (effRole == TankRole.FEED) {
                    (entry.densityPrevOverride15 == null && entry.densityPrev15 == null && prevEntry.densityToday15 == null && prevEntry.density15 == null)
                  } else {
                    (entry.densityPrevOverride30 == null && entry.densityPrev30 == null && prevEntry.densityToday30 == null && prevEntry.density30 == null)
                  }

                  val hasValidationWarning = isMissingPresTemp || isMissingPresDens || isMissingPrevTemp || isMissingPrevDens

                  // Check Previous difference from saved previous day
                  val savedPrevVol = prevSnapshot.tankEntries[tank.name]?.volumeTodayInput
                  val currPrevVol = entry.volumePrevOverride ?: entry.volumePrevInput
                  val prevDiffers = currPrevVol != null && savedPrevVol != null && abs(currPrevVol - savedPrevVol) > 0.01

                  val isRowSelected = activeCell?.tankName == tank.name

                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .background(
                        when {
                          isRowSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
                          hasValidationWarning -> Color(0xFFFFFDE7)
                          else -> MaterialTheme.colorScheme.surface
                        }
                      )
                  ) {
                    // 1. Frozen Sticky Left Tank Column
                    Box(
                      modifier = Modifier
                        .width(142.dp)
                        .heightIn(min = 50.dp)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        .background(
                          if (isRowSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                          else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                      contentAlignment = Alignment.CenterStart
                    ) {
                      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                          Text(tank.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                          if (entry.isFromDipSheetToday) {
                            Surface(
                              color = MaterialTheme.colorScheme.primary,
                              shape = RoundedCornerShape(3.dp)
                            ) {
                              Text(
                                "D",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                              )
                            }
                          }
                          if (prevDiffers) {
                            Surface(
                              color = MaterialTheme.colorScheme.tertiaryContainer,
                              shape = RoundedCornerShape(3.dp),
                              modifier = Modifier.clickable { tankToUpdatePrev = tank }
                            ) {
                              Text("Diff", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                            }
                          }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                          Surface(
                            color = when (effRole) {
                              TankRole.FEED -> MaterialTheme.colorScheme.primaryContainer
                              TankRole.PRODUCT -> MaterialTheme.colorScheme.secondaryContainer
                              TankRole.BLENDING -> MaterialTheme.colorScheme.tertiaryContainer
                              TankRole.EXCLUDED -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.clickable { tankForRoleSheet = tank }
                          ) {
                            Text(
                              effRole.name.take(4),
                              style = MaterialTheme.typography.labelSmall,
                              fontWeight = FontWeight.Bold,
                              modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                          }

                          if (hasValidationWarning) {
                            Surface(
                              color = Color(0xFFFFECB3),
                              shape = RoundedCornerShape(3.dp)
                            ) {
                              Text("30°C!", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                            }
                          }
                        }
                      }
                    }

                    // 2. Horizontally Scrollable Data Cells
                    Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                      headerCols.forEach { col ->
                        val isCellActive = activeCell?.tankName == tank.name && activeCell?.column == col

                        val cellText = when (col) {
                          ReportGridColumn.PRESENT_VOL -> {
                            val v = entry.volumeTodayInput
                            if (v != null) formatVol(v) else "-"
                          }
                          ReportGridColumn.PRESENT_TEMP -> {
                            if (!hasCoeff) "-"
                            else {
                              val t = entry.tempTodayC ?: entry.temperatureC
                              if (t != null) String.format(Locale.US, "%.1f", t) else "-"
                            }
                          }
                          ReportGridColumn.PRESENT_DENSITY -> {
                            if (!hasCoeff) {
                              "-"
                            } else if (effRole == TankRole.FEED) {
                              val d = entry.densityToday15 ?: entry.density15
                              if (d != null) "${String.format(Locale.US, "%.4f", d)} @15" else "Req @15"
                            } else {
                              val d = entry.densityToday30 ?: entry.density30
                              if (d != null) "${String.format(Locale.US, "%.4f", d)} @30" else "Req @30"
                            }
                          }
                          ReportGridColumn.PREV_VOL -> {
                            if (prevVolResolved != null) formatVol(prevVolResolved) else "-"
                          }
                          ReportGridColumn.PREV_TEMP -> {
                            if (!hasCoeff) "-"
                            else {
                              val t = entry.tempPrevOverride ?: entry.tempPrevC ?: prevEntry.tempTodayC ?: prevEntry.temperatureC
                              if (t != null) String.format(Locale.US, "%.1f", t) else "-"
                            }
                          }
                          ReportGridColumn.PREV_DENSITY -> {
                            if (!hasCoeff) {
                              "-"
                            } else if (effRole == TankRole.FEED) {
                              val d = entry.densityPrevOverride15 ?: entry.densityPrev15 ?: prevEntry.densityToday15 ?: prevEntry.density15
                              if (d != null) "${String.format(Locale.US, "%.4f", d)} @15" else "Req @15"
                            } else {
                              val d = entry.densityPrevOverride30 ?: entry.densityPrev30 ?: prevEntry.densityToday30 ?: prevEntry.density30
                              if (d != null) "${String.format(Locale.US, "%.4f", d)} @30" else "Req @30"
                            }
                          }
                          ReportGridColumn.VARIANCE -> {
                            val vNow = entry.volumeTodayInput
                            if (vNow != null || prevVolResolved != null) {
                              val delta = (vNow ?: 0.0) - (prevVolResolved ?: 0.0)
                              formatVol(delta, showSign = true)
                            } else "-"
                          }
                          ReportGridColumn.RECEIVED -> {
                            if (effRole == TankRole.FEED) {
                              val recSum = if (entry.receivedEntries.isNotEmpty()) entry.receivedEntries.sumOf { it.volumeNatural }
                              else (todaySnapshot.otherData.ngcReceivedNatural[tank.name] ?: 0.0)
                              if (recSum > 0.0) {
                                val countTag = if (entry.receivedEntries.size > 1) " (${entry.receivedEntries.size})" else ""
                                formatVol(recSum) + countTag
                              } else "+ Rec"
                            } else "-"
                          }
                          ReportGridColumn.DELIVERED -> {
                            if (effRole == TankRole.PRODUCT || effRole == TankRole.BLENDING) {
                              val delSum = entry.deliveredEntries.sumOf { it.volumeNatural }
                              if (delSum > 0.0) {
                                val countTag = if (entry.deliveredEntries.size > 1) " (${entry.deliveredEntries.size})" else ""
                                formatVol(delSum) + countTag
                              } else "+ Del"
                            } else "-"
                          }
                          ReportGridColumn.ADJUSTMENT -> {
                            val adj = entry.adjustmentL
                            if (abs(adj) > 0.001) formatVol(adj, showSign = true) else "-"
                          }
                          ReportGridColumn.NOTE -> {
                            entry.adjustmentNote.ifBlank { "-" }
                          }
                        }

                        // Cell-level validation state: Soft amber warning vs Red missing indicator
                        val isCellCriticalMissing = when (col) {
                          ReportGridColumn.PRESENT_TEMP -> isMissingPresTemp
                          ReportGridColumn.PRESENT_DENSITY -> isMissingPresDens
                          else -> false
                        }
                        val isCellSoftWarning = when (col) {
                          ReportGridColumn.PREV_TEMP -> isMissingPrevTemp
                          ReportGridColumn.PREV_DENSITY -> isMissingPrevDens
                          else -> false
                        }

                        val isClickable = when (col) {
                          ReportGridColumn.VARIANCE -> false
                          ReportGridColumn.RECEIVED -> effRole == TankRole.FEED
                          ReportGridColumn.DELIVERED -> effRole == TankRole.PRODUCT || effRole == TankRole.BLENDING
                          ReportGridColumn.PRESENT_DENSITY, ReportGridColumn.PREV_DENSITY,
                          ReportGridColumn.PRESENT_TEMP, ReportGridColumn.PREV_TEMP -> hasCoeff
                          else -> true
                        }

                        val cellBg = when {
                          isCellActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                          isCellCriticalMissing -> Color(0xFFFFEBEE) // Soft red alert
                          isCellSoftWarning -> Color(0xFFFFF8E1) // Soft yellow warning
                          col == ReportGridColumn.VARIANCE -> MaterialTheme.colorScheme.surfaceContainerLowest
                          !isClickable -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                          else -> Color.Transparent
                        }

                        val cellBorderColor = when {
                          isCellActive -> MaterialTheme.colorScheme.primary
                          isCellCriticalMissing -> Color(0xFFE53935)
                          isCellSoftWarning -> Color(0xFFFFB300)
                          else -> MaterialTheme.colorScheme.outlineVariant
                        }

                        Box(
                          modifier = Modifier
                            .width(col.widthDp.dp)
                            .heightIn(min = 50.dp)
                            .border(
                              width = if (isCellActive) 2.dp else if (isCellCriticalMissing || isCellSoftWarning) 1.5.dp else 0.5.dp,
                              color = cellBorderColor
                            )
                            .background(cellBg)
                            .clickable(enabled = isClickable) {
                              if (col == ReportGridColumn.RECEIVED) {
                                // Activate inline ledger row or create first receipt entry immediately
                                val existingRec = entry.receivedEntries.firstOrNull()
                                if (existingRec != null) {
                                  activeCell = null
                                  activeLedgerCell = LedgerCellCoordinate(tank.name, true, existingRec.id, LedgerGridColumn.VOLUME)
                                } else {
                                  addMovementEntryForTank(tank, true)
                                }
                              } else if (col == ReportGridColumn.DELIVERED) {
                                val existingDel = entry.deliveredEntries.firstOrNull()
                                if (existingDel != null) {
                                  activeCell = null
                                  activeLedgerCell = LedgerCellCoordinate(tank.name, false, existingDel.id, LedgerGridColumn.VOLUME)
                                } else {
                                  addMovementEntryForTank(tank, false)
                                }
                              } else {
                                activeLedgerCell = null
                                activeCell = GridCellCoordinate(tank.name, col)
                              }
                            }
                            .padding(horizontal = 6.dp),
                          contentAlignment = if (col == ReportGridColumn.NOTE) Alignment.CenterStart else Alignment.CenterEnd
                        ) {
                          val textColor = when {
                            isCellCriticalMissing -> Color(0xFFC62828)
                            isCellSoftWarning -> Color(0xFFE65100)
                            col == ReportGridColumn.VARIANCE -> {
                              val delta = (entry.volumeTodayInput ?: 0.0) - (prevVolResolved ?: 0.0)
                              if (delta > 0.01) Color(0xFF2E7D32) else if (delta < -0.01) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            col in listOf(ReportGridColumn.RECEIVED, ReportGridColumn.DELIVERED) && cellText.startsWith("+") -> MaterialTheme.colorScheme.primary
                            !isClickable -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else -> MaterialTheme.colorScheme.onSurface
                          }

                          Text(
                            text = cellText,
                            fontFamily = if (col == ReportGridColumn.NOTE) FontFamily.Default else FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isCellActive || isCellCriticalMissing || col in listOf(ReportGridColumn.PRESENT_VOL, ReportGridColumn.PREV_VOL, ReportGridColumn.VARIANCE)) FontWeight.Bold else FontWeight.Normal,
                            color = textColor,
                            maxLines = 1
                          )
                        }
                      }
                    }
                  }
                }
              }
            }

            renderSection("FEED TANKS", feedTanks)
            renderSection("PRODUCT TANKS", productTanks)
            renderSection("BLENDING TANKS", blendingTanks)
            renderSection("EXCLUDED TANKS", excludedTanks)

            // C) PERMANENT ANCHORED SUMMARY ROW AT BOTTOM OF MAIN TANK MATRIX
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
              // Frozen Left Summary Cell
              Box(
                modifier = Modifier
                  .width(142.dp)
                  .height(52.dp)
                  .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                  .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
              ) {
                Column {
                  Text("GRID TOTALS", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                  Text("${filteredTanks.size - excludedTanks.size} active tanks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                }
              }

              // Horizontally Scrollable Summary Totals
              Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                headerCols.forEach { col ->
                  val summaryLabel: String
                  val summaryVal: String
                  when (col) {
                    ReportGridColumn.PRESENT_VOL -> {
                      summaryLabel = "Total Present"
                      summaryVal = formatVol(sumPresentLitres)
                    }
                    ReportGridColumn.PREV_VOL -> {
                      summaryLabel = "Total Prev"
                      summaryVal = formatVol(sumPrevLitres)
                    }
                    ReportGridColumn.VARIANCE -> {
                      summaryLabel = "Net Δ Stock"
                      summaryVal = formatVol(sumVarianceLitres, showSign = true)
                    }
                    ReportGridColumn.RECEIVED -> {
                      summaryLabel = "Total Rec"
                      summaryVal = formatVol(sumReceivedLitres)
                    }
                    ReportGridColumn.DELIVERED -> {
                      summaryLabel = "Total Del"
                      summaryVal = formatVol(sumDeliveredLitres)
                    }
                    ReportGridColumn.ADJUSTMENT -> {
                      summaryLabel = "Total Adj"
                      summaryVal = formatVol(sumAdjustmentLitres, showSign = true)
                    }
                    else -> {
                      summaryLabel = ""
                      summaryVal = "-"
                    }
                  }

                  Box(
                    modifier = Modifier
                      .width(col.widthDp.dp)
                      .height(52.dp)
                      .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                      .background(
                        if (col in listOf(ReportGridColumn.PRESENT_VOL, ReportGridColumn.VARIANCE, ReportGridColumn.RECEIVED, ReportGridColumn.DELIVERED))
                          MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else Color.Transparent
                      )
                      .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterEnd
                  ) {
                    if (summaryLabel.isNotEmpty()) {
                      Column(horizontalAlignment = Alignment.End) {
                        Text(summaryLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                        Text(
                          text = summaryVal,
                          fontFamily = FontFamily.Monospace,
                          fontWeight = FontWeight.ExtraBold,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.primary
                        )
                      }
                    } else {
                      Text("-", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f))
                    }
                  }
                }
              }
            }
          }
        }
      }

      // 4. LIVE MOVEMENT LEDGERS MATRIX (Feed Receipts & Product Deliveries — Inline Multi-Row Ledger)
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            // Top Bar for Live Movement Ledgers
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  "Live Movement Ledgers (Receipts & Deliveries)",
                  fontWeight = FontWeight.ExtraBold,
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.primary
                )
                Text(
                  "Multi-row ledger with per-batch Temp & Density tracking (@30°C live conversion)",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            // Quick Add Row Bar for Feed Receipts & Product Deliveries
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 10.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Quick Add:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
              feedTanks.forEach { ft ->
                AssistChip(
                  onClick = { addMovementEntryForTank(ft, true) },
                  label = { Text("+ ${ft.name} Rec", style = MaterialTheme.typography.labelSmall) }
                )
              }
              (productTanks + blendingTanks).forEach { pt ->
                AssistChip(
                  onClick = { addMovementEntryForTank(pt, false) },
                  label = { Text("+ ${pt.name} Del", style = MaterialTheme.typography.labelSmall) }
                )
              }
            }

            // Movement Ledger Freeze-Pane Header
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
              Box(
                modifier = Modifier
                  .width(142.dp)
                  .height(44.dp)
                  .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                  .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
              ) {
                Text("Tank / Type", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall)
              }

              Row(modifier = Modifier.horizontalScroll(ledgerHorizontalScrollState)) {
                LedgerGridColumn.entries.forEach { lCol ->
                  val hText = when (lCol) {
                    LedgerGridColumn.VOLUME -> "Volume ($unitSuffix)"
                    LedgerGridColumn.VOL_30C -> "Vol @30° ($unitSuffix)"
                    else -> lCol.title
                  }
                  Box(
                    modifier = Modifier
                      .width(lCol.widthDp.dp)
                      .height(44.dp)
                      .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                      .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(hText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                  }
                }
              }
            }

            // Movement Ledger Rows
            var totalRecNat = 0.0
            var totalRec30 = 0.0
            var totalDelNat = 0.0
            var totalDel30 = 0.0

            if (ledgerRows.isEmpty()) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  "No movement batches logged yet. Tap any '+ Rec' / '+ Del' cell or Quick Add chip above to log movements inline.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(horizontal = 16.dp)
                )
              }
            } else {
              ledgerRows.forEach { item ->
                val tank = item.tank
                val tEntry = todaySnapshot.tankEntries[tank.name] ?: ProductionReportTankEntry(tankName = tank.name)
                val effRole = ProductionReportEngine.getTankEffectiveRole(tank, tEntry)
                val hasCoeff = tank.coefficientSource != CoefficientSourceType.NONE

                val fallbackTemp = tEntry.tempTodayC ?: tEntry.temperatureC ?: 30.0
                val fallbackDens = if (effRole == TankRole.FEED) {
                  tEntry.densityToday15 ?: tEntry.density15 ?: 0.7360
                } else {
                  tEntry.densityToday30 ?: tEntry.density30 ?: 0.7500
                }

                val effTemp = item.entry.temperatureC ?: fallbackTemp
                val effDens = item.entry.density ?: fallbackDens
                val coeff = if (!hasCoeff) 0.0 else if (effRole == TankRole.FEED) {
                  ProductionReportEngine.findCoefficient(effDens, setup.vcf30Table).first
                } else {
                  ProductionReportEngine.findCoefficient(effDens, setup.vccTable).first
                }
                val volNat = item.entry.volumeNatural
                val vol30 = if (hasCoeff) volNat - (volNat * coeff * (effTemp - 30.0)) else volNat

                if (item.isReceived) {
                  totalRecNat += volNat
                  totalRec30 += vol30
                } else {
                  totalDelNat += volNat
                  totalDel30 += vol30
                }

                val isRowActive = activeLedgerCell?.entryId == item.entry.id

                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(
                      if (isRowActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                      else MaterialTheme.colorScheme.surface
                    )
                ) {
                  // Frozen Left Cell: Tank Name + Type Badge + Add another batch button
                  Box(
                    modifier = Modifier
                      .width(142.dp)
                      .height(48.dp)
                      .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                      .background(MaterialTheme.colorScheme.surfaceContainerLow)
                      .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Column {
                        Text(tank.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Surface(
                          color = if (item.isReceived) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                          shape = RoundedCornerShape(3.dp)
                        ) {
                          Text(
                            if (item.isReceived) "REC #${item.entryIndex + 1}" else "DEL #${item.entryIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                          )
                        }
                      }
                      IconButton(
                        onClick = { addMovementEntryForTank(tank, item.isReceived) },
                        modifier = Modifier.size(26.dp)
                      ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Add another batch", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                      }
                    }
                  }

                  // Scrollable Ledger Cells
                  Row(modifier = Modifier.horizontalScroll(ledgerHorizontalScrollState)) {
                    LedgerGridColumn.entries.forEach { lCol ->
                      val isLCellActive = activeLedgerCell?.entryId == item.entry.id && activeLedgerCell?.column == lCol

                      when (lCol) {
                        LedgerGridColumn.ACTION -> {
                          Box(
                            modifier = Modifier
                              .width(lCol.widthDp.dp)
                              .height(48.dp)
                              .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentAlignment = Alignment.Center
                          ) {
                            IconButton(
                              onClick = {
                                val map = todaySnapshot.tankEntries.toMutableMap()
                                val curr = map[tank.name] ?: ProductionReportTankEntry(tankName = tank.name)
                                map[tank.name] = if (item.isReceived) {
                                  curr.copy(receivedEntries = curr.receivedEntries.filterNot { it.id == item.entry.id })
                                } else {
                                  curr.copy(deliveredEntries = curr.deliveredEntries.filterNot { it.id == item.entry.id })
                                }
                                onUpdateTodaySnapshot(todaySnapshot.copy(tankEntries = map))
                                if (activeLedgerCell?.entryId == item.entry.id) {
                                  activeLedgerCell = null
                                }
                              },
                              modifier = Modifier.size(32.dp)
                            ) {
                              Icon(Icons.Default.DeleteOutline, contentDescription = "Remove batch", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                          }
                        }
                        LedgerGridColumn.VOL_30C -> {
                          Box(
                            modifier = Modifier
                              .width(lCol.widthDp.dp)
                              .height(48.dp)
                              .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                              .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                              .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterEnd
                          ) {
                            Text(
                              text = if (volNat > 0.0) formatVol(vol30) else "-",
                              fontFamily = FontFamily.Monospace,
                              fontWeight = FontWeight.Bold,
                              style = MaterialTheme.typography.bodySmall,
                              color = MaterialTheme.colorScheme.primary
                            )
                          }
                        }
                        else -> {
                          val textVal = when (lCol) {
                            LedgerGridColumn.VOLUME -> if (volNat > 0.0) formatVol(volNat) else "0"
                            LedgerGridColumn.TEMP -> if (!hasCoeff) "-" else String.format(Locale.US, "%.1f", effTemp)
                            LedgerGridColumn.DENSITY -> if (!hasCoeff) "-" else "${String.format(Locale.US, "%.4f", effDens)} ${if (item.isReceived) "@15" else "@30"}"
                            LedgerGridColumn.NOTE -> item.entry.note.ifBlank { "-" }
                            else -> "-"
                          }
                          val canEdit = lCol == LedgerGridColumn.VOLUME || lCol == LedgerGridColumn.NOTE || hasCoeff

                          Box(
                            modifier = Modifier
                              .width(lCol.widthDp.dp)
                              .height(48.dp)
                              .border(
                                width = if (isLCellActive) 2.dp else 0.5.dp,
                                color = if (isLCellActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                              )
                              .background(
                                if (isLCellActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else Color.Transparent
                              )
                              .clickable(enabled = canEdit) {
                                activeCell = null
                                activeLedgerCell = LedgerCellCoordinate(tank.name, item.isReceived, item.entry.id, lCol)
                              }
                              .padding(horizontal = 6.dp),
                            contentAlignment = if (lCol == LedgerGridColumn.NOTE) Alignment.CenterStart else Alignment.CenterEnd
                          ) {
                            Text(
                              text = textVal,
                              fontFamily = if (lCol == LedgerGridColumn.NOTE) FontFamily.Default else FontFamily.Monospace,
                              fontWeight = if (isLCellActive || lCol == LedgerGridColumn.VOLUME) FontWeight.Bold else FontWeight.Normal,
                              style = MaterialTheme.typography.bodySmall,
                              color = if (!canEdit) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
                              maxLines = 1
                            )
                          }
                        }
                      }
                    }
                  }
                }
              }
            }

            // Permanent Anchored Summary Row for Live Movement Ledgers
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
              Box(
                modifier = Modifier
                  .width(142.dp)
                  .height(50.dp)
                  .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                  .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
              ) {
                Column {
                  Text("LEDGER TOTALS", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                  Text("${ledgerRows.size} movement(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                }
              }

              Row(modifier = Modifier.horizontalScroll(ledgerHorizontalScrollState)) {
                // Total Natural Volume (Receipts & Deliveries)
                Box(
                  modifier = Modifier
                    .width(LedgerGridColumn.VOLUME.widthDp.dp)
                    .height(50.dp)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .padding(horizontal = 6.dp),
                  contentAlignment = Alignment.CenterEnd
                ) {
                  Column(horizontalAlignment = Alignment.End) {
                    Text("Rec: ${formatVol(totalRecNat)}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Del: ${formatVol(totalDelNat)}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                  }
                }

                // Temp placeholder
                Box(
                  modifier = Modifier
                    .width(LedgerGridColumn.TEMP.widthDp.dp)
                    .height(50.dp)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                  contentAlignment = Alignment.Center
                ) {
                  Text("Natural", style = MaterialTheme.typography.labelSmall)
                }

                // Density placeholder
                Box(
                  modifier = Modifier
                    .width(LedgerGridColumn.DENSITY.widthDp.dp)
                    .height(50.dp)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                  contentAlignment = Alignment.Center
                ) {
                  Text("→ @30°C", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                // Total @30°C Volume (Receipts & Deliveries)
                Box(
                  modifier = Modifier
                    .width(LedgerGridColumn.VOL_30C.widthDp.dp)
                    .height(50.dp)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    .padding(horizontal = 6.dp),
                  contentAlignment = Alignment.CenterEnd
                ) {
                  Column(horizontalAlignment = Alignment.End) {
                    Text("Rec: ${formatVol(totalRec30)}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Del: ${formatVol(totalDel30)}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                  }
                }

                // Net Balance in Note Column
                Box(
                  modifier = Modifier
                    .width((LedgerGridColumn.NOTE.widthDp + LedgerGridColumn.ACTION.widthDp).dp)
                    .height(50.dp)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .padding(horizontal = 8.dp),
                  contentAlignment = Alignment.CenterEnd
                ) {
                  Column(horizontalAlignment = Alignment.End) {
                    Text("Net Flow (Rec - Del)", style = MaterialTheme.typography.labelSmall)
                    Text(
                      "${formatVol(totalRecNat - totalDelNat, showSign = true)} $unitSuffix",
                      fontFamily = FontFamily.Monospace,
                      fontWeight = FontWeight.Bold,
                      style = MaterialTheme.typography.bodySmall
                    )
                  }
                }
              }
            }
          }
        }
      }

      // 5. "Other Operating Data" expandable section
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { showOtherData = !showOtherData },
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text("Other Operating Data", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Blending use, internal consumption, waste meters, operating loads", style = MaterialTheme.typography.bodySmall)
              }
              Icon(if (showOtherData) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }

            if (showOtherData) {
              HorizontalDivider()
              OtherOperatingDataEditor(
                otherData = todaySnapshot.otherData,
                setup = setup,
                blendRecords = blendRecords,
                onUpdate = { onUpdateTodaySnapshot(todaySnapshot.copy(otherData = it)) }
              )
            }
          }
        }
      }
    }

    // 6A. DOCKED INDUSTRIAL KEYPAD FOR MAIN TANK MATRIX
    if (activeCell != null) {
      val coord = activeCell!!
      val tank = tanks.find { it.name == coord.tankName }
      if (tank != null) {
        val entry = todaySnapshot.tankEntries[tank.name] ?: ProductionReportTankEntry(tankName = tank.name)
        val prevEntry = prevSnapshot.tankEntries[tank.name] ?: ProductionReportTankEntry(tankName = tank.name)
        val effRole = ProductionReportEngine.getTankEffectiveRole(tank, entry)
        val hasCoeff = tank.coefficientSource != CoefficientSourceType.NONE && effRole != TankRole.EXCLUDED

        val rawValue = when (coord.column) {
          ReportGridColumn.PRESENT_VOL -> {
            val v = entry.volumeTodayInput
            if (v != null) {
              val disp = if (displayUnit == UnitType.M3) v / 1000.0 else v
              if (disp % 1.0 == 0.0) disp.toInt().toString() else disp.toString()
            } else ""
          }
          ReportGridColumn.PRESENT_TEMP -> (entry.tempTodayC ?: entry.temperatureC)?.toString() ?: ""
          ReportGridColumn.PRESENT_DENSITY -> {
            if (effRole == TankRole.FEED) (entry.densityToday15 ?: entry.density15)?.toString() ?: ""
            else (entry.densityToday30 ?: entry.density30)?.toString() ?: ""
          }
          ReportGridColumn.PREV_VOL -> {
            val v = entry.volumePrevOverride ?: entry.volumePrevInput ?: prevEntry.volumeTodayInput
            if (v != null) {
              val disp = if (displayUnit == UnitType.M3) v / 1000.0 else v
              if (disp % 1.0 == 0.0) disp.toInt().toString() else disp.toString()
            } else ""
          }
          ReportGridColumn.PREV_TEMP -> (entry.tempPrevOverride ?: entry.tempPrevC ?: prevEntry.tempTodayC ?: prevEntry.temperatureC)?.toString() ?: ""
          ReportGridColumn.PREV_DENSITY -> {
            if (effRole == TankRole.FEED) (entry.densityPrevOverride15 ?: entry.densityPrev15 ?: prevEntry.densityToday15 ?: prevEntry.density15)?.toString() ?: ""
            else (entry.densityPrevOverride30 ?: entry.densityPrev30 ?: prevEntry.densityToday30 ?: prevEntry.density30)?.toString() ?: ""
          }
          ReportGridColumn.ADJUSTMENT -> {
            val adj = entry.adjustmentL
            if (abs(adj) > 0.001) adj.toInt().toString() else ""
          }
          ReportGridColumn.NOTE -> entry.adjustmentNote
          else -> ""
        }

        var textInput by remember(coord.tankName, coord.column, displayUnit) { mutableStateOf(rawValue) }

        val applyChange: (String) -> Unit = { newStr ->
          textInput = newStr
          val numVal = newStr.toDoubleOrNull()
          val updatedTodayEntries = todaySnapshot.tankEntries.toMutableMap()
          val current = updatedTodayEntries[tank.name] ?: ProductionReportTankEntry(tankName = tank.name)

          val modified = when (coord.column) {
            ReportGridColumn.PRESENT_VOL -> {
              val litres = if (numVal != null) {
                if (displayUnit == UnitType.M3) numVal * 1000.0 else numVal
              } else null
              current.copy(
                volumeTodayInput = litres,
                useVolumeModeToday = true,
                isFromDipSheetToday = false
              )
            }
            ReportGridColumn.PRESENT_TEMP -> current.copy(tempTodayC = numVal, temperatureC = numVal)
            ReportGridColumn.PRESENT_DENSITY -> {
              if (effRole == TankRole.FEED) current.copy(densityToday15 = numVal, density15 = numVal)
              else current.copy(densityToday30 = numVal, density30 = numVal)
            }
            ReportGridColumn.PREV_VOL -> {
              val litres = if (numVal != null) {
                if (displayUnit == UnitType.M3) numVal * 1000.0 else numVal
              } else null
              current.copy(
                volumePrevOverride = litres,
                volumePrevInput = litres,
                useVolumeModePrev = true,
                isFromDipSheetPrev = false
              )
            }
            ReportGridColumn.PREV_TEMP -> current.copy(tempPrevOverride = numVal, tempPrevC = numVal, temperatureC = numVal)
            ReportGridColumn.PREV_DENSITY -> {
              if (effRole == TankRole.FEED) current.copy(densityPrevOverride15 = numVal, densityPrev15 = numVal, density15 = numVal)
              else current.copy(densityPrevOverride30 = numVal, densityPrev30 = numVal, density30 = numVal)
            }
            ReportGridColumn.ADJUSTMENT -> current.copy(adjustmentL = numVal ?: 0.0)
            ReportGridColumn.NOTE -> current.copy(adjustmentNote = newStr)
            else -> current
          }
          updatedTodayEntries[tank.name] = modified
          onUpdateTodaySnapshot(todaySnapshot.copy(tankEntries = updatedTodayEntries))
        }

        // Editable columns for horizontal Tab traversal
        val editableColsForTank: (Tank) -> List<ReportGridColumn> = { t ->
          val tEntry = todaySnapshot.tankEntries[t.name] ?: ProductionReportTankEntry(tankName = t.name)
          val role = ProductionReportEngine.getTankEffectiveRole(t, tEntry)
          val coeffEnabled = t.coefficientSource != CoefficientSourceType.NONE && role != TankRole.EXCLUDED
          val list = mutableListOf(ReportGridColumn.PRESENT_VOL)
          if (coeffEnabled) {
            list.add(ReportGridColumn.PRESENT_TEMP)
            list.add(ReportGridColumn.PRESENT_DENSITY)
          }
          list.add(ReportGridColumn.PREV_VOL)
          if (coeffEnabled) {
            list.add(ReportGridColumn.PREV_TEMP)
            list.add(ReportGridColumn.PREV_DENSITY)
          }
          if (showMoreColumns) {
            list.add(ReportGridColumn.ADJUSTMENT)
            list.add(ReportGridColumn.NOTE)
          }
          list
        }

        // Enter moves DOWN to next row's corresponding column
        val onEnterDown: () -> Unit = {
          if (filteredTanks.isNotEmpty()) {
            val currIdx = filteredTanks.indexOfFirst { it.name == coord.tankName }.coerceAtLeast(0)
            val nextTank = filteredTanks[(currIdx + 1) % filteredTanks.size]
            val nextTankCols = editableColsForTank(nextTank)
            val targetCol = if (coord.column in nextTankCols) coord.column else ReportGridColumn.PRESENT_VOL
            activeCell = GridCellCoordinate(nextTank.name, targetCol)
          }
        }

        val onMoveUp: () -> Unit = {
          if (filteredTanks.isNotEmpty()) {
            val currIdx = filteredTanks.indexOfFirst { it.name == coord.tankName }.coerceAtLeast(0)
            val prevIdx = if (currIdx - 1 < 0) filteredTanks.size - 1 else currIdx - 1
            val prevTank = filteredTanks[prevIdx]
            val prevTankCols = editableColsForTank(prevTank)
            val targetCol = if (coord.column in prevTankCols) coord.column else ReportGridColumn.PRESENT_VOL
            activeCell = GridCellCoordinate(prevTank.name, targetCol)
          }
        }

        // Tab moves RIGHT across columns in the same row, then wraps to next row's first column
        val onTabRight: () -> Unit = {
          val cols = editableColsForTank(tank)
          val colIdx = cols.indexOf(coord.column)
          if (colIdx >= 0 && colIdx < cols.size - 1) {
            activeCell = GridCellCoordinate(tank.name, cols[colIdx + 1])
          } else if (filteredTanks.isNotEmpty()) {
            val currIdx = filteredTanks.indexOfFirst { it.name == coord.tankName }.coerceAtLeast(0)
            val nextTank = filteredTanks[(currIdx + 1) % filteredTanks.size]
            activeCell = GridCellCoordinate(nextTank.name, ReportGridColumn.PRESENT_VOL)
          }
        }

        val onShiftTabLeft: () -> Unit = {
          val cols = editableColsForTank(tank)
          val colIdx = cols.indexOf(coord.column)
          if (colIdx > 0) {
            activeCell = GridCellCoordinate(tank.name, cols[colIdx - 1])
          } else if (filteredTanks.isNotEmpty()) {
            val currIdx = filteredTanks.indexOfFirst { it.name == coord.tankName }.coerceAtLeast(0)
            val prevIdx = if (currIdx - 1 < 0) filteredTanks.size - 1 else currIdx - 1
            val prevTank = filteredTanks[prevIdx]
            val prevCols = editableColsForTank(prevTank)
            activeCell = GridCellCoordinate(prevTank.name, prevCols.last())
          }
        }

        val unitTag = when (coord.column) {
          ReportGridColumn.PRESENT_VOL, ReportGridColumn.PREV_VOL -> unitSuffix
          ReportGridColumn.PRESENT_TEMP, ReportGridColumn.PREV_TEMP -> "°C"
          ReportGridColumn.PRESENT_DENSITY, ReportGridColumn.PREV_DENSITY -> if (effRole == TankRole.FEED) "@15" else "@30"
          ReportGridColumn.ADJUSTMENT -> "L"
          else -> null
        }

        val (today30Preview, _) = ProductionReportEngine.resolveTankVolume(entry, tank, setup, CalculationBasis.TEMP_30C, false, calibrationProvider)
        val livePreview = if (today30Preview > 0.0) "@30°C: ${formatVol(today30Preview)} $unitSuffix" else null

        val presVol = entry.volumeTodayInput ?: 0.0
        val warnText = if (presVol > 0.0 && hasCoeff && (entry.tempTodayC == null && entry.temperatureC == null || (if (effRole == TankRole.FEED) entry.densityToday15 == null && entry.density15 == null else entry.densityToday30 == null && entry.density30 == null))) {
          "Temp & Density (${if (effRole == TankRole.FEED) "@15" else "@30"}) required for 30 °C report"
        } else null

        IndustrialKeypadDock(
          cellCoordinateLabel = tank.name,
          columnTitle = coord.column.title,
          unitBadge = unitTag,
          valueText = textInput,
          isTextMode = coord.column == ReportGridColumn.NOTE,
          allowNegative = coord.column == ReportGridColumn.ADJUSTMENT,
          validationWarning = warnText,
          livePreviewText = livePreview,
          onValueChange = applyChange,
          onEnterMoveDown = onEnterDown,
          onMoveUp = onMoveUp,
          onTabMoveRight = onTabRight,
          onShiftTabMoveLeft = onShiftTabLeft,
          onClose = { activeCell = null },
          modifier = Modifier.align(Alignment.BottomCenter)
        )
      }
    }

    // 6B. DOCKED INDUSTRIAL KEYPAD FOR LIVE MOVEMENT LEDGERS MATRIX
    if (activeLedgerCell != null) {
      val lCoord = activeLedgerCell!!
      val currentIdx = ledgerRows.indexOfFirst { it.entry.id == lCoord.entryId }
      val rowItem = ledgerRows.getOrNull(currentIdx)
      if (rowItem != null) {
        val tank = rowItem.tank
        val mov = rowItem.entry
        val effRole = ProductionReportEngine.getTankEffectiveRole(tank, todaySnapshot.tankEntries[tank.name])
        val hasCoeff = tank.coefficientSource != CoefficientSourceType.NONE

        val initialStr = when (lCoord.column) {
          LedgerGridColumn.VOLUME -> {
            if (mov.volumeNatural > 0.0) {
              val disp = if (displayUnit == UnitType.M3) mov.volumeNatural / 1000.0 else mov.volumeNatural
              if (disp % 1.0 == 0.0) disp.toInt().toString() else disp.toString()
            } else ""
          }
          LedgerGridColumn.TEMP -> mov.temperatureC?.toString() ?: ""
          LedgerGridColumn.DENSITY -> mov.density?.toString() ?: ""
          LedgerGridColumn.NOTE -> mov.note
          else -> ""
        }

        var ledgerInput by remember(lCoord.entryId, lCoord.column, displayUnit) { mutableStateOf(initialStr) }

        val updateLedgerEntry: (String) -> Unit = { newStr ->
          ledgerInput = newStr
          val numVal = newStr.toDoubleOrNull()
          val map = todaySnapshot.tankEntries.toMutableMap()
          val curr = map[tank.name] ?: ProductionReportTankEntry(tankName = tank.name)
          val list = (if (rowItem.isReceived) curr.receivedEntries else curr.deliveredEntries).toMutableList()
          val idxInTank = list.indexOfFirst { it.id == mov.id }
          if (idxInTank >= 0) {
            val updatedMov = when (lCoord.column) {
              LedgerGridColumn.VOLUME -> {
                val litres = if (numVal != null) {
                  if (displayUnit == UnitType.M3) numVal * 1000.0 else numVal
                } else 0.0
                mov.copy(volumeNatural = litres)
              }
              LedgerGridColumn.TEMP -> mov.copy(temperatureC = numVal)
              LedgerGridColumn.DENSITY -> mov.copy(density = numVal)
              LedgerGridColumn.NOTE -> mov.copy(note = newStr)
              else -> mov
            }
            list[idxInTank] = updatedMov
            map[tank.name] = if (rowItem.isReceived) curr.copy(receivedEntries = list) else curr.copy(deliveredEntries = list)
            onUpdateTodaySnapshot(todaySnapshot.copy(tankEntries = map))
          }
        }

        val editableLedgerCols = if (hasCoeff) {
          listOf(LedgerGridColumn.VOLUME, LedgerGridColumn.TEMP, LedgerGridColumn.DENSITY, LedgerGridColumn.NOTE)
        } else {
          listOf(LedgerGridColumn.VOLUME, LedgerGridColumn.NOTE)
        }

        val onLedgerEnterDown: () -> Unit = {
          if (ledgerRows.isNotEmpty()) {
            val nextRow = ledgerRows[(currentIdx + 1) % ledgerRows.size]
            activeLedgerCell = LedgerCellCoordinate(nextRow.tank.name, nextRow.isReceived, nextRow.entry.id, lCoord.column)
          }
        }

        val onLedgerMoveUp: () -> Unit = {
          if (ledgerRows.isNotEmpty()) {
            val prevIdx = if (currentIdx - 1 < 0) ledgerRows.size - 1 else currentIdx - 1
            val prevRow = ledgerRows[prevIdx]
            activeLedgerCell = LedgerCellCoordinate(prevRow.tank.name, prevRow.isReceived, prevRow.entry.id, lCoord.column)
          }
        }

        val onLedgerTabRight: () -> Unit = {
          val cIdx = editableLedgerCols.indexOf(lCoord.column)
          if (cIdx >= 0 && cIdx < editableLedgerCols.size - 1) {
            activeLedgerCell = lCoord.copy(column = editableLedgerCols[cIdx + 1])
          } else if (ledgerRows.isNotEmpty()) {
            val nextRow = ledgerRows[(currentIdx + 1) % ledgerRows.size]
            activeLedgerCell = LedgerCellCoordinate(nextRow.tank.name, nextRow.isReceived, nextRow.entry.id, LedgerGridColumn.VOLUME)
          }
        }

        val onLedgerShiftTabLeft: () -> Unit = {
          val cIdx = editableLedgerCols.indexOf(lCoord.column)
          if (cIdx > 0) {
            activeLedgerCell = lCoord.copy(column = editableLedgerCols[cIdx - 1])
          } else if (ledgerRows.isNotEmpty()) {
            val prevIdx = if (currentIdx - 1 < 0) ledgerRows.size - 1 else currentIdx - 1
            val prevRow = ledgerRows[prevIdx]
            activeLedgerCell = LedgerCellCoordinate(prevRow.tank.name, prevRow.isReceived, prevRow.entry.id, editableLedgerCols.last())
          }
        }

        val unitBadge = when (lCoord.column) {
          LedgerGridColumn.VOLUME -> unitSuffix
          LedgerGridColumn.TEMP -> "°C"
          LedgerGridColumn.DENSITY -> if (effRole == TankRole.FEED) "@15" else "@30"
          else -> null
        }

        IndustrialKeypadDock(
          cellCoordinateLabel = "${tank.name} ${if (rowItem.isReceived) "REC #${rowItem.entryIndex + 1}" else "DEL #${rowItem.entryIndex + 1}"}",
          columnTitle = lCoord.column.title,
          unitBadge = unitBadge,
          valueText = ledgerInput,
          isTextMode = lCoord.column == LedgerGridColumn.NOTE,
          allowNegative = false,
          onValueChange = updateLedgerEntry,
          onEnterMoveDown = onLedgerEnterDown,
          onMoveUp = onLedgerMoveUp,
          onTabMoveRight = onLedgerTabRight,
          onShiftTabMoveLeft = onLedgerShiftTabLeft,
          onClose = { activeLedgerCell = null },
          modifier = Modifier.align(Alignment.BottomCenter)
        )
      }
    }
  }

  // 7. TANK ROLE BOTTOM SHEET
  if (tankForRoleSheet != null) {
    val targetTank = tankForRoleSheet!!
    val currentEntry = todaySnapshot.tankEntries[targetTank.name] ?: ProductionReportTankEntry(tankName = targetTank.name)

    TankRoleBottomSheet(
      tank = targetTank,
      currentEntry = currentEntry,
      products = products,
      storedPasswordHash = settings.passwordHash,
      onDismiss = { tankForRoleSheet = null },
      onApplyReportOnly = { newRole, productGroup ->
        val updatedEntry = currentEntry.copy(roleOverride = newRole, productGroupOverride = productGroup)
        val map = todaySnapshot.tankEntries.toMutableMap()
        map[targetTank.name] = updatedEntry
        onUpdateTodaySnapshot(todaySnapshot.copy(tankEntries = map))
        tankForRoleSheet = null
        Toast.makeText(context, "Role updated for this report only", Toast.LENGTH_SHORT).show()
      },
      onApplyGlobal = { newType, productGroup ->
        val updatedTank = targetTank.copy(
          type = newType,
          productId = productGroup ?: targetTank.productId
        )
        onUpdateTank(updatedTank)
        onLogEdit("ROLE_CHANGE_GLOBAL", targetTank.name, "Changed type to $newType with group $productGroup globally")
        val role = when (newType) {
          TankType.FEED -> TankRole.FEED
          TankType.PRODUCT -> TankRole.PRODUCT
          TankType.BLENDING -> TankRole.BLENDING
          TankType.N2_STORAGE -> TankRole.EXCLUDED
        }
        val updatedEntry = currentEntry.copy(roleOverride = role, productGroupOverride = productGroup)
        val map = todaySnapshot.tankEntries.toMutableMap()
        map[targetTank.name] = updatedEntry
        onUpdateTodaySnapshot(todaySnapshot.copy(tankEntries = map))
        tankForRoleSheet = null
        Toast.makeText(context, "Role updated globally in Tank Master", Toast.LENGTH_SHORT).show()
      }
    )
  }

  // 8. OPTIONAL MOVEMENT ENTRIES POPUP (if opened explicitly)
  if (movementDialogInfo != null) {
    val (mTank, isRec) = movementDialogInfo!!
    val entry = todaySnapshot.tankEntries[mTank.name] ?: ProductionReportTankEntry(tankName = mTank.name)
    val entriesList = if (isRec) entry.receivedEntries else entry.deliveredEntries
    val effRole = ProductionReportEngine.getTankEffectiveRole(mTank, entry)

    MovementEntriesDialog(
      tank = mTank,
      isReceived = isRec,
      currentEntries = entriesList,
      tankPresentTemp = entry.tempTodayC ?: entry.temperatureC ?: 30.0,
      tankPresentDensity = if (effRole == TankRole.FEED) (entry.densityToday15 ?: entry.density15 ?: 0.736) else (entry.densityToday30 ?: entry.density30 ?: 0.750),
      densityBasisLabel = if (effRole == TankRole.FEED) "@15" else "@30",
      unit = displayUnit,
      onDismiss = { movementDialogInfo = null },
      onSave = { updatedList ->
        val map = todaySnapshot.tankEntries.toMutableMap()
        val curr = map[mTank.name] ?: ProductionReportTankEntry(tankName = mTank.name)
        map[mTank.name] = if (isRec) curr.copy(receivedEntries = updatedList) else curr.copy(deliveredEntries = updatedList)
        onUpdateTodaySnapshot(todaySnapshot.copy(tankEntries = map))
        movementDialogInfo = null
        Toast.makeText(context, "Saved ${if (isRec) "receipts" else "deliveries"} for ${mTank.name}", Toast.LENGTH_SHORT).show()
      }
    )
  }

  // 9. UPDATE PREVIOUS DAY'S RECORD CONFIRMATION DIALOG
  if (tankToUpdatePrev != null) {
    val uTank = tankToUpdatePrev!!
    val entry = todaySnapshot.tankEntries[uTank.name]
    val currPrevVol = entry?.volumePrevOverride ?: entry?.volumePrevInput ?: 0.0

    AlertDialog(
      onDismissRequest = { tankToUpdatePrev = null },
      title = { Text("Update Previous Day's Record?") },
      text = {
        Text("Update the saved Present volume of ${uTank.name} on $prevDateStr to match today's Previous value (${String.format(Locale.US, "%,.0f", currPrevVol)} L)?")
      },
      confirmButton = {
        Button(onClick = {
          val prevMap = prevSnapshot.tankEntries.toMutableMap()
          val currPrev = prevMap[uTank.name] ?: ProductionReportTankEntry(tankName = uTank.name)
          prevMap[uTank.name] = currPrev.copy(
            volumeTodayInput = currPrevVol,
            tempTodayC = entry?.tempPrevOverride ?: entry?.tempPrevC ?: currPrev.tempTodayC,
            densityToday15 = entry?.densityPrevOverride15 ?: entry?.densityPrev15 ?: currPrev.densityToday15,
            densityToday30 = entry?.densityPrevOverride30 ?: entry?.densityPrev30 ?: currPrev.densityToday30
          )
          val newPrevSnap = prevSnapshot.copy(tankEntries = prevMap, lastModified = System.currentTimeMillis())
          onUpdatePrevSnapshot(newPrevSnap)
          repository.saveProductionReportDay(newPrevSnap)

          if (entry != null) {
            val todayMap = todaySnapshot.tankEntries.toMutableMap()
            todayMap[uTank.name] = entry.copy(volumePrevOverride = null)
            onUpdateTodaySnapshot(todaySnapshot.copy(tankEntries = todayMap))
          }

          tankToUpdatePrev = null
          Toast.makeText(context, "Updated record for $prevDateStr", Toast.LENGTH_SHORT).show()
        }) {
          Text("Update")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { tankToUpdatePrev = null }) {
          Text("Cancel")
        }
      }
    )
  }
}

/**
 * Multi-entry popup for Feed Receipts and Product Deliveries
 */
@Composable
fun MovementEntriesDialog(
  tank: Tank,
  isReceived: Boolean,
  currentEntries: List<MovementTransactionEntry>,
  tankPresentTemp: Double,
  tankPresentDensity: Double,
  densityBasisLabel: String,
  unit: UnitType,
  onDismiss: () -> Unit,
  onSave: (List<MovementTransactionEntry>) -> Unit
) {
  var entries by remember {
    mutableStateOf(
      if (currentEntries.isEmpty()) {
        listOf(MovementTransactionEntry(volumeNatural = 0.0, temperatureC = tankPresentTemp, density = tankPresentDensity))
      } else {
        currentEntries
      }
    )
  }

  val unitLabel = if (unit == UnitType.M3) "m³" else "L"
  val totalNatLitres = entries.sumOf { it.volumeNatural }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (isReceived) Icons.Default.Input else Icons.Default.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
          Text("${tank.name} - ${if (isReceived) "Receipts" else "Deliveries"}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          Text(if (isReceived) "Feed received into tank" else "Product delivered / sold from tank", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        LazyColumn(
          modifier = Modifier.weight(1f, fill = false),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(entries.indices.toList()) { index ->
            val item = entries[index]
            val dispVol = if (unit == UnitType.M3) item.volumeNatural / 1000.0 else item.volumeNatural
            var volStr by remember(item.id) { mutableStateOf(if (dispVol > 0) dispVol.toString() else "") }
            var tempStr by remember(item.id) { mutableStateOf(item.temperatureC?.toString() ?: "") }
            var densStr by remember(item.id) { mutableStateOf(item.density?.toString() ?: "") }

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Entry #${index + 1}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                  if (entries.size > 1) {
                    IconButton(
                      onClick = { entries = entries.filterIndexed { i, _ -> i != index } },
                      modifier = Modifier.size(24.dp)
                    ) {
                      Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                  }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  OutlinedTextField(
                    value = volStr,
                    onValueChange = { nv ->
                      volStr = nv
                      val num = nv.toDoubleOrNull() ?: 0.0
                      val actualLitres = if (unit == UnitType.M3) num * 1000.0 else num
                      val updated = entries.toMutableList()
                      updated[index] = item.copy(volumeNatural = actualLitres)
                      entries = updated
                    },
                    label = { Text("Volume ($unitLabel)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                  )

                  OutlinedTextField(
                    value = tempStr,
                    onValueChange = { nv ->
                      tempStr = nv
                      val updated = entries.toMutableList()
                      updated[index] = item.copy(temperatureC = nv.toDoubleOrNull())
                      entries = updated
                    },
                    label = { Text("Temp (°C)") },
                    placeholder = { Text(String.format(Locale.US, "%.1f", tankPresentTemp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(0.9f)
                  )

                  OutlinedTextField(
                    value = densStr,
                    onValueChange = { nv ->
                      densStr = nv
                      val updated = entries.toMutableList()
                      updated[index] = item.copy(density = nv.toDoubleOrNull())
                      entries = updated
                    },
                    label = { Text("Dens $densityBasisLabel") },
                    placeholder = { Text(String.format(Locale.US, "%.4f", tankPresentDensity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1.0f)
                  )
                }
              }
            }
          }
        }

        // Add Entry Button
        OutlinedButton(
          onClick = {
            entries = entries + MovementTransactionEntry(volumeNatural = 0.0, temperatureC = tankPresentTemp, density = tankPresentDensity)
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Add Entry Row")
        }

        // Running Total Card
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Total Quantity (${entries.size} entries):", style = MaterialTheme.typography.labelSmall)
              Text(
                if (unit == UnitType.M3) String.format(Locale.US, "%,.3f m³ (%,.0f L)", totalNatLitres / 1000.0, totalNatLitres)
                else String.format(Locale.US, "%,.0f L", totalNatLitres),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = {
        val validList = entries.filter { it.volumeNatural > 0 }
        onSave(validList)
      }) {
        Text("Save & Apply")
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
 * Bottom Sheet for Changing Tank Role (Scope: Report Only vs Globally)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TankRoleBottomSheet(
  tank: Tank,
  currentEntry: ProductionReportTankEntry,
  products: List<Product>,
  storedPasswordHash: String,
  onDismiss: () -> Unit,
  onApplyReportOnly: (TankRole, String?) -> Unit,
  onApplyGlobal: (TankType, String?) -> Unit
) {
  val defaultRole = when (tank.type) {
    TankType.FEED -> TankRole.FEED
    TankType.PRODUCT -> TankRole.PRODUCT
    TankType.BLENDING -> TankRole.BLENDING
    TankType.N2_STORAGE -> TankRole.EXCLUDED
  }
  var selectedRole by remember { mutableStateOf(currentEntry.roleOverride ?: defaultRole) }
  val standardGroups = listOf("LN", "Reformate", "Jet A-1", "SKO/JBO/MTT", "Diesel", "MS", "Octane", "DSN", "LPG", "Other")
  var selectedProductGroup by remember {
    mutableStateOf(currentEntry.productGroupOverride ?: tank.linkedProductId.ifBlank { "Other" })
  }
  var isGlobalScope by remember { mutableStateOf(false) }
  var passwordInput by remember { mutableStateOf("") }
  var passwordError by remember { mutableStateOf<String?>(null) }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Text(
        "Configure Role for ${tank.name}",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
      )

      // a) Type / Role Selector
      Text("1. Select Role:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf(TankRole.FEED, TankRole.PRODUCT, TankRole.BLENDING, TankRole.EXCLUDED).forEachIndexed { idx, r ->
          SegmentedButton(
            selected = selectedRole == r,
            onClick = { selectedRole = r },
            shape = SegmentedButtonDefaults.itemShape(index = idx, count = 4)
          ) {
            Text(r.name.lowercase().replaceFirstChar { it.uppercase() })
          }
        }
      }

      // Product group picker if Product or Blending
      if (selectedRole == TankRole.PRODUCT || selectedRole == TankRole.BLENDING) {
        Text("Product Group (Required):", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        var expandedGroup by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
          expanded = expandedGroup,
          onExpandedChange = { expandedGroup = !expandedGroup }
        ) {
          OutlinedTextField(
            value = selectedProductGroup,
            onValueChange = { selectedProductGroup = it },
            readOnly = true,
            label = { Text("Product Group") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedGroup) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
          )
          ExposedDropdownMenu(
            expanded = expandedGroup,
            onDismissRequest = { expandedGroup = false }
          ) {
            standardGroups.forEach { grp ->
              DropdownMenuItem(
                text = { Text(grp) },
                onClick = {
                  selectedProductGroup = grp
                  expandedGroup = false
                }
              )
            }
          }
        }
      }

      // b) Scope Selector
      Text("2. Scope:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = !isGlobalScope,
          onClick = { isGlobalScope = false },
          label = { Text("This report only") },
          modifier = Modifier.weight(1f)
        )
        FilterChip(
          selected = isGlobalScope,
          onClick = { isGlobalScope = true },
          label = { Text("Change globally in Tank master") },
          modifier = Modifier.weight(1f)
        )
      }

      if (isGlobalScope) {
        OutlinedTextField(
          value = passwordInput,
          onValueChange = {
            passwordInput = it
            passwordError = null
          },
          label = { Text("Administrator Password") },
          visualTransformation = PasswordVisualTransformation(),
          isError = passwordError != null,
          supportingText = passwordError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
          modifier = Modifier.fillMaxWidth()
        )
      }

      // c) Confirmation Line Showing Effect
      val prevDesc = when (tank.type) {
        TankType.FEED -> "Raw materials (Feed)"
        TankType.PRODUCT -> "Product group ${tank.linkedProductId.ifBlank { "Other" }}"
        TankType.BLENDING -> "Blending (${tank.linkedProductId.ifBlank { "MS/Octane" }})"
        TankType.N2_STORAGE -> "Excluded / N2"
      }
      val newDesc = when (selectedRole) {
        TankRole.FEED -> "Raw materials (Feed)"
        TankRole.PRODUCT -> "Product group $selectedProductGroup"
        TankRole.BLENDING -> "Blending ($selectedProductGroup)"
        TankRole.EXCLUDED -> "Excluded"
      }

      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            "${tank.name} moves from $prevDesc to $newDesc.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
          )
        }
      }

      // Action Buttons
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = {
            if (isGlobalScope) {
              if (!SecurityUtils.verifyPassword(passwordInput, storedPasswordHash)) {
                passwordError = "Incorrect password"
                return@Button
              }
              val newType = when (selectedRole) {
                TankRole.FEED -> TankType.FEED
                TankRole.PRODUCT -> TankType.PRODUCT
                TankRole.BLENDING -> TankType.BLENDING
                TankRole.EXCLUDED -> TankType.N2_STORAGE
              }
              onApplyGlobal(newType, if (selectedRole == TankRole.PRODUCT || selectedRole == TankRole.BLENDING) selectedProductGroup else null)
            } else {
              onApplyReportOnly(selectedRole, if (selectedRole == TankRole.PRODUCT || selectedRole == TankRole.BLENDING) selectedProductGroup else null)
            }
          }
        ) {
          Text(if (isGlobalScope) "Apply Globally" else "Apply for Report")
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

/**
 * Single Tank Card on Input Tab
 */
@Composable
fun ProductionReportTankCard(
  tank: Tank,
  setup: ProductionReportSetup,
  settings: AppSettings,
  prevEntry: ProductionReportTankEntry,
  todayEntry: ProductionReportTankEntry,
  onUpdatePrev: (ProductionReportTankEntry) -> Unit,
  onUpdateToday: (ProductionReportTankEntry) -> Unit,
  calibrationProvider: (String, Double) -> Double?,
  onInspectTank: (Tank) -> Unit,
  onOpenRoleSheet: () -> Unit,
  products: List<Product> = emptyList()
) {
  val defaultTankRole = when (tank.type) {
    TankType.FEED -> TankRole.FEED
    TankType.PRODUCT -> TankRole.PRODUCT
    TankType.BLENDING -> TankRole.BLENDING
    TankType.N2_STORAGE -> TankRole.EXCLUDED
  }
  val effectiveRole = ProductionReportEngine.getTankEffectiveRole(tank, todayEntry)
  val isExcluded = effectiveRole == TankRole.EXCLUDED
  val roleDiffers = todayEntry.roleOverride != null && todayEntry.roleOverride != defaultTankRole

  val (prevNatVol, _) = ProductionReportEngine.resolveTankVolume(prevEntry, tank, setup, CalculationBasis.NATURAL, true, calibrationProvider)
  val (prev30Vol, _) = ProductionReportEngine.resolveTankVolume(prevEntry, tank, setup, CalculationBasis.TEMP_30C, true, calibrationProvider)

  val (todayNatVol, _) = ProductionReportEngine.resolveTankVolume(todayEntry, tank, setup, CalculationBasis.NATURAL, false, calibrationProvider)
  val (today30Vol, todayCoeff) = ProductionReportEngine.resolveTankVolume(todayEntry, tank, setup, CalculationBasis.TEMP_30C, false, calibrationProvider)

  val isDipToday = !todayEntry.useVolumeModeToday && tank.dipTable.isNotEmpty() && tank.defaultInputMode == InputMode.DIP
  val calCheck = if (isDipToday) {
    ProductionReportEngine.checkCalibration(tank, todayEntry.dipMmToday ?: todayEntry.dipMm, settings.invalidInputThreshold)
  } else {
    ProductionReportEngine.CalibrationCheckResult(true, "Volume Input (Direct)")
  }

  // Calibration changed check on saved days
  val currentCalVol = if (isDipToday && (todayEntry.dipMmToday ?: todayEntry.dipMm) != null) {
    Calculators.calculateDipVolume(tank, (todayEntry.dipMmToday ?: todayEntry.dipMm)!!, settings.invalidInputThreshold)?.totalVolume
  } else null
  val hasCalChanged = isDipToday && todayEntry.savedNaturalVolume != null && currentCalVol != null && kotlin.math.abs(currentCalVol - todayEntry.savedNaturalVolume) > 0.01

  val linkedProd = products.find { it.id == tank.productId || it.tankIds.contains(tank.id) }

  Card(
    colors = CardDefaults.cardColors(
      containerColor = if (isExcluded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
    ),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .padding(12.dp)
        .alpha(if (isExcluded) 0.6f else 1.0f),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Header: Tank Name + Type + Role Chip + Differ Badge + Calibration
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tank.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (linkedProd != null) {
              ProductBadge(name = linkedProd.name, shortCode = linkedProd.shortCode, colorHex = linkedProd.colorHex)
            } else {
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  tank.type.name,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }

          val shapeName = tank.shape.name.lowercase().replaceFirstChar { it.uppercase() }
          Text(
            "$shapeName • Cap: ${String.format(Locale.US, "%,.0f", tank.maxVolumeLiters)} L",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        // Role Chip + Differ Badge
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
          AssistChip(
            onClick = onOpenRoleSheet,
            label = { Text(effectiveRole.name) },
            leadingIcon = {
              Icon(
                when (effectiveRole) {
                  TankRole.FEED -> Icons.Default.Input
                  TankRole.PRODUCT -> Icons.Default.CheckCircle
                  TankRole.BLENDING -> Icons.Default.Tune
                  TankRole.EXCLUDED -> Icons.Default.Block
                },
                contentDescription = null,
                modifier = Modifier.size(14.dp)
              )
            },
            colors = AssistChipDefaults.assistChipColors(
              containerColor = when (effectiveRole) {
                TankRole.FEED -> MaterialTheme.colorScheme.primaryContainer
                TankRole.PRODUCT -> MaterialTheme.colorScheme.secondaryContainer
                TankRole.BLENDING -> MaterialTheme.colorScheme.tertiaryContainer
                TankRole.EXCLUDED -> MaterialTheme.colorScheme.surfaceVariant
              }
            )
          )

          if (roleDiffers) {
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(4.dp)
            ) {
              Text(
                "Role differs from default",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
              )
            }
          }
        }
      }

      if (!isExcluded) {
        HorizontalDivider()

        // Two columns: Previous Day vs Today
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          // PREVIOUS DAY COLUMN
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("PREVIOUS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
              Surface(
                color = if (prevEntry.useVolumeModePrev) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  if (prevEntry.useVolumeModePrev) "V" else "D",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
              }
            }

            // Segmented control Dip | Volume
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
              SegmentedButton(
                selected = !prevEntry.useVolumeModePrev,
                onClick = { onUpdatePrev(prevEntry.copy(useVolumeModePrev = false)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
              ) {
                Text("Dip", style = MaterialTheme.typography.labelSmall)
              }
              SegmentedButton(
                selected = prevEntry.useVolumeModePrev,
                onClick = { onUpdatePrev(prevEntry.copy(useVolumeModePrev = true)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
              ) {
                Text("Vol", style = MaterialTheme.typography.labelSmall)
              }
            }

            if (prevEntry.useVolumeModePrev) {
              OutlinedTextField(
                value = prevEntry.volumePrevInput?.toString() ?: "",
                onValueChange = { newVal -> onUpdatePrev(prevEntry.copy(volumePrevInput = newVal.toDoubleOrNull())) },
                label = { Text("Volume (L, natural)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
            } else {
              OutlinedTextField(
                value = (prevEntry.dipMmPrev ?: prevEntry.dipMm)?.toString() ?: "",
                onValueChange = { newVal ->
                  val d = newVal.toDoubleOrNull()
                  onUpdatePrev(prevEntry.copy(dipMmPrev = d, dipMm = d))
                },
                label = { Text("Dip (mm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
            }

            OutlinedTextField(
              value = (prevEntry.tempPrevC ?: prevEntry.temperatureC)?.toString() ?: "",
              onValueChange = { newVal ->
                val t = newVal.toDoubleOrNull()
                onUpdatePrev(prevEntry.copy(tempPrevC = t, temperatureC = t))
              },
              label = { Text("Temp (°C)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )

            if (effectiveRole == TankRole.FEED) {
              OutlinedTextField(
                value = (prevEntry.densityPrev15 ?: prevEntry.density15)?.toString() ?: "",
                onValueChange = { newVal ->
                  val d15 = newVal.toDoubleOrNull()
                  onUpdatePrev(prevEntry.copy(densityPrev15 = d15, density15 = d15))
                },
                label = { Text("Density @15") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
            } else if (tank.coefficientSource != CoefficientSourceType.NONE) {
              OutlinedTextField(
                value = (prevEntry.densityPrev30 ?: prevEntry.density30)?.toString() ?: "",
                onValueChange = { newVal ->
                  val d30 = newVal.toDoubleOrNull()
                  onUpdatePrev(prevEntry.copy(densityPrev30 = d30, density30 = d30))
                },
                label = { Text("Density @30") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
            }

            Text("Nat: ${String.format(Locale.US, "%,.0f", prevNatVol)} L", style = MaterialTheme.typography.labelSmall)
            Text("@30: ${String.format(Locale.US, "%,.0f", prev30Vol)} L", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
          }

          // TODAY COLUMN
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("TODAY", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
              Surface(
                color = if (todayEntry.useVolumeModeToday) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  if (todayEntry.useVolumeModeToday) "V" else "D",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
              }
            }

            // Segmented control Dip | Volume
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
              SegmentedButton(
                selected = !todayEntry.useVolumeModeToday,
                onClick = { onUpdateToday(todayEntry.copy(useVolumeModeToday = false)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
              ) {
                Text("Dip", style = MaterialTheme.typography.labelSmall)
              }
              SegmentedButton(
                selected = todayEntry.useVolumeModeToday,
                onClick = { onUpdateToday(todayEntry.copy(useVolumeModeToday = true)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
              ) {
                Text("Vol", style = MaterialTheme.typography.labelSmall)
              }
            }

            if (todayEntry.useVolumeModeToday) {
              OutlinedTextField(
                value = todayEntry.volumeTodayInput?.toString() ?: "",
                onValueChange = { newVal -> onUpdateToday(todayEntry.copy(volumeTodayInput = newVal.toDoubleOrNull())) },
                label = { Text("Volume (L, natural)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
            } else {
              OutlinedTextField(
                value = (todayEntry.dipMmToday ?: todayEntry.dipMm)?.toString() ?: "",
                onValueChange = { newVal ->
                  val d = newVal.toDoubleOrNull()
                  onUpdateToday(todayEntry.copy(dipMmToday = d, dipMm = d))
                },
                label = { Text("Dip (mm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
            }

            OutlinedTextField(
              value = (todayEntry.tempTodayC ?: todayEntry.temperatureC)?.toString() ?: "",
              onValueChange = { newVal ->
                val t = newVal.toDoubleOrNull()
                onUpdateToday(todayEntry.copy(tempTodayC = t, temperatureC = t))
              },
              label = { Text("Temp (°C)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )

            if (effectiveRole == TankRole.FEED) {
              OutlinedTextField(
                value = (todayEntry.densityToday15 ?: todayEntry.density15)?.toString() ?: "",
                onValueChange = { newVal ->
                  val d15 = newVal.toDoubleOrNull()
                  onUpdateToday(todayEntry.copy(densityToday15 = d15, density15 = d15))
                },
                label = { Text("Density @15") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
            } else if (tank.coefficientSource != CoefficientSourceType.NONE) {
              OutlinedTextField(
                value = (todayEntry.densityToday30 ?: todayEntry.density30)?.toString() ?: "",
                onValueChange = { newVal ->
                  val d30 = newVal.toDoubleOrNull()
                  onUpdateToday(todayEntry.copy(densityToday30 = d30, density30 = d30))
                },
                label = { Text("Density @30") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
            }

            Text("Nat: ${String.format(Locale.US, "%,.0f", todayNatVol)} L", style = MaterialTheme.typography.labelSmall)
            Text("@30: ${String.format(Locale.US, "%,.0f", today30Vol)} L", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
          }
        }

        if (hasCalChanged) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              "⚠️ Calibration changed since saved (Saved: ${String.format(Locale.US, "%,.0f", todayEntry.savedNaturalVolume)} L, Calc: ${String.format(Locale.US, "%,.0f", currentCalVol)} L)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(6.dp)
            )
          }
        }
      }
    }
  }
}

/**
 * Editor for "Other Operating Data" (Blending Use, Deliveries, Raw Splits, Meters, Loads)
 */
@Composable
fun OtherOperatingDataEditor(
  otherData: ProductionReportOtherData,
  setup: ProductionReportSetup,
  blendRecords: List<BlendRecord>,
  onUpdate: (ProductionReportOtherData) -> Unit
) {
  val context = LocalContext.current

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // 1. Blending Use
    Text("1. Blending Use (Natural Quantities)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    val bu = otherData.blendingUse

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text("Light Naphtha & Reformate for HOBC / MS", style = MaterialTheme.typography.bodySmall)
      TextButton(onClick = {
        var lnHobcTot = 0.0
        var lnMsTot = 0.0
        var refHobcTot = 0.0
        var refMsTot = 0.0
        blendRecords.forEach { br ->
          val isHobc = br.programName.contains("HOBC", ignoreCase = true) || br.tankName.contains("510")
          val isMs = br.programName.contains("MS", ignoreCase = true) || br.tankName.contains("509")
          br.componentTransfers.forEach { ct ->
            val isLn = ct.componentName.contains("Light", ignoreCase = true) || ct.componentName.contains("LN", ignoreCase = true)
            val isRef = ct.componentName.contains("Reform", ignoreCase = true)
            if (isHobc && isLn) lnHobcTot += ct.transferredL
            if (isHobc && isRef) refHobcTot += ct.transferredL
            if (isMs && isLn) lnMsTot += ct.transferredL
            if (isMs && isRef) refMsTot += ct.transferredL
          }
        }
        val updated = bu.copy(
          lnHobc = lnHobcTot,
          lnMs = lnMsTot,
          reformateHobc = refHobcTot,
          reformateMs = refMsTot,
          isAutoFilledFromTransfers = true
        )
        onUpdate(otherData.copy(blendingUse = updated))
        Toast.makeText(context, "Auto-filled from saved blend transfers!", Toast.LENGTH_SHORT).show()
      }) {
        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Auto-fill from Blends")
      }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = if (bu.lnHobc == 0.0) "" else bu.lnHobc.toString(),
        onValueChange = { onUpdate(otherData.copy(blendingUse = bu.copy(lnHobc = it.toDoubleOrNull() ?: 0.0))) },
        label = { Text("LN -> HOBC (L)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f)
      )
      OutlinedTextField(
        value = if (bu.lnMs == 0.0) "" else bu.lnMs.toString(),
        onValueChange = { onUpdate(otherData.copy(blendingUse = bu.copy(lnMs = it.toDoubleOrNull() ?: 0.0))) },
        label = { Text("LN -> MS (L)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f)
      )
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = if (bu.reformateHobc == 0.0) "" else bu.reformateHobc.toString(),
        onValueChange = { onUpdate(otherData.copy(blendingUse = bu.copy(reformateHobc = it.toDoubleOrNull() ?: 0.0))) },
        label = { Text("Ref -> HOBC (L)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f)
      )
      OutlinedTextField(
        value = if (bu.reformateMs == 0.0) "" else bu.reformateMs.toString(),
        onValueChange = { onUpdate(otherData.copy(blendingUse = bu.copy(reformateMs = it.toDoubleOrNull() ?: 0.0))) },
        label = { Text("Ref -> MS (L)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f)
      )
    }

    HorizontalDivider()

    // 2. Deliveries / Sales
    Text("2. Deliveries / Sales & Internal Use", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    listOf("OCTANE", "MS", "SOLVENT", "DIESEL", "JET", "SKO", "LPG").forEach { pCode ->
      val pData = otherData.productDeliveries[pCode] ?: ProductDeliveryData()
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(pCode, fontWeight = FontWeight.Bold, modifier = Modifier.width(75.dp), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
          value = if (pData.deliveredNatural == 0.0) "" else pData.deliveredNatural.toString(),
          onValueChange = {
            val map = otherData.productDeliveries.toMutableMap()
            map[pCode] = pData.copy(deliveredNatural = it.toDoubleOrNull() ?: 0.0)
            onUpdate(otherData.copy(productDeliveries = map))
          },
          label = { Text("Delivered (L)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
          value = if (pData.internalNatural == 0.0) "" else pData.internalNatural.toString(),
          onValueChange = {
            val map = otherData.productDeliveries.toMutableMap()
            map[pCode] = pData.copy(internalNatural = it.toDoubleOrNull() ?: 0.0)
            onUpdate(otherData.copy(productDeliveries = map))
          },
          label = { Text("Internal (L)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.weight(1f)
        )
      }
    }

    HorizontalDivider()

    // 3. Process Waste Meter Readings (kg/h)
    Text("3. Process Waste Meters (kg/h)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    setup.processWasteMeters.forEach { meter ->
      val currentVal = otherData.processWasteKgH[meter.tag] ?: otherData.processWasteKgH[meter.id] ?: 0.0
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(meter.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
          Text("${meter.tag} (${meter.category})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
          value = if (currentVal == 0.0) "" else currentVal.toString(),
          onValueChange = {
            val map = otherData.processWasteKgH.toMutableMap()
            map[meter.tag] = it.toDoubleOrNull() ?: 0.0
            onUpdate(otherData.copy(processWasteKgH = map))
          },
          label = { Text("kg/h") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.width(130.dp)
        )
      }
    }

    HorizontalDivider()

    // 4. Plant Operating Loads Conversion (kg/hr | bbl/day | m3/hr)
    Text("4. Plant Operating Loads (Conversion & Efficiency)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

    val units = listOf(
      Triple("CFU", otherData.operatingLoads.cfu, setup.constants.designLoadCfuBblDay),
      Triple("KNHT", otherData.operatingLoads.knht, setup.constants.designLoadKnhtBblDay),
      Triple("CRU", otherData.operatingLoads.cru, setup.constants.designLoadCruBblDay)
    )

    units.forEach { (unitName, unitData, designLoad) ->
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("$unitName Operating Load", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text("Design: ${String.format(Locale.US, "%,.0f", designLoad)} bbl/day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }

          // Mode Selector: kg/hr | bbl/day | m3/hr
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
              selected = unitData.mode == OperatingLoadMode.KG_HR,
              onClick = {
                val updated = unitData.copy(mode = OperatingLoadMode.KG_HR)
                val newLoads = when (unitName) {
                  "CFU" -> otherData.operatingLoads.copy(cfu = updated)
                  "KNHT" -> otherData.operatingLoads.copy(knht = updated)
                  else -> otherData.operatingLoads.copy(cru = updated)
                }
                onUpdate(otherData.copy(operatingLoads = newLoads))
              },
              shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
            ) {
              Text("kg/hr")
            }
            SegmentedButton(
              selected = unitData.mode == OperatingLoadMode.BBL_DAY,
              onClick = {
                val updated = unitData.copy(mode = OperatingLoadMode.BBL_DAY)
                val newLoads = when (unitName) {
                  "CFU" -> otherData.operatingLoads.copy(cfu = updated)
                  "KNHT" -> otherData.operatingLoads.copy(knht = updated)
                  else -> otherData.operatingLoads.copy(cru = updated)
                }
                onUpdate(otherData.copy(operatingLoads = newLoads))
              },
              shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
            ) {
              Text("bbl/day")
            }
            SegmentedButton(
              selected = unitData.mode == OperatingLoadMode.M3_HR,
              onClick = {
                val updated = unitData.copy(mode = OperatingLoadMode.M3_HR)
                val newLoads = when (unitName) {
                  "CFU" -> otherData.operatingLoads.copy(cfu = updated)
                  "KNHT" -> otherData.operatingLoads.copy(knht = updated)
                  else -> otherData.operatingLoads.copy(cru = updated)
                }
                onUpdate(otherData.copy(operatingLoads = newLoads))
              },
              shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
            ) {
              Text("m3/hr")
            }
          }

          // Inputs according to mode
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = if (unitData.flowValue == 0.0) "" else unitData.flowValue.toString(),
              onValueChange = {
                val f = it.toDoubleOrNull() ?: 0.0
                val updated = unitData.copy(flowValue = f)
                val bbl = when (updated.mode) {
                  OperatingLoadMode.KG_HR -> if (updated.feedDensity > 0) f * 24.0 / updated.feedDensity / 158.987 else 0.0
                  OperatingLoadMode.M3_HR -> f * 24.0 / 0.158987
                  OperatingLoadMode.BBL_DAY -> f
                }
                val newLoads = when (unitName) {
                  "CFU" -> otherData.operatingLoads.copy(cfu = updated, cfuBblDay = bbl)
                  "KNHT" -> otherData.operatingLoads.copy(knht = updated, knhtBblDay = bbl)
                  else -> otherData.operatingLoads.copy(cru = updated, cruBblDay = bbl)
                }
                onUpdate(otherData.copy(operatingLoads = newLoads))
              },
              label = {
                Text(
                  when (unitData.mode) {
                    OperatingLoadMode.KG_HR -> "Flow (kg/hr)"
                    OperatingLoadMode.BBL_DAY -> "Flow (bbl/day)"
                    OperatingLoadMode.M3_HR -> "Flow (m3/hr)"
                  }
                )
              },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f)
            )

            if (unitData.mode == OperatingLoadMode.KG_HR) {
              OutlinedTextField(
                value = unitData.feedDensity.toString(),
                onValueChange = {
                  val d = it.toDoubleOrNull() ?: 0.74
                  val updated = unitData.copy(feedDensity = d)
                  val bbl = if (d > 0) updated.flowValue * 24.0 / d / 158.987 else 0.0
                  val newLoads = when (unitName) {
                    "CFU" -> otherData.operatingLoads.copy(cfu = updated, cfuBblDay = bbl)
                    "KNHT" -> otherData.operatingLoads.copy(knht = updated, knhtBblDay = bbl)
                    else -> otherData.operatingLoads.copy(cru = updated, cruBblDay = bbl)
                  }
                  onUpdate(otherData.copy(operatingLoads = newLoads))
                },
                label = { Text("Feed Density (kg/L)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
              )
            }
          }

          // Live calculation display
          val bblDay = when (unitData.mode) {
            OperatingLoadMode.KG_HR -> if (unitData.feedDensity > 0) unitData.flowValue * 24.0 / unitData.feedDensity / 158.987 else 0.0
            OperatingLoadMode.M3_HR -> unitData.flowValue * 24.0 / 0.158987
            OperatingLoadMode.BBL_DAY -> unitData.flowValue
          }
          val m3Day = when (unitData.mode) {
            OperatingLoadMode.KG_HR -> if (unitData.feedDensity > 0) unitData.flowValue * 24.0 / unitData.feedDensity / 1000.0 else 0.0
            OperatingLoadMode.M3_HR -> unitData.flowValue * 24.0
            OperatingLoadMode.BBL_DAY -> unitData.flowValue * 0.158987
          }
          val eff = if (designLoad > 0) (bblDay / designLoad) * 100.0 else 0.0

          Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                "Result: ${String.format(Locale.US, "%,.1f", bblDay)} bbl/day  •  ${String.format(Locale.US, "%,.1f", m3Day)} m³/day  •  Eff: ${String.format(Locale.US, "%.1f", eff)}%",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
              )
              val formulaStr = when (unitData.mode) {
                OperatingLoadMode.KG_HR -> "Formula: bbl/day = Flow x 24 / density / 158.987  |  m³/day = Flow x 24 / density / 1000"
                OperatingLoadMode.M3_HR -> "Formula: bbl/day = m³/hr x 24 / 0.158987"
                OperatingLoadMode.BBL_DAY -> "Formula: bbl/day (direct)  |  Eff = bbl/day / design x 100"
              }
              Text(formulaStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
    }

    // 5. Feed composition text
    OutlinedTextField(
      value = otherData.feedCompositionText,
      onValueChange = { onUpdate(otherData.copy(feedCompositionText = it)) },
      label = { Text("Feed Composition Text") },
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@Composable
fun InputModeTag(mode: InputMode) {
  Surface(
    shape = RoundedCornerShape(3.dp),
    color = if (mode == InputMode.VOLUME) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
  ) {
    Text(
      text = if (mode == InputMode.VOLUME) "V" else "D",
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = if (mode == InputMode.VOLUME) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
    )
  }
}

@Composable
fun AdjTag() {
  Surface(
    shape = RoundedCornerShape(3.dp),
    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
  ) {
    Text(
      text = "adj",
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onErrorContainer,
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
    )
  }
}

@Composable
fun TempDensityInfoTag(tempC: Double?, density: Double?, coeff: Double?) {
  val str = buildString {
    if (tempC != null) append("${String.format(Locale.US, "%.1f", tempC)}°C")
    if (density != null) {
      if (isNotEmpty()) append(" • ")
      append("d=${String.format(Locale.US, "%.4f", density)}")
    }
    if (coeff != null) {
      if (isNotEmpty()) append(" • ")
      append("c=${String.format(Locale.US, "%.5f", coeff)}")
    }
  }
  if (str.isNotEmpty()) {
    Surface(
      shape = RoundedCornerShape(3.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = Modifier.padding(horizontal = 2.dp)
    ) {
      Text(
        text = str,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
      )
    }
  }
}

/**
 * Tab 1 & 2: Report View Tab (Natural or 30 °C)
 */
@Composable
fun ProductionReportViewTab(
  result: ProductionReportCalculationResult,
  setup: ProductionReportSetup,
  onShareText: () -> Unit,
  onShareWhatsApp: () -> Unit = {},
  onExportExcel: () -> Unit,
  onExportBothExcel: () -> Unit,
  onDownloadPdf: () -> Unit,
  onSharePdf: () -> Unit
) {
  var isTableView by remember { mutableStateOf(false) }

  fun formatVal(num: Double, negBrackets: Boolean = false): String {
    return if (num == 0.0) {
      "-"
    } else if (negBrackets && num < 0) {
      String.format(Locale.US, "(%,.0f)", abs(num))
    } else {
      String.format(Locale.US, "%,.0f", num)
    }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    contentPadding = PaddingValues(vertical = 14.dp)
  ) {
    // 1. Header Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            "DAILY PRODUCTION REPORT (${if (result.basis == CalculationBasis.TEMP_30C) "30 °C BASIS" else "NATURAL BASIS"})",
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
          Text("Reporting Date: ${result.reportDate} 10:00 AM", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
          Text("Duration: ${result.durationText}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        }
      }
    }

    // 2. Dual Production Loss (Option A vs Option B)
    item {
      DualProductionLossCard(
        result = result
      )
    }

    // View toggle: Cards vs Table
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Layout Mode", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = !isTableView,
            onClick = { isTableView = false },
            label = { Text("Cards View") },
            leadingIcon = { Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(16.dp)) }
          )
          FilterChip(
            selected = isTableView,
            onClick = { isTableView = true },
            label = { Text("Table View") },
            leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp)) }
          )
        }
      }
    }

    // 3. Raw Materials Table / Cards
    item {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Raw Materials (Feed Tanks)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
              Text(
                "Total: ${formatVal(result.totalFeed)} L",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }

          if (isTableView) {
            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.padding(bottom = 4.dp)) {
                  Text("Tank", fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
                  Text("Product", fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                  Text("Present (L)", fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                  Text("Previous (L)", fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                  Text("Received (L)", fontWeight = FontWeight.Bold, modifier = Modifier.width(105.dp), textAlign = TextAlign.End)
                  Text("Processed (L)", fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp), textAlign = TextAlign.End)
                  if (result.basis == CalculationBasis.TEMP_30C) {
                    Text("Temp/Dens/Coeff", fontWeight = FontWeight.Bold, modifier = Modifier.width(150.dp), textAlign = TextAlign.Center)
                  }
                }
                HorizontalDivider()
                result.rawTanks.forEach { rt ->
                  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Row(modifier = Modifier.width(90.dp), verticalAlignment = Alignment.CenterVertically) {
                      Text(rt.tankName, fontWeight = FontWeight.SemiBold)
                      Spacer(modifier = Modifier.width(4.dp))
                      InputModeTag(rt.inputMode)
                      if (rt.hasAdjustment) AdjTag()
                    }
                    Text(rt.productCode, modifier = Modifier.width(80.dp))
                    Text(formatVal(rt.todayVolume), modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                    Text(formatVal(rt.previousVolume), modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                    Text(formatVal(rt.received), modifier = Modifier.width(105.dp), textAlign = TextAlign.End)
                    Text(formatVal(rt.processed), fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.primary)
                    if (result.basis == CalculationBasis.TEMP_30C) {
                      Box(modifier = Modifier.width(150.dp), contentAlignment = Alignment.Center) {
                        TempDensityInfoTag(rt.tempC, rt.density15, rt.coefficient)
                      }
                    }
                  }
                }
                HorizontalDivider()
                // Raw subtotal row
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                  Text("Total Raw Materials", fontWeight = FontWeight.Bold, modifier = Modifier.width(170.dp))
                  Text(formatVal(result.totalRawToday), fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                  Text(formatVal(result.totalRawPrevious), fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                  Text(formatVal(result.totalRawReceived), fontWeight = FontWeight.Bold, modifier = Modifier.width(105.dp), textAlign = TextAlign.End)
                  Text(formatVal(result.totalFeed), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(120.dp), textAlign = TextAlign.End)
                }
              }
            }
          } else {
            // Cards View
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              result.rawTanks.forEach { rt ->
                Card(
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(rt.tankName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        ProductBadge(name = rt.productCode)
                        InputModeTag(rt.inputMode)
                        if (rt.hasAdjustment) AdjTag()
                      }
                      if (result.basis == CalculationBasis.TEMP_30C) {
                        TempDensityInfoTag(rt.tempC, rt.density15, rt.coefficient)
                      }
                    }
                    // 3 clearly labelled numbers
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Column {
                        Text("1. Present stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${formatVal(rt.todayVolume)} L", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                      }
                      Column {
                        Text("2. Previous stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${formatVal(rt.previousVolume)} L", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                      }
                      Column(horizontalAlignment = Alignment.End) {
                        Text("3. Processed qty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${formatVal(rt.processed)} L", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                      }
                    }
                    if (rt.received > 0) {
                      Text("Received: ${formatVal(rt.received)} L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                  }
                }
              }

              // Subtotal card
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Total Raw Subtotal", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                  Column(horizontalAlignment = Alignment.End) {
                    Text("Processed: ${formatVal(result.totalFeed)} L", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Stock: ${formatVal(result.totalRawToday)} L (Prev: ${formatVal(result.totalRawPrevious)} L)", style = MaterialTheme.typography.labelSmall)
                  }
                }
              }
            }
          }
        }
      }
    }

    // 4. Products Table / Cards
    item {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Finished Products (Production)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
              Text(
                "Total: ${formatVal(result.totalProduced)} L",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }

          if (isTableView) {
            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.padding(bottom = 4.dp)) {
                  Text("Product", fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
                  Text("Tank", fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
                  Text("Present (L)", fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                  Text("Previous (L)", fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                  Text("Change/Prod (L)", fontWeight = FontWeight.Bold, modifier = Modifier.width(115.dp), textAlign = TextAlign.End)
                  Text("Group Prod (L)", fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp), textAlign = TextAlign.End)
                  if (result.basis == CalculationBasis.TEMP_30C) {
                    Text("Temp/Dens/Coeff", fontWeight = FontWeight.Bold, modifier = Modifier.width(150.dp), textAlign = TextAlign.Center)
                  }
                }
                HorizontalDivider()
                result.productGroups.forEach { pg ->
                  pg.tanks.forEach { pt ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                      Text(pg.productName, modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
                      Row(modifier = Modifier.width(90.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(pt.tankName, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(4.dp))
                        InputModeTag(pt.inputMode)
                        if (pt.hasAdjustment) AdjTag()
                      }
                      Text(formatVal(pt.todayVolume), modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                      Text(formatVal(pt.previousVolume), modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                      Text(
                        formatVal(pt.changeD, true),
                        modifier = Modifier.width(115.dp),
                        textAlign = TextAlign.End,
                        color = if (pt.changeD < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                      )
                      Text("-", modifier = Modifier.width(130.dp), textAlign = TextAlign.End)
                      if (result.basis == CalculationBasis.TEMP_30C) {
                        Box(modifier = Modifier.width(150.dp), contentAlignment = Alignment.Center) {
                          TempDensityInfoTag(pt.tempC, pt.density30, pt.coefficient)
                        }
                      }
                    }
                  }
                  // Group total row
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)) {
                      Text("Total ${pg.productName}", fontWeight = FontWeight.Bold, modifier = Modifier.width(190.dp), color = MaterialTheme.colorScheme.primary)
                      Text(formatVal(pg.totalTodayVolume), fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                      Text(formatVal(pg.totalPreviousVolume), fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                      Text(
                        formatVal(pg.totalChangeD, true),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(115.dp),
                        textAlign = TextAlign.End,
                        color = if (pg.totalChangeD < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        "${formatVal(pg.groupProduction)} (${String.format(Locale.US, "%.2f%%", pg.percentOfFeed)})",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(130.dp),
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.primary
                      )
                      if (result.basis == CalculationBasis.TEMP_30C) {
                        Spacer(modifier = Modifier.width(150.dp))
                      }
                    }
                  }
                }
                HorizontalDivider()
                // Product subtotal row
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                  Text("Total Finished Products", fontWeight = FontWeight.Bold, modifier = Modifier.width(190.dp))
                  Text(formatVal(result.totalProductToday), fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                  Text(formatVal(result.totalProductPrevious), fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                  Text(
                    formatVal(result.productSubTotal, true),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(115.dp),
                    textAlign = TextAlign.End,
                    color = if (result.productSubTotal < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                  )
                  Text(formatVal(result.totalProduced), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(130.dp), textAlign = TextAlign.End)
                }
              }
            }
          } else {
            // Cards View
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              result.productGroups.forEach { pg ->
                Card(
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(pg.productName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        ProductBadge(name = pg.productCode)
                      }
                      Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
                        Text(
                          "Group Prod: ${formatVal(pg.groupProduction)} L (${String.format(Locale.US, "%.2f%%", pg.percentOfFeed)})",
                          fontWeight = FontWeight.Bold,
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onPrimaryContainer,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                      }
                    }

                    // Per-tank cards
                    pg.tanks.forEach { pt ->
                      Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                      ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                          Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                          ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                              Text(pt.tankName, fontWeight = FontWeight.Bold)
                              InputModeTag(pt.inputMode)
                              if (pt.hasAdjustment) AdjTag()
                            }
                            if (result.basis == CalculationBasis.TEMP_30C) {
                              TempDensityInfoTag(pt.tempC, pt.density30, pt.coefficient)
                            }
                          }
                          // 3 clearly labelled numbers
                          Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                          ) {
                            Column {
                              Text("1. Present stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                              Text("${formatVal(pt.todayVolume)} L", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Column {
                              Text("2. Previous stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                              Text("${formatVal(pt.previousVolume)} L", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                              Text("3. Change/Prod", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                              Text(
                                "${formatVal(pt.changeD, true)} L",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (pt.changeD < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                              )
                            }
                          }
                        }
                      }
                    }

                    // Group summary banner
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text("Group Total: ${formatVal(pg.totalTodayVolume)} L (Prev: ${formatVal(pg.totalPreviousVolume)} L)", style = MaterialTheme.typography.labelSmall)
                      if (pg.delivered > 0 || pg.usedForBlending > 0) {
                        Text(
                          "Deliv: ${formatVal(pg.delivered)} | Blend: ${formatVal(pg.usedForBlending)}",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.secondary
                        )
                      }
                    }
                  }
                }
              }

              // Subtotal card
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Total Products Subtotal", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                  Column(horizontalAlignment = Alignment.End) {
                    Text("Produced: ${formatVal(result.totalProduced)} L", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Change: ${formatVal(result.productSubTotal, true)} L", style = MaterialTheme.typography.labelSmall)
                  }
                }
              }
            }
          }
        }
      }
    }

    // 5. Blending Tanks Block (if present)
    if (result.blendingTanks.isNotEmpty()) {
      item {
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text("Blending Tanks", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                Text("MS & Octane blended products (not in Product %)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                Text(
                  "Total: ${formatVal(result.totalBlendingProduction)} L",
                  fontWeight = FontWeight.Bold,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }

            if (isTableView) {
              Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text("Tank", fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
                    Text("Product", fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp))
                    Text("Present (L)", fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                    Text("Previous (L)", fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                    Text("Delivered (L)", fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                    Text("Production (L)", fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp), textAlign = TextAlign.End)
                  }
                  HorizontalDivider()
                  result.blendingTanks.forEach { bt ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                      Row(modifier = Modifier.width(90.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(bt.tankName, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(4.dp))
                        InputModeTag(bt.inputMode)
                        if (bt.hasAdjustment) AdjTag()
                      }
                      Text(bt.productName, modifier = Modifier.width(110.dp))
                      Text(formatVal(bt.todayVolume), modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                      Text(formatVal(bt.previousVolume), modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                      Text(formatVal(bt.delivered), modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                      Text(formatVal(bt.production), fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.primary)
                    }
                  }
                  HorizontalDivider()
                  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("Total Blending Tanks", fontWeight = FontWeight.Bold, modifier = Modifier.width(200.dp))
                    Text(formatVal(result.totalBlendingToday), fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                    Text(formatVal(result.totalBlendingPrevious), fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                    Text(formatVal(result.totalBlendingDelivered), fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                    Text(formatVal(result.totalBlendingProduction), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(120.dp), textAlign = TextAlign.End)
                  }
                }
              }
            } else {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                result.blendingTanks.forEach { bt ->
                  Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                          Text(bt.tankName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                          ProductBadge(name = bt.productCode)
                          InputModeTag(bt.inputMode)
                          if (bt.hasAdjustment) AdjTag()
                        }
                        if (result.basis == CalculationBasis.TEMP_30C) {
                          TempDensityInfoTag(bt.tempC, bt.density30, bt.coefficient)
                        }
                      }
                      // 3 clearly labelled numbers
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                        Column {
                          Text("1. Present stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                          Text("${formatVal(bt.todayVolume)} L", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column {
                          Text("2. Previous stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                          Text("${formatVal(bt.previousVolume)} L", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                          Text("3. Production", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                          Text("${formatVal(bt.production)} L", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        }
                      }
                      if (bt.delivered > 0) {
                        Text("Delivered: ${formatVal(bt.delivered)} L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
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

    // 5. Process Waste & Loss Breakdown
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Process Waste & Loss Reconciliation", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
              Text("13 meters • Density: ${setup.constants.processWasteDensityKgL} kg/L • Denom: ${String.format(Locale.US, "%,.0f", result.totalFeed)} L (${if (result.basis == CalculationBasis.TEMP_30C) "30 °C Feed" else "Natural Feed"})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!result.isWasteEntered) {
              Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  "Not entered",
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }

          if (!result.isWasteEntered) {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  "Process waste meter data is not entered for this date. Enter readings in 'Other operating data' under the Input tab.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          } else {
            // Table Header
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(4.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text("Meter & Tag", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(2.2f))
                Text("kg/h", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                Text("kg/day", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
                Text("Vol (L)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, modifier = Modifier.weight(1.3f))
                Text("% Feed", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
              }
            }

            // 13 meter rows
            result.wasteItems.forEachIndexed { idx, wi ->
              val rowBg = if (idx % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color.Transparent
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(rowBg)
                  .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(2.2f)) {
                  Text(wi.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                  Text("${wi.tag} • ${wi.category.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(String.format(Locale.US, "%,.1f", wi.kgH), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                Text(String.format(Locale.US, "%,.1f", wi.kgDay), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
                Text(String.format(Locale.US, "%,.0f", wi.litres24h), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1.3f))
                Text(String.format(Locale.US, "%.2f%%", wi.percentOfFeed), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
              }
            }

            // TOTAL row
            HorizontalDivider()
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
              shape = RoundedCornerShape(4.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("TOTAL (13 Meters)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(2.2f))
                Text(String.format(Locale.US, "%,.1f", result.totalWasteKgH), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                Text(String.format(Locale.US, "%,.1f", result.totalWasteKgDay), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
                Text(String.format(Locale.US, "%,.0f L", result.totalWasteLitres), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1.3f), color = MaterialTheme.colorScheme.primary)
                Text(String.format(Locale.US, "%.2f%%", result.totalMeasuredLossPct), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
              }
            }
          }

          HorizontalDivider()

          // Flaring & Loss Breakdown Card
          Text("Flaring & Measured Loss Summary", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Total Measured Loss % (Total Waste / Feed)", style = MaterialTheme.typography.bodySmall)
              Text(if (!result.isWasteEntered) "Not entered" else String.format(Locale.US, "%.2f%%", result.totalMeasuredLossPct), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Unmeasured Process Loss % (Loss % - Measured %)", style = MaterialTheme.typography.bodySmall)
              Text(if (!result.isWasteEntered) "Not entered" else String.format(Locale.US, "%.2f%%", result.unmeasuredLossPct), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Fuel for Heaters %", style = MaterialTheme.typography.bodySmall)
              Text(if (!result.isWasteEntered) "Not entered" else String.format(Locale.US, "%.2f%%", result.fuelForHeatersPct), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("H2 Make-up for KNHT %", style = MaterialTheme.typography.bodySmall)
              Text(if (!result.isWasteEntered) "Not entered" else String.format(Locale.US, "%.2f%%", result.h2MakeUpKnhtPct), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Actual Flaring % (Loss % - Fuel % - H2 %)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
              Text(
                if (!result.isWasteEntered) "Not entered" else String.format(Locale.US, "%.2f%%", result.actualFlaringPct),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
                color = if (!result.isWasteEntered) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
              )
            }
          }
        }
      }
    }

    // 6. Dry-out Forecast Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Raw Material Dry-Out Forecast", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
          Text("Current Raw Stock: ${String.format(Locale.US, "%,.0f", result.dryOutStockL)} L (Reserve: 840,000 L)")
          Text("Consumable Inventory: ${String.format(Locale.US, "%,.0f", result.dryOutConsumableL)} L", fontWeight = FontWeight.SemiBold)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Days Remaining: ${String.format(Locale.US, "%.2f", result.dryOutDays)} days", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Expected Date: ${result.dryOutDateStr}", fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // 7. Actions (Share / Export / PDF)
    item {
      Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = onDownloadPdf, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Download PDF")
          }
          OutlinedButton(onClick = onSharePdf, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Share PDF")
          }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onShareText, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Notes, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Share Text")
          }
          Button(onClick = onExportExcel, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (result.basis == CalculationBasis.TEMP_30C) "Export 30 °C (.xlsx)" else "Export Natural (.xlsx)")
          }
        }
        OutlinedButton(onClick = onExportBothExcel, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Export both (.xlsx)")
        }
        Button(
          onClick = onShareWhatsApp,
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

/**
 * Tab 3: ERP (30 °C) Tab
 */
@Composable
fun ProductionReportErpTab(
  erpRows: List<ErpRowData>,
  dateStr: String,
  prevDateStr: String,
  calcResult: ProductionReportCalculationResult,
  settings: AppSettings,
  onExportCsv: () -> Unit,
  onShareWhatsApp: () -> Unit = {},
  onDownloadPdf: () -> Unit,
  onSharePdf: () -> Unit
) {
  val themeBlue = Color(0xFF0F4C81)
  var isTableView by remember { mutableStateOf(false) }

  val tol = settings.erpToleranceL
  val unbalancedRows = erpRows.filter { abs(it.difference) > tol }
  val allBalanced = unbalancedRows.isEmpty()

  val finishedCodes = listOf("LN", "REFORMATE", "JET", "SKO", "DIESEL", "LPG", "MS", "OCTANE", "DSN")
  val finishedRows = erpRows.filter { it.itemCode in finishedCodes }
  val rawCodes = listOf("COND_EXP", "COND_LOC", "NAPH_EXP", "NAPH_LOC")
  val rawRows = erpRows.filter { it.itemCode in rawCodes }

  fun formatVal(v: Double): String = if (v == 0.0) "-" else String.format(Locale.US, "%,.1f", v)

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    contentPadding = PaddingValues(vertical = 12.dp)
  ) {
    // 1. Professional Header Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = themeBlue),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                settings.plantName.ifBlank { "Condensate Fractionation Plant" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Text(
                "Daily Production & Stock Statement (ERP)",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.9f)
              )
            }

            // Checks Badge
            Surface(
              color = if (allBalanced) Color(0xFF16A34A) else Color(0xFFD97706),
              shape = RoundedCornerShape(16.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  if (allBalanced) Icons.Default.CheckCircle else Icons.Default.Warning,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(14.dp)
                )
                Text(
                  if (allBalanced) "All balanced (±${tol.toInt()} L)" else "${unbalancedRows.size} outside tolerance",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
              }
            }
          }

          HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Period: $prevDateStr 10:00 to $dateStr 10:00", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            Text("Basis: Volumes @ 30 °C, litres", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
          }
        }
      }
    }

    // 2. Dual Production Loss (Option A vs Option B)
    item {
      DualProductionLossCard(
        result = calcResult
      )
    }

    // 3. View Switcher on Phone
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Finished Products (9 items)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = themeBlue)
        SingleChoiceSegmentedButtonRow {
          SegmentedButton(
            selected = !isTableView,
            onClick = { isTableView = false },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
          ) {
            Text("Cards", style = MaterialTheme.typography.labelSmall)
          }
          SegmentedButton(
            selected = isTableView,
            onClick = { isTableView = true },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
          ) {
            Text("Table", style = MaterialTheme.typography.labelSmall)
          }
        }
      }
    }

    // 4. Finished Products Section (Cards vs Table)
    if (!isTableView) {
      items(finishedRows) { r ->
        val isBalanced = abs(r.difference) <= tol
        var expanded by remember { mutableStateOf(false) }

        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                  color = themeBlue.copy(alpha = 0.15f),
                  shape = RoundedCornerShape(4.dp)
                ) {
                  Text(r.itemCode, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = themeBlue)
                }
                Text(r.itemName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
              }

              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                  Text("Closing: ${formatVal(r.closingStock)} L", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                  Text(
                    if (isBalanced) "Balanced" else "Diff: ${String.format(Locale.US, "%+,.0f", r.difference)} L",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isBalanced) Color(0xFF16A34A) else Color(0xFFD97706)
                  )
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
              }
            }

            if (expanded) {
              HorizontalDivider()
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Opening Stock:", style = MaterialTheme.typography.bodySmall)
                Text("${formatVal(r.openingStock)} L", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Production / Rec:", style = MaterialTheme.typography.bodySmall)
                Text("${formatVal(r.productionOrReceived)} L", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
              }
              if (r.usedForBlending != 0.0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Used for Blending:", style = MaterialTheme.typography.bodySmall)
                  Text("${formatVal(r.usedForBlending)} L", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Delivered / Sold:", style = MaterialTheme.typography.bodySmall)
                Text("${formatVal(r.delivered)} L", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Internal Use / Evap:", style = MaterialTheme.typography.bodySmall)
                Text("${formatVal(r.internalUseOrEvap)} L", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Physical Stock:", style = MaterialTheme.typography.bodySmall)
                Text("${formatVal(r.physicalStock)} L", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      }
    } else {
      item {
        Card(modifier = Modifier.fillMaxWidth()) {
          Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              // Sticky-style Header
              Row(
                modifier = Modifier
                  .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                  .padding(vertical = 6.dp, horizontal = 4.dp)
              ) {
                Text("Product", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(110.dp), style = MaterialTheme.typography.labelSmall)
                Text("Opening", fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall)
                Text("Prod/Rec", fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall)
                Text("Blending", fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.width(85.dp), style = MaterialTheme.typography.labelSmall)
                Text("Delivered", fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall)
                Text("Internal", fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.width(85.dp), style = MaterialTheme.typography.labelSmall)
                Text("Closing", fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.width(95.dp), style = MaterialTheme.typography.labelSmall)
                Text("Check", fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.width(95.dp), style = MaterialTheme.typography.labelSmall)
              }

              finishedRows.forEachIndexed { idx, r ->
                val isAlt = idx % 2 == 1
                val isBalanced = abs(r.difference) <= tol
                Row(
                  modifier = Modifier
                    .background(if (isAlt) Color(0xFFF8FAFC) else Color.White)
                    .padding(vertical = 5.dp, horizontal = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(r.itemName, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
                  Text(formatVal(r.openingStock), textAlign = TextAlign.End, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
                  Text(formatVal(r.productionOrReceived), textAlign = TextAlign.End, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
                  Text(formatVal(r.usedForBlending), textAlign = TextAlign.End, modifier = Modifier.width(85.dp), style = MaterialTheme.typography.bodySmall)
                  Text(formatVal(r.delivered), textAlign = TextAlign.End, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
                  Text(formatVal(r.internalUseOrEvap), textAlign = TextAlign.End, modifier = Modifier.width(85.dp), style = MaterialTheme.typography.bodySmall)
                  Text(formatVal(r.closingStock), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, modifier = Modifier.width(95.dp), style = MaterialTheme.typography.bodySmall)
                  Surface(
                    color = if (isBalanced) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.width(95.dp)
                  ) {
                    Text(
                      if (isBalanced) "Balanced" else String.format(Locale.US, "Diff: %+,.0f", r.difference),
                      textAlign = TextAlign.Center,
                      fontWeight = FontWeight.Bold,
                      color = if (isBalanced) Color(0xFF16A34A) else Color(0xFFD97706),
                      style = MaterialTheme.typography.labelSmall,
                      modifier = Modifier.padding(vertical = 2.dp)
                    )
                  }
                }
              }

              // Finished products Total Row
              HorizontalDivider()
              Row(
                modifier = Modifier
                  .background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                  .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("Total Finished", fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), style = MaterialTheme.typography.labelSmall)
                Text(formatVal(finishedRows.sumOf { it.openingStock }), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall)
                Text(formatVal(finishedRows.sumOf { it.productionOrReceived }), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall)
                Text(formatVal(finishedRows.sumOf { it.usedForBlending }), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, modifier = Modifier.width(85.dp), style = MaterialTheme.typography.labelSmall)
                Text(formatVal(finishedRows.sumOf { it.delivered }), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall)
                Text(formatVal(finishedRows.sumOf { it.internalUseOrEvap }), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, modifier = Modifier.width(85.dp), style = MaterialTheme.typography.labelSmall)
                Text(formatVal(finishedRows.sumOf { it.closingStock }), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, modifier = Modifier.width(95.dp), style = MaterialTheme.typography.labelSmall)
                Text("-", textAlign = TextAlign.Center, modifier = Modifier.width(95.dp), style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }
      }
    }

    // 5. Raw Materials Section
    item {
      Text("Raw Materials (Condensate & Naphtha)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = themeBlue)
    }

    item {
      Card(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
          Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              modifier = Modifier
                .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                .padding(vertical = 6.dp, horizontal = 4.dp)
            ) {
              Text("Raw Material", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(150.dp), style = MaterialTheme.typography.labelSmall)
              Text("Opening", fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.width(95.dp), style = MaterialTheme.typography.labelSmall)
              Text("Received", fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.width(95.dp), style = MaterialTheme.typography.labelSmall)
              Text("Processed", fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.width(95.dp), style = MaterialTheme.typography.labelSmall)
              Text("Closing", fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.width(105.dp), style = MaterialTheme.typography.labelSmall)
              Text("Check", fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall)
            }

            rawRows.forEachIndexed { idx, r ->
              val isAlt = idx % 2 == 1
              Row(
                modifier = Modifier
                  .background(if (isAlt) Color(0xFFF8FAFC) else Color.White)
                  .padding(vertical = 5.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(r.itemName, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(150.dp), style = MaterialTheme.typography.bodySmall)
                Text(formatVal(r.openingStock), textAlign = TextAlign.End, modifier = Modifier.width(95.dp), style = MaterialTheme.typography.bodySmall)
                Text(formatVal(r.productionOrReceived), textAlign = TextAlign.End, modifier = Modifier.width(95.dp), style = MaterialTheme.typography.bodySmall)
                Text(formatVal(r.internalUseOrEvap), textAlign = TextAlign.End, modifier = Modifier.width(95.dp), style = MaterialTheme.typography.bodySmall)
                Text(formatVal(r.closingStock), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, modifier = Modifier.width(105.dp), style = MaterialTheme.typography.bodySmall)
                Surface(
                  color = Color(0xFFDCFCE7),
                  shape = RoundedCornerShape(4.dp),
                  modifier = Modifier.width(90.dp)
                ) {
                  Text("Balanced", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 2.dp))
                }
              }
            }

            // Raw Materials Total Row
            HorizontalDivider()
            Row(
              modifier = Modifier
                .background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                .padding(vertical = 6.dp, horizontal = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Total Raw Materials", fontWeight = FontWeight.Bold, modifier = Modifier.width(150.dp), style = MaterialTheme.typography.labelSmall)
              Text(formatVal(rawRows.sumOf { it.openingStock }), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, modifier = Modifier.width(95.dp), style = MaterialTheme.typography.labelSmall)
              Text(formatVal(rawRows.sumOf { it.productionOrReceived }), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, modifier = Modifier.width(95.dp), style = MaterialTheme.typography.labelSmall)
              Text(formatVal(rawRows.sumOf { it.internalUseOrEvap }), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, modifier = Modifier.width(95.dp), style = MaterialTheme.typography.labelSmall)
              Text(formatVal(rawRows.sumOf { it.closingStock }), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, modifier = Modifier.width(105.dp), style = MaterialTheme.typography.labelSmall)
              Text("-", textAlign = TextAlign.Center, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
    }

    // 6. Plant Operating Efficiency
    item {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Plant Operating Efficiency & Unit Loads", fontWeight = FontWeight.Bold, color = themeBlue)
          calcResult.plantEfficiency.forEach { (unit, eff) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(unit, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Text("${String.format(Locale.US, "%.1f", eff)}%", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = themeBlue)
              }
              LinearProgressIndicator(
                progress = { (eff / 100.0).toFloat().coerceIn(0f, 1f) },
                color = themeBlue,
                trackColor = themeBlue.copy(alpha = 0.2f),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
                  .clip(RoundedCornerShape(3.dp))
              )
            }
          }
        }
      }
    }

    // 7. Fuel Gas & Loss Breakdown
    item {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Fuel Gas & Loss Breakdown", fontWeight = FontWeight.Bold, color = themeBlue)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Actual Flaring:", style = MaterialTheme.typography.bodySmall)
            Text(String.format(Locale.US, "%.2f%%", calcResult.actualFlaringPct), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Fuel for Heaters:", style = MaterialTheme.typography.bodySmall)
            Text(String.format(Locale.US, "%.2f%%", calcResult.fuelForHeatersPct), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("H2 Make-up for KNHT:", style = MaterialTheme.typography.bodySmall)
            Text(String.format(Locale.US, "%.2f%%", calcResult.h2MakeUpKnhtPct), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Unmeasured Process Loss:", style = MaterialTheme.typography.bodySmall)
            Text(String.format(Locale.US, "%.2f%%", calcResult.unmeasuredLossPct), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }

    // 8. Actions (Download PDF, Share PDF, Export CSV)
    item {
      Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = onDownloadPdf, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Download PDF")
          }
          OutlinedButton(onClick = onSharePdf, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Share PDF")
          }
        }
        OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Export ERP CSV")
        }
        Button(
          onClick = onShareWhatsApp,
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

/**
 * Tab 4: Product % Tab
 */
@Composable
fun ProductionReportProductYieldTab(
  result: ProductionReportCalculationResult,
  setup: ProductionReportSetup,
  onToggleJetSko: (Boolean) -> Unit
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    contentPadding = PaddingValues(vertical = 12.dp)
  ) {
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
            Text("Include Jet A-1 & SKO in Yield", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text("Controls whether Jet and JBO/MTT are counted in total product %", style = MaterialTheme.typography.bodySmall)
          }
          Switch(
            checked = setup.constants.includeJetAndSkoInYield,
            onCheckedChange = onToggleJetSko
          )
        }
      }
    }

    item {
      Text("Product Yields (% of Total Feed)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }

    items(result.productSummaries) { ps ->
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(ps.productName, fontWeight = FontWeight.Bold)
            Text(
              "${String.format(Locale.US, "%,.0f", ps.productionH)} L (${String.format(Locale.US, "%.2f%%", ps.percentOfFeed)})",
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }
          LinearProgressIndicator(
            progress = { (ps.percentOfFeed / 100.0).toFloat().coerceIn(0f, 1f) },
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
              .fillMaxWidth()
              .height(8.dp)
              .clip(RoundedCornerShape(4.dp))
          )
        }
      }
    }

    item {
      HorizontalDivider()
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Total Produced Product", fontWeight = FontWeight.Bold)
        val prodYieldPct = if (result.totalFeed > 0) result.totalProduced / result.totalFeed * 100.0 else 0.0
        Text("${String.format(Locale.US, "%.2f%%", prodYieldPct)} (${String.format(Locale.US, "%,.0f", result.totalProduced)} L)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Option A Loss (Total Product)", fontWeight = FontWeight.Bold)
        Text("${String.format(Locale.US, "%.2f%%", result.lossPercentOptionA)} (${String.format(Locale.US, "%,.0f", result.lossOptionA)} L)", fontWeight = FontWeight.Bold, color = if (result.lossOptionA < 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Option B Loss (All-Tank TOV)", fontWeight = FontWeight.Bold)
        Text("${String.format(Locale.US, "%.2f%%", result.lossPercentOptionB)} (${String.format(Locale.US, "%,.0f", result.lossOptionB)} L)", fontWeight = FontWeight.Bold, color = if (result.lossOptionB < 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
      }
      if (kotlin.math.abs(result.diffLossMethods) >= 0.01) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("  • Difference (Option A - Option B)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
          Text("${String.format(Locale.US, "%+,.0f", result.diffLossMethods)} L", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        }
      }
    }

    // Process Waste & Flaring optional group
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Process Waste & Flaring Breakdown", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
          if (!result.isWasteEntered) {
            Surface(
              color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
              shape = RoundedCornerShape(4.dp)
            ) {
              Text(
                "Process waste data: Not entered",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Actual Flaring %", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text(String.format(Locale.US, "%.2f%%", result.actualFlaringPct), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Fuel for Heaters %", style = MaterialTheme.typography.bodySmall)
                Text(String.format(Locale.US, "%.2f%%", result.fuelForHeatersPct), fontWeight = FontWeight.SemiBold)
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("H2 Make-up for KNHT %", style = MaterialTheme.typography.bodySmall)
                Text(String.format(Locale.US, "%.2f%%", result.h2MakeUpKnhtPct), fontWeight = FontWeight.SemiBold)
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Measured Loss %", style = MaterialTheme.typography.bodySmall)
                Text(String.format(Locale.US, "%.2f%%", result.totalMeasuredLossPct), fontWeight = FontWeight.SemiBold)
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Unmeasured Process Loss %", style = MaterialTheme.typography.bodySmall)
                Text(String.format(Locale.US, "%.2f%%", result.unmeasuredLossPct), fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ProductionReportSetupTab(
  setup: ProductionReportSetup,
  tanks: List<Tank>,
  onSaveSetup: (ProductionReportSetup) -> Unit,
  onLogEdit: (String, String, String) -> Unit
) {
  val context = LocalContext.current
  var setupSection by remember { mutableIntStateOf(0) }
  val sections = listOf("Tanks Master (Global)", "Coefficient Tables", "Constants", "Waste Meters")

  // Tables state
  var selectedTableTab by remember { mutableIntStateOf(0) } // 0 = VCC, 1 = VCF30
  var vccList by remember(setup.vccTable) { mutableStateOf(setup.vccTable) }
  var vcf30List by remember(setup.vcf30Table) { mutableStateOf(setup.vcf30Table) }

  // Search & Test state
  var tableSearchQuery by remember { mutableStateOf("") }
  var testDensityInput by remember { mutableStateOf("") }

  // Dialog states for tables
  var editingRowIndex by remember { mutableStateOf<Int?>(null) }
  var editBoundInput by remember { mutableStateOf("") }
  var editCoeffInput by remember { mutableStateOf("") }
  var showAddRowDialog by remember { mutableStateOf(false) }
  var addBoundInput by remember { mutableStateOf("") }
  var addCoeffInput by remember { mutableStateOf("") }
  var showBulkPasteDialog by remember { mutableStateOf(false) }
  var bulkPasteInput by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    ScrollableTabRow(
      selectedTabIndex = setupSection,
      edgePadding = 4.dp
    ) {
      sections.forEachIndexed { idx, s ->
        Tab(
          selected = setupSection == idx,
          onClick = { setupSection = idx },
          text = { Text(s) }
        )
      }
    }

    when (setupSection) {
      0 -> { // 1. Tanks Master (Global)
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("Single Source of Truth: Tank Master", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
              Text("Tank definitions, roles, product links, input modes, and report orders are managed globally in the Tank Master (Tanks & Calibration tab). All modules synchronize from this single source.", style = MaterialTheme.typography.bodySmall)
            }
          }

          Text("Configured Tanks (${tanks.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

          LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tanks.sortedBy { it.reportOrder }) { t ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      Text(t.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                      Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                      ) {
                        Text(t.type.name, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                      }
                      if (t.linkedProductId.isNotBlank()) {
                        Text("Linked: ${t.linkedProductId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                    }

                    Surface(
                      color = if (t.includeInProductionReport) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant,
                      shape = RoundedCornerShape(4.dp)
                    ) {
                      Text(
                        if (t.includeInProductionReport) "In Report (#${t.reportOrder})" else "Excluded",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (t.includeInProductionReport) Color(0xFF166534) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }

                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Input: ${t.defaultInputMode.name} | Coeff: ${t.coefficientSource.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    val calOk = t.dipTable.isNotEmpty()
                    Text(
                      "${t.shape.name} • Cap: ${String.format(Locale.US, "%,.0f L", t.maxVolumeLiters)} (${if (calOk) "Calibrated" else "Direct Vol"})",
                      style = MaterialTheme.typography.labelSmall,
                      color = if (calOk) Color(0xFF16A34A) else MaterialTheme.colorScheme.tertiary,
                      fontWeight = FontWeight.SemiBold
                    )
                  }
                }
              }
            }
          }
        }
      }

      1 -> { // 2. Coefficient Tables (VCC & VCF30)
        val isVcc = selectedTableTab == 0
        val currentTable = if (isVcc) vccList else vcf30List
        val tableName = if (isVcc) "VCC (Product @30)" else "VCF30 (Raw Material @15)"

        val valResult = remember(currentTable) { ProductionReportEngine.validateCoefficientTable(currentTable) }

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          // Sub-tabs: VCC vs VCF30
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
              selected = selectedTableTab == 0,
              onClick = { selectedTableTab = 0 },
              shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
              Text("VCC (Product, @30)")
            }
            SegmentedButton(
              selected = selectedTableTab == 1,
              onClick = { selectedTableTab = 1 },
              shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
              Text("VCF30 (Raw, @15)")
            }
          }

          // Validation Alerts
          if (valResult.errors.isNotEmpty()) {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("⚠️ Validation Errors (Saving Blocked):", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelMedium)
                valResult.errors.take(3).forEach { err ->
                  Text("• $err", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
              }
            }
          } else if (valResult.warnings.isNotEmpty()) {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("ℹ️ Recommendations:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                valResult.warnings.take(2).forEach { w ->
                  Text("• $w", style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }

          // Live Test Box
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("Live Density Lookup Test", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                OutlinedTextField(
                  value = testDensityInput,
                  onValueChange = { testDensityInput = it },
                  label = { Text(if (isVcc) "Density @30" else "Density @15") },
                  placeholder = { Text("e.g. 0.650") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  singleLine = true,
                  modifier = Modifier.weight(1f)
                )

                val testDensity = testDensityInput.toDoubleOrNull()
                val (matchedCoeff, matchedRow) = ProductionReportEngine.findCoefficient(testDensity, currentTable)

                Column(modifier = Modifier.weight(1.4f)) {
                  if (testDensity != null) {
                    val firstBound = currentTable.firstOrNull()?.lowerBound ?: 0.0
                    val isBelow = testDensity < firstBound
                    Text(
                      "Matched Coeff: ${String.format(Locale.US, "%.5f", matchedCoeff)}",
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.primary,
                      style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                      "Lower Bound: ${matchedRow?.lowerBound ?: "-"}",
                      style = MaterialTheme.typography.labelSmall
                    )
                    if (isBelow) {
                      Text("⚠️ Below lowest bound ($firstBound). Using first row.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                  } else {
                    Text("Enter density to test", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              }
            }
          }

          // Search & Action Toolbar
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedTextField(
              value = tableSearchQuery,
              onValueChange = { tableSearchQuery = it },
              placeholder = { Text("Search density/coeff...") },
              singleLine = true,
              leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
              modifier = Modifier.weight(1f)
            )

            IconButton(onClick = {
              addBoundInput = ""
              addCoeffInput = ""
              showAddRowDialog = true
            }) {
              Icon(Icons.Default.Add, contentDescription = "Add Row", tint = MaterialTheme.colorScheme.primary)
            }

            IconButton(onClick = {
              bulkPasteInput = ""
              showBulkPasteDialog = true
            }) {
              Icon(Icons.Default.ContentPaste, contentDescription = "Bulk Paste", tint = MaterialTheme.colorScheme.secondary)
            }

            IconButton(onClick = {
              val csvContent = buildString {
                appendLine("LowerBound,Coefficient")
                currentTable.forEach { r -> appendLine("${r.lowerBound},${r.coefficient}") }
              }
              val filename = if (isVcc) "VCC_table.csv" else "VCF30_table.csv"
              val intent = ProductionReportExportUtils.exportCsvFile(context, filename, csvContent)
              if (intent != null) context.startActivity(Intent.createChooser(intent, "Export Table CSV"))
            }) {
              Icon(Icons.Default.Share, contentDescription = "Export CSV")
            }

            IconButton(onClick = {
              if (isVcc) {
                vccList = ProductionReportSetup.defaultVccTable()
              } else {
                vcf30List = ProductionReportSetup.defaultVcf30Table()
              }
              Toast.makeText(context, "Reset to factory defaults", Toast.LENGTH_SHORT).show()
            }) {
              Icon(Icons.Default.RestartAlt, contentDescription = "Reset Defaults")
            }
          }

          // Table Rows List
          val filteredRowsWithIndex = currentTable.mapIndexed { index, row -> Pair(index, row) }
            .filter { (_, row) ->
              tableSearchQuery.isBlank() ||
                row.lowerBound.toString().contains(tableSearchQuery) ||
                row.coefficient.toString().contains(tableSearchQuery)
            }

          LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            items(filteredRowsWithIndex) { (origIdx, row) ->
              val isOffending = valResult.errorIndices.contains(origIdx)
              val isWarning = valResult.warningIndices.contains(origIdx)

              Card(
                colors = CardDefaults.cardColors(
                  containerColor = if (isOffending) MaterialTheme.colorScheme.errorContainer else if (isWarning) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("#${origIdx + 1}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Bound: ${row.lowerBound}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text("Coeff: ${String.format(Locale.US, "%.5f", row.coefficient)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                  }

                  Row {
                    IconButton(
                      onClick = {
                        editingRowIndex = origIdx
                        editBoundInput = row.lowerBound.toString()
                        editCoeffInput = row.coefficient.toString()
                      },
                      modifier = Modifier.size(32.dp)
                    ) {
                      Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                      onClick = {
                        if (isVcc) {
                          val m = vccList.toMutableList()
                          m.removeAt(origIdx)
                          vccList = m
                        } else {
                          val m = vcf30List.toMutableList()
                          m.removeAt(origIdx)
                          vcf30List = m
                        }
                      },
                      modifier = Modifier.size(32.dp)
                    ) {
                      Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                  }
                }
              }
            }
          }

          // Save Tables Button
          Button(
            onClick = {
              if (valResult.errors.isNotEmpty()) {
                Toast.makeText(context, "Cannot save: please fix validation errors first", Toast.LENGTH_LONG).show()
                return@Button
              }
              val newTableVersionId = UUID.randomUUID().toString()
              val updatedSetup = setup.copy(
                tableVersionId = newTableVersionId,
                vccTable = vccList.sortedBy { it.lowerBound },
                vcf30Table = vcf30List.sortedBy { it.lowerBound }
              )
              onLogEdit("EDIT_COEFF_TABLES", "VCC/VCF30 Tables", "Updated coefficient tables (New Version: ${newTableVersionId.take(8)})")
              onSaveSetup(updatedSetup)
              Toast.makeText(context, "Coefficient tables saved! (Version: ${newTableVersionId.take(8)})", Toast.LENGTH_SHORT).show()
            },
            enabled = valResult.errors.isEmpty(),
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Save Tables (Generate New Version)")
          }
        }
      }

      2 -> { // 3. Constants
        var basisTemp by remember { mutableStateOf(setup.constants.basisTempC.toString()) }
        var wasteDensity by remember { mutableStateOf(setup.constants.processWasteDensityKgL.toString()) }
        var reserveStock by remember { mutableStateOf(setup.constants.minimumReserveStockL.toString()) }
        var reportTime by remember { mutableStateOf(setup.constants.reportTime) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = basisTemp,
            onValueChange = { basisTemp = it },
            label = { Text("Conversion Basis Temp (°C)") },
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = wasteDensity,
            onValueChange = { wasteDensity = it },
            label = { Text("Process Waste Density (kg/L)") },
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = reserveStock,
            onValueChange = { reserveStock = it },
            label = { Text("Minimum Reserve Stock (L)") },
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = reportTime,
            onValueChange = { reportTime = it },
            label = { Text("Report Time (e.g. 10:00)") },
            modifier = Modifier.fillMaxWidth()
          )
          Button(onClick = {
            val updated = setup.copy(
              constants = setup.constants.copy(
                basisTempC = basisTemp.toDoubleOrNull() ?: 30.0,
                processWasteDensityKgL = wasteDensity.toDoubleOrNull() ?: 0.74,
                minimumReserveStockL = reserveStock.toDoubleOrNull() ?: 840000.0,
                reportTime = reportTime
              )
            )
            onSaveSetup(updated)
          }) {
            Text("Save Constants")
          }
        }
      }

      3 -> { // 4. Waste Meters
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          items(setup.processWasteMeters) { m ->
            Card(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text(m.name, fontWeight = FontWeight.Bold)
                  Text("Tag: ${m.tag} | Category: ${m.category}", style = MaterialTheme.typography.bodySmall)
                }
              }
            }
          }
        }
      }
    }
  }



  // Dialog: Edit Table Row
  if (editingRowIndex != null) {
    val idx = editingRowIndex!!
    AlertDialog(
      onDismissRequest = { editingRowIndex = null },
      title = { Text("Edit Row #${idx + 1}", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = editBoundInput,
            onValueChange = { editBoundInput = it },
            label = { Text("Lower Density Bound") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = editCoeffInput,
            onValueChange = { editCoeffInput = it },
            label = { Text("Coefficient per °C") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(onClick = {
          val b = editBoundInput.toDoubleOrNull()
          val c = editCoeffInput.toDoubleOrNull()
          if (b != null && c != null) {
            val isVcc = selectedTableTab == 0
            if (isVcc) {
              val m = vccList.toMutableList()
              m[idx] = DensityCoefficientRow(b, c)
              vccList = m
            } else {
              val m = vcf30List.toMutableList()
              m[idx] = DensityCoefficientRow(b, c)
              vcf30List = m
            }
            editingRowIndex = null
          } else {
            Toast.makeText(context, "Both values must be numeric", Toast.LENGTH_SHORT).show()
          }
        }) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { editingRowIndex = null }) { Text("Cancel") }
      }
    )
  }

  // Dialog: Add Table Row
  if (showAddRowDialog) {
    AlertDialog(
      onDismissRequest = { showAddRowDialog = false },
      title = { Text("Add Row to ${if (selectedTableTab == 0) "VCC" else "VCF30"}", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = addBoundInput,
            onValueChange = { addBoundInput = it },
            label = { Text("Lower Density Bound") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = addCoeffInput,
            onValueChange = { addCoeffInput = it },
            label = { Text("Coefficient per °C") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(onClick = {
          val b = addBoundInput.toDoubleOrNull()
          val c = addCoeffInput.toDoubleOrNull()
          if (b != null && c != null) {
            val isVcc = selectedTableTab == 0
            if (isVcc) {
              vccList = (vccList + DensityCoefficientRow(b, c)).sortedBy { it.lowerBound }
            } else {
              vcf30List = (vcf30List + DensityCoefficientRow(b, c)).sortedBy { it.lowerBound }
            }
            showAddRowDialog = false
          } else {
            Toast.makeText(context, "Both values must be numeric", Toast.LENGTH_SHORT).show()
          }
        }) {
          Text("Add")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddRowDialog = false }) { Text("Cancel") }
      }
    )
  }

  // Dialog: Bulk Paste
  if (showBulkPasteDialog) {
    AlertDialog(
      onDismissRequest = { showBulkPasteDialog = false },
      title = { Text("Bulk Paste from Excel / CSV", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Paste two columns (Lower Bound, Coefficient) separated by comma, tab, or space:", style = MaterialTheme.typography.bodySmall)
          OutlinedTextField(
            value = bulkPasteInput,
            onValueChange = { bulkPasteInput = it },
            label = { Text("Data (Row per line)") },
            placeholder = { Text("0.627, 0.00152\n0.640, 0.00150") },
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp)
          )
        }
      },
      confirmButton = {
        Button(onClick = {
          val lines = bulkPasteInput.trim().lines().filter { it.isNotBlank() }
          val parsedRows = mutableListOf<DensityCoefficientRow>()
          for (line in lines) {
            val parts = line.split(Regex("[,\\t\\s]+")).filter { it.isNotBlank() }
            if (parts.size >= 2) {
              val b = parts[0].toDoubleOrNull()
              val c = parts[1].toDoubleOrNull()
              if (b != null && c != null) {
                parsedRows.add(DensityCoefficientRow(b, c))
              }
            }
          }
          if (parsedRows.isNotEmpty()) {
            val sorted = parsedRows.sortedBy { it.lowerBound }
            if (selectedTableTab == 0) {
              vccList = sorted
            } else {
              vcf30List = sorted
            }
            showBulkPasteDialog = false
            Toast.makeText(context, "Loaded ${sorted.size} rows successfully", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(context, "Could not parse any valid rows", Toast.LENGTH_SHORT).show()
          }
        }) {
          Text("Replace Table")
        }
      },
      dismissButton = {
        TextButton(onClick = { showBulkPasteDialog = false }) { Text("Cancel") }
      }
    )
  }
}
