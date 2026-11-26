package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.*
import com.example.engine.*
import java.util.Locale
import kotlin.math.abs

fun TempCorrectionMethod.shortLabel(): String = when (this) {
  TempCorrectionMethod.VCF_ASTM -> "VCF (ASTM)"
  TempCorrectionMethod.VCC_TABLE -> "VCC Table"
  TempCorrectionMethod.VCF30_TABLE -> "VCF30 Table"
}

fun DensityRefTemp.shortLabel(): String = when (this) {
  DensityRefTemp.AT_15C -> "@15 °C"
  DensityRefTemp.AT_30C -> "@30 °C"
}

/**
 * Compact selector strip for Density Input Reference Temperature (@15°C vs @30°C),
 * Active 30°C Correction Method (VCF ASTM vs VCC Table vs VCF30 Table), and Edit Tables button.
 */
@Composable
fun DensityRefAndMethodSelector(
  densityRefTemp: DensityRefTemp,
  onDensityRefTempChange: (DensityRefTemp) -> Unit,
  activeMethod: TempCorrectionMethod,
  onMethodChange: (TempCorrectionMethod) -> Unit,
  onOpenTableEditor: () -> Unit,
  modifier: Modifier = Modifier
) {
  val haptics = rememberHapticController()

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Density Input Basis:",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )
        TextButton(
          onClick = {
            haptics.trigger(HapticEvent.MODE_SWITCH)
            onOpenTableEditor()
          },
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
          modifier = Modifier.testTag("open_vcf_vcc_table_editor_button")
        ) {
          Icon(Icons.Default.Edit, contentDescription = "Edit VCC and VCF Tables", modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Edit VCC / VCF Tables", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        DensityRefTemp.values().forEach { ref ->
          FilterChip(
            selected = densityRefTemp == ref,
            onClick = {
              haptics.trigger(HapticEvent.MODE_SWITCH)
              onDensityRefTempChange(ref)
            },
            label = {
              Text(
                text = if (ref == DensityRefTemp.AT_15C) "Density @15 °C" else "Density @30 °C",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (densityRefTemp == ref) FontWeight.Bold else FontWeight.Medium
              )
            },
            modifier = Modifier
              .weight(1f)
              .testTag("density_ref_${ref.name.lowercase()}")
          )
        }
      }

      Text(
        text = "Active 30 °C Method (Tap acceptable result):",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        TempCorrectionMethod.values().forEach { method ->
          FilterChip(
            selected = activeMethod == method,
            onClick = {
              haptics.trigger(HapticEvent.MODE_SWITCH)
              onMethodChange(method)
            },
            label = {
              Text(
                text = method.shortLabel(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (activeMethod == method) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            },
            modifier = Modifier
              .weight(1f)
              .testTag("temp_method_${method.name.lowercase()}")
          )
        }
      }
    }
  }
}

/**
 * Side-by-side VCF vs VCC (+ VCF30 Table) comparison card showing exact deviation and
 * allowing 1-tap selection of the acceptable method.
 */
@Composable
fun VcfVccComparisonCard(
  result: DualTempCorrectionResult,
  settings: AppSettings,
  onSelectMethod: (TempCorrectionMethod) -> Unit,
  onOpenTableEditor: () -> Unit,
  title: String = "VCF vs VCC Comparison (@30 °C)",
  modifier: Modifier = Modifier
) {
  val haptics = rememberHapticController()
  val devColor = when {
    result.deviationVcfVsVccPercent <= 0.05 -> Color(0xFF0F766E)
    result.deviationVcfVsVccPercent <= 0.20 -> Color(0xFFB45309)
    else -> MaterialTheme.colorScheme.error
  }
  val devBg = when {
    result.deviationVcfVsVccPercent <= 0.05 -> Color(0xFF0D9488).copy(alpha = 0.14f)
    result.deviationVcfVsVccPercent <= 0.20 -> Color(0xFFF59E0B).copy(alpha = 0.16f)
    else -> MaterialTheme.colorScheme.errorContainer
  }

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.ExtraBold,
          color = MaterialTheme.colorScheme.primary
        )
        TextButton(
          onClick = {
            haptics.trigger(HapticEvent.MODE_SWITCH)
            onOpenTableEditor()
          },
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Edit Tables", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
      }

      // Equivalent densities pill row
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "ρ@15°C: ${String.format(Locale.US, "%.2f", result.rho15KgM3)} kg/m³",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (result.inputDensityRefTemp == DensityRefTemp.AT_15C) FontWeight.ExtraBold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "ρ@30°C: ${String.format(Locale.US, "%.2f", result.rho30KgM3)} kg/m³",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (result.inputDensityRefTemp == DensityRefTemp.AT_30C) FontWeight.ExtraBold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }

      if (result.vcfObserved.warning != null) {
        Text(
          text = result.vcfObserved.warning,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error
        )
      }

      // Side-by-side selectable cards: VCF (ASTM) vs VCC (Table)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        MethodResultTile(
          label = "VCF (ASTM)",
          volumeFormatted = UnitUtils.formatVolume(result.volume30Vcf, settings.unit, settings = settings),
          detailLine1 = "VCF(t): ${String.format(Locale.US, "%.4f", result.vcfObserved.vcf)}",
          detailLine2 = "VCF(30): ${String.format(Locale.US, "%.4f", result.vcfAt30.vcf)}",
          isSelected = result.selectedMethod == TempCorrectionMethod.VCF_ASTM,
          onClick = {
            haptics.trigger(HapticEvent.MODE_SWITCH)
            onSelectMethod(TempCorrectionMethod.VCF_ASTM)
          },
          modifier = Modifier.weight(1f)
        )

        MethodResultTile(
          label = "VCC Table",
          volumeFormatted = UnitUtils.formatVolume(result.volume30Vcc, settings.unit, settings = settings),
          detailLine1 = "Coeff: ${String.format(Locale.US, "%.5f", result.vccCoefficient)}",
          detailLine2 = "Factor: ${String.format(Locale.US, "%.5f", result.vccMultiplier)}",
          isSelected = result.selectedMethod == TempCorrectionMethod.VCC_TABLE,
          onClick = {
            haptics.trigger(HapticEvent.MODE_SWITCH)
            onSelectMethod(TempCorrectionMethod.VCC_TABLE)
          },
          modifier = Modifier.weight(1f)
        )
      }

      // Third selectable row: VCF30 Table (@15°C linear coefficient table)
      MethodResultTile(
        label = "VCF30 Table (Linear @15°C Density)",
        volumeFormatted = UnitUtils.formatVolume(result.volume30Vcf30Table, settings.unit, settings = settings),
        detailLine1 = "Coeff: ${String.format(Locale.US, "%.5f", result.vcf30Coefficient)} (Bound ${result.vcf30MatchedBoundKgL ?: "-"})  •  Factor: ${String.format(Locale.US, "%.5f", result.vcf30Multiplier)}",
        detailLine2 = "Vol @15°C (ASTM): ${UnitUtils.formatVolume(result.volume15Vcf, settings.unit, settings = settings)}  •  α15: ${String.format(Locale.US, "%.7f", result.vcfObserved.alpha15)}",
        isSelected = result.selectedMethod == TempCorrectionMethod.VCF30_TABLE,
        onClick = {
          haptics.trigger(HapticEvent.MODE_SWITCH)
          onSelectMethod(TempCorrectionMethod.VCF30_TABLE)
        },
        modifier = Modifier.fillMaxWidth()
      )

      // Deviation Banner (VCF vs VCC)
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = devBg,
        border = BorderStroke(1.dp, devColor.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "VCF vs VCC Deviation",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = devColor
            )
            Text(
              text = result.deviationStatus,
              style = MaterialTheme.typography.labelSmall,
              color = devColor
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            val sign = if (result.deviationVcfVsVccL >= 0) "+" else "-"
            Text(
              text = "$sign${UnitUtils.formatVolume(abs(result.deviationVcfVsVccL), settings.unit, settings = settings)}",
              style = MaterialTheme.typography.labelLarge,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.ExtraBold,
              color = devColor
            )
            Text(
              text = "${String.format(Locale.US, "%.4f", result.deviationVcfVsVccPercent)}%",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = devColor
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MethodResultTile(
  label: String,
  volumeFormatted: String,
  detailLine1: String,
  detailLine2: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    border = BorderStroke(
      width = if (isSelected) 2.dp else 1.dp,
      color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    ),
    modifier = modifier.clickable(onClick = onClick)
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.ExtraBold,
          color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        if (isSelected) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Active method",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
          )
        }
      }
      Text(
        text = volumeFormatted,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = detailLine1,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = detailLine2,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

/**
 * Full-screen Material 3 Dialog to edit VCC Table (@30°C), VCF30 Table (@15°C),
 * and ASTM D1250-80 VCF K0/K1/K2 band parameters. Persists directly to TankRepository.
 */
@Composable
fun VcfVccTableEditorDialog(
  repository: TankRepository,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val setup by repository.productionReportSetup.collectAsState()
  val settings by repository.settings.collectAsState()
  val haptics = rememberHapticController()

  var selectedTab by remember { mutableIntStateOf(0) } // 0 = VCC, 1 = VCF30, 2 = ASTM VCF K0/K1

  var vccRows by remember(setup.vccTable) { mutableStateOf(setup.vccTable) }
  var vcf30Rows by remember(setup.vcf30Table) { mutableStateOf(setup.vcf30Table) }
  var astmBands by remember(settings.customVcfBandParams) {
    mutableStateOf(VcfEngine.effectiveBandParams(settings.customVcfBandParams))
  }

  val vccValidation = remember(vccRows) { ProductionReportEngine.validateCoefficientTable(vccRows) }
  val vcf30Validation = remember(vcf30Rows) { ProductionReportEngine.validateCoefficientTable(vcf30Rows) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .fillMaxHeight(0.88f)
        .testTag("vcf_vcc_table_editor_dialog")
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Editable VCC & VCF Data Tables",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "Changes apply live across Transfer, Blending, Loss, VCF & ERP Report",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          TextButton(onClick = onDismiss) {
            Text("Close")
          }
        }

        TabRow(selectedTabIndex = selectedTab) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text("VCC (@30°C)", maxLines = 1) }
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text("VCF30 (@15°C)", maxLines = 1) }
          )
          Tab(
            selected = selectedTab == 2,
            onClick = { selectedTab = 2 },
            text = { Text("ASTM K0/K1", maxLines = 1) }
          )
        }

        // Tab Body
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
          when (selectedTab) {
            0 -> CoefficientTableEditorBody(
              rows = vccRows,
              densityHeader = "Density @30°C (kg/L)",
              coeffHeader = "VCC Coeff",
              validation = vccValidation,
              onRowsChange = { vccRows = it },
              onResetDefault = {
                vccRows = ProductionReportSetup.defaultVccTable()
                Toast.makeText(context, "VCC table reset to factory defaults.", Toast.LENGTH_SHORT).show()
              }
            )
            1 -> CoefficientTableEditorBody(
              rows = vcf30Rows,
              densityHeader = "Density @15°C (kg/L)",
              coeffHeader = "VCF30 Coeff",
              validation = vcf30Validation,
              onRowsChange = { vcf30Rows = it },
              onResetDefault = {
                vcf30Rows = ProductionReportSetup.defaultVcf30Table()
                Toast.makeText(context, "VCF30 table reset to factory defaults.", Toast.LENGTH_SHORT).show()
              }
            )
            2 -> AstmVcfBandsEditorBody(
              bands = astmBands,
              onBandsChange = { astmBands = it },
              onResetDefault = {
                astmBands = VcfEngine.defaultVcfBandParams()
                Toast.makeText(context, "ASTM D1250-80 constants reset to defaults.", Toast.LENGTH_SHORT).show()
              }
            )
          }
        }

        HorizontalDivider()

        // Footer Save Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f)
          ) {
            Text("Cancel")
          }
          Button(
            onClick = {
              val sortedVcc = vccRows.sortedBy { it.lowerBound }
              val sortedVcf30 = vcf30Rows.sortedBy { it.lowerBound }
              repository.saveProductionReportSetup(
                setup.copy(
                  vccTable = sortedVcc,
                  vcf30Table = sortedVcf30
                )
              )
              repository.updateSettings(
                settings.copy(customVcfBandParams = astmBands)
              )
              haptics.trigger(HapticEvent.SUCCESS)
              Toast.makeText(context, "VCC, VCF30 & ASTM tables saved!", Toast.LENGTH_SHORT).show()
              onDismiss()
            },
            modifier = Modifier
              .weight(1.4f)
              .testTag("save_vcf_vcc_tables_button")
          ) {
            Text("Save All Tables", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun CoefficientTableEditorBody(
  rows: List<DensityCoefficientRow>,
  densityHeader: String,
  coeffHeader: String,
  validation: ProductionReportEngine.TableValidationResult,
  onRowsChange: (List<DensityCoefficientRow>) -> Unit,
  onResetDefault: () -> Unit
) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "${rows.size} rows (auto-sorted on save)",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
          onClick = onResetDefault,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Reset", style = MaterialTheme.typography.labelSmall)
        }
        Button(
          onClick = {
            val nextBound = (rows.maxOfOrNull { it.lowerBound } ?: 0.700) + 0.010
            val nextCoeff = rows.lastOrNull()?.coefficient ?: 0.00100
            onRowsChange(rows + DensityCoefficientRow(Math.round(nextBound * 10000.0) / 10000.0, nextCoeff))
          },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Add Row", style = MaterialTheme.typography.labelSmall)
        }
      }
    }

    if (validation.errors.isNotEmpty()) {
      Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = validation.errors.take(2).joinToString("\n"),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.padding(8.dp)
        )
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text("#", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
      Text(densityHeader, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
      Text(coeffHeader, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
      Spacer(modifier = Modifier.width(36.dp))
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      itemsIndexed(rows) { idx, row ->
        var boundStr by remember(idx, row.lowerBound) { mutableStateOf(row.lowerBound.toString()) }
        var coeffStr by remember(idx, row.coefficient) { mutableStateOf(row.coefficient.toString()) }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = "${idx + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
          )
          OutlinedTextField(
            value = boundStr,
            onValueChange = { txt ->
              boundStr = txt
              txt.toDoubleOrNull()?.let { parsed ->
                val updated = rows.toMutableList()
                updated[idx] = updated[idx].copy(lowerBound = parsed)
                onRowsChange(updated)
              }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = coeffStr,
            onValueChange = { txt ->
              coeffStr = txt
              txt.toDoubleOrNull()?.let { parsed ->
                val updated = rows.toMutableList()
                updated[idx] = updated[idx].copy(coefficient = parsed)
                onRowsChange(updated)
              }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f)
          )
          IconButton(
            onClick = {
              if (rows.size > 1) {
                val updated = rows.toMutableList()
                updated.removeAt(idx)
                onRowsChange(updated)
              }
            },
            modifier = Modifier.size(36.dp)
          ) {
            Icon(
              Icons.Default.Delete,
              contentDescription = "Delete row",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AstmVcfBandsEditorBody(
  bands: List<VcfBandParam>,
  onBandsChange: (List<VcfBandParam>) -> Unit,
  onResetDefault: () -> Unit
) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "α15 = K2 + K0/ρ² + K1/ρ  (ASTM D1250-80)",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.primary
      )
      OutlinedButton(
        onClick = onResetDefault,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Reset ASTM", style = MaterialTheme.typography.labelSmall)
      }
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      itemsIndexed(bands) { idx, band ->
        var minStr by remember(band.id, band.minDensity15) { mutableStateOf(band.minDensity15.toString()) }
        var maxStr by remember(band.id, band.maxDensity15) { mutableStateOf(band.maxDensity15.toString()) }
        var k0Str by remember(band.id, band.k0) { mutableStateOf(band.k0.toString()) }
        var k1Str by remember(band.id, band.k1) { mutableStateOf(band.k1.toString()) }
        var k2Str by remember(band.id, band.k2Offset) { mutableStateOf(band.k2Offset.toString()) }

        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = band.bandName,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              OutlinedTextField(
                value = minStr,
                onValueChange = { txt ->
                  minStr = txt
                  txt.toDoubleOrNull()?.let { v ->
                    val next = bands.toMutableList()
                    next[idx] = next[idx].copy(minDensity15 = v)
                    onBandsChange(next)
                  }
                },
                label = { Text("Min ρ15") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
              )
              OutlinedTextField(
                value = maxStr,
                onValueChange = { txt ->
                  maxStr = txt
                  txt.toDoubleOrNull()?.let { v ->
                    val next = bands.toMutableList()
                    next[idx] = next[idx].copy(maxDensity15 = v)
                    onBandsChange(next)
                  }
                },
                label = { Text("Max ρ15") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              OutlinedTextField(
                value = k0Str,
                onValueChange = { txt ->
                  k0Str = txt
                  txt.toDoubleOrNull()?.let { v ->
                    val next = bands.toMutableList()
                    next[idx] = next[idx].copy(k0 = v)
                    onBandsChange(next)
                  }
                },
                label = { Text("K0") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
              )
              OutlinedTextField(
                value = k1Str,
                onValueChange = { txt ->
                  k1Str = txt
                  txt.toDoubleOrNull()?.let { v ->
                    val next = bands.toMutableList()
                    next[idx] = next[idx].copy(k1 = v)
                    onBandsChange(next)
                  }
                },
                label = { Text("K1") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
              )
              OutlinedTextField(
                value = k2Str,
                onValueChange = { txt ->
                  k2Str = txt
                  txt.toDoubleOrNull()?.let { v ->
                    val next = bands.toMutableList()
                    next[idx] = next[idx].copy(k2Offset = v)
                    onBandsChange(next)
                  }
                },
                label = { Text("K2 / α0") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }
    }
  }
}
