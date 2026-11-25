package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Chemical
import com.example.data.DosingTank
import com.example.data.TankRepository
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InjectionSetupScreen(repository: TankRepository, onBack: () -> Unit) {
  val context = LocalContext.current
  val dosingTanks by repository.dosingTanks.collectAsState()
  val chemicals by repository.chemicals.collectAsState()

  var selectedTab by remember { mutableStateOf(0) }

  var editingTank by remember { mutableStateOf<DosingTank?>(null) }
  var showTankDialog by remember { mutableStateOf(false) }

  var editingChem by remember { mutableStateOf<Chemical?>(null) }
  var showChemDialog by remember { mutableStateOf(false) }

  Scaffold(
    contentWindowInsets = WindowInsets.statusBars,
    topBar = {
      TopAppBar(
        title = {
          Text(
            "Injection Setup Lists",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = {
            if (selectedTab == 0) {
              editingTank = null
              showTankDialog = true
            } else {
              editingChem = null
              showChemDialog = true
            }
          }) {
            Icon(Icons.Default.Add, contentDescription = "Add")
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      TabRow(selectedTabIndex = selectedTab) {
        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Dosing Tanks (${dosingTanks.size})") })
        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Chemicals (${chemicals.size})") })
      }

      if (selectedTab == 0) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
          items(dosingTanks) { tank ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(tank.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                  if (!tank.isDefault) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                      Text("Edited", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                  }
                }
                Text("Dimensions: D ${tank.diameterMm} mm x H ${tank.heightMm} mm")
                Text("Litres per mm: ${String.format(java.util.Locale.US, "%.4f", tank.calculatedLitresPerMm)} L/mm (1 L = ${String.format(java.util.Locale.US, "%.2f", tank.mmPerLitre)} mm)")
                Text("Capacity: ${String.format(java.util.Locale.US, "%.1f", tank.capacityLiters)} L | Linked Chem: ${tank.linkedChemicalName}")

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                  TextButton(onClick = { editingTank = tank; showTankDialog = true }) { Text("Edit") }
                  TextButton(onClick = {
                    repository.deleteDosingTank(tank.id)
                    Toast.makeText(context, "Deleted tank", Toast.LENGTH_SHORT).show()
                  }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
              }
            }
          }
        }
      } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
          items(chemicals) { chem ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(chem.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                  if (!chem.isDefault) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                      Text("Edited", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                  }
                }
                Text("Density: ${chem.densityKgL} kg/L (${chem.densityKgM3} kg/m³) | Active: ${chem.activeFractionPercent}%")
                Text("Element Factor: ${chem.elementFactor} | Target PPM: ${chem.targetWtPpm} (${chem.minPpm}-${chem.maxPpm})")
                Text("Basis Stream: ${chem.dosingBasisStreamName}")

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                  TextButton(onClick = { editingChem = chem; showChemDialog = true }) { Text("Edit") }
                  TextButton(onClick = {
                    repository.deleteChemical(chem.id)
                    Toast.makeText(context, "Deleted chemical", Toast.LENGTH_SHORT).show()
                  }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
              }
            }
          }
        }
      }
    }

    if (showTankDialog) {
      TankEditorModal(
        tank = editingTank,
        chemicals = chemicals,
        onDismiss = { showTankDialog = false; editingTank = null },
        onSave = { t ->
          repository.addOrUpdateDosingTank(t)
          showTankDialog = false
          editingTank = null
          Toast.makeText(context, "Dosing tank saved", Toast.LENGTH_SHORT).show()
        }
      )
    }

    if (showChemDialog) {
      ChemicalEditorModal(
        chem = editingChem,
        onDismiss = { showChemDialog = false; editingChem = null },
        onSave = { c ->
          repository.addOrUpdateChemical(c)
          showChemDialog = false
          editingChem = null
          Toast.makeText(context, "Chemical saved", Toast.LENGTH_SHORT).show()
        }
      )
    }
  }
}

@Composable
fun TankEditorModal(tank: DosingTank?, chemicals: List<Chemical>, onDismiss: () -> Unit, onSave: (DosingTank) -> Unit) {
  var name by remember { mutableStateOf(tank?.name ?: "") }
  var diameter by remember { mutableStateOf(tank?.diameterMm?.toString() ?: "1000.0") }
  var height by remember { mutableStateOf(tank?.heightMm?.toString() ?: "1500.0") }
  var override by remember { mutableStateOf(tank?.overrideLitresPerMm?.toString() ?: "") }
  var linkedChem by remember { mutableStateOf(tank?.linkedChemicalName ?: chemicals.firstOrNull()?.name ?: "") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (tank == null) "Add Dosing Tank" else "Edit Dosing Tank") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tank Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = diameter, onValueChange = { diameter = it }, label = { Text("Diameter (mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("Height (mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = override, onValueChange = { override = it }, label = { Text("Override Litres per mm (Optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

        Text("Linked Chemical:", fontWeight = FontWeight.Bold)
        chemicals.forEach { c ->
          Row(
            modifier = Modifier.fillMaxWidth().clickable { linkedChem = c.name },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(c.name)
            RadioButton(selected = linkedChem == c.name, onClick = { linkedChem = c.name })
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = {
        val d = diameter.toDoubleOrNull() ?: 1000.0
        val h = height.toDoubleOrNull() ?: 1500.0
        val ov = override.toDoubleOrNull()
        if (name.isBlank()) return@Button
        onSave(
          DosingTank(
            id = tank?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            diameterMm = d,
            heightMm = h,
            overrideLitresPerMm = ov,
            linkedChemicalName = linkedChem
          )
        )
      }) {
        Text("Save")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}

@Composable
fun ChemicalEditorModal(chem: Chemical?, onDismiss: () -> Unit, onSave: (Chemical) -> Unit) {
  var name by remember { mutableStateOf(chem?.name ?: "") }
  var density by remember { mutableStateOf(chem?.densityKgL?.toString() ?: "1.0") }
  var activePct by remember { mutableStateOf(chem?.activeFractionPercent?.toString() ?: "100.0") }
  var factor by remember { mutableStateOf(chem?.elementFactor?.toString() ?: "1.0") }
  var basis by remember { mutableStateOf(chem?.dosingBasisStreamName ?: "CRU feed (kg/hr)") }
  var targetPpm by remember { mutableStateOf(chem?.targetWtPpm?.toString() ?: "5.0") }
  var minPpm by remember { mutableStateOf(chem?.minPpm?.toString() ?: "4.0") }
  var maxPpm by remember { mutableStateOf(chem?.maxPpm?.toString() ?: "6.0") }
  var productHeader by remember { mutableStateOf(chem?.productHeaderType ?: "General") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (chem == null) "Add Chemical" else "Edit Chemical") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 450.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Chemical Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = density, onValueChange = { density = it }, label = { Text("Density (kg/L)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = activePct, onValueChange = { activePct = it }, label = { Text("Active / Solution % (0 - 100)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = factor, onValueChange = { factor = it }, label = { Text("Element Content Factor (default 1)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = basis, onValueChange = { basis = it }, label = { Text("Dosing Basis Stream Name") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = targetPpm, onValueChange = { targetPpm = it }, label = { Text("Target PPM") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
          OutlinedTextField(value = minPpm, onValueChange = { minPpm = it }, label = { Text("Min PPM") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
          OutlinedTextField(value = maxPpm, onValueChange = { maxPpm = it }, label = { Text("Max PPM") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = productHeader, onValueChange = { productHeader = it }, label = { Text("Product Header Type (e.g. Nalco)") }, modifier = Modifier.fillMaxWidth())
      }
    },
    confirmButton = {
      Button(onClick = {
        val dens = density.toDoubleOrNull() ?: 1.0
        val act = activePct.toDoubleOrNull() ?: 100.0
        val fac = factor.toDoubleOrNull() ?: 1.0
        val tgt = targetPpm.toDoubleOrNull() ?: 5.0
        val mn = minPpm.toDoubleOrNull() ?: 4.0
        val mx = maxPpm.toDoubleOrNull() ?: 6.0
        if (name.isBlank()) return@Button
        onSave(
          Chemical(
            id = chem?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            densityKgL = dens,
            activeFractionPercent = act.coerceIn(0.0, 100.0),
            elementFactor = fac,
            dosingBasisStreamName = basis,
            targetWtPpm = tgt,
            minPpm = mn,
            maxPpm = mx,
            productHeaderType = productHeader
          )
        )
      }) {
        Text("Save")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}
