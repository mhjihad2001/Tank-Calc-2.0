package com.example.ui.screens.blending

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.data.Tank
import com.example.data.UnitType
import com.example.engine.UnitUtils

data class StageVisualMarker(
  val stageName: String,
  val componentName: String,
  val volumeL: Double,
  val dipMm: Double,
  val color: Color
)

@Composable
fun BlendTankVisual(
  tank: Tank?,
  markers: List<StageVisualMarker>,
  currentUnit: UnitType,
  settings: AppSettings? = null,
  modifier: Modifier = Modifier
) {
  val capacity = (tank?.maxVolumeLiters ?: 500000.0).coerceAtLeast(1000.0)
  val finalVolume = markers.lastOrNull()?.volumeL ?: 0.0
  val fillFraction = (finalVolume / capacity).toFloat().coerceIn(0f, 1f)

  val animatedFill by animateFloatAsState(
    targetValue = fillFraction,
    animationSpec = tween(durationMillis = 600),
    label = "tank_fill_anim"
  )

  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Tank Level Profile: ${tank?.name ?: "Unknown"}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            text = "Capacity: ${UnitUtils.formatVolume(capacity, currentUnit, settings = settings)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          if (fillFraction >= 0.90f) {
            Surface(color = Color(0xFFB91C1C), shape = RoundedCornerShape(8.dp)) {
              Text(
                text = "HLA ≥90%",
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
              )
            }
          }
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp)
          ) {
            Text(
              text = "${(fillFraction * 100).toInt()}% Full • Ullage: ${UnitUtils.formatVolume((capacity - finalVolume).coerceAtLeast(0.0), currentUnit, settings = settings)}",
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(200.dp)
          .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
      ) {
        Canvas(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
        ) {
          val canvasWidth = size.width
          val canvasHeight = size.height
          val tankWidth = 110.dp.toPx()
          val tankLeft = (canvasWidth - tankWidth) / 2f
          val tankTop = 10f
          val tankBottom = canvasHeight - 10f
          val tankEffectiveHeight = tankBottom - tankTop

          // Tank background & shell (Ullage region)
          val tankRect = Size(tankWidth, tankEffectiveHeight)
          drawRoundRect(
            color = Color(0xFFE2E8F0),
            topLeft = Offset(tankLeft, tankTop),
            size = tankRect,
            cornerRadius = CornerRadius(16f, 16f)
          )

          // Unpumpable / Heel region (bottom 5%)
          val heelHeight = tankEffectiveHeight * 0.05f
          val heelY = tankBottom - heelHeight
          drawRoundRect(
            color = Color(0xFF94A3B8).copy(alpha = 0.35f),
            topLeft = Offset(tankLeft, heelY),
            size = Size(tankWidth, heelHeight),
            cornerRadius = CornerRadius(8f, 8f)
          )

          // Liquid fill
          val liquidHeight = tankEffectiveHeight * animatedFill
          if (liquidHeight > 0) {
            val liquidTop = tankBottom - liquidHeight
            val liquidBrush = Brush.verticalGradient(
              colors = listOf(
                Color(0xFF14B8A6),
                Color(0xFF0E5AA7),
                Color(0xFF0B2A5B)
              ),
              startY = liquidTop,
              endY = tankBottom
            )
            drawRoundRect(
              brush = liquidBrush,
              topLeft = Offset(tankLeft, liquidTop),
              size = Size(tankWidth, liquidHeight),
              cornerRadius = CornerRadius(12f, 12f)
            )
          }

          // Tank shell border
          drawRoundRect(
            color = Color(0xFF0B2A5B),
            topLeft = Offset(tankLeft, tankTop),
            size = tankRect,
            cornerRadius = CornerRadius(16f, 16f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f)
          )

          val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)

          // HLA (90% High-Level Alarm) line
          val hlaY = tankBottom - (tankEffectiveHeight * 0.90f)
          drawLine(
            color = Color(0xFFDC2626),
            start = Offset(tankLeft - 14f, hlaY),
            end = Offset(tankLeft + tankWidth + 14f, hlaY),
            strokeWidth = 2.5f,
            pathEffect = dashEffect
          )

          // Heel (5% Unpumpable) line
          drawLine(
            color = Color(0xFFD97706),
            start = Offset(tankLeft - 14f, heelY),
            end = Offset(tankLeft + tankWidth + 14f, heelY),
            strokeWidth = 2f,
            pathEffect = dashEffect
          )

          // Draw graduation ticks on tank shell
          for (i in 1..4) {
            val tickY = tankBottom - (tankEffectiveHeight * (i * 0.25f))
            drawLine(
              color = Color(0xFF94A3B8),
              start = Offset(tankLeft, tickY),
              end = Offset(tankLeft + 14f, tickY),
              strokeWidth = 2f
            )
            drawLine(
              color = Color(0xFF94A3B8),
              start = Offset(tankLeft + tankWidth - 14f, tickY),
              end = Offset(tankLeft + tankWidth, tickY),
              strokeWidth = 2f
            )
          }

          // Draw stage level markers
          markers.forEachIndexed { index, marker ->
            if (marker.volumeL > 0) {
              val stageFrac = (marker.volumeL / capacity).toFloat().coerceIn(0f, 1f)
              val markerY = tankBottom - (tankEffectiveHeight * stageFrac)

              // Line across tank
              drawLine(
                color = marker.color,
                start = Offset(tankLeft - 20f, markerY),
                end = Offset(tankLeft + tankWidth + 20f, markerY),
                strokeWidth = 3f,
                pathEffect = dashEffect
              )

              // Circle indicators at edges
              drawCircle(
                color = marker.color,
                radius = 5f,
                center = Offset(tankLeft - 20f, markerY)
              )
              drawCircle(
                color = marker.color,
                radius = 5f,
                center = Offset(tankLeft + tankWidth + 20f, markerY)
              )
            }
          }
        }

        // Overlay stage badges alongside the visual
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
          verticalArrangement = Arrangement.SpaceBetween
        ) {
          markers.forEachIndexed { idx, marker ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = if (idx % 2 == 0) Arrangement.Start else Arrangement.End
            ) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = marker.color.copy(alpha = 0.9f),
                shadowElevation = 2.dp
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Text(
                    text = "${marker.stageName}:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.White
                  )
                  Text(
                    text = "${marker.dipMm.toInt()} mm (${UnitUtils.formatVolume(marker.volumeL, currentUnit, settings = settings)})",
                    fontSize = 11.sp,
                    color = Color.White
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
