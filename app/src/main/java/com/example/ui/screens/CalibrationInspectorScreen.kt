package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.engine.Calculators
import com.example.engine.TankNameMatcher
import com.example.engine.UnitUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationInspectorScreen(
  repository: TankRepository,
  initialTankName: String? = null,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val tanks by repository.tanks.collectAsState()
  val settings by repository.settings.collectAsState()

  var selectedTankName by remember(tanks, initialTankName) {
    mutableStateOf(initialTankName ?: tanks.firstOrNull()?.name ?: "")
  }

  val selectedTank = tanks.find { it.name.equals(selectedTankName, ignoreCase = true) } ?: tanks.firstOrNull()

  var selectedTab by remember { mutableStateOf(0) }

  // State for Dip Table editing
  var dipRows by remember(selectedTank) {
    mutableStateOf(selectedTank?.dipTable?.sortedBy { it.dipMm } ?: emptyList())
  }
  var editingDipRowIndex by remember { mutableStateOf<Int?>(null) }
  var showAddDipDialog by remember { mutableStateOf(false) }
  var showBatchPasteDialog by remember { mutableStateOf(false) }

  // State for Fraction Table editing (mm 0..9)
  val fracValues = remember(selectedTank) {
    val map = mutableStateMapOf<Int, String>()
    (0..9).forEach { mm ->
      val existing = selectedTank?.fractionTable?.find { it.mm == mm }
      val vol = existing?.let { TankNameMatcher.findVolume(it.volumes, selectedTank.name) } ?: 0.0
      map[mm] = if (vol == 0.0 && mm == 0) "0.0" else String.format(Locale.US, "%.2f", vol)
    }
    map
  }

  // State for N2 Table editing
  var n2Rows by remember(selectedTank) {
    mutableStateOf(selectedTank?.n2Table?.sortedBy { it.heightMm } ?: emptyList())
  }
  var showAddN2Dialog by remember { mutableStateOf(false) }
  var editingN2RowIndex by remember { mutableStateOf<Int?>(null) }

  // Diagnostics test input
  var testDipInput by remember { mutableStateOf("154.5") }
  var runAuditForAll by remember { mutableStateOf(false) }

  BackHandler {
    onBack()
  }

  fun saveDipTableToTank() {
    if (selectedTank == null) return
    val tankName = selectedTank.name
    val updatedDipList = dipRows.sortedBy { it.dipMm }.map { row ->
      val currentVol = TankNameMatcher.findVolume(row.volumes, tankName) ?: 0.0
      val updatedVols = row.volumes + (tankName to currentVol)
      DipRow(dipMm = row.dipMm, volumes = updatedVols)
    }
    val updatedTank = selectedTank.copy(dipTable = updatedDipList)
    repository.addOrUpdateTank(updatedTank)
    Toast.makeText(context, "Saved ${updatedDipList.size} dip calibration points for $tankName", Toast.LENGTH_SHORT).show()
  }

  fun saveFractionsToTank() {
    if (selectedTank == null) return
    val tankName = selectedTank.name
    val updatedFracList = (0..9).map { mm ->
      val valStr = fracValues[mm] ?: "0.0"
      val vol = valStr.toDoubleOrNull() ?: 0.0
      val existingRow = selectedTank.fractionTable.find { it.mm == mm }
      val newVols = (existingRow?.volumes ?: emptyMap()) + (tankName to vol)
      FractionRow(mm = mm, volumes = newVols)
    }
    val updatedTank = selectedTank.copy(fractionTable = updatedFracList)
    repository.addOrUpdateTank(updatedTank)
    Toast.makeText(context, "Saved 10 fraction points (0-9 mm) for $tankName", Toast.LENGTH_SHORT).show()
  }

  fun saveN2TableToTank() {
    if (selectedTank == null) return
    val tankName = selectedTank.name
    val updatedN2List = n2Rows.sortedBy { it.heightMm }.map { row ->
      val currentVol = TankNameMatcher.findVolume(row.volumes, tankName) ?: 0.0
      val updatedVols = row.volumes + (tankName to currentVol)
      N2Row(heightMm = row.heightMm, volumes = updatedVols)
    }
    val updatedTank = selectedTank.copy(n2Table = updatedN2List)
    repository.addOrUpdateTank(updatedTank)
    Toast.makeText(context, "Saved ${updatedN2List.size} N2 calibration points for $tankName", Toast.LENGTH_SHORT).show()
  }

  Scaffold(
    contentWindowInsets = WindowInsets.statusBars,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              "Calibration Editor",
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              selectedTank?.name ?: "No Tank",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.secondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          // Quick save button
          IconButton(onClick = {
            if (selectedTab == 0 && selectedTank?.type != TankType.N2_STORAGE) saveDipTableToTank()
            else if (selectedTab == 1 && selectedTank?.type != TankType.N2_STORAGE) saveFractionsToTank()
            else if (selectedTab == 0 && selectedTank?.type == TankType.N2_STORAGE) saveN2TableToTank()
            else {
              saveDipTableToTank()
              saveFractionsToTank()
            }
          }) {
            Icon(Icons.Default.Save, contentDescription = "Save Changes", tint = MaterialTheme.colorScheme.primary)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Tank Selector Row
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Select Refinery Tank:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
          androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(tanks) { t ->
              FilterChip(
                selected = t.name.equals(selectedTank?.name, ignoreCase = true),
                onClick = { selectedTankName = t.name },
                label = { Text(t.name) },
                leadingIcon = if (t.name.equals(selectedTank?.name, ignoreCase = true)) {
                  { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
              )
            }
          }
        }
      }

      if (selectedTank == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("No tank selected or no tanks available in repository.")
        }
      } else {
        val isN2 = selectedTank.type == TankType.N2_STORAGE
        val isDirectMm = remember(selectedTank) { Calculators.isDirectPerMmTank(selectedTank) }

        if (!isN2) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text("Mode:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            FilterChip(
              selected = selectedTank.dipCalibrationMode == DipCalibrationMode.AUTO,
              onClick = {
                repository.addOrUpdateTank(selectedTank.copy(dipCalibrationMode = DipCalibrationMode.AUTO))
              },
              label = { Text(if (isDirectMm && selectedTank.dipCalibrationMode == DipCalibrationMode.AUTO) "Auto (1mm)" else "Auto", style = MaterialTheme.typography.labelSmall) }
            )
            FilterChip(
              selected = selectedTank.dipCalibrationMode == DipCalibrationMode.DIRECT_1MM,
              onClick = {
                repository.addOrUpdateTank(selectedTank.copy(dipCalibrationMode = DipCalibrationMode.DIRECT_1MM))
                Toast.makeText(context, "${selectedTank.name}: Direct 1mm (No Fraction)", Toast.LENGTH_SHORT).show()
              },
              label = { Text("Direct 1mm", style = MaterialTheme.typography.labelSmall) }
            )
            FilterChip(
              selected = selectedTank.dipCalibrationMode == DipCalibrationMode.BASE_10MM_PLUS_FRACTION,
              onClick = {
                repository.addOrUpdateTank(selectedTank.copy(dipCalibrationMode = DipCalibrationMode.BASE_10MM_PLUS_FRACTION))
                Toast.makeText(context, "${selectedTank.name}: 10mm + Fraction", Toast.LENGTH_SHORT).show()
              },
              label = { Text("10mm+Frac", style = MaterialTheme.typography.labelSmall) }
            )
          }
        }

        // Tabs Row
        TabRow(
          selectedTabIndex = selectedTab,
          containerColor = MaterialTheme.colorScheme.surface
        ) {
          if (!isN2) {
            Tab(
              selected = selectedTab == 0,
              onClick = { selectedTab = 0 },
              text = { Text("Dip Chart (${dipRows.size})") },
              icon = { Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              text = { Text(if (isDirectMm) "Fractions (N/A)" else "Fractions (0-9mm)") },
              icon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
              selected = selectedTab == 2,
              onClick = { selectedTab = 2 },
              text = { Text("Diagnostics & Audit") },
              icon = { Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
          } else {
            Tab(
              selected = selectedTab == 0,
              onClick = { selectedTab = 0 },
              text = { Text("N2 Chart (${n2Rows.size})") },
              icon = { Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              text = { Text("Diagnostics & Audit") },
              icon = { Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
          }
        }

        // Tab Content
        when {
          !isN2 && selectedTab == 0 -> {
            // DIP CHART EDITOR
            DipChartEditorTab(
              tank = selectedTank,
              dipRows = dipRows,
              onAddPoint = { dip, vol ->
                val newRow = DipRow(dipMm = dip, volumes = mapOf(selectedTank.name to vol))
                dipRows = (dipRows.filter { it.dipMm != dip } + newRow).sortedBy { it.dipMm }
                saveDipTableToTank()
              },
              onEditPoint = { index, dip, vol ->
                val mutable = dipRows.toMutableList()
                val oldRow = mutable[index]
                val updatedVols = oldRow.volumes + (selectedTank.name to vol)
                mutable[index] = DipRow(dipMm = dip, volumes = updatedVols)
                dipRows = mutable.sortedBy { it.dipMm }
                saveDipTableToTank()
              },
              onDeletePoint = { index ->
                val mutable = dipRows.toMutableList()
                mutable.removeAt(index)
                dipRows = mutable
                saveDipTableToTank()
              },
              onBatchPaste = { lines ->
                val newPoints = mutableListOf<DipRow>()
                lines.lines().forEach { line ->
                  val parts = line.split(",", ";", "\t", " ").map { it.trim() }.filter { it.isNotEmpty() }
                  if (parts.size >= 2) {
                    val d = parts[0].toDoubleOrNull()
                    val v = parts[1].toDoubleOrNull()
                    if (d != null && v != null) {
                      newPoints.add(DipRow(dipMm = d, volumes = mapOf(selectedTank.name to v)))
                    }
                  }
                }
                if (newPoints.isNotEmpty()) {
                  val combined = (dipRows + newPoints).distinctBy { it.dipMm }.sortedBy { it.dipMm }
                  dipRows = combined
                  saveDipTableToTank()
                  Toast.makeText(context, "Added ${newPoints.size} points from paste", Toast.LENGTH_SHORT).show()
                }
              },
              onSave = { saveDipTableToTank() }
            )
          }

          !isN2 && selectedTab == 1 -> {
            // FRACTIONS (0..9 mm) EDITOR
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              if (isDirectMm) {
                Card(
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                      text = "Direct 1 mm Calibration Active for ${selectedTank.name}: Every mm dip is looked up directly in the Dip Chart. Fraction table (0–9 mm) is NOT applied.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                  }
                }
              }
              FractionsEditorTab(
                tank = selectedTank,
                fracValues = fracValues,
                onValueChange = { mm, text ->
                  fracValues[mm] = text
                },
                onAutoFillRate = { rate ->
                  (0..9).forEach { mm ->
                    fracValues[mm] = String.format(Locale.US, "%.2f", mm * rate)
                  }
                  saveFractionsToTank()
                  Toast.makeText(context, "Auto-filled fractions with rate $rate L/mm", Toast.LENGTH_SHORT).show()
                },
                onSave = { saveFractionsToTank() }
              )
            }
          }

          isN2 && selectedTab == 0 -> {
            // N2 CHART EDITOR
            N2ChartEditorTab(
              tank = selectedTank,
              n2Rows = n2Rows,
              onAddPoint = { h, vol ->
                val newRow = N2Row(heightMm = h, volumes = mapOf(selectedTank.name to vol))
                n2Rows = (n2Rows.filter { it.heightMm != h } + newRow).sortedBy { it.heightMm }
                saveN2TableToTank()
              },
              onEditPoint = { index, h, vol ->
                val mutable = n2Rows.toMutableList()
                val oldRow = mutable[index]
                val updatedVols = oldRow.volumes + (selectedTank.name to vol)
                mutable[index] = N2Row(heightMm = h, volumes = updatedVols)
                n2Rows = mutable.sortedBy { it.heightMm }
                saveN2TableToTank()
              },
              onDeletePoint = { index ->
                val mutable = n2Rows.toMutableList()
                mutable.removeAt(index)
                n2Rows = mutable
                saveN2TableToTank()
              },
              onSave = { saveN2TableToTank() }
            )
          }

          else -> {
            // DIAGNOSTICS & AUDIT TAB
            DiagnosticsAndAuditTab(
              tank = selectedTank,
              allTanks = tanks,
              testDipInput = testDipInput,
              onTestDipChange = { testDipInput = it }
            )
          }
        }
      }
    }
  }
}

@Composable
fun DipChartEditorTab(
  tank: Tank,
  dipRows: List<DipRow>,
  onAddPoint: (Double, Double) -> Unit,
  onEditPoint: (Int, Double, Double) -> Unit,
  onDeletePoint: (Int) -> Unit,
  onBatchPaste: (String) -> Unit,
  onSave: () -> Unit
) {
  var showAddDialog by remember { mutableStateOf(false) }
  var showPasteDialog by remember { mutableStateOf(false) }
  var editingIndex by remember { mutableStateOf<Int?>(null) }

  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // Toolbar card
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
          onClick = { showAddDialog = true },
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Add Point")
        }
        OutlinedButton(
          onClick = { showPasteDialog = true },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
          Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Paste CSV")
        }
      }
      Button(
        onClick = onSave,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
      ) {
        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Save Dip Chart")
      }
    }

    // Dip Table List
    if (dipRows.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Text("No dip calibration data for ${tank.name}. Tap 'Add Point' or 'Paste CSV' to start entering dip vs volume points.")
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        items(dipRows.size) { index ->
          val row = dipRows[index]
          val vol = TankNameMatcher.findVolume(row.volumes, tank.name) ?: 0.0

          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
                    Text("#${index + 1}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                  }
                  Text("Dip: ${String.format(Locale.US, "%.1f", row.dipMm)} mm", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
                Text("Volume: ${String.format(Locale.US, "%.2f", vol)} Litres", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
              }
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { editingIndex = index }) {
                  Icon(Icons.Default.Edit, contentDescription = "Edit Point", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { onDeletePoint(index) }) {
                  Icon(Icons.Default.Delete, contentDescription = "Delete Point", tint = MaterialTheme.colorScheme.error)
                }
              }
            }
          }
        }
      }
    }
  }

  // Add Point Dialog
  if (showAddDialog) {
    PointEditorDialog(
      title = "Add Calibration Point to ${tank.name}",
      initialDip = "",
      initialVol = "",
      onDismiss = { showAddDialog = false },
      onConfirm = { d, v ->
        onAddPoint(d, v)
        showAddDialog = false
      }
    )
  }

  // Edit Point Dialog
  if (editingIndex != null && editingIndex!! in dipRows.indices) {
    val r = dipRows[editingIndex!!]
    val currentV = TankNameMatcher.findVolume(r.volumes, tank.name) ?: 0.0
    PointEditorDialog(
      title = "Edit Point #${editingIndex!! + 1} (${tank.name})",
      initialDip = r.dipMm.toString(),
      initialVol = currentV.toString(),
      onDismiss = { editingIndex = null },
      onConfirm = { d, v ->
        onEditPoint(editingIndex!!, d, v)
        editingIndex = null
      }
    )
  }

  // Batch Paste Dialog
  if (showPasteDialog) {
    BatchPasteDialog(
      title = "Paste Dip Calibration Data (${tank.name})",
      placeholder = "10, 4500\n20, 9000\n30, 13500\n...",
      onDismiss = { showPasteDialog = false },
      onConfirm = { text ->
        onBatchPaste(text)
        showPasteDialog = false
      }
    )
  }
}

@Composable
fun FractionsEditorTab(
  tank: Tank,
  fracValues: MutableMap<Int, String>,
  onValueChange: (Int, String) -> Unit,
  onAutoFillRate: (Double) -> Unit,
  onSave: () -> Unit
) {
  var showRateDialog by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Fractions Calibration (0 to 9 mm remainder)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Text("Enter the volume in Litres corresponding to 0 mm through 9 mm for ${tank.name}.", style = MaterialTheme.typography.bodySmall)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedButton(
            onClick = { showRateDialog = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
          ) {
            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Auto-fill by Rate (L/mm)")
          }
          Button(
            onClick = onSave,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Save Fractions")
          }
        }
      }
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      items(10) { mm ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Surface(
                color = if (mm == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  "$mm mm",
                  fontWeight = FontWeight.Bold,
                  style = MaterialTheme.typography.labelMedium,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
              if (mm == 0) {
                Text("(Base / Zero mm)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
              }
            }

            OutlinedTextField(
              value = fracValues[mm] ?: "",
              onValueChange = { onValueChange(mm, it) },
              label = { Text("Volume (L)") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.width(160.dp)
            )
          }
        }
      }
    }
  }

  if (showRateDialog) {
    var rateInput by remember { mutableStateOf("45.0") }
    AlertDialog(
      onDismissRequest = { showRateDialog = false },
      title = { Text("Auto-fill Fractions by Rate") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Enter litres per millimetre (L/mm) for ${tank.name}. This will populate mm 0..9 with 0 * rate, 1 * rate, ..., 9 * rate.")
          OutlinedTextField(
            value = rateInput,
            onValueChange = { rateInput = it },
            label = { Text("Litres per mm (L/mm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(onClick = {
          val r = rateInput.toDoubleOrNull() ?: 45.0
          onAutoFillRate(r)
          showRateDialog = false
        }) {
          Text("Generate")
        }
      },
      dismissButton = {
        TextButton(onClick = { showRateDialog = false }) { Text("Cancel") }
      }
    )
  }
}

@Composable
fun N2ChartEditorTab(
  tank: Tank,
  n2Rows: List<N2Row>,
  onAddPoint: (Double, Double) -> Unit,
  onEditPoint: (Int, Double, Double) -> Unit,
  onDeletePoint: (Int) -> Unit,
  onSave: () -> Unit
) {
  var showAddDialog by remember { mutableStateOf(false) }
  var editingIndex by remember { mutableStateOf<Int?>(null) }

  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Button(onClick = { showAddDialog = true }) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Add N2 Point")
      }
      Button(onClick = onSave) {
        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Save N2 Chart")
      }
    }

    if (n2Rows.isEmpty()) {
      Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Text("No N2 calibration data. Tap 'Add N2 Point' to start.")
      }
    } else {
      LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(n2Rows.size) { index ->
          val r = n2Rows[index]
          val v = TankNameMatcher.findVolume(r.volumes, tank.name) ?: 0.0
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text("Water Height: ${r.heightMm} mm", fontWeight = FontWeight.Bold)
                Text("Volume: ${v} Litres", style = MaterialTheme.typography.bodySmall)
              }
              Row {
                IconButton(onClick = { editingIndex = index }) {
                  Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { onDeletePoint(index) }) {
                  Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
              }
            }
          }
        }
      }
    }
  }

  if (showAddDialog) {
    PointEditorDialog(
      title = "Add N2 Point (${tank.name})",
      initialDip = "",
      initialVol = "",
      dipLabel = "Water Height (mm)",
      onDismiss = { showAddDialog = false },
      onConfirm = { h, v ->
        onAddPoint(h, v)
        showAddDialog = false
      }
    )
  }

  if (editingIndex != null && editingIndex!! in n2Rows.indices) {
    val r = n2Rows[editingIndex!!]
    val v = TankNameMatcher.findVolume(r.volumes, tank.name) ?: 0.0
    PointEditorDialog(
      title = "Edit N2 Point #${editingIndex!! + 1}",
      initialDip = r.heightMm.toString(),
      initialVol = v.toString(),
      dipLabel = "Water Height (mm)",
      onDismiss = { editingIndex = null },
      onConfirm = { h, vol ->
        onEditPoint(editingIndex!!, h, vol)
        editingIndex = null
      }
    )
  }
}

@Composable
fun DiagnosticsAndAuditTab(
  tank: Tank,
  allTanks: List<Tank>,
  testDipInput: String,
  onTestDipChange: (String) -> Unit
) {
  var showAllAudit by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Tank Identity & Normalization Card
    item {
      Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Tank Identity & Header Matching", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
          Text("• Registered Name: ${tank.name}")
          Text("• Normalized Name: ${TankNameMatcher.normalize(tank.name)}")
          val dipKeys = tank.dipTable.firstOrNull()?.volumes?.keys ?: emptySet()
          val fracKeys = tank.fractionTable.firstOrNull()?.volumes?.keys ?: emptySet()
          Text("• Column in Dip Table: ${dipKeys.find { TankNameMatcher.simpleMatch(it, tank.name) } ?: "Direct (${tank.name})"}")
          Text("• Column in Fraction Table: ${fracKeys.find { TankNameMatcher.simpleMatch(it, tank.name) } ?: "Direct (${tank.name})"}")
          val isDirectMmTank = Calculators.isDirectPerMmTank(tank)
          Text("• Calibration Mode: ${if (isDirectMmTank) "Direct 1 mm (No Fraction)" else "Standard 10 mm Base + 0..9 mm Fraction"}")
          Text("• Dip Rows Count: ${tank.dipTable.size}")
          Text("• Fraction Rows Count: ${if (isDirectMmTank) "Not Required (Direct 1 mm)" else "${tank.fractionTable.size} of 10"}")
        }
      }
    }

    // Live Test Dip Trace
    item {
      Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Live Dip-to-Volume Calculation Test", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
          OutlinedTextField(
            value = testDipInput,
            onValueChange = onTestDipChange,
            label = { Text("Enter Test Dip (mm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
          )

          val testD = testDipInput.toDoubleOrNull() ?: 0.0
          val result = Calculators.calculateDipVolume(tank, testD)

          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("Calculation Trace:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
              Text("1. Input Dip: ${result.inputDip} mm")
              if (result.usedDirectMmLookup) {
                Text("2. Direct 1 mm Lookup: ${result.baseDip} mm -> ${String.format(Locale.US, "%.2f", result.baseVolume)} L (No Fraction)")
                Text("3. Total Calculated Volume: ${String.format(Locale.US, "%.2f", result.totalVolume)} L", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
              } else {
                Text("2. Base Dip: ${result.baseDip} mm -> Base Vol: ${String.format(Locale.US, "%.2f", result.baseVolume)} L")
                Text("3. Remainder Frac: ${result.fracMm} mm -> Frac Vol: ${String.format(Locale.US, "%.2f", result.fracVolume)} L")
                Text("4. Total Calculated Volume: ${String.format(Locale.US, "%.2f", result.totalVolume)} L", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
              }

              if (result.warning != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(4.dp)) {
                  Text(result.warning!!, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(6.dp), style = MaterialTheme.typography.bodySmall)
                }
              } else {
                Surface(color = Color(0xFF16A34A).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                  Text("✓ Calculation OK & Verified", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }
        }
      }
    }

    // Complete Refinery Audit
    item {
      Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Complete Refinery Calibration Audit", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Button(
              onClick = { showAllAudit = !showAllAudit },
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
              Text(if (showAllAudit) "Hide Audit" else "Check All Tanks")
            }
          }

          if (showAllAudit) {
            allTanks.forEach { t ->
              val isDirect = Calculators.isDirectPerMmTank(t)
              val hasDip = t.dipTable.isNotEmpty()
              val hasFrac = t.fractionTable.size == 10
              val sampleDip = if (t.dipTable.isNotEmpty()) {
                val minD = t.dipTable.minOf { it.dipMm }
                val maxD = t.dipTable.maxOf { it.dipMm }
                if (154.5 in minD..maxD) 154.5 else ((minD + maxD) / 2.0)
              } else 154.5
              val testRes = Calculators.calculateDipVolume(t, sampleDip)
              val hasError = testRes.warning != null || !hasDip || (!hasFrac && !isDirect && t.type != TankType.N2_STORAGE)

              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
              ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t.name, fontWeight = FontWeight.Bold)
                    Surface(
                      color = if (!hasError) Color(0xFF16A34A).copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                      shape = RoundedCornerShape(4.dp)
                    ) {
                      Text(
                        if (!hasError) "OK" else "Issues Detected",
                        color = if (!hasError) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }
                  Text(
                    if (isDirect) "Dip Rows: ${t.dipTable.size} | Direct 1 mm (No Fraction)" else "Dip Rows: ${t.dipTable.size} | Fraction Rows: ${t.fractionTable.size}/10",
                    style = MaterialTheme.typography.bodySmall
                  )
                  if (testRes.warning != null) {
                    Text("• ${testRes.warning}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
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

@Composable
fun PointEditorDialog(
  title: String,
  initialDip: String,
  initialVol: String,
  dipLabel: String = "Dip (mm)",
  onDismiss: () -> Unit,
  onConfirm: (Double, Double) -> Unit
) {
  var dipText by remember { mutableStateOf(initialDip) }
  var volText by remember { mutableStateOf(initialVol) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = dipText,
          onValueChange = { dipText = it },
          label = { Text(dipLabel) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = volText,
          onValueChange = { volText = it },
          label = { Text("Volume (Litres)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(onClick = {
        val d = dipText.toDoubleOrNull()
        val v = volText.toDoubleOrNull()
        if (d != null && v != null) {
          onConfirm(d, v)
        }
      }) {
        Text("Save Point")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
fun BatchPasteDialog(
  title: String,
  placeholder: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit
) {
  var content by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Paste or type lines of 'Dip, Volume' (one per line):", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
          value = content,
          onValueChange = { content = it },
          placeholder = { Text(placeholder) },
          modifier = Modifier.fillMaxWidth().height(180.dp),
          maxLines = 10
        )
      }
    },
    confirmButton = {
      Button(onClick = { onConfirm(content) }) {
        Text("Import Points")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}
