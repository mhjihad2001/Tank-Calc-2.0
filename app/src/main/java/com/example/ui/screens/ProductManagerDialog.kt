package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.components.ProductBadge
import com.example.ui.components.parseHexColor
import java.util.UUID

val PRESET_PRODUCT_COLORS = listOf(
  "#2E7D32", // Green
  "#0288D1", // Sky Blue
  "#E65100", // Deep Orange
  "#6A1B9A", // Purple
  "#00897B", // Teal
  "#F57C00", // Amber
  "#C2185B", // Pink/Crimson
  "#3949AB"  // Indigo
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductManagerDialog(
  repository: TankRepository,
  onDismiss: () -> Unit,
  requireAuth: (() -> Unit) -> Unit
) {
  val context = LocalContext.current
  val products by repository.products.collectAsState()
  val tanks by repository.tanks.collectAsState()

  var editingProduct by remember { mutableStateOf<Product?>(null) }
  var isAddingNew by remember { mutableStateOf(false) }
  var productToDelete by remember { mutableStateOf<Product?>(null) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.9f)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            "Product Master List",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            "Configure refinery products & link tanks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Button(
            onClick = {
              requireAuth {
                isAddingNew = true
                editingProduct = Product(
                  name = "",
                  shortCode = "",
                  category = ProductCategory.PRODUCT,
                  defaultDensity15 = 750.0,
                  colorHex = PRESET_PRODUCT_COLORS.first()
                )
              }
            },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Product")
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }
      }

      if (products.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("No products configured. Tap 'Add Product' to create one.")
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(products) { prod ->
            val linkedTanks = tanks.filter { it.productId == prod.id || prod.tankIds.contains(it.id) }
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductBadge(
                      name = prod.name,
                      shortCode = prod.shortCode,
                      colorHex = prod.colorHex
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AssistChip(
                      onClick = {},
                      label = { Text(if (prod.category == ProductCategory.PRODUCT) "Product" else "Intermediate") },
                      modifier = Modifier.height(28.dp)
                    )
                  }
                  Row {
                    IconButton(
                      onClick = {
                        requireAuth {
                          isAddingNew = false
                          editingProduct = prod
                        }
                      }
                    ) {
                      Icon(Icons.Default.Edit, contentDescription = "Edit Product", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                      onClick = {
                        requireAuth {
                          productToDelete = prod
                        }
                      }
                    ) {
                      Icon(Icons.Default.Delete, contentDescription = "Delete Product", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    "Default Density @15°C: ${prod.defaultDensity15?.let { "$it kg/m³" } ?: "Not set"}",
                    style = MaterialTheme.typography.bodySmall
                  )
                  Text(
                    if (prod.isActive) "Active" else "Inactive",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (prod.isActive) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                  )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                // Linked tanks
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = if (linkedTanks.isNotEmpty()) {
                      "Linked Tanks: " + linkedTanks.joinToString(", ") { it.name }
                    } else {
                      "No tanks linked (Tap edit to assign product tanks)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (linkedTanks.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = if (linkedTanks.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  // Edit or Add Product Dialog
  if (editingProduct != null) {
    ProductEditorDialog(
      initialProduct = editingProduct!!,
      allTanks = tanks.filter { it.type == TankType.PRODUCT },
      allProducts = products,
      onDismiss = { editingProduct = null },
      onSave = { updatedProduct ->
        repository.addOrUpdateProduct(updatedProduct, logAction = true)
        editingProduct = null
        Toast.makeText(context, "Product '${updatedProduct.name}' saved!", Toast.LENGTH_SHORT).show()
      }
    )
  }

  // Delete Confirmation Dialog
  if (productToDelete != null) {
    val prod = productToDelete!!
    val count = tanks.count { it.productId == prod.id || prod.tankIds.contains(it.id) }
    AlertDialog(
      onDismissRequest = { productToDelete = null },
      title = { Text("Delete Product '${prod.name}'?") },
      text = {
        Text(
          if (count > 0) {
            "Deleting this product will unlink $count assigned tank(s). Are you sure you want to proceed?"
          } else {
            "Are you sure you want to delete this product?"
          }
        )
      },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          onClick = {
            repository.deleteProduct(prod.id, logAction = true)
            productToDelete = null
            Toast.makeText(context, "Product '${prod.name}' deleted", Toast.LENGTH_SHORT).show()
          }
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { productToDelete = null }) {
          Text("Cancel")
        }
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditorDialog(
  initialProduct: Product,
  allTanks: List<Tank>,
  allProducts: List<Product>,
  onDismiss: () -> Unit,
  onSave: (Product) -> Unit
) {
  var name by remember { mutableStateOf(initialProduct.name) }
  var shortCode by remember { mutableStateOf(initialProduct.shortCode) }
  var category by remember { mutableStateOf(initialProduct.category) }
  var densityStr by remember { mutableStateOf(initialProduct.defaultDensity15?.toString() ?: "") }
  var colorHex by remember { mutableStateOf(initialProduct.colorHex) }
  var isActive by remember { mutableStateOf(initialProduct.isActive) }

  var erpReportGroup by remember { mutableStateOf(initialProduct.erpReportGroup) }
  val reportGroups = listOf("LN", "Reformate", "Kerosene/Jet A-1", "JBO", "MTT", "Diesel", "MS", "Octane", "DSN", "LPG", "Other")
  var groupExpanded by remember { mutableStateOf(false) }

  // Initial set of tank IDs linked to this product
  var selectedTankIds by remember {
    mutableStateOf(
      (initialProduct.tankIds + allTanks.filter { it.productId == initialProduct.id }.map { it.id }).toSet()
    )
  }

  // Move confirmation state
  var pendingMoveTank by remember { mutableStateOf<Pair<Tank, Product>?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        if (initialProduct.name.isBlank()) "New Product" else "Edit Product (${initialProduct.name})",
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        item {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Product Name *") },
            placeholder = { Text("e.g. Reformate, HOBC") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )
        }

        item {
          OutlinedTextField(
            value = shortCode,
            onValueChange = { shortCode = it.uppercase().take(8) },
            label = { Text("Short Code *") },
            placeholder = { Text("e.g. REF, HOBC, LN") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )
        }

        item {
          ExposedDropdownMenuBox(
            expanded = groupExpanded,
            onExpandedChange = { groupExpanded = it },
            modifier = Modifier.fillMaxWidth()
          ) {
            OutlinedTextField(
              value = erpReportGroup,
              onValueChange = {},
              readOnly = true,
              label = { Text("ERP Row / Report Group") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) },
              modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
              expanded = groupExpanded,
              onDismissRequest = { groupExpanded = false }
            ) {
              reportGroups.forEach { grp ->
                DropdownMenuItem(
                  text = { Text(grp) },
                  onClick = {
                    erpReportGroup = grp
                    groupExpanded = false
                  }
                )
              }
            }
          }
        }

        item {
          Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
              selected = category == ProductCategory.PRODUCT,
              onClick = { category = ProductCategory.PRODUCT },
              shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
              Text("Product")
            }
            SegmentedButton(
              selected = category == ProductCategory.INTERMEDIATE,
              onClick = { category = ProductCategory.INTERMEDIATE },
              shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
              Text("Intermediate")
            }
          }
        }

        item {
          OutlinedTextField(
            value = densityStr,
            onValueChange = { densityStr = it },
            label = { Text("Default Density @15°C (kg/m³) [Optional]") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )
        }

        item {
          Text("Color Tag", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            PRESET_PRODUCT_COLORS.forEach { hex ->
              val isSelected = colorHex.equals(hex, ignoreCase = true)
              val color = parseHexColor(hex)
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(color)
                  .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    shape = CircleShape
                  )
                  .clickable { colorHex = hex }
              ) {
                if (isSelected) {
                  Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                      .size(18.dp)
                      .align(Alignment.Center)
                  )
                }
              }
            }
          }
        }

        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Active in Plant", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = isActive, onCheckedChange = { isActive = it })
          }
        }

        item {
          HorizontalDivider()
          Text(
            "Link Product Tanks (Multi-Select)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            "Each tank can only be linked to one product. Linking an already linked tank will move it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        if (allTanks.isEmpty()) {
          item {
            Text("No product-type tanks available.", style = MaterialTheme.typography.bodySmall)
          }
        } else {
          items(allTanks) { tank ->
            val isChecked = selectedTankIds.contains(tank.id)
            val currentOtherProduct = allProducts.find { it.id != initialProduct.id && (it.id == tank.productId || it.tankIds.contains(tank.id)) }

            Card(
              colors = CardDefaults.cardColors(
                containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
              ),
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  if (isChecked) {
                    selectedTankIds = selectedTankIds - tank.id
                  } else {
                    if (currentOtherProduct != null) {
                      pendingMoveTank = Pair(tank, currentOtherProduct)
                    } else {
                      selectedTankIds = selectedTankIds + tank.id
                    }
                  }
                }
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text(tank.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                  if (currentOtherProduct != null) {
                    Text(
                      "Currently linked to: ${currentOtherProduct.name}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.error
                    )
                  } else {
                    Text("Unassigned", style = MaterialTheme.typography.bodySmall)
                  }
                }
                Checkbox(
                  checked = isChecked,
                  onCheckedChange = {
                    if (isChecked) {
                      selectedTankIds = selectedTankIds - tank.id
                    } else {
                      if (currentOtherProduct != null) {
                        pendingMoveTank = Pair(tank, currentOtherProduct)
                      } else {
                        selectedTankIds = selectedTankIds + tank.id
                      }
                    }
                  }
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (name.isBlank()) return@Button
          val parsedDensity = densityStr.toDoubleOrNull()
          val updated = initialProduct.copy(
            name = name.trim(),
            shortCode = if (shortCode.isBlank()) name.take(4).uppercase() else shortCode.trim(),
            category = category,
            defaultDensity15 = parsedDensity,
            colorHex = colorHex,
            isActive = isActive,
            tankIds = selectedTankIds.toList(),
            erpReportGroup = erpReportGroup
          )
          onSave(updated)
        },
        enabled = name.isNotBlank()
      ) {
        Text("Save Product")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )

  // Move Tank Confirmation Dialog
  if (pendingMoveTank != null) {
    val (tankToMove, otherProd) = pendingMoveTank!!
    AlertDialog(
      onDismissRequest = { pendingMoveTank = null },
      title = { Text("Move Tank ${tankToMove.name}?") },
      text = {
        Text("Tank '${tankToMove.name}' is currently linked to '${otherProd.name}'. Do you want to move it to '$name'?")
      },
      confirmButton = {
        Button(
          onClick = {
            selectedTankIds = selectedTankIds + tankToMove.id
            pendingMoveTank = null
          }
        ) {
          Text("Move Tank")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingMoveTank = null }) {
          Text("Cancel")
        }
      }
    )
  }
}
