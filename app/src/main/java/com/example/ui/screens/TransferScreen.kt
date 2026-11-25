package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.engine.Calculators
import com.example.engine.ExportUtils
import com.example.engine.UnitQuickSwitchChip
import com.example.engine.UnitUtils
import com.example.engine.VcfEngine
import com.example.ui.components.AppScreen
import com.example.ui.components.NumberInput
import com.example.ui.components.ProductBadge
import com.example.ui.components.TankPickerSheet
import com.example.ui.screens.blending.BlendSection
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
  repository: TankRepository,
  requestedTxType: TransactionType? = null,
  onTxTypeHandled: () -> Unit = {}
) {
  val context = LocalContext.current
  val rawTanks by repository.tanks.collectAsState()
  val tanks = remember(rawTanks) { rawTanks.filter { it.type != TankType.N2_STORAGE } }
  val products by repository.products.collectAsState()
  val transactions by repository.transactions.collectAsState()
  val settings by repository.settings.collectAsState()
  val haptics = com.example.engine.rememberHapticController()

  var selectedTankName by remember(tanks) { mutableStateOf(tanks.firstOrNull()?.name ?: "") }
  var txType by remember { mutableStateOf(TransactionType.RECEIVE) }
  var productName by remember { mutableStateOf("Naphtha") }
  var initialDipStr by remember { mutableStateOf("100.0") }
  var finalDipStr by remember { mutableStateOf("450.0") }
  var calcMode by remember { mutableStateOf(CalculationMode.NORMAL) }

  var densityStr by remember { mutableStateOf("750.0") }
  var initTempStr by remember { mutableStateOf("25.0") }
  var finalTempStr by remember { mutableStateOf("28.0") }

  var showHistory by remember { mutableStateOf(false) }
  var showPicker by remember { mutableStateOf(false) }
  var draftSavedIndicator by remember { mutableStateOf(false) }
  var showClearConfirm by remember { mutableStateOf(false) }
  var showOverflowMenu by remember { mutableStateOf(false) }

  LaunchedEffect(requestedTxType) {
    if (requestedTxType != null) {
      txType = requestedTxType
      showHistory = false
      onTxTypeHandled()
    }
  }

  val selectedTank = tanks.find { it.name == selectedTankName } ?: tanks.firstOrNull()
  val tankProduct = products.find { it.id == selectedTank?.productId }
  val initDip = initialDipStr.toDoubleOrNull() ?: 0.0
  val finalDip = finalDipStr.toDoubleOrNull() ?: 0.0

  // Restore draft
  LaunchedEffect(Unit) {
    val draft = repository.getDraft("transfer_blend")
    if (draft != null) {
      try {
        val json = JSONObject(draft.payloadJson)
        val tank = json.optString("tank", "")
        if (tank.isNotBlank() && tanks.any { it.name.equals(tank, ignoreCase = true) }) {
          selectedTankName = tank
        }
        if (json.has("prodName")) productName = json.getString("prodName")
        if (json.has("initDip")) initialDipStr = json.getString("initDip")
        if (json.has("finalDip")) finalDipStr = json.getString("finalDip")
        if (json.has("density")) densityStr = json.getString("density")
        if (json.has("initTemp")) initTempStr = json.getString("initTemp")
        if (json.has("finalTemp")) finalTempStr = json.getString("finalTemp")
      } catch (_: Exception) {}
    }
  }

  // Auto-save draft (400ms debounce)
  LaunchedEffect(selectedTankName, productName, initialDipStr, finalDipStr, densityStr, initTempStr, finalTempStr, calcMode) {
    delay(400L)
    val payload = JSONObject().apply {
      put("tank", selectedTankName)
      put("prodName", productName)
      put("initDip", initialDipStr)
      put("finalDip", finalDipStr)
      put("density", densityStr)
      put("initTemp", initTempStr)
      put("finalTemp", finalTempStr)
      put("calcMode", calcMode.name)
    }.toString()
    repository.saveDraft("transfer_blend", "default", payload, "$selectedTankName: $initialDipStr -> $finalDipStr mm")
    draftSavedIndicator = true
    delay(1500L)
    draftSavedIndicator = false
  }

  val initRes = selectedTank?.let { Calculators.calculateDipVolume(it, initDip, settings.invalidInputThreshold) }
  val finalRes = selectedTank?.let { Calculators.calculateDipVolume(it, finalDip, settings.invalidInputThreshold) }

  val initVol = initRes?.totalVolume ?: 0.0
  val finalVol = finalRes?.totalVolume ?: 0.0
  val naturalQty = kotlin.math.abs(finalVol - initVol)

  val rho15 = densityStr.toDoubleOrNull() ?: 750.0
  val tInit = initTempStr.toDoubleOrNull() ?: 25.0
  val tFinal = finalTempStr.toDoubleOrNull() ?: 28.0

  val vcfNatRes = VcfEngine.calculateVcf(rho15, (tInit + tFinal) / 2.0, settings.vcfTableType, settings.roundVcfTo4Decimals, settings.roundTempTo025)
  val vcf30Res = VcfEngine.calculateVcf(rho15, 30.0, settings.vcfTableType, settings.roundVcfTo4Decimals, settings.roundTempTo025)

  val vol15 = if (calcMode == CalculationMode.TEMP_30C) naturalQty * vcfNatRes.vcf else 0.0
  val vol30 = if (calcMode == CalculationMode.TEMP_30C && vcf30Res.vcf > 0) naturalQty * vcfNatRes.vcf / vcf30Res.vcf else 0.0

  AppScreen(
    title = if (txType == TransactionType.BLEND) "Product Blending" else "Tank Transfer",
    subtitle = if (draftSavedIndicator) "Draft saved" else null,
    actions = {
      UnitQuickSwitchChip(
        currentUnit = settings.unit,
        onUnitChange = { repository.updateSettings(settings.copy(unit = it)) },
        modifier = Modifier.padding(end = 2.dp)
      )
      Box {
        IconButton(onClick = { showOverflowMenu = true }) {
          Icon(Icons.Default.MoreVert, contentDescription = "More Actions")
        }
        DropdownMenu(
          expanded = showOverflowMenu,
          onDismissRequest = { showOverflowMenu = false }
        ) {
          if (txType != TransactionType.BLEND) {
            DropdownMenuItem(
              text = { Text(if (showHistory) "Back to Calculator" else "Transfer History") },
              leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
              onClick = {
                showOverflowMenu = false
                showHistory = !showHistory
              }
            )
          }
          DropdownMenuItem(
            text = { Text("Clear / Reset Form") },
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
    Column(
      modifier = Modifier.fillMaxSize()
    ) {
      // Transaction Type Selector Bar
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Type:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
          TransactionType.values().forEach { t ->
            FilterChip(
              selected = txType == t,
              onClick = {
                haptics.trigger(com.example.engine.HapticEvent.MODE_SWITCH)
                txType = t
                showHistory = false
              },
              label = {
                Text(
                  when (t) {
                    TransactionType.RECEIVE -> "Receive"
                    TransactionType.DELIVERY -> "Delivery"
                    TransactionType.BLEND -> "Blend"
                  },
                  fontWeight = if (txType == t) FontWeight.Bold else FontWeight.Normal
                )
              },
              leadingIcon = if (t == TransactionType.BLEND) {
                { Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp)) }
              } else null
            )
          }
        }
      }

      if (txType == TransactionType.BLEND) {
        BlendSection(
          repository = repository,
          modifier = Modifier.fillMaxSize()
        )
      } else if (showHistory) {
        LazyColumn(
          modifier = Modifier.fillMaxSize().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Text("Transaction History Log (${transactions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              TextButton(onClick = { showHistory = false }) { Text("Back to Calculator") }
            }
          }
          if (transactions.isEmpty()) {
            item { Text("No recorded transactions yet.") }
          } else {
            items(transactions) { tx ->
              Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${tx.tankName} - ${tx.type}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(tx.dateTime, style = MaterialTheme.typography.bodySmall)
                  }
                  Text("Product: ${tx.productName} | Mode: ${tx.mode}")
                  Text("Quantity: ${UnitUtils.formatVolume(tx.quantity, settings.unit, settings = settings)} (Init Dip: ${tx.initialDip} mm -> Final Dip: ${tx.finalDip} mm)")
                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = {
                      val report = ExportUtils.formatTransactionReport(tx, settings.unit, settings)
                      ExportUtils.copyToClipboard(context, report, "Transaction Summary")
                    }) {
                      Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                  }
                }
              }
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          item {
            Text("Selected Tank", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text(if (selectedTank != null) "${selectedTank.name} (${selectedTank.type})" else "Select Tank...", fontWeight = FontWeight.Bold)
                  if (tankProduct != null) {
                    ProductBadge(name = tankProduct.name, shortCode = tankProduct.shortCode)
                  }
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
              }
            }
          }

          item {
            OutlinedTextField(
              value = productName,
              onValueChange = { productName = it },
              label = { Text("Product Name (e.g. Naphtha, Condensate)") },
              modifier = Modifier.fillMaxWidth()
            )
          }

        item {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberInput(
              value = initialDipStr,
              onValueChange = { initialDipStr = it },
              label = "Initial Dip",
              suffix = "mm",
              modifier = Modifier.weight(1f)
            )
            NumberInput(
              value = finalDipStr,
              onValueChange = { finalDipStr = it },
              label = "Final Dip",
              suffix = "mm",
              modifier = Modifier.weight(1f)
            )
          }
        }

        item {
          Text("Calculation Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalculationMode.values().forEach { m ->
              FilterChip(
                selected = calcMode == m,
                onClick = {
                  haptics.trigger(com.example.engine.HapticEvent.MODE_SWITCH)
                  calcMode = m
                },
                label = { Text(if (m == CalculationMode.NORMAL) "Normal (Natural)" else "30 °C Corrected") }
              )
            }
          }
        }

        if (calcMode == CalculationMode.TEMP_30C) {
          item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Temperature & Density Correction Inputs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                NumberInput(
                  value = densityStr,
                  onValueChange = { densityStr = it },
                  label = "Density @15 °C",
                  suffix = "kg/m³",
                  modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                  NumberInput(
                    value = initTempStr,
                    onValueChange = { initTempStr = it },
                    label = "Initial Temp",
                    suffix = "°C",
                    modifier = Modifier.weight(1f)
                  )
                  NumberInput(
                    value = finalTempStr,
                    onValueChange = { finalTempStr = it },
                    label = "Final Temp",
                    suffix = "°C",
                    modifier = Modifier.weight(1f)
                  )
                }
              }
            }
          }
        }

        item {
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Transfer Calculation Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
              Divider()
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Initial Volume:")
                Text(UnitUtils.formatVolume(initVol, settings.unit, settings = settings), fontWeight = FontWeight.SemiBold)
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Final Volume:")
                Text(UnitUtils.formatVolume(finalVol, settings.unit, settings = settings), fontWeight = FontWeight.SemiBold)
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Natural Transferred Quantity:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(UnitUtils.formatVolume(naturalQty, settings.unit, settings = settings), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
              }

              if (calcMode == CalculationMode.TEMP_30C) {
                Divider()
                Text("VCF Engine Breakdown:", fontWeight = FontWeight.SemiBold)
                Text("VCF Natural: ${vcfNatRes.vcf} (${vcfNatRes.bandName})")
                Text("VCF @30 °C: ${vcf30Res.vcf}")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Transferred Volume @15 °C:", fontWeight = FontWeight.Bold)
                  Text(UnitUtils.formatVolume(vol15, settings.unit, settings = settings), fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Transferred Volume @30 °C:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                  Text(UnitUtils.formatVolume(vol30, settings.unit, settings = settings), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }
              }
            }
          }
        }

        item {
          Button(
            onClick = {
              val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
              val tx = TransactionRecord(
                tankName = selectedTankName,
                type = txType,
                productName = productName,
                dateTime = dateStr,
                initialDip = initDip,
                finalDip = finalDip,
                initialVolume = initVol,
                finalVolume = finalVol,
                quantity = naturalQty,
                mode = calcMode,
                density15 = if (calcMode == CalculationMode.TEMP_30C) rho15 else null,
                initialTemp = if (calcMode == CalculationMode.TEMP_30C) tInit else null,
                finalTemp = if (calcMode == CalculationMode.TEMP_30C) tFinal else null,
                vcfNatural = if (calcMode == CalculationMode.TEMP_30C) vcfNatRes.vcf else null,
                vcf30 = if (calcMode == CalculationMode.TEMP_30C) vcf30Res.vcf else null,
                volume15 = if (calcMode == CalculationMode.TEMP_30C) vol15 else null,
                volume30 = if (calcMode == CalculationMode.TEMP_30C) vol30 else null
              )
              repository.addTransaction(tx)
              repository.deleteDraft("transfer_blend")
              haptics.trigger(com.example.engine.HapticEvent.SUCCESS)
              Toast.makeText(context, "Transaction saved to history log", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Save Transaction to Log")
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
        title = { Text("Clear Draft / Reset?") },
        text = { Text("Reset inputs and discard the current draft for Transfer?") },
        confirmButton = {
          Button(onClick = {
            repository.deleteDraft("transfer_blend")
            initialDipStr = ""
            finalDipStr = ""
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
