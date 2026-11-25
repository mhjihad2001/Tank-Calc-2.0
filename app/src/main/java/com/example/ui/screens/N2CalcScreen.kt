package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.TankType
import com.example.data.TankRepository
import com.example.engine.Calculators
import com.example.engine.UnitQuickSwitchChip
import com.example.engine.UnitUtils
import com.example.ui.components.AppScreen
import com.example.ui.components.NumberInput
import com.example.ui.components.TankPickerSheet
import kotlinx.coroutines.delay
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun N2CalcScreen(
  repository: TankRepository,
  onBack: (() -> Unit)? = null
) {
  val tanks by repository.tanks.collectAsState()
  val settings by repository.settings.collectAsState()
  val n2Tanks = tanks.filter { it.type == TankType.N2_STORAGE || it.n2Table.isNotEmpty() }

  var selectedTankName by remember(n2Tanks) { mutableStateOf(n2Tanks.firstOrNull()?.name ?: tanks.firstOrNull()?.name ?: "") }
  var heightInput by remember { mutableStateOf("125.0") }
  var showPicker by remember { mutableStateOf(false) }
  var draftSavedIndicator by remember { mutableStateOf(false) }
  var showClearConfirm by remember { mutableStateOf(false) }
  var showOverflowMenu by remember { mutableStateOf(false) }

  // Restore draft on mount
  LaunchedEffect(Unit) {
    val draft = repository.getDraft("n2_calc")
    if (draft != null) {
      try {
        val json = JSONObject(draft.payloadJson)
        val tank = json.optString("tank", "")
        val h = json.optString("height", "")
        if (tank.isNotBlank()) selectedTankName = tank
        if (h.isNotBlank()) heightInput = h
      } catch (_: Exception) {}
    }
  }

  // Auto-save draft with 400ms debounce
  LaunchedEffect(selectedTankName, heightInput) {
    delay(400L)
    val payload = JSONObject().apply {
      put("tank", selectedTankName)
      put("height", heightInput)
    }.toString()
    repository.saveDraft("n2_calc", "default", payload, "$selectedTankName ($heightInput mm)")
    draftSavedIndicator = true
    delay(1500L)
    draftSavedIndicator = false
  }

  val selectedTank = tanks.find { it.name == selectedTankName } ?: tanks.firstOrNull()
  val heightVal = heightInput.toDoubleOrNull() ?: 0.0

  val (volume, breakdown, warning) = remember(selectedTank, heightVal) {
    if (selectedTank != null) {
      Calculators.calculateN2Volume(selectedTank, heightVal)
    } else {
      Triple(0.0, "No N2 tank selected", null)
    }
  }

  AppScreen(
    title = "Nitrogen (N2)",
    subtitle = if (draftSavedIndicator) "Draft saved" else null,
    onBack = onBack,
    actions = {
      UnitQuickSwitchChip(
        currentUnit = settings.unit,
        onUnitChange = { repository.updateSettings(settings.copy(unit = it)) }
      )
      Box {
        IconButton(onClick = { showOverflowMenu = true }) {
          Icon(Icons.Default.MoreVert, contentDescription = "More Actions")
        }
        DropdownMenu(
          expanded = showOverflowMenu,
          onDismissRequest = { showOverflowMenu = false }
        ) {
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
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        Text("Selected N2 Storage Tank", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedCard(
          onClick = { showPicker = true },
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(if (selectedTank != null) "${selectedTank.name} (${selectedTank.type})" else "Select Tank...", fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
          }
        }
      }

      item {
        NumberInput(
          value = heightInput,
          onValueChange = { heightInput = it },
          label = "Water Column Height",
          suffix = "mm"
        )
      }

      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("N2 Calculation Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Divider()

            if (warning != null) {
              Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(warning, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                }
              }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Water Column Height:")
              Text("$heightVal mm", fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Interpolation Breakdown:")
              Text(breakdown, style = MaterialTheme.typography.bodySmall)
            }
            Divider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Calculated N2 Volume:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
              Text(UnitUtils.formatVolume(volume, settings.unit, settings = settings), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
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

    if (showClearConfirm) {
      AlertDialog(
        onDismissRequest = { showClearConfirm = false },
        title = { Text("Clear / New Calculation?") },
        text = { Text("Reset inputs and clear this draft?") },
        confirmButton = {
          Button(onClick = {
            heightInput = ""
            repository.deleteDraft("n2_calc")
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
