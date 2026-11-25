package com.example.ui.screens.blending

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BlendProgram
import com.example.data.BlendStage
import com.example.data.Tank
import com.example.ui.components.TankPickerSheet
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlendProgramEditorDialog(
  initialProgram: BlendProgram?,
  allTanks: List<Tank>,
  onDismiss: () -> Unit,
  onSave: (BlendProgram) -> Unit,
  onDelete: ((String) -> Unit)? = null
) {
  var programName by remember { mutableStateOf(initialProgram?.name ?: "New Blending Program") }
  var selectedTankName by remember { mutableStateOf(initialProgram?.tankName ?: allTanks.firstOrNull()?.name ?: "T-510") }
  var stages by remember {
    mutableStateOf(
      initialProgram?.stages ?: listOf(
        BlendStage(stageName = "Initial", componentName = "", orderIndex = 0),
        BlendStage(stageName = "After Reformate", componentName = "Reformate", orderIndex = 1),
        BlendStage(stageName = "After Light Naphtha", componentName = "Light Naphtha", orderIndex = 2)
      )
    )
  }

  var showTankPicker by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (initialProgram != null) "Edit Blend Program" else "New Blend Program",
          fontWeight = FontWeight.Bold
        )
        if (initialProgram != null && onDelete != null) {
          IconButton(onClick = { showDeleteConfirm = true }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete Program", tint = MaterialTheme.colorScheme.error)
          }
        }
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        OutlinedTextField(
          value = programName,
          onValueChange = { programName = it; errorMessage = null },
          label = { Text("Program Name (e.g. HOBC Blending)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Text("Blending Tank", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        OutlinedCard(
          onClick = { showTankPicker = true },
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = selectedTankName,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
              val matched = allTanks.find { it.name.equals(selectedTankName, ignoreCase = true) }
              Text(
                text = if (matched != null) "${matched.type} | Max ${matched.maxVolumeLiters.toInt()} L" else "Select tank...",
                style = MaterialTheme.typography.bodySmall
              )
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Stages (${stages.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          TextButton(onClick = {
            val newIdx = stages.size
            stages = stages + BlendStage(
              stageName = "Stage ${newIdx + 1}",
              componentName = "Component $newIdx",
              orderIndex = newIdx
            )
          }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Stage")
          }
        }

        if (errorMessage != null) {
          Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          itemsIndexed(stages) { index, stage ->
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "Stage ${index + 1}${if (index == 0) " (Starting)" else ""}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                  )
                  Row {
                    IconButton(
                      onClick = {
                        if (index > 0) {
                          val mutable = stages.toMutableList()
                          val temp = mutable[index]
                          mutable[index] = mutable[index - 1]
                          mutable[index - 1] = temp
                          stages = mutable.mapIndexed { idx, s -> s.copy(orderIndex = idx) }
                        }
                      },
                      enabled = index > 0,
                      modifier = Modifier.size(32.dp)
                    ) {
                      Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                      onClick = {
                        if (index < stages.size - 1) {
                          val mutable = stages.toMutableList()
                          val temp = mutable[index]
                          mutable[index] = mutable[index + 1]
                          mutable[index + 1] = temp
                          stages = mutable.mapIndexed { idx, s -> s.copy(orderIndex = idx) }
                        }
                      },
                      enabled = index < stages.size - 1,
                      modifier = Modifier.size(32.dp)
                    ) {
                      Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                      onClick = {
                        if (stages.size > 2) {
                          val mutable = stages.toMutableList()
                          mutable.removeAt(index)
                          stages = mutable.mapIndexed { idx, s -> s.copy(orderIndex = idx) }
                        }
                      },
                      enabled = stages.size > 2,
                      modifier = Modifier.size(32.dp)
                    ) {
                      Icon(Icons.Default.Close, contentDescription = "Remove Stage", modifier = Modifier.size(18.dp))
                    }
                  }
                }

                OutlinedTextField(
                  value = stage.stageName,
                  onValueChange = { newName ->
                    stages = stages.mapIndexed { idx, s -> if (idx == index) s.copy(stageName = newName) else s }
                  },
                  label = { Text("Stage Name") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )

                if (index > 0) {
                  OutlinedTextField(
                    value = stage.componentName,
                    onValueChange = { newComp ->
                      stages = stages.mapIndexed { idx, s -> if (idx == index) s.copy(componentName = newComp) else s }
                    },
                    label = { Text("Component Added (e.g. Reformate)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                  )
                } else {
                  Text(
                    text = "Initial Stage: Starting dip before adding any components",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = {
        if (programName.isBlank()) {
          errorMessage = "Program name cannot be empty."
          return@Button
        }
        if (stages.size < 2) {
          errorMessage = "A blend program must have at least 2 stages."
          return@Button
        }
        val updated = BlendProgram(
          id = initialProgram?.id ?: UUID.randomUUID().toString(),
          name = programName.trim(),
          tankName = selectedTankName.trim(),
          stages = stages.mapIndexed { idx, s -> s.copy(orderIndex = idx) },
          isDefault = initialProgram?.isDefault ?: false
        )
        onSave(updated)
      }) {
        Text("Save Program")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )

  if (showTankPicker) {
    TankPickerSheet(
      tanks = allTanks,
      selectedTankNames = setOf(selectedTankName),
      isMultiSelect = false,
      onDismiss = { showTankPicker = false },
      onSingleSelected = { tank ->
        selectedTankName = tank.name
        showTankPicker = false
      }
    )
  }

  if (showDeleteConfirm && initialProgram != null && onDelete != null) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text("Delete Blend Program?") },
      text = { Text("Are you sure you want to delete '${initialProgram.name}'? This action cannot be undone.") },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          onClick = {
            showDeleteConfirm = false
            onDelete(initialProgram.id)
          }
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
      }
    )
  }
}
