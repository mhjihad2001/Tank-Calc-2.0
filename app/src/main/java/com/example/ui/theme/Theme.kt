package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalSunlightMode = compositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
  primary = PrimaryBlueDark,
  onPrimary = OnPrimaryDark,
  primaryContainer = PrimaryContainerDark,
  onPrimaryContainer = OnPrimaryContainerDark,
  secondary = SecondaryTealDark,
  onSecondary = OnSecondaryDark,
  secondaryContainer = SecondaryContainerDark,
  onSecondaryContainer = OnSecondaryContainerDark,
  tertiary = TertiaryBlueDark,
  background = BackgroundDark,
  onBackground = Color(0xFFF8FAFC),
  surface = SurfaceDark,
  onSurface = Color(0xFFF8FAFC),
  surfaceVariant = SurfaceVariantDark,
  onSurfaceVariant = OnSurfaceVariantDark,
  outline = OutlineDark
)

private val LightColorScheme = lightColorScheme(
  primary = PrimaryBlue,
  onPrimary = OnPrimaryWhite,
  primaryContainer = PrimaryContainerBlue,
  onPrimaryContainer = OnPrimaryContainerBlue,
  secondary = SecondaryTeal,
  onSecondary = OnSecondaryWhite,
  secondaryContainer = SecondaryContainerTeal,
  onSecondaryContainer = OnSecondaryContainerTeal,
  tertiary = TertiaryBlue,
  onTertiary = OnTertiaryWhite,
  tertiaryContainer = TertiaryContainerBlue,
  onTertiaryContainer = OnTertiaryContainerBlue,
  background = BackgroundLight,
  onBackground = Color(0xFF0F172A),
  surface = SurfaceLight,
  onSurface = Color(0xFF0F172A),
  surfaceVariant = SurfaceVariantLight,
  onSurfaceVariant = OnSurfaceVariantLight,
  outline = OutlineLight
)

// Ultra-High-Contrast Outdoor Yard / Sunlight Color Scheme
private val SunlightColorScheme = lightColorScheme(
  primary = SunlightPrimary,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFE0ECFF),
  onPrimaryContainer = Color.Black,
  secondary = SunlightSecondary,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFCCFBF1),
  onSecondaryContainer = Color.Black,
  tertiary = DeepBluePrimary,
  onTertiary = Color.White,
  background = SunlightBackground,
  onBackground = SunlightText,
  surface = SunlightSurface,
  onSurface = SunlightText,
  surfaceVariant = SunlightSurfaceVariant,
  onSurfaceVariant = SunlightText,
  outline = SunlightOutline,
  outlineVariant = Color.Black,
  error = Color(0xFFB91C1C),
  onError = Color.White,
  errorContainer = Color(0xFFFEE2E2),
  onErrorContainer = Color.Black
)

@Composable
fun TankCalcTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  sunlightMode: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit
) {
  MyApplicationTheme(
    darkTheme = darkTheme,
    sunlightMode = sunlightMode,
    dynamicColor = dynamicColor,
    content = content
  )
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  sunlightMode: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit
) {
  val colorScheme = when {
    sunlightMode -> SunlightColorScheme
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  CompositionLocalProvider(LocalSunlightMode provides sunlightMode) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
  }
}
