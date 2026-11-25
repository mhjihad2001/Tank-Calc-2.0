package com.example.ui.screens.blending

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.data.Tank
import com.example.engine.Calculators
import com.example.engine.UnitUtils
import java.util.Locale

@Composable
fun BlendDipEstimatorCard(
  tank: Tank?,
  stage1DipStr: String,
  settings: AppSettings,
  modifier: Modifier = Modifier
) {
  var expanded by remember { mutableStateOf(true) }
  var initialDipStr by remember(tank?.id) { mutableStateOf(stage1DipStr) }
  var transferVolStr by remember(tank?.id) { mutableStateOf("") }

  // Keep initial dip synced on first load if user hasn't typed anything yet
  LaunchedEffect(stage1DipStr, tank?.id) {
    if (initialDipStr.isBlank() && stage1DipStr.isNotBlank()) {
      initialDipStr = stage1DipStr
    }
  }

  val initialDipVal = initialDipStr.trim().toDoubleOrNull()
  val transferVolTyped = transferVolStr.trim().toDoubleOrNull()

  val estimation = remember(tank, initialDipVal, transferVolTyped, settings.unit, settings.invalidInputThreshold) {
    if (tank != null && initialDipVal != null && transferVolTyped != null) {
      val transferVolL = UnitUtils.toLitres(transferVolTyped, settings.unit)
      Calculators.estimateTargetDip(
        tank = tank,
        initialDipMm = initialDipVal,
        transferVolumeL = transferVolL,
        invalidThreshold = settings.invalidInputThreshold
      )
    } else {
      null
    }
  }

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    border = BorderStroke(
      width = 1.dp,
      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    )
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Compact Header Row (always >= 48dp touch target)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .defaultMinSize(minHeight = 48.dp)
          .clickable { expanded = !expanded }
          .padding(horizontal = 12.dp, vertical = 8.dp)
          .testTag("blend_dip_estimator_toggle"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(
            imageVector = Icons.Default.Straighten,
            contentDescription = "Dip Estimation",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = "Dip Estimation",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          if (tank != null) {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(6.dp)
            ) {
              Text(
                text = tank.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
          // Inline compact result pill when collapsed
          if (!expanded && estimation?.estimatedDipMm != null) {
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = RoundedCornerShape(6.dp)
            ) {
              Text(
                text = "Est: ${String.format(Locale.US, "%,.0f", estimation.estimatedDipMm)} mm",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
        }

        Icon(
          imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = if (expanded) "Collapse dip estimation" else "Expand dip estimation",
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (tank == null) {
            Text(
              text = "Select a blend program with a calibrated tank to estimate dip.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          } else {
            // Compact 2-column inputs
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedTextField(
                value = initialDipStr,
                onValueChange = { initialDipStr = it },
                label = { Text("Initial Dip (mm)", maxLines = 1) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                  .weight(1f)
                  .testTag("blend_est_initial_dip")
              )
              OutlinedTextField(
                value = transferVolStr,
                onValueChange = { transferVolStr = it },
                label = { Text("Transfer Vol (${UnitUtils.unitLabel(settings.unit)})", maxLines = 1) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                  .weight(1f)
                  .testTag("blend_est_transfer_vol")
              )
            }

            // Tiny quick-action row (only shown when helpful)
            val showSyncStage1 = stage1DipStr.isNotBlank() && stage1DipStr != initialDipStr
            val showClear = initialDipStr.isNotBlank() || transferVolStr.isNotBlank()
            if (showSyncStage1 || showClear) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (showSyncStage1) {
                  TextButton(
                    onClick = { initialDipStr = stage1DipStr },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("blend_est_use_stage1")
                  ) {
                    Text(
                      text = "Use Stage 1 ($stage1DipStr mm)",
                      style = MaterialTheme.typography.labelSmall
                    )
                  }
                } else {
                  Spacer(modifier = Modifier.width(1.dp))
                }

                if (showClear) {
                  TextButton(
                    onClick = {
                      initialDipStr = ""
                      transferVolStr = ""
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("blend_est_clear")
                  ) {
                    Icon(
                      imageVector = Icons.Default.Close,
                      contentDescription = "Clear estimation",
                      modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                  }
                }
              }
            }

            // Compact Estimated Dip Result Strip
            if (estimation != null) {
              val hasEstDip = estimation.estimatedDipMm != null
              val containerColor = when {
                !hasEstDip || estimation.exceedsCapacity -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
              }
              val contentColor = when {
                !hasEstDip || estimation.exceedsCapacity -> MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.onPrimaryContainer
              }

              Surface(
                color = containerColor,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("blend_est_result_card")
              ) {
                Column(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  if (hasEstDip) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Column {
                        Text(
                          text = "Estimated Dip",
                          style = MaterialTheme.typography.labelSmall,
                          color = contentColor.copy(alpha = 0.85f)
                        )
                        Row(
                          verticalAlignment = Alignment.Bottom,
                          horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                          Text(
                            text = "${String.format(Locale.US, "%,.0f", estimation.estimatedDipMm)} mm",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = contentColor,
                            modifier = Modifier.testTag("blend_est_dip_value")
                          )
                          val delta = estimation.dipDeltaMm ?: 0.0
                          val sign = if (delta >= 0) "+" else ""
                          Text(
                            text = "($sign${String.format(Locale.US, "%.0f", delta)} mm)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor.copy(alpha = 0.85f),
                            modifier = Modifier.padding(bottom = 2.dp)
                          )
                        }
                      }

                      Column(horizontalAlignment = Alignment.End) {
                        Text(
                          text = "Init: ${UnitUtils.formatVolume(estimation.initialVolumeL, settings.unit, settings = settings)}",
                          style = MaterialTheme.typography.labelSmall,
                          color = contentColor.copy(alpha = 0.85f)
                        )
                        Text(
                          text = "Target: ${UnitUtils.formatVolume(estimation.targetVolumeL, settings.unit, settings = settings)}",
                          style = MaterialTheme.typography.labelMedium,
                          fontWeight = FontWeight.Bold,
                          color = contentColor
                        )
                      }
                    }
                  }

                  if (estimation.warning != null) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = contentColor,
                        modifier = Modifier.size(14.dp)
                      )
                      Text(
                        text = estimation.warning,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
