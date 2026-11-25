package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun parseHexColor(colorHex: String?, fallback: Color = Color(0xFF1565C0)): Color {
  if (colorHex.isNullOrBlank()) return fallback
  return try {
    val clean = colorHex.trim().removePrefix("#")
    val colorLong = clean.toLong(16)
    if (clean.length == 6) {
      Color(0xFF000000 or colorLong)
    } else if (clean.length == 8) {
      Color(colorLong)
    } else {
      fallback
    }
  } catch (_: Exception) {
    fallback
  }
}

@Composable
fun ProductBadge(
  name: String,
  shortCode: String? = null,
  colorHex: String? = null,
  modifier: Modifier = Modifier,
  showDotOnly: Boolean = false
) {
  val color = parseHexColor(colorHex, MaterialTheme.colorScheme.primary)

  if (showDotOnly) {
    Box(
      modifier = modifier
        .size(10.dp)
        .clip(CircleShape)
        .background(color)
    )
    return
  }

  val label = if (!shortCode.isNullOrBlank()) "$name ($shortCode)" else name

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(color.copy(alpha = 0.15f))
      .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
      .padding(horizontal = 8.dp, vertical = 3.dp)
  ) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(color)
    )
    Spacer(modifier = Modifier.width(5.dp))
    Text(
      text = label,
      color = color,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold
    )
  }
}
