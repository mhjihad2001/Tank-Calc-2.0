package com.example.ui.screens.cru

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.CruActivityRecord
import com.example.data.CruLookupTables
import com.example.data.PressureUnit
import com.example.data.TankRepository
import com.example.engine.CruEngine
import com.example.engine.ExportUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CruSorActivityTab(
  repository: TankRepository,
  lookupTables: CruLookupTables,
  actualWaitExternal: Double?,
  onSetActualWait: (Double) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val records by repository.cruActivityRecords.collectAsState()

  var pressureUnit by remember { mutableStateOf(PressureUnit.BAR_G) }

  // Inputs
  var ronStr by remember { mutableStateOf("98.0") }
  var indexStr by remember { mutableStateOf("35.0") }
  var pressureStr by remember { mutableStateOf("13.5") }
  var whsvStr by remember { mutableStateOf("2.0") }
  var mabpStr by remember { mutableStateOf("125.6") }
  var actualWaitStr by remember { mutableStateOf(actualWaitExternal?.let { "%.2f".format(it) } ?: "502.1") }

  var runDayStr by remember { mutableStateOf("") }
  val currentDateTime = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()) }
  var dateTimeStr by remember { mutableStateOf(currentDateTime) }
  var remarksStr by remember { mutableStateOf("") }

  // Sync when external tool provides actual WAIT
  LaunchedEffect(actualWaitExternal) {
    if (actualWaitExternal != null) {
      actualWaitStr = "%.2f".format(actualWaitExternal)
    }
  }

  // Helper card state
  var showHelper by remember { mutableStateOf(false) }
  var aromaticsStr by remember { mutableStateOf("") }
  var naphthenesStr by remember { mutableStateOf("") }

  var showHistoryDialog by remember { mutableStateOf(false) }

  val ron = ronStr.toDoubleOrNull() ?: 0.0
  val index = indexStr.toDoubleOrNull() ?: 0.0
  val pressure = pressureStr.toDoubleOrNull() ?: 0.0
  val whsv = whsvStr.toDoubleOrNull() ?: 0.0
  val mabp = mabpStr.toDoubleOrNull() ?: 0.0
  val actualWait = actualWaitStr.toDoubleOrNull() ?: 0.0

  val result = remember(ron, index, pressure, whsv, mabp, actualWait, pressureUnit, lookupTables) {
    CruEngine.calculateSorWaitAndActivity(
      ron = ron,
      naphthenesAromatics = index,
      pressure = pressure,
      whsv = whsv,
      mabp = mabp,
      actualWait = actualWait,
      pressureUnit = pressureUnit,
      tables = lookupTables
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. Result Summary Card
    Card(
      colors = CardDefaults.cardColors(
        containerColor = if (result.activityLoss > 10.0) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("Catalyst Activity Loss (Deactivation)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text("Actual WAIT - Corrected SOR WAIT", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          val lossColor = if (result.activityLoss > 10.0) Color(0xFFC62828) else Color(0xFF2E7D32)
          Text(
            text = (if (result.activityLoss >= 0) "+" else "") + "%.2f °C".format(result.activityLoss),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = lossColor,
            modifier = Modifier.testTag("cru_activity_loss_display")
          )
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        // Key figures row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column {
            Text("Actual WAIT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("%.2f °C".format(result.actualWait), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SOR WAIT Base", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              "%.2f °C".format(result.sorWaitBase),
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.testTag("cru_sor_wait_base_display")
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text("Corrected SOR WAIT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              "%.2f °C".format(result.correctedSorWait),
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.testTag("cru_corrected_sor_wait_display")
            )
          }
        }

        // Detailed Corrections breakdown
        Surface(
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
          shape = RoundedCornerShape(8.dp)
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Corrections vs Axens Ref Conditions:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("• ΔT Pressure (vs ${lookupTables.refPressureBarG} bar g):", style = MaterialTheme.typography.bodySmall)
              Text((if (result.deltaPressure >= 0) "+" else "") + "%.2f °C".format(result.deltaPressure), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("• ΔT WHSV (vs ${lookupTables.refWhsv} hr⁻¹):", style = MaterialTheme.typography.bodySmall)
              Text((if (result.deltaWhsv >= 0) "+" else "") + "%.2f °C".format(result.deltaWhsv), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("• ΔT MABP (vs ${lookupTables.refMabpC} °C):", style = MaterialTheme.typography.bodySmall)
              Text((if (result.deltaMabp >= 0) "+" else "") + "%.2f °C".format(result.deltaMabp), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
            }
          }
        }

        // Warnings
        if (result.warnings.isNotEmpty()) {
          Surface(
            color = Color(0xFFFFF3E0),
            shape = RoundedCornerShape(8.dp)
          ) {
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(result.warnings.joinToString("; "), color = Color(0xFFE65100), style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
    }

    // 2. Input Parameters Card
    Card {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Operating Conditions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          Row {
            PressureUnit.values().forEach { unit ->
              FilterChip(
                selected = pressureUnit == unit,
                onClick = { pressureUnit = unit },
                label = { Text(unit.symbol) }
              )
              Spacer(modifier = Modifier.width(4.dp))
            }
          }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = ronStr,
            onValueChange = { ronStr = it },
            label = { Text("RON Clear") },
            modifier = Modifier.weight(1f).testTag("cru_sor_ron_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
          )
          OutlinedTextField(
            value = indexStr,
            onValueChange = { indexStr = it },
            label = { Text("A + 0.85N (vol %)") },
            modifier = Modifier.weight(1f).testTag("cru_sor_index_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
          )
        }

        // A + 0.85N Helper toggle button
        TextButton(
          onClick = { showHelper = !showHelper },
          modifier = Modifier.align(Alignment.End)
        ) {
          Icon(if (showHelper) Icons.Default.ExpandLess else Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(if (showHelper) "Hide A+0.85N Helper" else "A + 0.85N Helper")
        }

        if (showHelper) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Calculate Feed Index: A + 0.85 × N", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = aromaticsStr,
                  onValueChange = { aromaticsStr = it },
                  label = { Text("Aromatics A (%)") },
                  modifier = Modifier.weight(1f),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  singleLine = true
                )
                OutlinedTextField(
                  value = naphthenesStr,
                  onValueChange = { naphthenesStr = it },
                  label = { Text("Naphthenes N (%)") },
                  modifier = Modifier.weight(1f),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  singleLine = true
                )
              }
              val a = aromaticsStr.toDoubleOrNull() ?: 0.0
              val n = naphthenesStr.toDoubleOrNull() ?: 0.0
              val calcIdx = a + 0.85 * n
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("Result: %.2f vol %%".format(calcIdx), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Button(
                  onClick = {
                    indexStr = "%.2f".format(calcIdx)
                    showHelper = false
                  },
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                  Text("Apply to Feed Index")
                }
              }
            }
          }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = pressureStr,
            onValueChange = { pressureStr = it },
            label = { Text("Pressure (${pressureUnit.symbol})") },
            modifier = Modifier.weight(1f).testTag("cru_sor_pressure_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
          )
          OutlinedTextField(
            value = whsvStr,
            onValueChange = { whsvStr = it },
            label = { Text("WHSV (hr⁻¹)") },
            modifier = Modifier.weight(1f).testTag("cru_sor_whsv_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
          )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = mabpStr,
            onValueChange = { mabpStr = it },
            label = { Text("MABP (°C)") },
            modifier = Modifier.weight(1f).testTag("cru_sor_mabp_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
          )
          OutlinedTextField(
            value = actualWaitStr,
            onValueChange = {
              actualWaitStr = it
              it.toDoubleOrNull()?.let { v -> onSetActualWait(v) }
            },
            label = { Text("Actual WAIT (°C)") },
            modifier = Modifier.weight(1f).testTag("cru_sor_actual_wait_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
          )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = dateTimeStr,
            onValueChange = { dateTimeStr = it },
            label = { Text("Date & Time") },
            modifier = Modifier.weight(1.2f),
            singleLine = true
          )
          OutlinedTextField(
            value = runDayStr,
            onValueChange = { runDayStr = it },
            label = { Text("Run Day (Opt)") },
            modifier = Modifier.weight(0.8f),
            singleLine = true
          )
        }

        OutlinedTextField(
          value = remarksStr,
          onValueChange = { remarksStr = it },
          label = { Text("Remarks (Optional)") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        // Actions
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = {
              val rec = CruActivityRecord(
                dateTime = dateTimeStr,
                runDay = runDayStr,
                ronClear = ron,
                anIndex = index,
                pressureVal = pressure,
                pressureUnit = pressureUnit,
                whsv = whsv,
                mabp = mabp,
                actualWait = actualWait,
                sorWaitBase = result.sorWaitBase,
                pressCorr = result.deltaPressure,
                whsvCorr = result.deltaWhsv,
                mabpCorr = result.deltaMabp,
                correctedSorWait = result.correctedSorWait,
                activityLoss = result.activityLoss,
                remarks = remarksStr
              )
              repository.addOrUpdateCruActivityRecord(rec)
              Toast.makeText(context, "Activity record saved!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f).testTag("cru_save_record_button")
          ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Save Record")
          }

          OutlinedButton(
            onClick = { showHistoryDialog = true },
            modifier = Modifier.weight(1f).testTag("cru_view_history_button")
          ) {
            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("History (${records.size})")
          }
        }
      }
    }

    // 3. Mini Trend Canvas if records exist
    if (records.isNotEmpty()) {
      Card {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Activity Loss Trend (°C)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text("${records.size} points", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }

          val recentRecords = records.takeLast(15)
          val maxLoss = (recentRecords.maxOfOrNull { it.activityLoss } ?: 10.0).coerceAtLeast(5.0)
          val minLoss = (recentRecords.minOfOrNull { it.activityLoss } ?: 0.0).coerceAtMost(0.0)

          Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val width = size.width
            val height = size.height
            val range = (maxLoss - minLoss).coerceAtLeast(1.0)

            // Draw zero line
            val zeroY = height - ((0.0 - minLoss) / range * height).toFloat()
            drawLine(
              color = Color.LightGray,
              start = Offset(0f, zeroY),
              end = Offset(width, zeroY),
              strokeWidth = 2f
            )

            if (recentRecords.size > 1) {
              val stepX = width / (recentRecords.size - 1)
              val path = Path()

              recentRecords.forEachIndexed { idx, r ->
                val x = idx * stepX
                val y = height - ((r.activityLoss - minLoss) / range * height).toFloat()
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(color = Color(0xFF2E7D32), radius = 4f, center = Offset(x, y))
              }

              drawPath(path = path, color = Color(0xFF2E7D32), style = Stroke(width = 3f))
            } else if (recentRecords.size == 1) {
              val r = recentRecords[0]
              val y = height - ((r.activityLoss - minLoss) / range * height).toFloat()
              drawCircle(color = Color(0xFF2E7D32), radius = 5f, center = Offset(width / 2f, y))
            }
          }
        }
      }
    }
  }

  // History & Export Dialog
  if (showHistoryDialog) {
    AlertDialog(
      onDismissRequest = { showHistoryDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.width(8.dp))
          Text("CRU Activity Records", fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (records.isEmpty()) {
            Text("No saved activity records yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          } else {
            records.forEach { r ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
              ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(r.dateTime, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(
                      "Loss: " + (if (r.activityLoss >= 0) "+" else "") + "%.2f °C".format(r.activityLoss),
                      fontWeight = FontWeight.ExtraBold,
                      color = if (r.activityLoss > 10.0) Color(0xFFC62828) else Color(0xFF2E7D32)
                    )
                  }
                  Text(
                    "Actual: %.1f °C | Corrected SOR: %.1f °C (Base: %.1f °C)".format(r.actualWait, r.correctedSorWait, r.sorWaitBase),
                    style = MaterialTheme.typography.bodySmall
                  )
                  Text(
                    "RON: %.1f | A+0.85N: %.1f | WHSV: %.2f | P: %.1f %s".format(r.ron, r.naphthenesAromatics, r.whsv, r.pressure, r.pressureUnit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                  ) {
                    IconButton(
                      onClick = {
                        val rep = ExportUtils.formatCruActivityReport(r)
                        ExportUtils.copyToClipboard(context, rep, "CRU Activity Report")
                      },
                      modifier = Modifier.size(32.dp)
                    ) {
                      Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                      onClick = {
                        val intent = ExportUtils.exportCruActivityPdfFile(context, r)
                        if (intent != null) context.startActivity(Intent.createChooser(intent, "Share PDF"))
                        else Toast.makeText(context, "Failed to create PDF", Toast.LENGTH_SHORT).show()
                      },
                      modifier = Modifier.size(32.dp)
                    ) {
                      Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                      onClick = { repository.deleteCruActivityRecord(r.id) },
                      modifier = Modifier.size(32.dp)
                    ) {
                      Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (records.isNotEmpty()) {
            OutlinedButton(
              onClick = {
                val intent = ExportUtils.exportCruActivityCsvFile(context, records)
                if (intent != null) context.startActivity(Intent.createChooser(intent, "Export CSV"))
                else Toast.makeText(context, "Failed to create CSV", Toast.LENGTH_SHORT).show()
              }
            ) {
              Text("Export CSV")
            }
          }
          Button(onClick = { showHistoryDialog = false }) {
            Text("Close")
          }
        }
      }
    )
  }
}
