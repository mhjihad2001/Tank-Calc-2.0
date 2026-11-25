package com.example.engine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.data.UnitType
import com.example.ui.theme.LocalSunlightMode
import java.util.Locale

object UnitUtils {
  const val LITRES_PER_M3 = 1000.0
  const val LITRES_PER_BBL = 158.987295

  fun unitLabel(unit: UnitType): String = when (unit) {
    UnitType.LITRES -> "L"
    UnitType.M3 -> "m³"
    UnitType.BBL -> "bbl"
  }

  fun unitFullName(unit: UnitType): String = when (unit) {
    UnitType.LITRES -> "Litres (L)"
    UnitType.M3 -> "Cubic Metres (m³)"
    UnitType.BBL -> "Barrels (bbl)"
  }

  fun unitRateLabel(unit: UnitType): String = when (unit) {
    UnitType.LITRES -> "L/hr"
    UnitType.M3 -> "m³/hr"
    UnitType.BBL -> "bbl/hr"
  }

  fun defaultDecimals(unit: UnitType, settings: AppSettings? = null): Int {
    return when (unit) {
      UnitType.LITRES -> settings?.decimalPlacesL ?: 0
      UnitType.M3 -> settings?.decimalPlacesM3 ?: 3
      UnitType.BBL -> settings?.decimalPlacesBbl ?: 2
    }
  }

  /**
   * Converts volume stored in litres to the selected unit for display.
   */
  fun toDisplay(litres: Double, unit: UnitType): Double {
    return when (unit) {
      UnitType.LITRES -> litres
      UnitType.M3 -> litres / LITRES_PER_M3
      UnitType.BBL -> litres / LITRES_PER_BBL
    }
  }

  /**
   * Converts input typed in selected unit back to litres for internal storage.
   */
  fun toLitres(displayValue: Double, unit: UnitType): Double {
    return when (unit) {
      UnitType.LITRES -> displayValue
      UnitType.M3 -> displayValue * LITRES_PER_M3
      UnitType.BBL -> displayValue * LITRES_PER_BBL
    }
  }

  fun formatVolume(
    litres: Double,
    unit: UnitType,
    decimals: Int? = null,
    includeUnit: Boolean = true,
    settings: AppSettings? = null
  ): String {
    val displayVal = toDisplay(litres, unit)
    val dec = decimals ?: defaultDecimals(unit, settings)
    val formatted = if (dec <= 0) {
      String.format(Locale.US, "%,.0f", displayVal)
    } else {
      String.format(Locale.US, "%,.${dec}f", displayVal)
    }
    return if (includeUnit) "$formatted ${unitLabel(unit)}" else formatted
  }

  fun formatRate(
    litresPerHour: Double,
    unit: UnitType,
    decimals: Int? = null,
    settings: AppSettings? = null
  ): String {
    val displayVal = toDisplay(litresPerHour, unit)
    val dec = decimals ?: defaultDecimals(unit, settings)
    val formatted = if (dec <= 0) {
      String.format(Locale.US, "%,.0f", displayVal)
    } else {
      String.format(Locale.US, "%,.${dec}f", displayVal)
    }
    return "$formatted ${unitRateLabel(unit)}"
  }
}

/**
 * Compact single-pill Unit Selector (L / m³ / bbl) with dropdown menu.
 * Replaces the previous 3-chip horizontal row (~160dp) to prevent TopAppBar title clipping.
 */
@Composable
fun UnitQuickSwitchChip(
  currentUnit: UnitType,
  onUnitChange: (UnitType) -> Unit,
  modifier: Modifier = Modifier
) {
  var expanded by remember { mutableStateOf(false) }
  val isSunlight = LocalSunlightMode.current

  Box(modifier = modifier) {
    Surface(
      shape = RoundedCornerShape(8.dp),
      color = if (isSunlight) Color.White else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
      border = BorderStroke(
        width = if (isSunlight) 1.5.dp else 1.dp,
        color = if (isSunlight) Color.Black else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
      ),
      modifier = Modifier
        .height(34.dp)
        .clickable { expanded = true }
        .testTag("unit_quick_switch")
    ) {
      Row(
        modifier = Modifier.padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = UnitUtils.unitLabel(currentUnit),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.ExtraBold,
          color = if (isSunlight) Color.Black else MaterialTheme.colorScheme.onPrimaryContainer,
          maxLines = 1
        )
        Icon(
          imageVector = Icons.Default.ArrowDropDown,
          contentDescription = "Switch Unit",
          tint = if (isSunlight) Color.Black else MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(16.dp)
        )
      }
    }

    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }
    ) {
      UnitType.values().forEach { unit ->
        val selected = unit == currentUnit
        DropdownMenuItem(
          text = {
            Text(
              text = UnitUtils.unitFullName(unit),
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
              color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
          },
          trailingIcon = if (selected) {
            {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
              )
            }
          } else null,
          onClick = {
            onUnitChange(unit)
            expanded = false
          }
        )
      }
    }
  }
}

