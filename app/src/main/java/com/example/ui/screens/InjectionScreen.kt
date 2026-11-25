package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.engine.ExportUtils
import com.example.ui.components.AppScreen
import com.example.ui.components.AuthSession
import com.example.ui.components.PasswordDialog
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

enum class InputUnit { MM_DAY, L_HR, DIP }

data class DosingCellState(
  val mmDayText: String,
  val lHrText: String,
  val lastTyped: InputUnit = InputUnit.MM_DAY
)

data class DosingPointData(
  val id: String,
  val displayName: String,
  val tank: DosingTank,
  val chem: Chemical,
  val basisName: String,
  val basisKgHr: Double,
  val effectiveLHr: Double,
  val effectiveMmDay: Double,
  val chemKgDay: Double,
  val activeKgDay: Double,
  val wtPpm: Double,
  val reqMinLHr: Double,
  val reqTargetLHr: Double,
  val reqMaxLHr: Double,
  val status: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InjectionScreen(
  repository: TankRepository,
  onBack: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val settings by repository.settings.collectAsState()
  val dosingTanks by repository.dosingTanks.collectAsState()
  val chemicals by repository.chemicals.collectAsState()
  val savedRecords by repository.injectionRecords.collectAsState()

  var showSetupScreen by remember { mutableStateOf(false) }
  var showPasswordDialog by remember { mutableStateOf(false) }
  var showHistoryDialog by remember { mutableStateOf(false) }
  var showClearConfirm by remember { mutableStateOf(false) }
  var draftSavedIndicator by remember { mutableStateOf(false) }
  var showOverflowMenu by remember { mutableStateOf(false) }

  // Basis streams
  var feedBasisStr by remember { mutableStateOf("27904.5") }
  var c201BasisStr by remember { mutableStateOf("15198.0") }
  var c203BasisStr by remember { mutableStateOf("9064.0") }
  var combinedBasisStr by remember { mutableStateOf("24262.0") }
  var autoSumCombined by remember { mutableStateOf(true) }

  // C.I product selector: "Nalco 1%" vs "Chimec 10%"
  var selectedCiProduct by remember { mutableStateOf("Nalco 1%") }

  // Dip helper dialog state
  var dipDialogTargetId by remember { mutableStateOf<String?>(null) }
  var dipInitialMm by remember { mutableStateOf("500.0") }
  var dipFinalMm by remember { mutableStateOf("450.0") }
  var dipHours by remember { mutableStateOf("24.0") }

  // Tanks resolution
  val waterTank = dosingTanks.find { it.name.contains("Water", ignoreCase = true) }
    ?: dosingTanks.firstOrNull()
    ?: DosingTank(name = "Water Tank", diameterMm = 400.0, heightMm = 600.0, overrideLitresPerMm = 0.125664, linkedChemicalName = "DM Water")

  val c2cl4Tank = dosingTanks.find { it.name.contains("C2Cl4", ignoreCase = true) }
    ?: dosingTanks.getOrNull(1)
    ?: DosingTank(name = "C2Cl4 Tank", diameterMm = 1200.0, heightMm = 1400.0, overrideLitresPerMm = 1.13, linkedChemicalName = "C2Cl4 solution")

  val ciTank = dosingTanks.find { it.name.contains("C.I", ignoreCase = true) }
    ?: dosingTanks.getOrNull(2)
    ?: DosingTank(name = "C.I Tank", diameterMm = 3000.0, heightMm = 3500.0, overrideLitresPerMm = 7.06, linkedChemicalName = "Nalco C.I")

  // Chemicals resolution
  val waterChem = chemicals.find { it.name.contains("Water", ignoreCase = true) }
    ?: Chemical(name = "DM Water", densityKgL = 1.0, activeFractionPercent = 100.0, elementFactor = 1.0, targetWtPpm = 5.0, minPpm = 4.0, maxPpm = 6.0)

  val c2cl4Chem = chemicals.find { it.name.contains("C2Cl4", ignoreCase = true) }
    ?: Chemical(name = "C2Cl4 solution", densityKgL = 0.778, activeFractionPercent = 5.0, elementFactor = 0.885, targetWtPpm = 1.0, minPpm = 0.2, maxPpm = 1.0)

  val ciChem = if (selectedCiProduct.contains("Chimec", ignoreCase = true)) {
    chemicals.find { it.productHeaderType.equals("Chimec", ignoreCase = true) || it.name.contains("Chimec", ignoreCase = true) }
      ?: Chemical(name = "Chimec C.I", densityKgL = 0.773, activeFractionPercent = 10.0, elementFactor = 1.0, targetWtPpm = 15.0, minPpm = 14.0, maxPpm = 16.0, productHeaderType = "Chimec")
  } else {
    chemicals.find { it.productHeaderType.equals("Nalco", ignoreCase = true) || it.name.contains("Nalco", ignoreCase = true) }
      ?: Chemical(name = "Nalco C.I", densityKgL = 0.773, activeFractionPercent = 1.0, elementFactor = 1.0, targetWtPpm = 8.0, minPpm = 7.0, maxPpm = 9.0, productHeaderType = "Nalco")
  }

  // Linked inputs state for the 5 dosing points
  val cellStates = remember {
    mutableStateMapOf(
      "WATER" to DosingCellState(mmDayText = "27.0", lHrText = "0.1414", lastTyped = InputUnit.MM_DAY),
      "C2CL4" to DosingCellState(mmDayText = "18.0", lHrText = "0.8475", lastTyped = InputUnit.MM_DAY),
      "CI_COMBINED" to DosingCellState(mmDayText = "85.1", lHrText = "25.0337", lastTyped = InputUnit.MM_DAY),
      "CI_C201" to DosingCellState(mmDayText = "53.47", lHrText = "15.73", lastTyped = InputUnit.L_HR),
      "CI_C203" to DosingCellState(mmDayText = "31.89", lHrText = "9.38", lastTyped = InputUnit.L_HR)
    )
  }

  fun updateMmDay(id: String, newText: String, litresPerMm: Double) {
    val mm = newText.toDoubleOrNull()
    val newLHr = if (mm != null && litresPerMm > 0) {
      val lhr = mm * litresPerMm / 24.0
      String.format(Locale.US, "%.4f", lhr)
    } else if (newText.isBlank()) "" else cellStates[id]?.lHrText ?: ""
    cellStates[id] = DosingCellState(mmDayText = newText, lHrText = newLHr, lastTyped = InputUnit.MM_DAY)
  }

  fun updateLHr(id: String, newText: String, litresPerMm: Double) {
    val lhr = newText.toDoubleOrNull()
    val newMm = if (lhr != null && litresPerMm > 0) {
      val mm = lhr * 24.0 / litresPerMm
      String.format(Locale.US, "%.2f", mm)
    } else if (newText.isBlank()) "" else cellStates[id]?.mmDayText ?: ""
    cellStates[id] = DosingCellState(mmDayText = newMm, lHrText = newText, lastTyped = InputUnit.L_HR)
  }

  fun applyDip(id: String, litresPerMm: Double) {
    val init = dipInitialMm.toDoubleOrNull() ?: 0.0
    val final = dipFinalMm.toDoubleOrNull() ?: 0.0
    val hrs = dipHours.toDoubleOrNull() ?: 24.0
    val deltaMm = (init - final).coerceAtLeast(0.0)
    val mmDay = if (hrs > 0) deltaMm * 24.0 / hrs else 0.0
    val lhr = if (hrs > 0 && litresPerMm > 0) deltaMm * litresPerMm / hrs else (mmDay * litresPerMm / 24.0)
    cellStates[id] = DosingCellState(
      mmDayText = String.format(Locale.US, "%.2f", mmDay),
      lHrText = String.format(Locale.US, "%.4f", lhr),
      lastTyped = InputUnit.DIP
    )
    dipDialogTargetId = null
    Toast.makeText(context, "Dip applied to dosing cells", Toast.LENGTH_SHORT).show()
  }

  // Parse basis values
  val feedBasis = feedBasisStr.toDoubleOrNull() ?: 27904.5
  val c201Basis = c201BasisStr.toDoubleOrNull() ?: 15198.0
  val c203Basis = c203BasisStr.toDoubleOrNull() ?: 9064.0
  val combinedBasis = if (autoSumCombined) (c201Basis + c203Basis) else (combinedBasisStr.toDoubleOrNull() ?: (c201Basis + c203Basis))

  // Calculation function
  fun buildDosingPointData(
    id: String,
    displayName: String,
    tank: DosingTank,
    chem: Chemical,
    basisName: String,
    basisKgHr: Double
  ): DosingPointData {
    val cell = cellStates[id] ?: DosingCellState("0.0", "0.0")
    val litresPerMm = tank.calculatedLitresPerMm
    val lHr = cell.lHrText.toDoubleOrNull() ?: ((cell.mmDayText.toDoubleOrNull() ?: 0.0) * litresPerMm / 24.0)
    val mmDay = cell.mmDayText.toDoubleOrNull() ?: (if (litresPerMm > 0) lHr * 24.0 / litresPerMm else 0.0)

    val density = chem.densityKgL
    val activeFrac = chem.activeFraction
    val elemFactor = chem.elementFactor
    val chemKgDay = lHr * 24.0 * density
    val activeKgDay = chemKgDay * activeFrac * elemFactor
    val wtPpm = if (basisKgHr > 0) (lHr * density * activeFrac * elemFactor / basisKgHr) * 1_000_000.0 else 0.0

    val denom = density * activeFrac * elemFactor
    fun calcReq(ppm: Double): Double {
      return if (denom > 0 && basisKgHr > 0) (ppm * basisKgHr) / (1_000_000.0 * denom) else 0.0
    }
    val reqMin = calcReq(chem.minPpm)
    val reqTgt = calcReq(chem.targetWtPpm)
    val reqMax = calcReq(chem.maxPpm)

    val status = when {
      wtPpm < chem.minPpm - 0.001 -> "Below Min"
      wtPpm > chem.maxPpm + 0.001 -> "Above Max"
      else -> "Within Range"
    }

    return DosingPointData(
      id = id,
      displayName = displayName,
      tank = tank,
      chem = chem,
      basisName = basisName,
      basisKgHr = basisKgHr,
      effectiveLHr = lHr,
      effectiveMmDay = mmDay,
      chemKgDay = chemKgDay,
      activeKgDay = activeKgDay,
      wtPpm = wtPpm,
      reqMinLHr = reqMin,
      reqTargetLHr = reqTgt,
      reqMaxLHr = reqMax,
      status = status
    )
  }

  val points = listOf(
    buildDosingPointData("WATER", "DM Water", waterTank, waterChem, "CRU Feed", feedBasis),
    buildDosingPointData("C2CL4", "C2Cl4", c2cl4Tank, c2cl4Chem, "CRU Feed", feedBasis),
    buildDosingPointData("CI_COMBINED", "C.I combined", ciTank, ciChem, "Combined Overhead", combinedBasis),
    buildDosingPointData("CI_C201", "C.I C-201", ciTank, ciChem, "Overhead C-201", c201Basis),
    buildDosingPointData("CI_C203", "C.I C-203", ciTank, ciChem, "Overhead C-203", c203Basis)
  )

  fun saveCurrentRecords() {
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val newRecords = points.map { pt ->
      DailyInjectionRecord(
        date = today,
        timestamp = now,
        dosingPointName = pt.displayName,
        tankName = pt.tank.name,
        chemicalName = pt.chem.name,
        dosingMmDay = pt.effectiveMmDay,
        dosingLHr = pt.effectiveLHr,
        basisStreamKgHr = pt.basisKgHr,
        snapshotTank = pt.tank,
        snapshotChemical = pt.chem,
        wtPpm = pt.wtPpm,
        status = pt.status
      )
    }
    repository.saveInjectionRecords(repository.injectionRecords.value + newRecords)
    repository.deleteDraft("injection")
    Toast.makeText(context, "Saved ${newRecords.size} injection records", Toast.LENGTH_SHORT).show()
  }

  // Restore draft
  LaunchedEffect(Unit) {
    val draft = repository.getDraft("injection")
    if (draft != null) {
      try {
        val json = JSONObject(draft.payloadJson)
        if (json.has("feedBasis")) feedBasisStr = json.getString("feedBasis")
        if (json.has("c201Basis")) c201BasisStr = json.getString("c201Basis")
        if (json.has("c203Basis")) c203BasisStr = json.getString("c203Basis")
        if (json.has("combinedBasis")) combinedBasisStr = json.getString("combinedBasis")
        if (json.has("ciProduct")) selectedCiProduct = json.getString("ciProduct")
        if (json.has("cells")) {
          val cellsObj = json.getJSONObject("cells")
          cellsObj.keys().forEach { key ->
            val c = cellsObj.getJSONObject(key)
            cellStates[key] = DosingCellState(
              mmDayText = c.optString("mm", ""),
              lHrText = c.optString("lhr", "")
            )
          }
        }
      } catch (_: Exception) {}
    }
  }

  // Auto-save draft (400ms debounce)
  LaunchedEffect(feedBasisStr, c201BasisStr, c203BasisStr, combinedBasisStr, selectedCiProduct, cellStates.values.toList()) {
    delay(400L)
    val cellsObj = JSONObject()
    cellStates.forEach { (k, v) ->
      cellsObj.put(k, JSONObject().apply {
        put("mm", v.mmDayText)
        put("lhr", v.lHrText)
      })
    }
    val payload = JSONObject().apply {
      put("feedBasis", feedBasisStr)
      put("c201Basis", c201BasisStr)
      put("c203Basis", c203BasisStr)
      put("combinedBasis", combinedBasisStr)
      put("ciProduct", selectedCiProduct)
      put("cells", cellsObj)
    }.toString()
    repository.saveDraft("injection", "default", payload, "Daily Chemical Injection")
    draftSavedIndicator = true
    delay(1500L)
    draftSavedIndicator = false
  }

  if (showSetupScreen) {
    BackHandler { showSetupScreen = false }
    InjectionSetupScreen(
      repository = repository,
      onBack = { showSetupScreen = false }
    )
    return
  }

  AppScreen(
    title = "Chemical Injection",
    subtitle = if (draftSavedIndicator) "Draft saved" else null,
    onBack = onBack,
    actions = {
      IconButton(onClick = { saveCurrentRecords() }) {
        Icon(Icons.Default.Save, contentDescription = "Save Records")
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
            text = { Text("Injection History (${savedRecords.size})") },
            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
            onClick = {
              showOverflowMenu = false
              showHistoryDialog = true
            }
          )
          DropdownMenuItem(
            text = { Text("Share Summary") },
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
            onClick = {
              showOverflowMenu = false
              val summary = ExportUtils.formatDailyInjectionSummary(
                records = points.map { pt ->
                  DailyInjectionRecord(
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                    dosingPointName = pt.displayName,
                    tankName = pt.tank.name,
                    chemicalName = pt.chem.name,
                    dosingMmDay = pt.effectiveMmDay,
                    dosingLHr = pt.effectiveLHr,
                    basisStreamKgHr = pt.basisKgHr,
                    snapshotTank = pt.tank,
                    snapshotChemical = pt.chem,
                    wtPpm = pt.wtPpm,
                    status = pt.status
                  )
                },
                feedBasis = feedBasis,
                c201Basis = c201Basis,
                c203Basis = c203Basis,
                combinedBasis = combinedBasis,
                ciProduct = selectedCiProduct
              )
              ExportUtils.shareText(context, summary, "Daily Chemical Injection Summary")
            }
          )
          DropdownMenuItem(
            text = { Text("Chemical & Tank Setup 🔒") },
            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = {
              showOverflowMenu = false
              if (AuthSession.isUnlocked()) {
                showSetupScreen = true
              } else {
                showPasswordDialog = true
              }
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
    run {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Basis Streams Configuration Card
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(12.dp)
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  "Basis Stream Rates (kg/hr)",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text("Auto Sum", style = MaterialTheme.typography.labelSmall)
                  Switch(
                    checked = autoSumCombined,
                    onCheckedChange = { autoSumCombined = it },
                    modifier = Modifier.scale(0.8f)
                  )
                }
              }

              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = feedBasisStr,
                  onValueChange = { feedBasisStr = it },
                  label = { Text("Feed (kg/h)") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                  value = c201BasisStr,
                  onValueChange = {
                    c201BasisStr = it
                    if (autoSumCombined) {
                      val c1 = it.toDoubleOrNull() ?: 0.0
                      val c3 = c203BasisStr.toDoubleOrNull() ?: 0.0
                      combinedBasisStr = String.format(Locale.US, "%.1f", c1 + c3)
                    }
                  },
                  label = { Text("C-201 (kg/h)") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  modifier = Modifier.weight(1f)
                )
              }

              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = c203BasisStr,
                  onValueChange = {
                    c203BasisStr = it
                    if (autoSumCombined) {
                      val c1 = c201BasisStr.toDoubleOrNull() ?: 0.0
                      val c3 = it.toDoubleOrNull() ?: 0.0
                      combinedBasisStr = String.format(Locale.US, "%.1f", c1 + c3)
                    }
                  },
                  label = { Text("C-203 (kg/h)") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                  value = if (autoSumCombined) String.format(Locale.US, "%.1f", combinedBasis) else combinedBasisStr,
                  onValueChange = {
                    combinedBasisStr = it
                    autoSumCombined = false
                  },
                  label = { Text("Combined O/H") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  modifier = Modifier.weight(1f)
                )
              }
            }
          }
        }

        // C.I Product Selector Card
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  "Corrosion Inhibitor (C.I) Product Selector",
                  fontWeight = FontWeight.Bold,
                  style = MaterialTheme.typography.titleSmall
                )
                Surface(
                  color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Text(
                    "Active: ${ciChem.activeFractionPercent}%",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
              TabRow(
                selectedTabIndex = if (selectedCiProduct.contains("Chimec", ignoreCase = true)) 1 else 0,
                containerColor = MaterialTheme.colorScheme.surface
              ) {
                Tab(
                  selected = !selectedCiProduct.contains("Chimec", ignoreCase = true),
                  onClick = { selectedCiProduct = "Nalco 1%" },
                  text = { Text("Nalco 1% C.I (Target 8 ppm)") }
                )
                Tab(
                  selected = selectedCiProduct.contains("Chimec", ignoreCase = true),
                  onClick = { selectedCiProduct = "Chimec 10%" },
                  text = { Text("Chimec 10% C.I (Target 15 ppm)") }
                )
              }
            }
          }
        }

        // Section header
        item {
          Text(
            "Dosing Points & Targets Monitor",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        // 5 Dosing Point Cards
        items(points) { point ->
          val cellState = cellStates[point.id] ?: DosingCellState("0.0", "0.0")
          val isMmTyped = cellState.lastTyped == InputUnit.MM_DAY
          val isLHrTyped = cellState.lastTyped == InputUnit.L_HR

          val statusColor = when (point.status) {
            "Below Min" -> Color(0xFFD97706) // Amber
            "Above Max" -> MaterialTheme.colorScheme.error
            else -> Color(0xFF16A34A) // Green
          }

          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              // Card Header
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Column {
                    Text(point.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                      "${point.tank.name} • ${point.chem.name}${if (!point.chem.activeBatchDate.isNullOrBlank()) " • Batch: ${point.chem.activeBatchDate}" else ""}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.secondary
                    )
                  }
                }
                Surface(
                  color = statusColor.copy(alpha = 0.15f),
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Text(
                    "${String.format(Locale.US, "%.2f", point.wtPpm)} ppm",
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                  )
                }
              }

              Text(
                "Basis: ${point.basisName} = ${String.format(Locale.US, "%.1f", point.basisKgHr)} kg/h | Litres/mm: ${String.format(Locale.US, "%.4f", point.tank.calculatedLitresPerMm)} L/mm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
              )

              Divider()

              // === REQUIREMENT 1: Linked Inputs side-by-side with bold border & grey auto badge ===
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                // mm/d cell
                OutlinedTextField(
                  value = cellState.mmDayText,
                  onValueChange = { newVal ->
                    updateMmDay(point.id, newVal, point.tank.calculatedLitresPerMm)
                  },
                  label = { Text("mm/d") },
                  trailingIcon = {
                    if (!isMmTyped) {
                      Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(4.dp)
                      ) {
                        Text(
                          "auto",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.outline,
                          modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                      }
                    }
                  },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = if (isMmTyped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .then(
                      if (isMmTyped) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                      else Modifier
                    )
                )

                // L/hr cell
                OutlinedTextField(
                  value = cellState.lHrText,
                  onValueChange = { newVal ->
                    updateLHr(point.id, newVal, point.tank.calculatedLitresPerMm)
                  },
                  label = { Text("L/hr") },
                  trailingIcon = {
                    if (!isLHrTyped) {
                      Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(4.dp)
                      ) {
                        Text(
                          "auto",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.outline,
                          modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                      }
                    }
                  },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = if (isLHrTyped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .then(
                      if (isLHrTyped) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                      else Modifier
                    )
                )

                // Dip Helper button
                IconButton(
                  onClick = {
                    dipDialogTargetId = point.id
                  },
                  modifier = Modifier.size(44.dp)
                ) {
                  Icon(
                    Icons.Default.Straighten,
                    contentDescription = "Dip Method",
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
              }

              // === REQUIREMENT 2: Results show mm/day AND L/hr, wt ppm, and status badge ===
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(8.dp)
              ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    "Calculation Results:",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium
                  )

                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Actual Dosing Rate:", style = MaterialTheme.typography.bodySmall)
                    Text(
                      "${String.format(Locale.US, "%.4f", point.effectiveLHr)} L/hr  |  ${String.format(Locale.US, "%.2f", point.effectiveMmDay)} mm/d",
                      fontWeight = FontWeight.Bold,
                      style = MaterialTheme.typography.bodySmall
                    )
                  }

                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Chemical Consumption:", style = MaterialTheme.typography.bodySmall)
                    Text("${String.format(Locale.US, "%.2f", point.chemKgDay)} kg/day", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                  }

                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Active Concentration:", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      Text(
                        "${String.format(Locale.US, "%.3f", point.wtPpm)} ppm",
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        style = MaterialTheme.typography.bodySmall
                      )
                      Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                        Text(
                          point.status,
                          color = statusColor,
                          fontWeight = FontWeight.Bold,
                          style = MaterialTheme.typography.labelSmall,
                          modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                      }
                    }
                  }
                }
              }

              // === REQUIREMENT 3: Required Dosing (Min and Max, in L/hr only) ===
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(8.dp)
              ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      "Required Dosing (L/hr only):",
                      fontWeight = FontWeight.SemiBold,
                      style = MaterialTheme.typography.labelMedium
                    )
                    Surface(
                      color = statusColor.copy(alpha = 0.15f),
                      shape = RoundedCornerShape(4.dp)
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      ) {
                        Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
                        Text(
                          point.status,
                          color = statusColor,
                          fontWeight = FontWeight.Bold,
                          style = MaterialTheme.typography.labelSmall
                        )
                      }
                    }
                  }

                  // 3-column table: Min ppm -> L/hr, Target ppm -> L/hr, Max ppm -> L/hr
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                      .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                      Text("Min (${point.chem.minPpm} ppm)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                      Text(
                        "${String.format(Locale.US, "%.4f", point.reqMinLHr)} L/hr",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                      )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                      Text("Target (${point.chem.targetWtPpm} ppm)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                      Text(
                        "${String.format(Locale.US, "%.4f", point.reqTargetLHr)} L/hr",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                      )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                      Text("Max (${point.chem.maxPpm} ppm)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                      Text(
                        "${String.format(Locale.US, "%.4f", point.reqMaxLHr)} L/hr",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                      )
                    }
                  }

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      "Actual: ${String.format(Locale.US, "%.4f", point.effectiveLHr)} L/hr",
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      when (point.status) {
                        "Below Min" -> "● Below min limit"
                        "Above Max" -> "● Above max limit"
                        else -> "● Optimal within range"
                      },
                      color = statusColor,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.SemiBold
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    // Dip Helper Dialog
    if (dipDialogTargetId != null) {
      val targetPoint = points.find { it.id == dipDialogTargetId }
      val targetTank = targetPoint?.tank ?: waterTank
      AlertDialog(
        onDismissRequest = { dipDialogTargetId = null },
        title = { Text("Dip Method Helper: ${targetPoint?.displayName}") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              "Tank: ${targetTank.name} (Litres/mm = ${String.format(Locale.US, "%.4f", targetTank.calculatedLitresPerMm)})",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.secondary
            )
            OutlinedTextField(
              value = dipInitialMm,
              onValueChange = { dipInitialMm = it },
              label = { Text("Initial Dip (mm)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = dipFinalMm,
              onValueChange = { dipFinalMm = it },
              label = { Text("Final Dip (mm)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = dipHours,
              onValueChange = { dipHours = it },
              label = { Text("Duration (Hours)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth()
            )

            val iVal = dipInitialMm.toDoubleOrNull() ?: 0.0
            val fVal = dipFinalMm.toDoubleOrNull() ?: 0.0
            val hVal = dipHours.toDoubleOrNull() ?: 24.0
            val dMm = (iVal - fVal).coerceAtLeast(0.0)
            val dMmDay = if (hVal > 0) dMm * 24.0 / hVal else 0.0
            val dLHr = if (hVal > 0) dMm * targetTank.calculatedLitresPerMm / hVal else 0.0

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
              Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Level Drop: ${String.format(Locale.US, "%.1f", dMm)} mm", style = MaterialTheme.typography.bodySmall)
                Text("Calculated mm/d: ${String.format(Locale.US, "%.2f", dMmDay)} mm/d", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text("Calculated L/hr: ${String.format(Locale.US, "%.4f", dLHr)} L/hr", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
              }
            }
          }
        },
        confirmButton = {
          Button(onClick = {
            targetPoint?.let { applyDip(it.id, it.tank.calculatedLitresPerMm) }
          }) {
            Text("Apply to Dosing")
          }
        },
        dismissButton = {
          TextButton(onClick = { dipDialogTargetId = null }) { Text("Cancel") }
        }
      )
    }

    // History Dialog / Sheet
    if (showHistoryDialog) {
      AlertDialog(
        onDismissRequest = { showHistoryDialog = false },
        title = {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Injection History (${savedRecords.size})")
            if (savedRecords.isNotEmpty()) {
              IconButton(onClick = {
                val csvIntent = ExportUtils.exportDailyInjectionCsvFile(context, savedRecords)
                if (csvIntent != null) {
                  context.startActivity(Intent.createChooser(csvIntent, "Export Injection Records CSV"))
                } else {
                  val text = ExportUtils.formatDailyInjectionCsv(savedRecords)
                  ExportUtils.copyToClipboard(context, text, "Daily Injection CSV")
                }
              }) {
                Icon(Icons.Default.Download, contentDescription = "Export Excel CSV")
              }
            }
          }
        },
        text = {
          if (savedRecords.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
              Text("No injection records saved yet. Tap the Save button to record today's dosing.")
            }
          } else {
            LazyColumn(
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.heightIn(max = 420.dp)
            ) {
              items(savedRecords.reversed()) { rec ->
                Card(
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        rec.dosingPointName.ifEmpty { rec.tankName },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                      )
                      Text(
                        "${rec.date} ${rec.timestamp}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                      )
                    }
                    Text(
                      "• Dosing: ${String.format(Locale.US, "%.4f", rec.effectiveLHr)} L/hr  |  ${String.format(Locale.US, "%.2f", rec.effectiveMmDay)} mm/d",
                      fontWeight = FontWeight.SemiBold,
                      style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text(
                        "• PPM: ${String.format(Locale.US, "%.2f", rec.wtPpm)} (Tgt ${rec.snapshotChemical.targetWtPpm})",
                        style = MaterialTheme.typography.bodySmall
                      )
                      Surface(
                        color = if (rec.status == "Within Range") Color(0xFF16A34A).copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                      ) {
                        Text(
                          rec.status.ifEmpty { "Saved" },
                          style = MaterialTheme.typography.labelSmall,
                          color = if (rec.status == "Within Range") Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                          modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        },
        confirmButton = {
          if (savedRecords.isNotEmpty()) {
            TextButton(
              onClick = {
                repository.saveInjectionRecords(emptyList())
                Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
              }
            ) {
              Text("Clear All", color = MaterialTheme.colorScheme.error)
            }
          }
        },
        dismissButton = {
          TextButton(onClick = { showHistoryDialog = false }) { Text("Close") }
        }
      )
    }

    if (showPasswordDialog) {
      PasswordDialog(
        storedHash = settings.passwordHash,
        onDismiss = { showPasswordDialog = false },
        onSuccess = {
          showPasswordDialog = false
          AuthSession.unlock()
          showSetupScreen = true
        }
      )
    }

    if (showClearConfirm) {
      AlertDialog(
        onDismissRequest = { showClearConfirm = false },
        title = { Text("Clear Draft / Reset Inputs?") },
        text = { Text("This will reset all dosing inputs and remove the saved draft.") },
        confirmButton = {
          Button(onClick = {
            repository.deleteDraft("injection")
            feedBasisStr = "27904.5"
            c201BasisStr = "15198.0"
            c203BasisStr = "9064.0"
            combinedBasisStr = "24262.0"
            cellStates["WATER"] = DosingCellState("27.0", "0.1414")
            cellStates["C2CL4"] = DosingCellState("18.0", "0.8475")
            cellStates["CI_COMBINED"] = DosingCellState("85.1", "25.0337")
            cellStates["CI_C201"] = DosingCellState("53.47", "15.73")
            cellStates["CI_C203"] = DosingCellState("31.89", "9.38")
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
}
