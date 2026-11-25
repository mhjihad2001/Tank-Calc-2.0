package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Product
import com.example.data.Tank
import com.example.data.TankType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TankPickerSheet(
  tanks: List<Tank>,
  selectedTankNames: Set<String>,
  products: List<Product> = emptyList(),
  isMultiSelect: Boolean = false,
  onDismiss: () -> Unit,
  onSingleSelected: (Tank) -> Unit = {},
  onMultiSelected: (Set<String>) -> Unit = {}
) {
  var searchQuery by remember { mutableStateOf("") }
  var currentSelection by remember { mutableStateOf(selectedTankNames) }

  val productMap = remember(products) { products.associateBy { it.id } }

  val filteredTanks = tanks.filter {
    it.name.contains(searchQuery, ignoreCase = true) ||
    it.type.name.contains(searchQuery, ignoreCase = true) ||
    (it.productId?.let { pid -> productMap[pid]?.name?.contains(searchQuery, ignoreCase = true) } ?: false)
  }

  val feedTanks = filteredTanks.filter { it.type == TankType.FEED }
  val productTanks = filteredTanks.filter { it.type == TankType.PRODUCT }
  val blendingTanks = filteredTanks.filter { it.type == TankType.BLENDING }
  val n2Tanks = filteredTanks.filter { it.type == TankType.N2_STORAGE }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.85f)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(if (isMultiSelect) "Select Tanks (Multi-Select)" else "Select Tank", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (isMultiSelect) {
          TextButton(onClick = {
            currentSelection = tanks.map { it.name }.toSet()
            onMultiSelected(currentSelection)
          }) { Text("Select All") }
        }
      }

      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        label = { Text("Search tanks by name, type, or product...") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
      )

      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (feedTanks.isNotEmpty()) {
          item {
            TankGroupHeader(title = "Feed Tanks", onSelectAll = if (isMultiSelect) { {
              currentSelection = currentSelection + feedTanks.map { it.name }
              onMultiSelected(currentSelection)
            } } else null)
          }
          items(feedTanks) { tank ->
            TankPickerRow(
              tank = tank,
              product = null,
              isSelected = currentSelection.contains(tank.name),
              isMultiSelect = isMultiSelect
            ) {
              if (isMultiSelect) {
                currentSelection = if (currentSelection.contains(tank.name)) currentSelection - tank.name else currentSelection + tank.name
                onMultiSelected(currentSelection)
              } else {
                onSingleSelected(tank)
                onDismiss()
              }
            }
          }
        }

        if (productTanks.isNotEmpty()) {
          if (products.isNotEmpty()) {
            // Group by product
            val grouped = productTanks.groupBy { it.productId }
            for (prod in products) {
              val pTanks = grouped[prod.id] ?: emptyList()
              if (pTanks.isNotEmpty()) {
                item {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      ProductBadge(name = prod.name, shortCode = prod.shortCode, colorHex = prod.colorHex)
                      Spacer(modifier = Modifier.width(8.dp))
                      Text("(${pTanks.size} tanks)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isMultiSelect) {
                      TextButton(onClick = {
                        currentSelection = currentSelection + pTanks.map { it.name }
                        onMultiSelected(currentSelection)
                      }) { Text("Select All") }
                    }
                  }
                }
                items(pTanks) { tank ->
                  TankPickerRow(
                    tank = tank,
                    product = prod,
                    isSelected = currentSelection.contains(tank.name),
                    isMultiSelect = isMultiSelect
                  ) {
                    if (isMultiSelect) {
                      currentSelection = if (currentSelection.contains(tank.name)) currentSelection - tank.name else currentSelection + tank.name
                      onMultiSelected(currentSelection)
                    } else {
                      onSingleSelected(tank)
                      onDismiss()
                    }
                  }
                }
              }
            }

            // Unassigned product tanks
            val unassigned = grouped[null] ?: emptyList()
            if (unassigned.isNotEmpty()) {
              item {
                TankGroupHeader(title = "Unassigned Product Tanks", onSelectAll = if (isMultiSelect) { {
                  currentSelection = currentSelection + unassigned.map { it.name }
                  onMultiSelected(currentSelection)
                } } else null)
              }
              items(unassigned) { tank ->
                TankPickerRow(
                  tank = tank,
                  product = null,
                  isSelected = currentSelection.contains(tank.name),
                  isMultiSelect = isMultiSelect
                ) {
                  if (isMultiSelect) {
                    currentSelection = if (currentSelection.contains(tank.name)) currentSelection - tank.name else currentSelection + tank.name
                    onMultiSelected(currentSelection)
                  } else {
                    onSingleSelected(tank)
                    onDismiss()
                  }
                }
              }
            }
          } else {
            item {
              TankGroupHeader(title = "Product Tanks", onSelectAll = if (isMultiSelect) { {
                currentSelection = currentSelection + productTanks.map { it.name }
                onMultiSelected(currentSelection)
              } } else null)
            }
            items(productTanks) { tank ->
              TankPickerRow(
                tank = tank,
                product = tank.productId?.let { productMap[it] },
                isSelected = currentSelection.contains(tank.name),
                isMultiSelect = isMultiSelect
              ) {
                if (isMultiSelect) {
                  currentSelection = if (currentSelection.contains(tank.name)) currentSelection - tank.name else currentSelection + tank.name
                  onMultiSelected(currentSelection)
                } else {
                  onSingleSelected(tank)
                  onDismiss()
                }
              }
            }
          }
        }

        if (blendingTanks.isNotEmpty()) {
          item {
            TankGroupHeader(title = "Blending Tanks", onSelectAll = if (isMultiSelect) { {
              currentSelection = currentSelection + blendingTanks.map { it.name }
              onMultiSelected(currentSelection)
            } } else null)
          }
          items(blendingTanks) { tank ->
            val bProd = tank.blendedProductId?.let { productMap[it] }
            TankPickerRow(
              tank = tank,
              product = bProd,
              isSelected = currentSelection.contains(tank.name),
              isMultiSelect = isMultiSelect
            ) {
              if (isMultiSelect) {
                currentSelection = if (currentSelection.contains(tank.name)) currentSelection - tank.name else currentSelection + tank.name
                onMultiSelected(currentSelection)
              } else {
                onSingleSelected(tank)
                onDismiss()
              }
            }
          }
        }

        if (n2Tanks.isNotEmpty()) {
          item {
            TankGroupHeader(title = "Nitrogen (N2) Storage", onSelectAll = if (isMultiSelect) { {
              currentSelection = currentSelection + n2Tanks.map { it.name }
              onMultiSelected(currentSelection)
            } } else null)
          }
          items(n2Tanks) { tank ->
            TankPickerRow(tank = tank, isSelected = currentSelection.contains(tank.name), isMultiSelect = isMultiSelect) {
              if (isMultiSelect) {
                currentSelection = if (currentSelection.contains(tank.name)) currentSelection - tank.name else currentSelection + tank.name
                onMultiSelected(currentSelection)
              } else {
                onSingleSelected(tank)
                onDismiss()
              }
            }
          }
        }
      }

      if (isMultiSelect) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = {
            currentSelection = emptySet()
            onMultiSelected(currentSelection)
          }, modifier = Modifier.weight(1f)) {
            Text("Clear All")
          }
          Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
            Text("Done (${currentSelection.size} selected)")
          }
        }
      }
    }
  }
}

@Composable
fun TankGroupHeader(title: String, onSelectAll: (() -> Unit)? = null) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    if (onSelectAll != null) {
      TextButton(onClick = onSelectAll) { Text("Select Group") }
    }
  }
}

@Composable
fun TankPickerRow(
  tank: Tank,
  product: Product? = null,
  isSelected: Boolean,
  isMultiSelect: Boolean,
  onClick: () -> Unit
) {
  val haptics = com.example.engine.rememberHapticController()
  Card(
    modifier = Modifier.fillMaxWidth().clickable {
      haptics.trigger(com.example.engine.HapticEvent.CELL_SELECT)
      onClick()
    },
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(tank.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          if (product != null) {
            Spacer(modifier = Modifier.width(8.dp))
            ProductBadge(name = product.name, shortCode = product.shortCode, colorHex = product.colorHex)
          }
        }
        Text("Type: ${tank.type} | Shape: ${tank.shape} | Max: ${tank.maxVolumeLiters.toInt()} L", style = MaterialTheme.typography.bodySmall)
      }
      if (isMultiSelect) {
        Checkbox(checked = isSelected, onCheckedChange = { onClick() })
      } else {
        if (isSelected) {
          Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else {
          Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null)
        }
      }
    }
  }
}
