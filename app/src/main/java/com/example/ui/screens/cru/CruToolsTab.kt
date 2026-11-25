package com.example.ui.screens.cru

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.CruReactorBed
import com.example.data.TankRepository
import com.example.engine.CruEngine

enum class FeedRateUnit(val label: String) {
  TON_PER_HR("t/h"),
  KG_PER_HR("kg/h"),
  M3_PER_HR("m³/h"),
  BBL_PER_HR("bbl/h")
}

@Composable
fun CruToolsTab(
  repository: TankRepository,
  onApplyWaitToEstimator: (Double) -> Unit,
  onApplyWaitToSor: (Double) -> Unit,
  onApplyWhsvToBase: (Double) -> Unit,
  onApplyWhsvToNew: (Double) -> Unit,
  onApplyWhsvToSor: (Double) -> Unit,
  onApplyMabpToBase: (Double) -> Unit,
  onApplyMabpToNew: (Double) -> Unit,
  onApplyMabpToSor: (Double) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val beds by repository.cruReactorBeds.collectAsState()

  // 1. Bed Temperatures state (Tin, Tout for each bed)
  // 1. Bed Temperatures state (Tin, Tout for each bed)
  val bedTemps = remember(beds) {
    mutableStateMapOf<String, Pair<String, String>>().apply {
      beds.forEachIndexed { idx, b ->
        val defaultTin = when (idx) {
          0 -> "505.0"
          1 -> "503.0"
          2 -> "500.0"
          else -> "500.0"
        }
        val defaultTout = when (idx) {
          0 -> "445.0"
          1 -> "475.0"
          2 -> "490.0"
          else -> "485.0"
        }
        put(b.id, Pair(defaultTin, defaultTout))
      }
    }
  }

  // Calculate WAIT & WABT live
  val totalCatWeight: Double = beds.sumOf { it.catalystWeightTonnes }
  val waitWabtResult = remember(beds, bedTemps.toMap()) {
    val bedWeights = beds.map { it.catalystWeightTonnes }
    val bedTins = beds.map { b -> bedTemps[b.id]?.first?.toDoubleOrNull() ?: 0.0 }
    val bedTouts = beds.map { b -> bedTemps[b.id]?.second?.toDoubleOrNull() ?: 0.0 }

    val wait = CruEngine.calculateWait(bedWeights, bedTins)
    val wabt = CruEngine.calculateWabt(bedWeights, bedTins, bedTouts)

    val deltaTs = bedTins.zip(bedTouts).map { (tin, tout) -> tout - tin }
    val totalDeltaT = deltaTs.sum()

    Triple(wait, wabt, totalDeltaT)
  }

  // 2. WHSV Tool state
  var feedRateUnit by remember { mutableStateOf(FeedRateUnit.BBL_PER_HR) }
  var feedRateStr by remember { mutableStateOf("800.0") }
  var densityModeApi by remember { mutableStateOf(true) } // true: API, false: SG
  var apiStr by remember { mutableStateOf("55.0") }
  var sgStr by remember { mutableStateOf("0.757") }
  var catWeightStr by remember(totalCatWeight) { mutableStateOf("%.2f".format(totalCatWeight)) }

  // Sync API & SG
  fun updateApi(newApi: String) {
    apiStr = newApi
    val apiVal = newApi.toDoubleOrNull()
    if (apiVal != null && apiVal > -131.5) {
      val sgVal = 141.5 / (131.5 + apiVal)
      sgStr = "%.3f".format(sgVal)
    }
  }

  fun updateSg(newSg: String) {
    sgStr = newSg
    val sgVal = newSg.toDoubleOrNull()
    if (sgVal != null && sgVal > 0.0) {
      val apiVal = (141.5 / sgVal) - 131.5
      apiStr = "%.1f".format(apiVal)
    }
  }

  val feedRateVal = feedRateStr.toDoubleOrNull() ?: 0.0
  val catWeightVal = catWeightStr.toDoubleOrNull() ?: totalCatWeight
  val sgVal = sgStr.toDoubleOrNull() ?: 0.757

  // Calculate WHSV live
  val calculatedWhsv = remember(feedRateVal, feedRateUnit, sgVal, catWeightVal) {
    if (catWeightVal <= 0.0) 0.0
    else {
      val massRateTonPerHr = when (feedRateUnit) {
        FeedRateUnit.TON_PER_HR -> feedRateVal
        FeedRateUnit.KG_PER_HR -> feedRateVal / 1000.0
        FeedRateUnit.M3_PER_HR -> feedRateVal * sgVal
        FeedRateUnit.BBL_PER_HR -> feedRateVal * 0.1589873 * sgVal
      }
      massRateTonPerHr / catWeightVal
    }
  }

  // 3. MABP Tool state
  var t10Str by remember { mutableStateOf("105.0") }
  var t50Str by remember { mutableStateOf("125.0") }
  var t90Str by remember { mutableStateOf("145.0") }

  val t10 = t10Str.toDoubleOrNull() ?: 0.0
  val t50 = t50Str.toDoubleOrNull() ?: 0.0
  val t90 = t90Str.toDoubleOrNull() ?: 0.0

  val calculatedMabp = remember(t10, t50, t90) {
    CruEngine.calculateMabp(t10, t50, t90)
  }

  // 4. H2/HC Ratio state (Optional)
  var showH2Hc by remember { mutableStateOf(false) }
  var recycleGasFlowStr by remember { mutableStateOf("12000.0") } // Nm3/h
  var h2PurityStr by remember { mutableStateOf("85.0") } // vol %
  var feedRateH2Str by remember { mutableStateOf("96.0") } // t/h

  val recycleFlow = recycleGasFlowStr.toDoubleOrNull() ?: 0.0
  val h2Purity = h2PurityStr.toDoubleOrNull() ?: 0.0
  val feedMass = feedRateH2Str.toDoubleOrNull() ?: 0.0

  val h2HcRatio = remember(recycleFlow, h2Purity, feedMass) {
    CruEngine.calculateH2HcRatio(recycleFlow, h2Purity, feedMass)
  }

  // Edit bed dialog state
  var showEditBedDialog by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // -------------------------------------------------------------------------
    // TOOL 1: WAIT / WABT Multi-Bed Calculator
    // -------------------------------------------------------------------------
    Card(
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Thermostat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("WAIT / WABT Multi-Bed Calculator", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          }
          IconButton(onClick = { showEditBedDialog = true }) {
            Icon(Icons.Default.Settings, contentDescription = "Edit Beds", tint = MaterialTheme.colorScheme.primary)
          }
        }

        Text(
          "Total Catalyst: ${"%.2f".format(totalCatWeight)} t across ${beds.size} beds. Weighted average inlet & bed temperatures.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Bed temperature inputs
        beds.forEachIndexed { idx, b ->
          val current = bedTemps[b.id] ?: Pair("", "")
          val weightFrac = if (totalCatWeight > 0.0) (b.catalystWeightTonnes / totalCatWeight) * 100.0 else 0.0

          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(8.dp)
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                  "${b.name} (${b.catalystWeightTonnes} t, ${"%.1f".format(weightFrac)}%)",
                  fontWeight = FontWeight.SemiBold,
                  style = MaterialTheme.typography.bodyMedium
                )
                val tin = current.first.toDoubleOrNull() ?: 0.0
                val tout = current.second.toDoubleOrNull() ?: 0.0
                val dt = tout - tin
                Text(
                  "ΔT: " + (if (dt >= 0) "+" else "") + "%.1f °C".format(dt),
                  fontWeight = FontWeight.Medium,
                  style = MaterialTheme.typography.bodySmall,
                  color = if (dt < 0) Color(0xFFC62828) else Color(0xFF2E7D32)
                )
              }

              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = current.first,
                  onValueChange = { bedTemps[b.id] = Pair(it, current.second) },
                  label = { Text("Inlet Tin (°C)") },
                  modifier = Modifier.weight(1f).testTag("bed_${idx + 1}_tin"),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  singleLine = true
                )
                OutlinedTextField(
                  value = current.second,
                  onValueChange = { bedTemps[b.id] = Pair(current.first, it) },
                  label = { Text("Outlet Tout (°C)") },
                  modifier = Modifier.weight(1f).testTag("bed_${idx + 1}_tout"),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  singleLine = true
                )
              }
            }
          }
        }

        // Results Card
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(8.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Calculated WAIT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
              Text(
                "%.2f °C".format(waitWabtResult.first),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("cru_calculated_wait_display")
              )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Calculated WABT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
              Text(
                "%.2f °C".format(waitWabtResult.second),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("cru_calculated_wabt_display")
              )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Total Endotherm", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
              Text(
                "%.1f °C".format(waitWabtResult.third),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
            }
          }
        }

        // Apply Buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = {
              onApplyWaitToEstimator(waitWabtResult.first)
              Toast.makeText(context, "WAIT applied to Change Estimator!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f).testTag("cru_apply_wait_to_estimator")
          ) {
            Text("Use in Estimator")
          }
          OutlinedButton(
            onClick = {
              onApplyWaitToSor(waitWabtResult.first)
              Toast.makeText(context, "WAIT applied to SOR Activity!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f).testTag("cru_apply_wait_to_sor")
          ) {
            Text("Use in SOR Activity")
          }
        }
      }
    }

    // -------------------------------------------------------------------------
    // TOOL 2: WHSV Tool
    // -------------------------------------------------------------------------
    Card(
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("WHSV Calculator (Weight Hourly Space Velocity)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          }
        }

        Text(
          "WHSV (hr⁻¹) = Feed Rate (t/h) / Catalyst Weight (t)",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Feed Rate Unit chips
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          FeedRateUnit.values().forEach { u ->
            FilterChip(
              selected = feedRateUnit == u,
              onClick = { feedRateUnit = u },
              label = { Text(u.label) }
            )
          }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = feedRateStr,
            onValueChange = { feedRateStr = it },
            label = { Text("Feed Rate (${feedRateUnit.label})") },
            modifier = Modifier.weight(1f).testTag("cru_whsv_feed_rate_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
          )
          OutlinedTextField(
            value = catWeightStr,
            onValueChange = { catWeightStr = it },
            label = { Text("Catalyst Weight (t)") },
            modifier = Modifier.weight(1f).testTag("cru_whsv_cat_weight_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
          )
        }

        // If volumetric, show Density API / SG inputs
        if (feedRateUnit == FeedRateUnit.M3_PER_HR || feedRateUnit == FeedRateUnit.BBL_PER_HR) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = apiStr,
              onValueChange = { updateApi(it) },
              label = { Text("API Gravity (°API)") },
              modifier = Modifier.weight(1f).testTag("cru_whsv_api_input"),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true
            )
            OutlinedTextField(
              value = sgStr,
              onValueChange = { updateSg(it) },
              label = { Text("Specific Gravity (SG)") },
              modifier = Modifier.weight(1f).testTag("cru_whsv_sg_input"),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true
            )
          }
        }

        // Live Calculated Result Banner
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(8.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Calculated WHSV", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
              Text("Mass feed basis: ${"%.2f".format(feedRateVal * (if (feedRateUnit == FeedRateUnit.BBL_PER_HR) 0.1589873 * sgVal else if (feedRateUnit == FeedRateUnit.M3_PER_HR) sgVal else 1.0))} t/h", style = MaterialTheme.typography.bodySmall)
            }
            Text(
              "%.2f hr⁻¹".format(calculatedWhsv),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.testTag("cru_calculated_whsv_display")
            )
          }
        }

        // Apply Buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Button(
            onClick = {
              onApplyWhsvToBase(calculatedWhsv)
              Toast.makeText(context, "Applied to Base WHSV!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f).testTag("cru_whsv_apply_base")
          ) {
            Text("Estimator Base", style = MaterialTheme.typography.labelSmall)
          }
          Button(
            onClick = {
              onApplyWhsvToNew(calculatedWhsv)
              Toast.makeText(context, "Applied to New WHSV!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f).testTag("cru_whsv_apply_new")
          ) {
            Text("Estimator New", style = MaterialTheme.typography.labelSmall)
          }
          OutlinedButton(
            onClick = {
              onApplyWhsvToSor(calculatedWhsv)
              Toast.makeText(context, "Applied to SOR Activity!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f).testTag("cru_whsv_apply_sor")
          ) {
            Text("SOR Activity", style = MaterialTheme.typography.labelSmall)
          }
        }
      }
    }

    // -------------------------------------------------------------------------
    // TOOL 3: MABP Tool
    // -------------------------------------------------------------------------
    Card(
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Compress, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Feed MABP Calculator (Mean Average Boiling Point)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        Text(
          "Formula: MABP = (T10 + 2×T50 + T90) / 4 (°C)",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = t10Str,
            onValueChange = { t10Str = it },
            label = { Text("T10 (°C)") },
            modifier = Modifier.weight(1f).testTag("cru_mabp_t10_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
          )
          OutlinedTextField(
            value = t50Str,
            onValueChange = { t50Str = it },
            label = { Text("T50 (°C)") },
            modifier = Modifier.weight(1f).testTag("cru_mabp_t50_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
          )
          OutlinedTextField(
            value = t90Str,
            onValueChange = { t90Str = it },
            label = { Text("T90 (°C)") },
            modifier = Modifier.weight(1f).testTag("cru_mabp_t90_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
          )
        }

        Surface(
          color = MaterialTheme.colorScheme.tertiaryContainer,
          shape = RoundedCornerShape(8.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Calculated Feed MABP", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(
              "%.2f °C".format(calculatedMabp),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.testTag("cru_calculated_mabp_display")
            )
          }
        }

        // Apply Buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Button(
            onClick = {
              onApplyMabpToBase(calculatedMabp)
              Toast.makeText(context, "Applied to Base MABP!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f).testTag("cru_mabp_apply_base")
          ) {
            Text("Estimator Base", style = MaterialTheme.typography.labelSmall)
          }
          Button(
            onClick = {
              onApplyMabpToNew(calculatedMabp)
              Toast.makeText(context, "Applied to New MABP!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f).testTag("cru_mabp_apply_new")
          ) {
            Text("Estimator New", style = MaterialTheme.typography.labelSmall)
          }
          OutlinedButton(
            onClick = {
              onApplyMabpToSor(calculatedMabp)
              Toast.makeText(context, "Applied to SOR Activity!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f).testTag("cru_mabp_apply_sor")
          ) {
            Text("SOR Activity", style = MaterialTheme.typography.labelSmall)
          }
        }
      }
    }

    // -------------------------------------------------------------------------
    // TOOL 4: Optional H2/HC Ratio Tool
    // -------------------------------------------------------------------------
    Card(
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Grain, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("H₂ / HC Molar Ratio (Optional)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          }
          IconButton(onClick = { showH2Hc = !showH2Hc }) {
            Icon(if (showH2Hc) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
          }
        }

        if (showH2Hc) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = recycleGasFlowStr,
              onValueChange = { recycleGasFlowStr = it },
              label = { Text("Recycle Gas (Nm³/h)") },
              modifier = Modifier.weight(1f),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true
            )
            OutlinedTextField(
              value = h2PurityStr,
              onValueChange = { h2PurityStr = it },
              label = { Text("H₂ Purity (vol %)") },
              modifier = Modifier.weight(1f),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true
            )
          }

          OutlinedTextField(
            value = feedRateH2Str,
            onValueChange = { feedRateH2Str = it },
            label = { Text("Feed Rate (t/h)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
          )

          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Estimated H₂ / HC Mol Ratio", fontWeight = FontWeight.SemiBold)
              Text("%.2f mol/mol".format(h2HcRatio), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            }
          }
        }
      }
    }
  }

  // Edit Beds Dialog
  if (showEditBedDialog) {
    EditReactorBedsDialog(
      repository = repository,
      onDismiss = { showEditBedDialog = false }
    )
  }
}

@Composable
fun EditReactorBedsDialog(
  repository: TankRepository,
  onDismiss: () -> Unit
) {
  val beds by repository.cruReactorBeds.collectAsState()
  var editedBeds by remember(beds) { mutableStateOf(beds) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit Reactor Beds Configuration", fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text("Configure bed names and catalyst weights in metric tons (t).", style = MaterialTheme.typography.bodySmall)

        editedBeds.forEachIndexed { index, bed ->
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = bed.name,
                onValueChange = { newName ->
                  editedBeds = editedBeds.toMutableList().also { it[index] = bed.copy(name = newName) }
                },
                label = { Text("Bed Name") },
                modifier = Modifier.weight(1.2f),
                singleLine = true
              )
              OutlinedTextField(
                value = bed.catalystWeightTonnes.toString(),
                onValueChange = { newW ->
                  val w = newW.toDoubleOrNull() ?: 0.0
                  editedBeds = editedBeds.toMutableList().also { it[index] = bed.copy(catalystWeightTonnes = w) }
                },
                label = { Text("Weight (t)") },
                modifier = Modifier.weight(0.8f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
              )
            }
          }
        }

        val totalW: Double = editedBeds.sumOf { it.catalystWeightTonnes }
        Text("Total Catalyst: ${"%.2f".format(totalW)} t", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
      }
    },
    confirmButton = {
      Button(
        onClick = {
          repository.saveCruReactorBeds(editedBeds)
          onDismiss()
        }
      ) {
        Text("Save Beds")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
