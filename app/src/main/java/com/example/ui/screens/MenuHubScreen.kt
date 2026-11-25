package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.TankRepository
import com.example.data.TransactionType
import com.example.engine.UnitQuickSwitchChip
import com.example.ui.components.RefineryLogo
import com.example.ui.components.SunlightModeToggleAction
import com.example.ui.theme.LocalSunlightMode
import kotlinx.coroutines.launch

enum class MenuDestination {
  DIP_SINGLE,
  VOL_TO_DIP,
  BULK_DIP_MATRIX,
  TRANSFER_RECEIVE_DELIVERY,
  BLENDING,
  PRODUCTION_REPORT,
  PROCESS_LOSS,
  DAILY_INJECTION,
  SOLUTION_MAKING,
  CRU_WAIT,
  N2_CALC,
  VCF_CALCULATOR,
  TANK_MASTER,
  CALIBRATION_EDITOR,
  PRODUCT_MASTER,
  CLOUD_IMPORT,
  SAVED_DRAFTS,
  SETTINGS_BACKUP
}

private data class MenuModuleItem(
  val destination: MenuDestination,
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val accentColor: Color,
  val badgeText: String? = null
)

private data class MenuCategorySection(
  val title: String,
  val subtitle: String,
  val items: List<MenuModuleItem>
)

@Composable
fun MenuHubScreen(
  repository: TankRepository,
  onNavigate: (MenuDestination) -> Unit
) {
  val isSunlight = LocalSunlightMode.current
  val settings by repository.settings.collectAsState()
  val tanks by repository.tanks.collectAsState()
  val products by repository.products.collectAsState()
  val drafts by repository.drafts.collectAsState()
  var searchQuery by remember { mutableStateOf("") }

  val draftCount = drafts.size
  val calibratedCount = remember(tanks) { tanks.count { it.dipTable.isNotEmpty() || it.n2Table.isNotEmpty() } }

  val deepBlue = Color(0xFF0B2A5B)
  val royalBlue = Color(0xFF0E5AA7)
  val teal = Color(0xFF14B8A6)
  val amber = Color(0xFFD97706)
  val emerald = Color(0xFF059669)
  val indigo = Color(0xFF4F46E5)
  val crimson = Color(0xFFDC2626)
  val slate = Color(0xFF475569)

  val sections = remember(draftCount, tanks.size, products.size, settings.savedCloudBackupUrl) {
    listOf(
      MenuCategorySection(
        title = "Tank Farm & Volume Operations",
        subtitle = "Dip gauging, matrix entry, transfers & blending",
        items = listOf(
          MenuModuleItem(
            destination = MenuDestination.DIP_SINGLE,
            title = "Dip → Volume",
            subtitle = "Single tank level & ullage",
            icon = Icons.Default.Height,
            accentColor = royalBlue
          ),
          MenuModuleItem(
            destination = MenuDestination.VOL_TO_DIP,
            title = "Volume → Dip",
            subtitle = "Inverse target dip lookup",
            icon = Icons.Default.Straighten,
            accentColor = deepBlue
          ),
          MenuModuleItem(
            destination = MenuDestination.BULK_DIP_MATRIX,
            title = "Bulk Dip Matrix",
            subtitle = "Multi-tank Excel dip sheet",
            icon = Icons.Default.GridOn,
            accentColor = teal,
            badgeText = "MATRIX"
          ),
          MenuModuleItem(
            destination = MenuDestination.TRANSFER_RECEIVE_DELIVERY,
            title = "Tank Transfer",
            subtitle = "Receive & delivery logs",
            icon = Icons.Default.SwapHoriz,
            accentColor = emerald
          ),
          MenuModuleItem(
            destination = MenuDestination.BLENDING,
            title = "Product Blending",
            subtitle = "Multi-stage blend programs",
            icon = Icons.Default.Science,
            accentColor = indigo
          )
        )
      ),
      MenuCategorySection(
        title = "Daily Reports & Reconciliation",
        subtitle = "Shift production balance, ERP & process loss",
        items = listOf(
          MenuModuleItem(
            destination = MenuDestination.PRODUCTION_REPORT,
            title = "Production & ERP",
            subtitle = "Natural, 30 °C & ERP PDF",
            icon = Icons.Default.Summarize,
            accentColor = deepBlue
          ),
          MenuModuleItem(
            destination = MenuDestination.PROCESS_LOSS,
            title = "Process Loss",
            subtitle = "Feed vs product yield audit",
            icon = Icons.Default.Assessment,
            accentColor = crimson
          )
        )
      ),
      MenuCategorySection(
        title = "Process & Chemical Engineering",
        subtitle = "Dosing, recipes, reactor WAIT, N2 & ASTM VCF",
        items = listOf(
          MenuModuleItem(
            destination = MenuDestination.DAILY_INJECTION,
            title = "Chemical Injection",
            subtitle = "Daily dosing & wt-ppm logs",
            icon = Icons.Default.WaterDrop,
            accentColor = teal
          ),
          MenuModuleItem(
            destination = MenuDestination.SOLUTION_MAKING,
            title = "Solution Making",
            subtitle = "Batch dilution recipes",
            icon = Icons.Default.Biotech,
            accentColor = emerald
          ),
          MenuModuleItem(
            destination = MenuDestination.CRU_WAIT,
            title = "CRU WAIT",
            subtitle = "Reactor beds & SOR activity",
            icon = Icons.Default.Thermostat,
            accentColor = amber
          ),
          MenuModuleItem(
            destination = MenuDestination.N2_CALC,
            title = "N2 Water Column",
            subtitle = "Storage tank interpolation",
            icon = Icons.Default.Cloud,
            accentColor = royalBlue
          ),
          MenuModuleItem(
            destination = MenuDestination.VCF_CALCULATOR,
            title = "ASTM VCF Calc",
            subtitle = "D1250-80 15 °C & 30 °C",
            icon = Icons.Default.Calculate,
            accentColor = indigo,
            badgeText = "D1250"
          )
        )
      ),
      MenuCategorySection(
        title = "Master Data, Calibration & System",
        subtitle = "Charts, product catalog, drafts & system",
        items = listOf(
          MenuModuleItem(
            destination = MenuDestination.TANK_MASTER,
            title = "Tank Master",
            subtitle = "${tanks.size} tanks • CSV / Excel",
            icon = Icons.Default.Storage,
            accentColor = deepBlue
          ),
          MenuModuleItem(
            destination = MenuDestination.CALIBRATION_EDITOR,
            title = "Calibration Editor",
            subtitle = "10mm base & 0..9 fractions",
            icon = Icons.Default.FactCheck,
            accentColor = teal
          ),
          MenuModuleItem(
            destination = MenuDestination.PRODUCT_MASTER,
            title = "Product Master",
            subtitle = "${products.size} products & densities",
            icon = Icons.Default.Category,
            accentColor = amber
          ),
          MenuModuleItem(
            destination = MenuDestination.CLOUD_IMPORT,
            title = "Cloud Drive Sync",
            subtitle = if (settings.savedCloudBackupUrl.isNotBlank()) "1-tap Drive / cloud link" else "Google Drive / URL import",
            icon = Icons.Default.CloudDownload,
            accentColor = emerald,
            badgeText = "DRIVE"
          ),
          MenuModuleItem(
            destination = MenuDestination.SAVED_DRAFTS,
            title = "Saved Drafts",
            subtitle = "Resume shift work inputs",
            icon = Icons.Default.Description,
            accentColor = slate,
            badgeText = if (draftCount > 0) "$draftCount" else null
          ),
          MenuModuleItem(
            destination = MenuDestination.SETTINGS_BACKUP,
            title = "App Settings",
            subtitle = "Units, haptics, reports & updates",
            icon = Icons.Default.Settings,
            accentColor = royalBlue
          )
        )
      )
    )
  }

  val filteredSections = remember(sections, searchQuery) {
    val q = searchQuery.trim().lowercase()
    if (q.isEmpty()) {
      sections
    } else {
      sections.mapNotNull { sec ->
        val matching = sec.items.filter {
          it.title.lowercase().contains(q) ||
            it.subtitle.lowercase().contains(q) ||
            sec.title.lowercase().contains(q)
        }
        if (matching.isEmpty()) null else sec.copy(items = matching)
      }
    }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(if (isSunlight) Color.White else MaterialTheme.colorScheme.background)
      .testTag("menu_hub_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. Executive Refinery Identity & Quick Control Banner
    item {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSunlight) Color.White else deepBlue,
        border = if (isSunlight) BorderStroke(2.dp, Color.Black) else null,
        shadowElevation = if (isSunlight) 0.dp else 4.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .then(
              if (!isSunlight) {
                Modifier.background(
                  Brush.linearGradient(
                    colors = listOf(deepBlue, royalBlue)
                  )
                )
              } else {
                Modifier
              }
            )
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
              ) {
                RefineryLogo(
                  modifier = Modifier.size(44.dp),
                  withBackground = true
                )
                Column {
                  Text(
                    text = settings.companyName.ifBlank { "PARTEX PETRO LTD." },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSunlight) Color.Black else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = settings.plantName.ifBlank { "Condensate Fractionation Plant" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSunlight) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSunlight) deepBlue else Color(0xFF99F6E4),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }

            // Quick status & utility strip (zero overlap, wraps cleanly)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              // Calibrated Tanks Pill
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSunlight) Color(0xFFF1F5F9) else Color.White.copy(alpha = 0.14f),
                border = if (isSunlight) BorderStroke(1.dp, Color.Black) else null,
                modifier = Modifier
                  .height(34.dp)
                  .clickable { onNavigate(MenuDestination.TANK_MASTER) }
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = if (isSunlight) deepBlue else Color(0xFF2DD4BF),
                    modifier = Modifier.size(15.dp)
                  )
                  Text(
                    text = "$calibratedCount/${tanks.size} Tanks",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSunlight) Color.Black else Color.White,
                    maxLines = 1
                  )
                }
              }

              Spacer(modifier = Modifier.weight(1f))

              // Active Unit Switcher
              UnitQuickSwitchChip(
                currentUnit = settings.unit,
                onUnitChange = { repository.updateSettings(settings.copy(unit = it)) }
              )

              // Yard Sunlight Mode Toggle
              SunlightModeToggleAction(showLabel = true)
            }
          }
        }
      }
    }

    // 2. Quick Search Bar
    item {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search tools, reports, calibration, VCF...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Modules") },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Default.Clear, contentDescription = "Clear Search")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("menu_search_input")
      )
    }

    // 3. Categorized Module Sections
    if (filteredSections.isEmpty()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.outline)
            Text("No matching modules for \"$searchQuery\"", fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { searchQuery = "" }) {
              Text("Show All Modules")
            }
          }
        }
      }
    } else {
      filteredSections.forEach { section ->
        item(key = "header_${section.title}") {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Text(
              text = section.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.ExtraBold,
              color = if (isSunlight) Color.Black else MaterialTheme.colorScheme.primary
            )
            Text(
              text = section.subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = if (isSunlight) Color(0xFF0B2A5B) else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        // Render items in neat 2-column rows so LazyColumn scrolls smoothly with zero nested scroll conflicts
        val rows = section.items.chunked(2)
        items(
          count = rows.size,
          key = { rowIdx -> "${section.title}_row_$rowIdx" }
        ) { rowIdx ->
          val pair = rows[rowIdx]
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            pair.forEach { item ->
              MenuModuleCard(
                item = item,
                isSunlight = isSunlight,
                onClick = { onNavigate(item.destination) },
                modifier = Modifier.weight(1f)
              )
            }
            if (pair.size == 1) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }
    }

    // 4. Footer Branding & Quick Update Checker
    item {
      val context = androidx.compose.ui.platform.LocalContext.current
      val scope = rememberCoroutineScope()
      val haptics = com.example.engine.rememberHapticController()
      val installedVer = remember(context) { com.example.engine.AppUpdateManager.getInstalledVersionInfo(context) }
      var isChecking by remember { mutableStateOf(false) }
      var menuUpdateAvailable by remember { mutableStateOf<com.example.engine.UpdateCheckResult.UpdateAvailable?>(null) }
      var menuUpdateStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 6.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        HorizontalDivider(
          color = if (isSunlight) Color.Black else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Tank Calc v${installedVer.second} (Build ${installedVer.first})",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = if (isSunlight) Color.Black else MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Developed by Meherab Hossain Jihad",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = if (isSunlight) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          OutlinedButton(
            enabled = !isChecking,
            onClick = {
              haptics.trigger(com.example.engine.HapticEvent.MODE_SWITCH)
              isChecking = true
              menuUpdateStatus = null
              scope.launch {
                val manifestUrl = settings.customUpdateManifestUrl
                  .ifBlank { com.example.engine.AppUpdateManager.DEFAULT_MANIFEST_URL }
                when (val res = com.example.engine.AppUpdateManager.checkForUpdates(context, manifestUrl)) {
                  is com.example.engine.UpdateCheckResult.UpdateAvailable -> {
                    isChecking = false
                    haptics.trigger(com.example.engine.HapticEvent.SUCCESS)
                    menuUpdateAvailable = res
                  }
                  is com.example.engine.UpdateCheckResult.UpToDate -> {
                    isChecking = false
                    haptics.trigger(com.example.engine.HapticEvent.SUCCESS)
                    menuUpdateStatus = Pair(false, "Up to date (v${res.currentVersionName}, Build ${res.currentBuild})")
                    android.widget.Toast.makeText(
                      context,
                      "Up to date (v${res.currentVersionName})",
                      android.widget.Toast.LENGTH_SHORT
                    ).show()
                  }
                  is com.example.engine.UpdateCheckResult.Error -> {
                    isChecking = false
                    haptics.trigger(com.example.engine.HapticEvent.WARNING_ALERT)
                    menuUpdateStatus = Pair(true, res.message)
                    android.widget.Toast.makeText(
                      context,
                      res.message,
                      android.widget.Toast.LENGTH_LONG
                    ).show()
                  }
                }
              }
            },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.testTag("menu_check_updates_button")
          ) {
            Icon(
              imageVector = Icons.Default.SystemUpdate,
              contentDescription = "Check for Updates",
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (isChecking) "Checking..." else "Check for Updates",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold
            )
          }
        }

        menuUpdateStatus?.let { (isErr, msg) ->
          Text(
            text = msg,
            style = MaterialTheme.typography.labelSmall,
            color = if (isErr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
          )
        }
      }

      if (menuUpdateAvailable != null) {
        val upd = menuUpdateAvailable!!
        com.example.ui.components.AppUpdateDialog(
          manifest = upd.manifest,
          currentVersionName = upd.currentVersionName,
          currentBuild = upd.currentBuild,
          onDismiss = {
            if (!upd.manifest.forceUpdate) {
              menuUpdateAvailable = null
            }
          }
        )
      }
    }
  }
}

@Composable
private fun MenuModuleCard(
  item: MenuModuleItem,
  isSunlight: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = if (isSunlight) Color.White else MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(
      width = if (isSunlight) 1.8.dp else 1.dp,
      color = if (isSunlight) Color.Black else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    ),
    shadowElevation = if (isSunlight) 0.dp else 1.dp,
    modifier = modifier
      .height(106.dp)
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .testTag("menu_item_${item.destination.name.lowercase()}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(12.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .background(
              color = if (isSunlight) item.accentColor else item.accentColor.copy(alpha = 0.14f),
              shape = RoundedCornerShape(10.dp)
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = if (isSunlight) Color.White else item.accentColor,
            modifier = Modifier.size(20.dp)
          )
        }

        if (item.badgeText != null) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isSunlight) Color.Black else item.accentColor.copy(alpha = 0.18f)
          ) {
            Text(
              text = item.badgeText,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.ExtraBold,
              color = if (isSunlight) Color.White else item.accentColor,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              maxLines = 1
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = item.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = if (isSunlight) FontWeight.Black else FontWeight.Bold,
          color = if (isSunlight) Color.Black else MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = item.subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = if (isSunlight) Color(0xFF0B2A5B) else MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
