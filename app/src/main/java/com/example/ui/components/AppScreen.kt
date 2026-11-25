package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalSunlightMode

val LocalToggleSunlightMode = compositionLocalOf<() -> Unit> { {} }

object AppDimens {
  val screenPadding: Dp = 16.dp
  val sectionSpacing: Dp = 16.dp
  val cardPadding: Dp = 16.dp
  val itemSpacing: Dp = 12.dp
  val smallSpacing: Dp = 8.dp
  val cornerRadius: Dp = 12.dp
}

/**
 * Compact, high-visibility Yard / Sunlight Mode toggle button for TopAppBars.
 * Uses a sleek 36dp badge to preserve horizontal space for titles and primary actions.
 */
@Composable
fun SunlightModeToggleAction(
  modifier: Modifier = Modifier,
  showLabel: Boolean = false
) {
  val isSunlight = LocalSunlightMode.current
  val onToggle = LocalToggleSunlightMode.current
  val haptics = com.example.engine.rememberHapticController()

  Surface(
    shape = RoundedCornerShape(8.dp),
    color = if (isSunlight) Color(0xFF0B2A5B) else MaterialTheme.colorScheme.surfaceVariant,
    border = if (isSunlight) BorderStroke(1.5.dp, Color.Black) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    modifier = modifier
      .height(34.dp)
      .clickable {
        haptics.trigger(com.example.engine.HapticEvent.MODE_SWITCH)
        onToggle()
      }
      .testTag("sunlight_mode_toggle")
  ) {
    Row(
      modifier = Modifier.padding(horizontal = if (showLabel) 10.dp else 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Icon(
        imageVector = if (isSunlight) Icons.Filled.WbSunny else Icons.Outlined.WbSunny,
        contentDescription = if (isSunlight) "Yard Sunlight Mode ON" else "Enable Yard Sunlight Mode",
        tint = if (isSunlight) Color(0xFFFACC15) else MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp)
      )
      if (showLabel) {
        Text(
          text = if (isSunlight) "YARD ON" else "Yard Mode",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.ExtraBold,
          color = if (isSunlight) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
  title: String,
  subtitle: String? = null,
  onBack: (() -> Unit)? = null,
  showSunlightToggle: Boolean = true,
  actions: @Composable RowScope.() -> Unit = {},
  content: @Composable () -> Unit
) {
  val isSunlight = LocalSunlightMode.current
  Scaffold(
    contentWindowInsets = WindowInsets.statusBars,
    topBar = {
      Column {
        TopAppBar(
          title = {
            Column(verticalArrangement = Arrangement.Center) {
              Text(
                text = title,
                fontWeight = if (isSunlight) FontWeight.Black else FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSunlight) Color.Black else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (!subtitle.isNullOrBlank()) {
                Text(
                  text = subtitle,
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isSunlight) Color(0xFF0B2A5B) else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          },
          navigationIcon = {
            if (onBack != null) {
              val haptics = com.example.engine.rememberHapticController()
              IconButton(onClick = {
                haptics.trigger(com.example.engine.HapticEvent.NAV_STEP)
                onBack()
              }) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = "Back",
                  tint = if (isSunlight) Color.Black else MaterialTheme.colorScheme.onSurface
                )
              }
            }
          },
          actions = {
            if (showSunlightToggle) {
              SunlightModeToggleAction(modifier = Modifier.padding(end = 4.dp))
            }
            actions()
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (isSunlight) Color.White else MaterialTheme.colorScheme.surface
          )
        )
        HorizontalDivider(
          thickness = if (isSunlight) 1.5.dp else 0.5.dp,
          color = if (isSunlight) Color.Black else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
      }
    },
    containerColor = if (isSunlight) Color.White else MaterialTheme.colorScheme.background
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      content()
    }
  }
}

@Composable
fun AppScreen(
  modifier: Modifier = Modifier,
  scrollable: Boolean = true,
  content: @Composable ColumnScope.() -> Unit
) {
  val scrollModifier = if (scrollable) {
    Modifier.verticalScroll(rememberScrollState())
  } else {
    Modifier
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .then(scrollModifier)
      .padding(AppDimens.screenPadding),
    verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing),
    content = content
  )
}

@Composable
fun AppHeader(
  title: String,
  subtitle: String? = null,
  actions: @Composable RowScope.() -> Unit = {}
) {
  val isSunlight = LocalSunlightMode.current
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = if (isSunlight) FontWeight.Black else FontWeight.Bold,
          color = if (isSunlight) Color.Black else MaterialTheme.colorScheme.primary
        )
        if (subtitle != null) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSunlight) FontWeight.Bold else FontWeight.Normal,
            color = if (isSunlight) Color(0xFF0B2A5B) else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Row(
        horizontalArrangement = Arrangement.spacedBy(AppDimens.smallSpacing),
        verticalAlignment = Alignment.CenterVertically,
        content = {
          SunlightModeToggleAction()
          actions()
        }
      )
    }
    HorizontalDivider(
      modifier = Modifier.padding(top = 8.dp),
      thickness = if (isSunlight) 2.dp else 1.dp,
      color = if (isSunlight) Color(0xFF0B2A5B) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
  }
}

@Composable
fun AppSectionCard(
  modifier: Modifier = Modifier,
  title: String? = null,
  containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
  content: @Composable ColumnScope.() -> Unit
) {
  val isSunlight = LocalSunlightMode.current
  ElevatedCard(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(AppDimens.cornerRadius),
    colors = CardDefaults.elevatedCardColors(
      containerColor = if (isSunlight) Color.White else containerColor
    ),
    elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isSunlight) 0.dp else 2.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .then(
          if (isSunlight) {
            Modifier.background(Color.White)
          } else {
            Modifier
          }
        )
        .padding(AppDimens.cardPadding),
      verticalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
    ) {
      if (title != null) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = if (isSunlight) FontWeight.Black else FontWeight.Bold,
          color = if (isSunlight) Color(0xFF0B2A5B) else MaterialTheme.colorScheme.primary
        )
      }
      content()
    }
  }
}
