package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun RefineryLogo(
  modifier: Modifier = Modifier.size(120.dp),
  withBackground: Boolean = true
) {
  Box(modifier = modifier.aspectRatio(1f)) {
    Canvas(modifier = Modifier.matchParentSize()) {
      val w = size.width
      val h = size.height
      val scaleX = w / 1024f
      val scaleY = h / 1024f

      if (withBackground) {
        val bgBrush = Brush.linearGradient(
          colors = listOf(Color(0xFF0A192F), Color(0xFF0F2B48), Color(0xFF071224)),
          start = Offset(0f, 0f),
          end = Offset(w, h)
        )
        drawRoundRect(
          brush = bgBrush,
          size = size,
          cornerRadius = CornerRadius(160f * scaleX, 160f * scaleY)
        )
      }

      val liqBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF14B8A6), Color(0xFF0D9488)),
        startY = 708f * scaleY,
        endY = 792f * scaleY
      )

      val pipeBrush = Brush.horizontalGradient(
        colors = listOf(Color(0xFF0D9488), Color(0xFF14B8A6), Color(0xFF0D9488)),
        startX = 340f * scaleX,
        endX = 710f * scaleX
      )

      val column1Brush = Brush.horizontalGradient(
        colors = listOf(Color(0xFF1E3A5F), Color(0xFF3B82F6), Color(0xFF60A5FA), Color(0xFF2563EB), Color(0xFF0F2341)),
        startX = 380f * scaleX,
        endX = 470f * scaleX
      )

      val column2Brush = Brush.horizontalGradient(
        colors = listOf(Color(0xFF1E3A5F), Color(0xFF3B82F6), Color(0xFF60A5FA), Color(0xFF2563EB), Color(0xFF0F2341)),
        startX = 500f * scaleX,
        endX = 580f * scaleX
      )

      // ground
      drawRoundRect(
        color = Color(0xE6FFFFFF),
        topLeft = Offset(150f * scaleX, 800f * scaleY),
        size = Size(724f * scaleX, 24f * scaleY),
        cornerRadius = CornerRadius(12f * scaleX, 12f * scaleY)
      )

      // pipe rack
      drawRoundRect(
        brush = pipeBrush,
        topLeft = Offset(340f * scaleX, 740f * scaleY),
        size = Size(370f * scaleX, 14f * scaleY),
        cornerRadius = CornerRadius(7f * scaleX, 7f * scaleY)
      )
      drawRect(
        brush = pipeBrush,
        topLeft = Offset(470f * scaleX, 410f * scaleY),
        size = Size(30f * scaleX, 14f * scaleY)
      )
      drawRect(
        brush = pipeBrush,
        topLeft = Offset(580f * scaleX, 480f * scaleY),
        size = Size(120f * scaleX, 14f * scaleY)
      )

      // storage tank (left)
      drawRoundRect(
        color = Color(0x26FFFFFF),
        topLeft = Offset(200f * scaleX, 640f * scaleY),
        size = Size(140f * scaleX, 160f * scaleY),
        cornerRadius = CornerRadius(22f * scaleX, 22f * scaleY)
      )
      drawRoundRect(
        color = Color.White,
        topLeft = Offset(200f * scaleX, 640f * scaleY),
        size = Size(140f * scaleX, 160f * scaleY),
        cornerRadius = CornerRadius(22f * scaleX, 22f * scaleY),
        style = Stroke(width = 12f * scaleX)
      )
      // liquid in tank
      drawRoundRect(
        brush = liqBrush,
        topLeft = Offset(212f * scaleX, 708f * scaleY),
        size = Size(116f * scaleX, 84f * scaleY),
        cornerRadius = CornerRadius(12f * scaleX, 12f * scaleY)
      )

      // distillation column 1
      drawRoundRect(
        brush = column1Brush,
        topLeft = Offset(380f * scaleX, 260f * scaleY),
        size = Size(90f * scaleX, 540f * scaleY),
        cornerRadius = CornerRadius(45f * scaleX, 45f * scaleY)
      )
      // column 1 tray lines
      val blueLineColor = Color(0xFF0F2341)
      val strokeLine = 8f * scaleX
      val lineYs1 = listOf(360f, 440f, 520f, 600f, 680f)
      for (y in lineYs1) {
        drawLine(
          color = blueLineColor,
          start = Offset(392f * scaleX, y * scaleY),
          end = Offset(458f * scaleX, y * scaleY),
          strokeWidth = strokeLine,
          cap = StrokeCap.Round
        )
      }

      // column 2
      drawRoundRect(
        brush = column2Brush,
        topLeft = Offset(500f * scaleX, 340f * scaleY),
        size = Size(80f * scaleX, 460f * scaleY),
        cornerRadius = CornerRadius(40f * scaleX, 40f * scaleY)
      )
      val lineYs2 = listOf(430f, 510f, 590f, 670f)
      for (y in lineYs2) {
        drawLine(
          color = blueLineColor,
          start = Offset(512f * scaleX, y * scaleY),
          end = Offset(568f * scaleX, y * scaleY),
          strokeWidth = strokeLine,
          cap = StrokeCap.Round
        )
      }

      // flare stack
      drawRoundRect(
        color = Color(0xF5FFFFFF),
        topLeft = Offset(700f * scaleX, 300f * scaleY),
        size = Size(18f * scaleX, 500f * scaleY),
        cornerRadius = CornerRadius(9f * scaleX, 9f * scaleY)
      )

      // flare flame (outer orange)
      val flamePathOuter = Path().apply {
        moveTo(709f * scaleX, 300f * scaleY)
        cubicTo(
          676f * scaleX, 262f * scaleY,
          696f * scaleX, 232f * scaleY,
          709f * scaleX, 188f * scaleY
        )
        cubicTo(
          722f * scaleX, 232f * scaleY,
          744f * scaleX, 262f * scaleY,
          709f * scaleX, 300f * scaleY
        )
        close()
      }
      val outerFlameBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFFEF08A), Color(0xFFF97316), Color(0xFFEF4444)),
        startY = 188f * scaleY,
        endY = 300f * scaleY
      )
      drawPath(flamePathOuter, outerFlameBrush)

      // flare flame (inner yellow)
      val flamePathInner = Path().apply {
        moveTo(709f * scaleX, 292f * scaleY)
        cubicTo(
          694f * scaleX, 274f * scaleY,
          702f * scaleX, 258f * scaleY,
          709f * scaleX, 238f * scaleY
        )
        cubicTo(
          716f * scaleX, 258f * scaleY,
          724f * scaleX, 274f * scaleY,
          709f * scaleX, 292f * scaleY
        )
        close()
      }
      val innerFlameBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFFEF08A), Color(0xFFFDE68A)),
        startY = 238f * scaleY,
        endY = 292f * scaleY
      )
      drawPath(flamePathInner, innerFlameBrush)
    }
  }
}
