package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProductionReportCalculationResult
import java.util.Locale
import kotlin.math.abs

@Composable
fun DualProductionLossCard(
  result: ProductionReportCalculationResult,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    modifier = modifier.fillMaxWidth().testTag("dual_production_loss_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Header row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Analytics,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
          Column {
            Text(
              text = "Production Loss & Yield Comparison",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Feed consumed: ${String.format(Locale.US, "%,.2f L", result.totalFeed)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline,
              fontSize = 11.sp
            )
          }
        }
      }

      // Two columns: Option A and Option B side-by-side
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Option A Column
        LossOptionColumn(
          title = "Option A",
          subtitle = "Total Product / Feed",
          formulaHint = "Excl. Blending Tanks",
          totalVolumeLabel = "Total Product",
          totalVolumeValue = result.totalProductA,
          lossValue = result.lossOptionA,
          lossPercent = result.lossPercentOptionA,
          yieldPercent = result.yieldOptionA,
          modifier = Modifier.weight(1f).testTag("loss_option_a_column")
        )

        // Option B Column
        LossOptionColumn(
          title = "Option B",
          subtitle = "All-Tank TOV / Feed",
          formulaHint = "Incl. Blending Tanks",
          totalVolumeLabel = "Total Change",
          totalVolumeValue = result.totalChangeB,
          lossValue = result.lossOptionB,
          lossPercent = result.lossPercentOptionB,
          yieldPercent = result.yieldOptionB,
          modifier = Modifier.weight(1f).testTag("loss_option_b_column")
        )
      }

      // Difference (A - B) & Cause Row
      val hasAmberWarning = !result.isDiffExplainedByBlending
      val diffContainerBg = if (hasAmberWarning) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
      val diffBorderColor = if (hasAmberWarning) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
      val diffTextColor = if (hasAmberWarning) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurfaceVariant

      Surface(
        color = diffContainerBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, diffBorderColor),
        modifier = Modifier.fillMaxWidth().testTag("loss_difference_card")
      ) {
        Column(
          modifier = Modifier.padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = if (hasAmberWarning) Icons.Default.Warning else Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (hasAmberWarning) Color(0xFFD97706) else MaterialTheme.colorScheme.primary
              )
              Text(
                "Difference (A - B):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (hasAmberWarning) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurface
              )
            }
            Text(
              "${String.format(Locale.US, "%+,.2f L", result.diffAB)} (${String.format(Locale.US, "%+,.2f%%", result.lossPercentOptionA - result.lossPercentOptionB)})",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              color = if (hasAmberWarning) Color(0xFFB45309) else MaterialTheme.colorScheme.primary
            )
          }

          Text(
            text = result.diffExplanation,
            style = MaterialTheme.typography.bodySmall,
            color = diffTextColor,
            fontSize = 11.sp
          )
        }
      }
    }
  }
}

@Composable
private fun LossOptionColumn(
  title: String,
  subtitle: String,
  formulaHint: String,
  totalVolumeLabel: String,
  totalVolumeValue: Double,
  lossValue: Double,
  lossPercent: Double,
  yieldPercent: Double,
  modifier: Modifier = Modifier
) {
  val isGain = lossValue < 0.0
  val badgeBg = if (isGain) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
  val badgeText = if (isGain) Color(0xFF15803D) else MaterialTheme.colorScheme.error

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      // Header
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          fontSize = 10.sp
        )
        Text(
          text = formulaHint,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.outline,
          fontSize = 9.sp
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

      // Total product / Total change volume
      Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
          text = totalVolumeLabel,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 10.sp
        )
        Text(
          text = String.format(Locale.US, "%,.2f L", totalVolumeValue),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          fontSize = 12.sp
        )
      }

      // Loss badge
      Surface(
        shape = RoundedCornerShape(6.dp),
        color = badgeBg,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
          verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
          Text(
            text = if (isGain) "PROCESS GAIN" else "PROCESS LOSS",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = badgeText
          )
          Text(
            text = String.format(Locale.US, "%.2f%%", abs(lossPercent)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = badgeText
          )
          Text(
            text = String.format(Locale.US, "%,.2f L", abs(lossValue)),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = badgeText.copy(alpha = 0.85f),
            fontSize = 9.sp
          )
        }
      }

      // Yield
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Yield:",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 10.sp
        )
        Text(
          text = String.format(Locale.US, "%.2f%%", yieldPercent),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          fontSize = 11.sp
        )
      }
    }
  }
}
