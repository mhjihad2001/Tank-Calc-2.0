package com.example.ui.screens.blending

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.engine.UnitUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlendHistorySheet(
  records: List<BlendRecord>,
  allPrograms: List<BlendProgram>,
  allTanks: List<Tank>,
  currentUnit: UnitType,
  settings: AppSettings? = null,
  onDismiss: () -> Unit,
  onOpenRecord: (BlendRecord) -> Unit,
  onDeleteRecord: (String) -> Unit
) {
  var selectedProgramFilter by remember { mutableStateOf("ALL") }
  var selectedTankFilter by remember { mutableStateOf("ALL") }
  var searchQuery by remember { mutableStateOf("") }
  var selectedRecordForReport by remember { mutableStateOf<BlendRecord?>(null) }
  var recordToDelete by remember { mutableStateOf<BlendRecord?>(null) }

  val filteredRecords = records.filter { rec ->
    val matchesProgram = selectedProgramFilter == "ALL" || rec.programName.equals(selectedProgramFilter, ignoreCase = true)
    val matchesTank = selectedTankFilter == "ALL" || rec.tankName.equals(selectedTankFilter, ignoreCase = true)
    val matchesSearch = searchQuery.isBlank() ||
      rec.programName.contains(searchQuery, ignoreCase = true) ||
      rec.tankName.contains(searchQuery, ignoreCase = true) ||
      rec.dateTime.contains(searchQuery, ignoreCase = true) ||
      rec.batchNumber.contains(searchQuery, ignoreCase = true)
    matchesProgram && matchesTank && matchesSearch
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Blend History Log (${filteredRecords.size})", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Search Box
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text("Search by date, batch, or program...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Default.Clear, contentDescription = "Clear")
            }
          }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
      )

      // Filter Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // Program Filter
        var progExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.weight(1f)) {
          OutlinedButton(
            onClick = { progExpanded = true },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = if (selectedProgramFilter == "ALL") "All Programs" else selectedProgramFilter,
              maxLines = 1,
              style = MaterialTheme.typography.labelMedium
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
          }
          DropdownMenu(
            expanded = progExpanded,
            onDismissRequest = { progExpanded = false }
          ) {
            DropdownMenuItem(
              text = { Text("All Programs") },
              onClick = { selectedProgramFilter = "ALL"; progExpanded = false }
            )
            allPrograms.map { it.name }.distinct().forEach { progName ->
              DropdownMenuItem(
                text = { Text(progName) },
                onClick = { selectedProgramFilter = progName; progExpanded = false }
              )
            }
          }
        }

        // Tank Filter
        var tankExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.weight(1f)) {
          OutlinedButton(
            onClick = { tankExpanded = true },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = if (selectedTankFilter == "ALL") "All Tanks" else selectedTankFilter,
              maxLines = 1,
              style = MaterialTheme.typography.labelMedium
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
          }
          DropdownMenu(
            expanded = tankExpanded,
            onDismissRequest = { tankExpanded = false }
          ) {
            DropdownMenuItem(
              text = { Text("All Tanks") },
              onClick = { selectedTankFilter = "ALL"; tankExpanded = false }
            )
            allTanks.map { it.name }.distinct().forEach { tName ->
              DropdownMenuItem(
                text = { Text(tName) },
                onClick = { selectedTankFilter = tName; tankExpanded = false }
              )
            }
          }
        }
      }

      if (filteredRecords.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "No saved blend operations match the filter.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(filteredRecords, key = { it.id }) { rec ->
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column {
                    Text(
                      text = "${rec.programName} (${rec.tankName})",
                      fontWeight = FontWeight.Bold,
                      style = MaterialTheme.typography.titleMedium,
                      color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                      text = "${rec.dateTime} | Mode: ${if (rec.mode == CalculationMode.TEMP_30C) "30 °C" else "Natural"}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  if (rec.batchNumber.isNotBlank()) {
                    Surface(
                      color = MaterialTheme.colorScheme.secondaryContainer,
                      shape = MaterialTheme.shapes.small
                    ) {
                      Text(
                        text = rec.batchNumber,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }
                }

                val finalVol = if (rec.mode == CalculationMode.TEMP_30C && rec.finalVolume30L != null) rec.finalVolume30L else rec.finalVolumeL
                Text(
                  text = "Final Volume: ${UnitUtils.formatVolume(finalVol, currentUnit, settings = settings)}",
                  fontWeight = FontWeight.Bold
                )

                rec.componentTransfers.forEach { comp ->
                  val vol = if (rec.mode == CalculationMode.TEMP_30C && comp.transferred30L != null) comp.transferred30L else comp.transferredL
                  val label = if (comp.componentName.isNotBlank()) comp.componentName else comp.stageName
                  Text(
                    text = "• $label transferred: ${UnitUtils.formatVolume(vol, currentUnit, settings = settings)}",
                    style = MaterialTheme.typography.bodySmall
                  )
                }

                if (rec.remarks.isNotBlank()) {
                  Text(
                    text = "Remarks: ${rec.remarks}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.End,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  TextButton(onClick = { selectedRecordForReport = rec }) {
                    Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Report")
                  }
                  TextButton(onClick = { onOpenRecord(rec) }) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open & Edit")
                  }
                  IconButton(onClick = { recordToDelete = rec }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (selectedRecordForReport != null) {
    BlendReportDialog(
      record = selectedRecordForReport!!,
      currentUnit = currentUnit,
      settings = settings,
      onDismiss = { selectedRecordForReport = null }
    )
  }

  if (recordToDelete != null) {
    AlertDialog(
      onDismissRequest = { recordToDelete = null },
      title = { Text("Delete Blend Record?") },
      text = { Text("Are you sure you want to delete the record for '${recordToDelete!!.programName}' on ${recordToDelete!!.dateTime}?") },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          onClick = {
            onDeleteRecord(recordToDelete!!.id)
            recordToDelete = null
          }
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { recordToDelete = null }) { Text("Cancel") }
      }
    )
  }
}
