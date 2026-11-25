package com.example.ui.screens.cru

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.CruLookupTables
import com.example.data.TankRepository

@Composable
fun CruSetupDialog(
  repository: TankRepository,
  onDismiss: () -> Unit
) {
  val currentTables by repository.cruLookupTables.collectAsState()
  var showResetConfirm by remember { mutableStateOf(false) }

  // Editable state
  var whsvRefStr by remember(currentTables) { mutableStateOf<String>(currentTables.refWhsv.toString()) }
  var pRefStr by remember(currentTables) { mutableStateOf<String>(currentTables.refPressureBarG.toString()) }
  var mabpRefStr by remember(currentTables) { mutableStateOf<String>(currentTables.refMabp.toString()) }

  var fig16Str by remember(currentTables) { mutableStateOf<String>(currentTables.fig16WhsvY.joinToString(", ")) }
  var fig17Str by remember(currentTables) { mutableStateOf<String>(currentTables.fig17PressureY.joinToString(", ")) }
  var fig18Str by remember(currentTables) { mutableStateOf<String>(currentTables.fig18RonY.joinToString(", ")) }
  var fig19Str by remember(currentTables) { mutableStateOf<String>(currentTables.fig19AnY.joinToString(", ")) }
  var fig20Str by remember(currentTables) { mutableStateOf<String>(currentTables.fig20MabpY.joinToString(", ")) }

  var fig22_25Str by remember(currentTables) { mutableStateOf<String>(currentTables.fig22An25.joinToString(", ")) }
  var fig22_35Str by remember(currentTables) { mutableStateOf<String>(currentTables.fig22An35.joinToString(", ")) }
  var fig22_45Str by remember(currentTables) { mutableStateOf<String>(currentTables.fig22An45.joinToString(", ")) }
  var fig22_55Str by remember(currentTables) { mutableStateOf<String>(currentTables.fig22An55.joinToString(", ")) }

  var errorMessage by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text("CRU Axens Lookup Tables Setup", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Text(
          "Axens semi-regenerative reformer Figs 16-20 & 22 lookup tables. Values are interpolated linearly or bilinearly.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        errorMessage?.let {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small
          ) {
            Text(
              it,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(8.dp),
              style = MaterialTheme.typography.bodySmall
            )
          }
        }

        // Reference conditions
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Reference Conditions (Fig 22 Base)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = whsvRefStr,
                onValueChange = { whsvRefStr = it },
                label = { Text("WHSV ref (hr⁻¹)") },
                modifier = Modifier.weight(1f).testTag("cru_ref_whsv_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
              )
              OutlinedTextField(
                value = pRefStr,
                onValueChange = { pRefStr = it },
                label = { Text("P ref (bar g)") },
                modifier = Modifier.weight(1f).testTag("cru_ref_p_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
              )
            }
            OutlinedTextField(
              value = mabpRefStr,
              onValueChange = { mabpRefStr = it },
              label = { Text("MABP ref (°C)") },
              modifier = Modifier.fillMaxWidth().testTag("cru_ref_mabp_input"),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
          }
        }

        // Fig 16
        TableInputField(
          title = "Fig 16: WHSV ΔT Table (°C)",
          subtitle = "Range: 0.8 to 3.2 hr⁻¹ (step 0.1, 25 points)",
          value = fig16Str,
          onValueChange = { fig16Str = it },
          testTag = "fig16_input"
        )

        // Fig 17
        TableInputField(
          title = "Fig 17: Pressure ΔT Table (°C)",
          subtitle = "Range: 10 to 35 kg/cm² g (step 1.0, 26 points)",
          value = fig17Str,
          onValueChange = { fig17Str = it },
          testTag = "fig17_input"
        )

        // Fig 18
        TableInputField(
          title = "Fig 18: RON Clear ΔT Table (°C)",
          subtitle = "Range: 90 to 102 (step 0.5, 25 points)",
          value = fig18Str,
          onValueChange = { fig18Str = it },
          testTag = "fig18_input"
        )

        // Fig 19
        TableInputField(
          title = "Fig 19: A + 0.85N ΔT Table (°C)",
          subtitle = "Range: 24 to 50 vol % (step 1.0, 27 points)",
          value = fig19Str,
          onValueChange = { fig19Str = it },
          testTag = "fig19_input"
        )

        // Fig 20
        TableInputField(
          title = "Fig 20: MABP ΔT Table (°C)",
          subtitle = "Range: 100 to 150 °C (step 2.0, 26 points)",
          value = fig20Str,
          onValueChange = { fig20Str = it },
          testTag = "fig20_input"
        )

        // Fig 22 Curves
        Text("Fig 22: SOR WAIT vs RON (92 to 102, step 0.5, 21 values per curve)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        TableInputField(
          title = "Curve A+0.85N = 25 vol % (°C)",
          subtitle = "21 values for RON 92.0 to 102.0",
          value = fig22_25Str,
          onValueChange = { fig22_25Str = it },
          testTag = "fig22_25_input"
        )

        TableInputField(
          title = "Curve A+0.85N = 35 vol % (°C)",
          subtitle = "21 values for RON 92.0 to 102.0",
          value = fig22_35Str,
          onValueChange = { fig22_35Str = it },
          testTag = "fig22_35_input"
        )

        TableInputField(
          title = "Curve A+0.85N = 45 vol % (°C)",
          subtitle = "21 values for RON 92.0 to 102.0",
          value = fig22_45Str,
          onValueChange = { fig22_45Str = it },
          testTag = "fig22_45_input"
        )

        TableInputField(
          title = "Curve A+0.85N = 55 vol % (°C)",
          subtitle = "21 values for RON 92.0 to 102.0",
          value = fig22_55Str,
          onValueChange = { fig22_55Str = it },
          testTag = "fig22_55_input"
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          try {
            val parseList: (String, Int) -> List<Double> = { str, count ->
              val tokens = str.split(",").map { it.trim() }.filter { it.isNotEmpty() }
              if (tokens.size != count) {
                throw IllegalArgumentException("Expected $count numbers, but got ${tokens.size}")
              }
              tokens.map { it.toDouble() }
            }

            val newTables = currentTables.copy(
              refWhsv = whsvRefStr.toDouble(),
              refPressureBarG = pRefStr.toDouble(),
              refMabp = mabpRefStr.toDouble(),
              fig16WhsvY = parseList(fig16Str, 25),
              fig17PressureY = parseList(fig17Str, 26),
              fig18RonY = parseList(fig18Str, 25),
              fig19AnY = parseList(fig19Str, 27),
              fig20MabpY = parseList(fig20Str, 26),
              fig22An25 = parseList(fig22_25Str, 21),
              fig22An35 = parseList(fig22_35Str, 21),
              fig22An45 = parseList(fig22_45Str, 21),
              fig22An55 = parseList(fig22_55Str, 21)
            )

            repository.saveCruLookupTables(newTables)
            repository.addEditLog("EDIT", "CRU Lookup Tables", "Updated Axens CRU lookup tables and reference points")
            onDismiss()
          } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to save tables: invalid input format"
          }
        },
        modifier = Modifier.testTag("cru_save_tables_button")
      ) {
        Text("Save Tables")
      }
    },
    dismissButton = {
      Row {
        TextButton(
          onClick = { showResetConfirm = true },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("cru_reset_tables_button")
        ) {
          Text("Reset to Default")
        }
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = onDismiss) {
          Text("Cancel")
        }
      }
    }
  )

  if (showResetConfirm) {
    AlertDialog(
      onDismissRequest = { showResetConfirm = false },
      title = { Text("Reset to Factory Defaults?") },
      text = { Text("This will restore all Fig 16-20 and Fig 22 lookup values and reference conditions to the standard Axens manual specifications.") },
      confirmButton = {
        Button(
          onClick = {
            repository.resetCruLookupTablesToDefault()
            showResetConfirm = false
            onDismiss()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Text("Reset Now")
        }
      },
      dismissButton = {
        TextButton(onClick = { showResetConfirm = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun TableInputField(
  title: String,
  subtitle: String,
  value: String,
  onValueChange: (String) -> Unit,
  testTag: String
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = Modifier
        .fillMaxWidth()
        .testTag(testTag),
      maxLines = 4,
      textStyle = MaterialTheme.typography.bodySmall
    )
  }
}
