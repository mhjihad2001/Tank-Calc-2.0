package com.example.ui.screens.cru

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.CruLookupTables
import com.example.data.PressureUnit
import com.example.engine.CruEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CruChangeEstimatorTab(
  lookupTables: CruLookupTables,
  currentWaitExternal: Double?,
  onSetCurrentWait: (Double) -> Unit,
  modifier: Modifier = Modifier
) {
  var pressureUnit by remember { mutableStateOf(PressureUnit.BAR_G) }

  // Checkboxes
  var includeWhsv by remember { mutableStateOf(true) }
  var includePressure by remember { mutableStateOf(true) }
  var includeRon by remember { mutableStateOf(true) }
  var includeIndex by remember { mutableStateOf(true) }
  var includeMabp by remember { mutableStateOf(true) }

  // Base Case
  var baseWhsvStr by remember { mutableStateOf("1.8") }
  var basePressureStr by remember { mutableStateOf("13.5") }
  var baseRonStr by remember { mutableStateOf("96.0") }
  var baseIndexStr by remember { mutableStateOf("42.0") }
  var baseMabpStr by remember { mutableStateOf("130.0") }

  // New Case
  var newWhsvStr by remember { mutableStateOf("2.25") }
  var newPressureStr by remember { mutableStateOf("13.5") }
  var newRonStr by remember { mutableStateOf("100.0") }
  var newIndexStr by remember { mutableStateOf("35.0") }
  var newMabpStr by remember { mutableStateOf("122.0") }

  // Current WAIT
  var currentWaitStr by remember { mutableStateOf(currentWaitExternal?.let { "%.1f".format(it) } ?: "500.0") }

  LaunchedEffect(currentWaitExternal) {
    if (currentWaitExternal != null) {
      currentWaitStr = "%.1f".format(currentWaitExternal)
    }
  }

  // Parse inputs
  val baseWhsv = baseWhsvStr.toDoubleOrNull() ?: 0.0
  val basePressure = basePressureStr.toDoubleOrNull() ?: 0.0
  val baseRon = baseRonStr.toDoubleOrNull() ?: 0.0
  val baseIndex = baseIndexStr.toDoubleOrNull() ?: 0.0
  val baseMabp = baseMabpStr.toDoubleOrNull() ?: 0.0

  val newWhsv = newWhsvStr.toDoubleOrNull() ?: 0.0
  val newPressure = newPressureStr.toDoubleOrNull() ?: 0.0
  val newRon = newRonStr.toDoubleOrNull() ?: 0.0
  val newIndex = newIndexStr.toDoubleOrNull() ?: 0.0
  val newMabp = newMabpStr.toDoubleOrNull() ?: 0.0

  val currentWait = currentWaitStr.toDoubleOrNull()

  // Calculate live change
  val result = remember(
    baseWhsv, basePressure, baseRon, baseIndex, baseMabp,
    newWhsv, newPressure, newRon, newIndex, newMabp,
    pressureUnit, includeWhsv, includePressure, includeRon, includeIndex, includeMabp,
    lookupTables
  ) {
    CruEngine.calculateChangeEstimator(
      baseWhsv = baseWhsv,
      basePressure = basePressure,
      baseRon = baseRon,
      baseNaphthenesAromatics = baseIndex,
      baseMabp = baseMabp,
      newWhsv = newWhsv,
      newPressure = newPressure,
      newRon = newRon,
      newNaphthenesAromatics = newIndex,
      newMabp = newMabp,
      pressureUnit = pressureUnit,
      includeWhsv = includeWhsv,
      includePressure = includePressure,
      includeRon = includeRon,
      includeNaphthenesAromatics = includeIndex,
      includeMabp = includeMabp,
      tables = lookupTables
    )
  }

  val targetWait = if (currentWait != null) currentWait + result.totalDeltaWait else null

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. Prominent Operating Rule Banner
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
      shape = RoundedCornerShape(12.dp)
    ) {
      Row(
        modifier = Modifier.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          Icons.Default.Info,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onTertiaryContainer,
          modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            "Operating Rule (Axens Manual)",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
          )
          Text(
            "• Feed increase: Increase feed first, then raise temperature.\n• Feed reduction: Lower temperature first, then reduce feed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
          )
        }
      }
    }

    // 2. Pressure Unit Selector & Quick Action Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("Pressure Unit:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PressureUnit.values().forEach { unit ->
          FilterChip(
            selected = pressureUnit == unit,
            onClick = { pressureUnit = unit },
            label = { Text(unit.symbol) }
          )
        }
      }
    }

    // 3. Live Total ΔWAIT & Target WAIT Banner
    Card(
      colors = CardDefaults.cardColors(
        containerColor = when {
          result.totalDeltaWait <= 0.0 -> Color(0xFFE8F5E9)
          result.totalDeltaWait <= 10.0 -> Color(0xFFFFF8E1)
          else -> Color(0xFFFFEBEE)
        }
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("Total Estimated ΔWAIT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
              "Sum of active parameter corrections",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          val totalColor = when {
            result.totalDeltaWait <= 0.0 -> Color(0xFF2E7D32)
            result.totalDeltaWait <= 10.0 -> Color(0xFFE65100)
            else -> Color(0xFFC62828)
          }
          Text(
            text = (if (result.totalDeltaWait >= 0) "+" else "") + "%.2f °C".format(result.totalDeltaWait),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = totalColor,
            modifier = Modifier.testTag("cru_total_delta_wait")
          )
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        // Current & Target WAIT
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = currentWaitStr,
            onValueChange = {
              currentWaitStr = it
              it.toDoubleOrNull()?.let { v -> onSetCurrentWait(v) }
            },
            label = { Text("Current WAIT (°C)") },
            modifier = Modifier.weight(1f).testTag("cru_current_wait_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
          )

          Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
          ) {
            Text("Target WAIT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              text = if (targetWait != null) "%.2f °C".format(targetWait) else "---",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.testTag("cru_target_wait_display")
            )
          }
        }

        // Clamping warnings
        if (result.warnings.isNotEmpty()) {
          Surface(
            color = Color(0xFFFFF3E0),
            shape = RoundedCornerShape(8.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                result.warnings.joinToString("; "),
                color = Color(0xFFE65100),
                style = MaterialTheme.typography.labelSmall
              )
            }
          }
        }
      }
    }

    // 4. Quick Actions
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End
    ) {
      TextButton(
        onClick = {
          val tw = baseWhsvStr; baseWhsvStr = newWhsvStr; newWhsvStr = tw
          val tp = basePressureStr; basePressureStr = newPressureStr; newPressureStr = tp
          val tr = baseRonStr; baseRonStr = newRonStr; newRonStr = tr
          val ti = baseIndexStr; baseIndexStr = newIndexStr; newIndexStr = ti
          val tm = baseMabpStr; baseMabpStr = newMabpStr; newMabpStr = tm
        }
      ) {
        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Swap Base & New")
      }
      Spacer(modifier = Modifier.width(8.dp))
      TextButton(
        onClick = {
          baseWhsvStr = newWhsvStr
          basePressureStr = newPressureStr
          baseRonStr = newRonStr
          baseIndexStr = newIndexStr
          baseMabpStr = newMabpStr
        }
      ) {
        Text("Base = New")
      }
    }

    // 5. Input Cards for Base & New with Checkboxes & Live Individual Deltas
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Text("Parameter Comparisons (Fig 16 - Fig 20)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        // WHSV Row
        ParameterRow(
          title = "WHSV (hr⁻¹)",
          figDesc = "Fig 16",
          included = includeWhsv,
          onIncludedChange = { includeWhsv = it },
          baseValue = baseWhsvStr,
          onBaseChange = { baseWhsvStr = it },
          newValue = newWhsvStr,
          onNewChange = { newWhsvStr = it },
          deltaText = (if (result.deltaWhsv >= 0) "+" else "") + "%.2f °C".format(result.deltaWhsv),
          baseTag = "base_whsv_input",
          newTag = "new_whsv_input"
        )

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Pressure Row
        ParameterRow(
          title = "Pressure (${pressureUnit.symbol})",
          figDesc = "Fig 17",
          included = includePressure,
          onIncludedChange = { includePressure = it },
          baseValue = basePressureStr,
          onBaseChange = { basePressureStr = it },
          newValue = newPressureStr,
          onNewChange = { newPressureStr = it },
          deltaText = (if (result.deltaPressure >= 0) "+" else "") + "%.2f °C".format(result.deltaPressure),
          baseTag = "base_pressure_input",
          newTag = "new_pressure_input"
        )

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // RON Row
        ParameterRow(
          title = "RON Clear",
          figDesc = "Fig 18",
          included = includeRon,
          onIncludedChange = { includeRon = it },
          baseValue = baseRonStr,
          onBaseChange = { baseRonStr = it },
          newValue = newRonStr,
          onNewChange = { newRonStr = it },
          deltaText = (if (result.deltaRon >= 0) "+" else "") + "%.2f °C".format(result.deltaRon),
          baseTag = "base_ron_input",
          newTag = "new_ron_input"
        )

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // A + 0.85N Row
        ParameterRow(
          title = "A + 0.85N (vol %)",
          figDesc = "Fig 19",
          included = includeIndex,
          onIncludedChange = { includeIndex = it },
          baseValue = baseIndexStr,
          onBaseChange = { baseIndexStr = it },
          newValue = newIndexStr,
          onNewChange = { newIndexStr = it },
          deltaText = (if (result.deltaNaphthenesAromatics >= 0) "+" else "") + "%.2f °C".format(result.deltaNaphthenesAromatics),
          baseTag = "base_index_input",
          newTag = "new_index_input"
        )

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // MABP Row
        ParameterRow(
          title = "MABP (°C)",
          figDesc = "Fig 20",
          included = includeMabp,
          onIncludedChange = { includeMabp = it },
          baseValue = baseMabpStr,
          onBaseChange = { baseMabpStr = it },
          newValue = newMabpStr,
          onNewChange = { newMabpStr = it },
          deltaText = (if (result.deltaMabp >= 0) "+" else "") + "%.2f °C".format(result.deltaMabp),
          baseTag = "base_mabp_input",
          newTag = "new_mabp_input"
        )
      }
    }
  }
}

@Composable
private fun ParameterRow(
  title: String,
  figDesc: String,
  included: Boolean,
  onIncludedChange: (Boolean) -> Unit,
  baseValue: String,
  onBaseChange: (String) -> Unit,
  newValue: String,
  onNewChange: (String) -> Unit,
  deltaText: String,
  baseTag: String,
  newTag: String
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
          checked = included,
          onCheckedChange = onIncludedChange,
          modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.width(6.dp))
        Text("($figDesc)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }

      Surface(
        color = if (included) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp)
      ) {
        Text(
          text = if (included) deltaText else "0.00 °C",
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = if (included) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedTextField(
        value = baseValue,
        onValueChange = onBaseChange,
        label = { Text("Base Case") },
        modifier = Modifier.weight(1f).testTag(baseTag),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
      )
      OutlinedTextField(
        value = newValue,
        onValueChange = onNewChange,
        label = { Text("New Case") },
        modifier = Modifier.weight(1f).testTag(newTag),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
      )
    }
  }
}
