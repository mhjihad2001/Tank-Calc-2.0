package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.engine.BackupManager
import com.example.engine.Calculators
import com.example.engine.CsvParseResult
import com.example.engine.CsvParser
import com.example.engine.CsvTableType
import com.example.engine.ExportUtils
import com.example.engine.RestorePreview
import com.example.ui.components.AppScreen
import com.example.ui.components.AuthSession
import com.example.ui.components.CloudImportDialog
import com.example.ui.components.CloudImportMode
import com.example.ui.components.PasswordDialog
import com.example.ui.components.ProductBadge
import com.example.ui.components.VerifiedBackupRestoreFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TanksScreen(
  repository: TankRepository,
  onBack: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val tanks by repository.tanks.collectAsState()
  val products by repository.products.collectAsState()
  val settings by repository.settings.collectAsState()

  var showAddDialog by remember { mutableStateOf(false) }
  var showProductManager by remember { mutableStateOf(false) }
  var showWorkbookImportDialog by remember { mutableStateOf(false) }
  var showCsvImportDialog by remember { mutableStateOf(false) }
  var editingTank by remember { mutableStateOf<Tank?>(null) }
  var calibrationEditorTankName by remember { mutableStateOf<String?>(null) }
  var showCalibrationInspector by remember { mutableStateOf(false) }
  var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
  var showPasswordDialog by remember { mutableStateOf(false) }
  var showCloudBackupDialog by remember { mutableStateOf(false) }
  var cloudPendingJson by remember { mutableStateOf<String?>(null) }
  var cloudRestorePreview by remember { mutableStateOf<RestorePreview?>(null) }
  var cloudSourceDesc by remember { mutableStateOf("") }

  if (calibrationEditorTankName != null || showCalibrationInspector) {
    BackHandler {
      calibrationEditorTankName = null
      showCalibrationInspector = false
    }
    CalibrationInspectorScreen(
      repository = repository,
      initialTankName = calibrationEditorTankName,
      onBack = {
        calibrationEditorTankName = null
        showCalibrationInspector = false
      }
    )
    return
  }

  fun requireAuth(action: () -> Unit) {
    if (AuthSession.isUnlocked()) {
      action()
    } else {
      pendingAction = action
      showPasswordDialog = true
    }
  }

  AppScreen(
    title = "Tank Master",
    subtitle = "${tanks.size} tanks • ${products.size} products",
    onBack = onBack,
    actions = {
      // Direct Google Drive / Cloud Calibration Backup Sync
      IconButton(onClick = { showCloudBackupDialog = true }) {
        Icon(Icons.Default.CloudDownload, contentDescription = "Import from Google Drive / Cloud")
      }
      // Single primary Add Tank action (Products / Charts / CSV / Excel live in the clean action bar below)
      IconButton(onClick = {
        requireAuth { showAddDialog = true }
      }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Add, contentDescription = "Add Tank")
          Icon(Icons.Default.Lock, contentDescription = "Protected", modifier = Modifier.size(10.dp))
        }
      }
    }
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
    ) {
      // Action Row for direct access
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        OutlinedButton(
          onClick = { showProductManager = true },
          modifier = Modifier.weight(1.1f),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
          Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Products", style = MaterialTheme.typography.labelMedium)
        }

        OutlinedButton(
          onClick = { showCalibrationInspector = true },
          modifier = Modifier.weight(1.1f),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
          Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Charts", style = MaterialTheme.typography.labelMedium)
        }

        OutlinedButton(
          onClick = { requireAuth { showCsvImportDialog = true } },
          modifier = Modifier.weight(0.9f),
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
        ) {
          Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("CSV", style = MaterialTheme.typography.labelMedium)
          Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(10.dp))
        }

        OutlinedButton(
          onClick = { requireAuth { showWorkbookImportDialog = true } },
          modifier = Modifier.weight(0.9f),
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
        ) {
          Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Excel", style = MaterialTheme.typography.labelMedium)
          Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(10.dp))
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        "Configured Refinery Tanks (${tanks.size})",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
      )
      Spacer(modifier = Modifier.height(10.dp))

      if (tanks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("No tanks registered. Tap '+' to add a tank or import calibration workbook / CSV.")
        }
      } else {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxSize()
        ) {
          items(tanks) { tank ->
            val product = when (tank.type) {
              TankType.BLENDING -> tank.blendedProductId?.let { pid -> products.find { it.id == pid } }
              TankType.PRODUCT -> tank.productId?.let { pid -> products.find { it.id == pid } } ?: products.find { it.tankIds.contains(tank.id) }
              else -> null
            }
            TankCard(
              tank = tank,
              product = product,
              onEdit = {
                requireAuth { editingTank = tank }
              },
              onDelete = {
                requireAuth {
                  repository.deleteTank(tank.id)
                  Toast.makeText(context, "Tank ${tank.name} deleted", Toast.LENGTH_SHORT).show()
                }
              },
              onEditCalibration = {
                calibrationEditorTankName = tank.name
              }
            )
          }
          item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              "Developed by Meherab Hossain Jihad",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier.fillMaxWidth(),
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
          }
        }
      }
    }

    if (showProductManager) {
      ProductManagerDialog(
        repository = repository,
        onDismiss = { showProductManager = false },
        requireAuth = { requireAuth(it) }
      )
    }

    if (showPasswordDialog) {
      PasswordDialog(
        storedHash = settings.passwordHash,
        onDismiss = { showPasswordDialog = false; pendingAction = null },
        onSuccess = {
          showPasswordDialog = false
          pendingAction?.invoke()
          pendingAction = null
        }
      )
    }

    if (showAddDialog || editingTank != null) {
      TankEditorDialog(
        tank = editingTank,
        products = products,
        onDismiss = {
          showAddDialog = false
          editingTank = null
        },
        onSave = { tank, _ ->
          repository.addOrUpdateTank(tank, logAction = true)
          showAddDialog = false
          editingTank = null
          Toast.makeText(context, "Tank ${tank.name} saved successfully", Toast.LENGTH_SHORT).show()
        }
      )
    }

    if (showWorkbookImportDialog) {
      SingleWorkbookImportDialog(
        onDismiss = { showWorkbookImportDialog = false },
        onImportSuccess = { newTanks ->
          newTanks.forEach { repository.addOrUpdateTank(it) }
          showWorkbookImportDialog = false
          Toast.makeText(context, "Workbook calibration imported successfully!", Toast.LENGTH_LONG).show()
        }
      )
    }

    if (showCsvImportDialog) {
      DirectCsvImportDialog(
        repository = repository,
        onDismiss = { showCsvImportDialog = false },
        onImportSuccess = { newTanks ->
          newTanks.forEach { repository.addOrUpdateTank(it) }
          showCsvImportDialog = false
          Toast.makeText(context, "CSV calibration imported successfully!", Toast.LENGTH_LONG).show()
        }
      )
    }

    if (showCloudBackupDialog) {
      CloudImportDialog(
        repository = repository,
        mode = CloudImportMode.BACKUP_JSON,
        onDismiss = { showCloudBackupDialog = false },
        onBackupDownloaded = { json, preview, _, provider ->
          showCloudBackupDialog = false
          cloudPendingJson = json
          cloudRestorePreview = preview
          cloudSourceDesc = "Source: ${provider.displayName} direct download"
        }
      )
    }

    VerifiedBackupRestoreFlow(
      repository = repository,
      pendingJson = cloudPendingJson,
      preview = cloudRestorePreview,
      sourceDescription = cloudSourceDesc,
      requirePassword = true,
      onDismissAll = {
        cloudPendingJson = null
        cloudRestorePreview = null
      }
    )
  }
}

@Composable
fun TankCard(
  tank: Tank,
  product: Product? = null,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onEditCalibration: () -> Unit
) {
  Card(
    elevation = CardDefaults.cardElevation(2.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(
            imageVector = when (tank.type) {
              TankType.FEED -> Icons.Default.WaterDrop
              TankType.PRODUCT -> Icons.Default.LocalGasStation
              TankType.BLENDING -> Icons.Default.Science
              TankType.N2_STORAGE -> Icons.Default.Cloud
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
          )
          Text(tank.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
          if (tank.type == TankType.BLENDING && product != null) {
            ProductBadge(name = "Blended: ${product.name}", shortCode = product.shortCode, colorHex = product.colorHex)
          } else if (product != null) {
            ProductBadge(name = product.name, shortCode = product.shortCode, colorHex = product.colorHex)
          }
        }
        Row {
          IconButton(onClick = onEdit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Edit, contentDescription = "Edit")
              Icon(Icons.Default.Lock, contentDescription = "Locked", modifier = Modifier.size(10.dp))
            }
          }
          IconButton(onClick = onDelete) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
              Icon(Icons.Default.Lock, contentDescription = "Locked", modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.error)
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
      Divider()
      Spacer(modifier = Modifier.height(8.dp))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Type: ${tank.type}", style = MaterialTheme.typography.bodyMedium)
        Text("Shape: ${tank.shape}", style = MaterialTheme.typography.bodyMedium)
        Text("Max Vol: ${tank.maxVolumeLiters.toInt()} L", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
      }
      Spacer(modifier = Modifier.height(4.dp))
      val isDirectMm = remember(tank) { Calculators.isDirectPerMmTank(tank) }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Dip Rows: ${tank.dipTable.size}", style = MaterialTheme.typography.bodySmall)
        Text(
          text = if (isDirectMm) "Direct 1mm (No Frac)" else "Fraction Rows: ${tank.fractionTable.size}",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = if (isDirectMm) FontWeight.SemiBold else FontWeight.Normal,
          color = if (isDirectMm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text("N2 Rows: ${tank.n2Table.size}", style = MaterialTheme.typography.bodySmall)
      }
      Spacer(modifier = Modifier.height(4.dp))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Report: ${if (tank.includeInProductionReport) "Included" else "Excluded"}", style = MaterialTheme.typography.bodySmall, color = if (tank.includeInProductionReport) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Mode: ${tank.defaultInputMode.name}", style = MaterialTheme.typography.bodySmall)
        Text("Coeff: ${tank.coefficientSource.name.take(6)}", style = MaterialTheme.typography.bodySmall)
      }
      Spacer(modifier = Modifier.height(10.dp))
      OutlinedButton(
        onClick = onEditCalibration,
        modifier = Modifier.fillMaxWidth()
      ) {
        Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          when {
            tank.type == TankType.N2_STORAGE -> "Edit N2 Calibration Chart"
            isDirectMm -> "Edit Direct 1mm Calibration Chart"
            else -> "Edit Calibration Chart & Fractions"
          }
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TankEditorDialog(
  tank: Tank?,
  products: List<Product>,
  onDismiss: () -> Unit,
  onSave: (Tank, String?) -> Unit
) {
  var name by remember { mutableStateOf(tank?.name ?: "") }
  var type by remember { mutableStateOf(tank?.type ?: TankType.PRODUCT) }
  var shape by remember { mutableStateOf(tank?.shape ?: TankShape.VERTICAL) }
  var maxVol by remember { mutableStateOf(tank?.maxVolumeLiters?.toString() ?: "100000") }
  var selectedProductId by remember { mutableStateOf(tank?.productId ?: tank?.blendedProductId) }
  var pendingMoveProduct by remember { mutableStateOf<Product?>(null) }

  var includeInReport by remember { mutableStateOf(tank?.includeInProductionReport ?: true) }
  var coeffSource by remember { mutableStateOf(tank?.coefficientSource ?: CoefficientSourceType.VCF30_BY_DENSITY15) }
  var defaultInputMode by remember { mutableStateOf(tank?.defaultInputMode ?: InputMode.DIP) }
  var reportOrderStr by remember { mutableStateOf(tank?.reportOrder?.toString() ?: "0") }
  var dipCalibrationMode by remember { mutableStateOf(tank?.dipCalibrationMode ?: DipCalibrationMode.AUTO) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (tank == null) "Add New Tank" else "Edit Tank") },
    text = {
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
      ) {
        item {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Tank Name (e.g. T-103)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )
        }
        item {
          OutlinedTextField(
            value = maxVol,
            onValueChange = { maxVol = it },
            label = { Text("Max Capacity (Litres)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )
        }
        item {
          Text("Tank Type:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TankType.values().forEach { t ->
              FilterChip(
                selected = type == t,
                onClick = { type = t },
                label = { Text(t.name) }
              )
            }
          }
        }

        if (type == TankType.PRODUCT || type == TankType.BLENDING) {
          item {
            Text("Product:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              FilterChip(
                selected = selectedProductId == null,
                onClick = { selectedProductId = null },
                label = { Text("None (Unassigned)") }
              )
              products.forEach { prod ->
                FilterChip(
                  selected = selectedProductId == prod.id,
                  onClick = {
                    if (selectedProductId != null && selectedProductId != prod.id) {
                      pendingMoveProduct = prod
                    } else {
                      selectedProductId = prod.id
                    }
                  },
                  label = { Text("${prod.name} (${prod.shortCode})") }
                )
              }
            }
          }
        }

        item {
          Text("Tank Shape:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TankShape.values().forEach { s ->
              FilterChip(
                selected = shape == s,
                onClick = { shape = s },
                label = { Text(s.name) }
              )
            }
          }
        }

        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Include in Production Report", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = includeInReport, onCheckedChange = { includeInReport = it })
          }
        }

        item {
          Text("Default Input Mode:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InputMode.values().forEach { mode ->
              FilterChip(
                selected = defaultInputMode == mode,
                onClick = { defaultInputMode = mode },
                label = { Text(mode.name) }
              )
            }
          }
        }

        item {
          Text("Coefficient Source:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CoefficientSourceType.values().forEach { cs ->
              FilterChip(
                selected = coeffSource == cs,
                onClick = { coeffSource = cs },
                label = { Text(cs.name.take(6)) }
              )
            }
          }
        }

        if (type != TankType.N2_STORAGE) {
          item {
            Text("Dip Calibration Mode:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              FilterChip(
                selected = dipCalibrationMode == DipCalibrationMode.AUTO,
                onClick = { dipCalibrationMode = DipCalibrationMode.AUTO },
                label = { Text("Auto Detect") }
              )
              FilterChip(
                selected = dipCalibrationMode == DipCalibrationMode.BASE_10MM_PLUS_FRACTION,
                onClick = { dipCalibrationMode = DipCalibrationMode.BASE_10MM_PLUS_FRACTION },
                label = { Text("10mm + Fraction") }
              )
              FilterChip(
                selected = dipCalibrationMode == DipCalibrationMode.DIRECT_1MM,
                onClick = { dipCalibrationMode = DipCalibrationMode.DIRECT_1MM },
                label = { Text("Direct 1mm (No Frac)") }
              )
            }
            Text(
              "Use 'Direct 1mm' for tanks calibrated per mm (e.g. LPG 512A, 512B, 512C, 512D) where fraction is not applied.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = {
        if (name.isBlank()) return@Button
        val updated = Tank(
          id = tank?.id ?: java.util.UUID.randomUUID().toString(),
          name = name.trim(),
          type = type,
          shape = shape,
          maxVolumeLiters = maxVol.toDoubleOrNull() ?: 100000.0,
          dipTable = tank?.dipTable ?: emptyList(),
          fractionTable = tank?.fractionTable ?: emptyList(),
          n2Table = tank?.n2Table ?: emptyList(),
          productId = if (type == TankType.PRODUCT || type == TankType.BLENDING) selectedProductId else null,
          includeInProductionReport = includeInReport,
          coefficientSource = coeffSource,
          defaultInputMode = defaultInputMode,
          reportOrder = reportOrderStr.toIntOrNull() ?: 0,
          dipCalibrationMode = dipCalibrationMode
        )
        onSave(updated, if (type == TankType.PRODUCT || type == TankType.BLENDING) selectedProductId else null)
      }) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )

  if (pendingMoveProduct != null) {
    val targetProd = pendingMoveProduct!!
    val currentProd = products.find { it.id == selectedProductId }
    AlertDialog(
      onDismissRequest = { pendingMoveProduct = null },
      title = { Text("Move Tank ${name.ifBlank { tank?.name ?: "" }}?") },
      text = {
        Text("This tank is currently linked to '${currentProd?.name ?: "another product"}'. Do you want to move it to '${targetProd.name}'?")
      },
      confirmButton = {
        Button(onClick = {
          selectedProductId = targetProd.id
          pendingMoveProduct = null
        }) {
          Text("Move Tank")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingMoveProduct = null }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
fun SingleWorkbookImportDialog(onDismiss: () -> Unit, onImportSuccess: (List<Tank>) -> Unit) {
  val context = LocalContext.current
  var dipText by remember { mutableStateOf(ExportUtils.generateDipTemplateCsv()) }
  var fracText by remember { mutableStateOf(ExportUtils.generateFractionTemplateCsv()) }
  var n2Text by remember { mutableStateOf(ExportUtils.generateN2TemplateCsv()) }
  var previewMode by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }
  var validationWarnings by remember { mutableStateOf<List<String>>(emptyList()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Import Calibration Workbook (.xlsx)") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 450.dp)) {
        if (!previewMode) {
          Text("Workbook sheets: 'Dip', 'Fraction', and optional 'N2'.", style = MaterialTheme.typography.bodySmall)
          OutlinedTextField(
            value = dipText,
            onValueChange = { dipText = it },
            label = { Text("Sheet 'Dip' Data") },
            modifier = Modifier.fillMaxWidth().height(100.dp)
          )
          OutlinedTextField(
            value = fracText,
            onValueChange = { fracText = it },
            label = { Text("Sheet 'Fraction' Data (0..9 mm)") },
            modifier = Modifier.fillMaxWidth().height(100.dp)
          )
          OutlinedTextField(
            value = n2Text,
            onValueChange = { n2Text = it },
            label = { Text("Sheet 'N2' Data (Optional)") },
            modifier = Modifier.fillMaxWidth().height(100.dp)
          )
          if (errorMsg != null) {
            Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
          }
          OutlinedButton(onClick = {
            val template = "=== SHEET: Dip ===\n${ExportUtils.generateDipTemplateCsv()}\n\n=== SHEET: Fraction ===\n${ExportUtils.generateFractionTemplateCsv()}\n\n=== SHEET: N2 ===\n${ExportUtils.generateN2TemplateCsv()}"
            ExportUtils.shareText(context, template, "Workbook Templates")
          }) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Download / Share Workbook Template")
          }
        } else {
          Text("Preview & Validation Summary:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          if (validationWarnings.isNotEmpty()) {
            validationWarnings.forEach { w ->
              Text("• $w", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
          } else {
            Text("All validation checks passed successfully!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
          }
          Text("Ready to confirm import and create/update tanks.")
        }
      }
    },
    confirmButton = {
      if (!previewMode) {
        Button(onClick = {
          try {
            val warnings = mutableListOf<String>()
            val dipRes = CsvParser.parseCsvContent(dipText, CsvTableType.DIP)
            if (dipRes.errors.isNotEmpty()) {
              errorMsg = dipRes.errors.first()
              return@Button
            }
            warnings.addAll(dipRes.warnings)

            val fracRes = CsvParser.parseCsvContent(fracText, CsvTableType.FRACTION)
            if (fracRes.errors.isNotEmpty()) {
              warnings.add("Fraction: ${fracRes.errors.first()}")
            }
            warnings.addAll(fracRes.warnings)

            // Check tanks in Dip vs Fraction (skip warning for direct per-1mm calibrated tanks like LPG 512A-D)
            for (t in dipRes.tankNames) {
              if (!fracRes.tankNames.contains(t)) {
                val previewTank = Tank(
                  name = t,
                  type = TankType.PRODUCT,
                  dipTable = dipRes.dipRows,
                  fractionTable = emptyList()
                )
                if (!Calculators.isDirectPerMmTank(previewTank)) {
                  warnings.add("Tank '$t' in Dip sheet was not found in Fraction sheet.")
                }
              }
            }

            validationWarnings = warnings
            previewMode = true
          } catch (e: Exception) {
            errorMsg = "Parsing error: ${e.localizedMessage}"
          }
        }) {
          Text("Preview & Validate")
        }
      } else {
        Button(onClick = {
          try {
            val dipRes = CsvParser.parseCsvContent(dipText, CsvTableType.DIP)
            val fracRes = CsvParser.parseCsvContent(fracText, CsvTableType.FRACTION)
            val n2Res = if (n2Text.isNotBlank()) CsvParser.parseCsvContent(n2Text, CsvTableType.N2) else null

            val allNames = (dipRes.tankNames + (n2Res?.tankNames ?: emptyList())).distinct()
            val newTanks = allNames.map { tName ->
              val isN2 = (n2Res?.tankNames?.contains(tName) == true) && !dipRes.tankNames.contains(tName)
              Tank(
                name = tName,
                type = if (isN2) TankType.N2_STORAGE else TankType.PRODUCT,
                dipTable = dipRes.dipRows,
                fractionTable = fracRes.fractionRows,
                n2Table = n2Res?.n2Rows ?: emptyList(),
                maxVolumeLiters = dipRes.dipRows.maxOfOrNull { it.volumes[tName] ?: 0.0 } ?: 100000.0
              )
            }
            onImportSuccess(newTanks)
          } catch (e: Exception) {
            errorMsg = "Import failed: ${e.localizedMessage}"
            previewMode = false
          }
        }) {
          Text("Confirm Import")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
fun DirectCsvImportDialog(
  repository: TankRepository,
  onDismiss: () -> Unit,
  onImportSuccess: (List<Tank>) -> Unit
) {
  val context = LocalContext.current
  var selectedTab by remember { mutableStateOf(0) } // 0: Dip, 1: Fraction, 2: N2

  var dipCsv by remember { mutableStateOf(ExportUtils.generateDipTemplateCsv()) }
  var fracCsv by remember { mutableStateOf(ExportUtils.generateFractionTemplateCsv()) }
  var n2Csv by remember { mutableStateOf(ExportUtils.generateN2TemplateCsv()) }

  var previewMode by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }
  var previewResults by remember { mutableStateOf<List<CsvParseResult>>(emptyList()) }
  var showCloudCsvDialog by remember { mutableStateOf(false) }

  if (showCloudCsvDialog) {
    CloudImportDialog(
      repository = repository,
      mode = CloudImportMode.CSV_TABLE,
      onDismiss = { showCloudCsvDialog = false },
      onCsvDownloaded = { csvText, provider ->
        when (selectedTab) {
          0 -> dipCsv = csvText
          1 -> fracCsv = csvText
          2 -> n2Csv = csvText
        }
        errorMsg = null
        showCloudCsvDialog = false
        Toast.makeText(
          context,
          "Loaded ${if (selectedTab == 0) "Dip" else if (selectedTab == 1) "Fraction" else "N2"} CSV from ${provider.displayName}!",
          Toast.LENGTH_SHORT
        ).show()
      }
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Direct CSV Import for Calibration Data") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 480.dp)) {
        if (!previewMode) {
          Text("Import CSV tables directly from Google Sheets / Drive links or paste below.", style = MaterialTheme.typography.bodySmall)

          TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Dip CSV") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Fraction CSV") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("N2 CSV") })
          }

          OutlinedButton(
            onClick = { showCloudCsvDialog = true },
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Fetch ${if (selectedTab == 0) "Dip" else if (selectedTab == 1) "Fraction" else "N2"} CSV from Google Sheets / Drive")
          }

          when (selectedTab) {
            0 -> {
              OutlinedTextField(
                value = dipCsv,
                onValueChange = { dipCsv = it },
                label = { Text("Dip Table CSV (Column A = Dip mm)") },
                modifier = Modifier.fillMaxWidth().height(140.dp)
              )
              Button(
                onClick = { ExportUtils.shareText(context, ExportUtils.generateDipTemplateCsv(), "Dip_Template.csv") },
                modifier = Modifier.fillMaxWidth()
              ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download / Share Dip CSV Template")
              }
            }
            1 -> {
              OutlinedTextField(
                value = fracCsv,
                onValueChange = { fracCsv = it },
                label = { Text("Fraction Table CSV (Column A = mm 0..9)") },
                modifier = Modifier.fillMaxWidth().height(140.dp)
              )
              Button(
                onClick = { ExportUtils.shareText(context, ExportUtils.generateFractionTemplateCsv(), "Fraction_Template.csv") },
                modifier = Modifier.fillMaxWidth()
              ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download / Share Fraction CSV Template")
              }
            }
            2 -> {
              OutlinedTextField(
                value = n2Csv,
                onValueChange = { n2Csv = it },
                label = { Text("N2 Table CSV (Column A = Water height mm)") },
                modifier = Modifier.fillMaxWidth().height(140.dp)
              )
              Button(
                onClick = { ExportUtils.shareText(context, ExportUtils.generateN2TemplateCsv(), "N2_Template.csv") },
                modifier = Modifier.fillMaxWidth()
              ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download / Share N2 CSV Template")
              }
            }
          }

          if (errorMsg != null) {
            Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
          }
        } else {
          Text("CSV Parse & Validation Summary:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          LazyColumn(modifier = Modifier.heightIn(max = 250.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(previewResults) { res ->
              Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text("Table: ${res.detectedType} (${res.rowCount} rows)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                  Text("Tanks found: ${res.tankNames.joinToString()}", style = MaterialTheme.typography.bodySmall)
                  if (res.warnings.isNotEmpty()) {
                    for (w in res.warnings) {
                      Text("⚠️ $w", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                  }
                }
              }
            }
          }
          Text("Confirming will merge calibration tables and update existing tanks safely.", style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      if (!previewMode) {
        Button(onClick = {
          val results = mutableListOf<CsvParseResult>()
          if (dipCsv.isNotBlank()) {
            val res = CsvParser.parseCsvContent(dipCsv, CsvTableType.DIP)
            if (res.errors.isNotEmpty()) {
              errorMsg = "Dip: ${res.errors.first()}"
              return@Button
            }
            results.add(res)
          }
          if (fracCsv.isNotBlank()) {
            val res = CsvParser.parseCsvContent(fracCsv, CsvTableType.FRACTION)
            if (res.errors.isNotEmpty()) {
              errorMsg = "Fraction: ${res.errors.first()}"
              return@Button
            }
            results.add(res)
          }
          if (n2Csv.isNotBlank()) {
            val res = CsvParser.parseCsvContent(n2Csv, CsvTableType.N2)
            if (res.errors.isNotEmpty()) {
              errorMsg = "N2: ${res.errors.first()}"
              return@Button
            }
            results.add(res)
          }

          if (results.isEmpty()) {
            errorMsg = "Please enter or paste at least one CSV table."
            return@Button
          }

          previewResults = results
          previewMode = true
        }) {
          Text("Validate & Preview")
        }
      } else {
        Button(onClick = {
          try {
            val dipRes = previewResults.find { it.detectedType == CsvTableType.DIP }
            val fracRes = previewResults.find { it.detectedType == CsvTableType.FRACTION }
            val n2Res = previewResults.find { it.detectedType == CsvTableType.N2 }

            val dipTanks = dipRes?.tankNames ?: emptyList()
            val n2Tanks = n2Res?.tankNames ?: emptyList()
            val allNames = (dipTanks + n2Tanks).distinct()

            val newTanks = allNames.map { tName ->
              val isN2 = n2Tanks.contains(tName) && !dipTanks.contains(tName)
              Tank(
                name = tName,
                type = if (isN2) TankType.N2_STORAGE else TankType.PRODUCT,
                dipTable = dipRes?.dipRows ?: emptyList(),
                fractionTable = fracRes?.fractionRows ?: emptyList(),
                n2Table = n2Res?.n2Rows ?: emptyList(),
                maxVolumeLiters = dipRes?.dipRows?.maxOfOrNull { it.volumes[tName] ?: 0.0 } ?: 100000.0
              )
            }
            onImportSuccess(newTanks)
          } catch (e: Exception) {
            errorMsg = "Import confirmation error: ${e.localizedMessage}"
            previewMode = false
          }
        }) {
          Text("Confirm Import")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}
