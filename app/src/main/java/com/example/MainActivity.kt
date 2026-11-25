package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.TankRepository
import com.example.data.TransactionType
import com.example.engine.BackupManager
import com.example.engine.RestoreMode
import com.example.engine.RestorePreview
import com.example.ui.components.AppScreen
import com.example.ui.components.AuthSession
import com.example.ui.components.CloudImportDialog
import com.example.ui.components.CloudImportMode
import com.example.ui.components.PasswordDialog
import com.example.ui.components.RefineryLogo
import com.example.ui.components.VerifiedBackupRestoreFlow
import com.example.ui.screens.*
import com.example.ui.theme.TankCalcTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

enum class MainTab(val id: String, val label: String, val icon: ImageVector) {
  DIP("dip", "Dip", Icons.Default.Height),
  TRANSFER("transfer", "Transfer", Icons.Default.SwapHoriz),
  LOSS("loss", "Loss", Icons.Default.Assessment),
  PRODUCTION("production", "Production", Icons.Default.Summarize),
  MORE("more", "Menu", Icons.Default.GridView)
}

enum class MoreSubScreen {
  TANKS,
  CALIBRATION,
  INJECTION,
  SOLUTION,
  CRU_WAIT,
  N2_CALC,
  VCF_CALC,
  SETTINGS,
  DRAFTS
}

class MainActivity : ComponentActivity() {
  private lateinit var repository: TankRepository

  // Incoming "Open With / Share to Tank Calc" state (from Google Drive, WhatsApp, Telegram, Gmail)
  private val incomingIntentUri = MutableStateFlow<Uri?>(null)
  private val incomingSharedUrl = MutableStateFlow<String?>(null)

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    extractIncomingBackupIntent(intent)
  }

  private fun extractIncomingBackupIntent(intent: Intent?) {
    if (intent == null) return
    when (intent.action) {
      Intent.ACTION_VIEW -> {
        intent.data?.let { uri ->
          if (uri.scheme.equals("https", ignoreCase = true)) {
            incomingSharedUrl.value = uri.toString()
          } else {
            incomingIntentUri.value = uri
          }
        }
      }
      Intent.ACTION_SEND -> {
        val streamUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (streamUri != null) {
          incomingIntentUri.value = streamUri
        } else {
          val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
          if (!sharedText.isNullOrBlank()) {
            val urlMatch = Regex("""https://\S+""").find(sharedText)?.value
            if (!urlMatch.isNullOrBlank()) {
              incomingSharedUrl.value = urlMatch
            }
          }
        }
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    repository = TankRepository(applicationContext)
    extractIncomingBackupIntent(intent)
    enableEdgeToEdge()
    setContent {
      val settings by repository.settings.collectAsState()
      val toggleSunlightMode = remember(settings) {
        {
          repository.updateSettings(settings.copy(yardSunlightMode = !settings.yardSunlightMode))
        }
      }
      val hapticConfig = remember(settings.hapticsEnabled, settings.hapticIntensity) {
        com.example.engine.HapticConfig(
          enabled = settings.hapticsEnabled,
          intensity = settings.hapticIntensity
        )
      }
      CompositionLocalProvider(
        com.example.ui.components.LocalToggleSunlightMode provides toggleSunlightMode,
        com.example.engine.LocalHapticConfig provides hapticConfig
      ) {
        TankCalcTheme(sunlightMode = settings.yardSunlightMode) {
          com.example.ui.components.GlobalInAppKeyboardHost(enabled = settings.useInAppKeyboard) {
          val haptics = com.example.engine.rememberHapticController()
          val context = LocalContext.current
          var showSplash by remember { mutableStateOf(true) }
          val prefs = remember { context.getSharedPreferences("tank_calc_prefs", Context.MODE_PRIVATE) }
          var showOnboarding by remember {
            mutableStateOf(!prefs.getBoolean("onboarding_completed", false))
          }

          LaunchedEffect(Unit) {
            delay(1800L)
            showSplash = false
          }

          // Onboarding restore launcher
          val onboardingRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
              try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val json = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                val res = BackupManager.restoreBackup(repository, json, RestoreMode.REPLACE)
                if (res.success) {
                  prefs.edit().putBoolean("onboarding_completed", true).apply()
                  showOnboarding = false
                  Toast.makeText(context, "Setup loaded! All tanks and calibration ready.", Toast.LENGTH_LONG).show()
                } else {
                  Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                }
              } catch (e: Exception) {
                Toast.makeText(context, "Error reading file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
              }
            }
          }

          var selectedTab by remember { mutableStateOf(MainTab.DIP) }
          var moreSubScreen by remember { mutableStateOf<MoreSubScreen?>(null) }

          // Cross-tab deep-link states so Menu Hub can jump directly into sub-modes
          var requestedDipSubMode by remember { mutableStateOf<Int?>(null) }
          var requestedTransferType by remember { mutableStateOf<TransactionType?>(null) }

          // Direct Product Master bottom sheet launcher from Menu Hub (with password guard)
          var showProductManagerFromMenu by remember { mutableStateOf(false) }
          var pendingMenuAuthAction by remember { mutableStateOf<(() -> Unit)?>(null) }
          var showMenuPasswordDialog by remember { mutableStateOf(false) }

          // Global Cloud Drive Import & Share-to-TankCalc restore state
          var showGlobalCloudImportDialog by remember { mutableStateOf(false) }
          var globalCloudInitialUrl by remember { mutableStateOf<String?>(null) }
          var globalPendingJson by remember { mutableStateOf<String?>(null) }
          var globalRestorePreview by remember { mutableStateOf<RestorePreview?>(null) }
          var globalRestoreSource by remember { mutableStateOf("") }
          var globalRestoreFromOnboarding by remember { mutableStateOf(false) }

          val sharedUri by incomingIntentUri.collectAsState()
          val sharedUrl by incomingSharedUrl.collectAsState()

          // In-App Auto-Update state
          var availableUpdate by remember { mutableStateOf<com.example.engine.UpdateCheckResult.UpdateAvailable?>(null) }

          LaunchedEffect(Unit) {
            try {
              val res = com.example.engine.AppUpdateManager.checkForUpdates(context)
              if (res is com.example.engine.UpdateCheckResult.UpdateAvailable) {
                availableUpdate = res
              }
            } catch (_: Exception) {
              // Fail silently when offline
            }
          }

          // Handle incoming .json file opened/shared from Google Drive, WhatsApp, Telegram, Gmail
          LaunchedEffect(sharedUri) {
            val uri = sharedUri ?: return@LaunchedEffect
            incomingIntentUri.value = null
            try {
              val (json, previewRes) = withContext(Dispatchers.IO) {
                val maxBytes = 15 * 1024 * 1024
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                  val out = ByteArrayOutputStream()
                  val buf = ByteArray(16 * 1024)
                  var total = 0
                  while (true) {
                    val read = input.read(buf)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) throw IllegalArgumentException("File exceeds 15 MB limit.")
                    out.write(buf, 0, read)
                  }
                  out.toByteArray()
                } ?: throw IllegalArgumentException("Could not open shared file.")

                val text = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF')
                Pair(text, BackupManager.previewBackup(text))
              }
              previewRes.fold(
                onSuccess = { preview ->
                  showSplash = false
                  globalPendingJson = json
                  globalRestorePreview = preview
                  globalRestoreSource = "Shared directly to Tank Calc"
                  globalRestoreFromOnboarding = showOnboarding
                },
                onFailure = { err ->
                  Toast.makeText(context, "Shared file is not a valid Tank Calc backup: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                }
              )
            } catch (e: Exception) {
              Toast.makeText(context, "Could not read shared file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
          }

          // Handle incoming HTTPS cloud link shared to Tank Calc
          LaunchedEffect(sharedUrl) {
            val url = sharedUrl ?: return@LaunchedEffect
            incomingSharedUrl.value = null
            showSplash = false
            globalCloudInitialUrl = url
            globalRestoreFromOnboarding = showOnboarding
            showGlobalCloudImportDialog = true
          }

          fun requireMenuAuth(action: () -> Unit) {
            if (AuthSession.isUnlocked()) {
              action()
            } else {
              pendingMenuAuthAction = action
              showMenuPasswordDialog = true
            }
          }

          // Unified handler for all 17 Menu Hub destinations
          val onMenuNavigate: (MenuDestination) -> Unit = { dest ->
            when (dest) {
              MenuDestination.DIP_SINGLE -> {
                selectedTab = MainTab.DIP
                requestedDipSubMode = 0
                moreSubScreen = null
              }
              MenuDestination.VOL_TO_DIP -> {
                selectedTab = MainTab.DIP
                requestedDipSubMode = 1
                moreSubScreen = null
              }
              MenuDestination.BULK_DIP_MATRIX -> {
                selectedTab = MainTab.DIP
                requestedDipSubMode = 2
                moreSubScreen = null
              }
              MenuDestination.TRANSFER_RECEIVE_DELIVERY -> {
                selectedTab = MainTab.TRANSFER
                requestedTransferType = TransactionType.RECEIVE
                moreSubScreen = null
              }
              MenuDestination.BLENDING -> {
                selectedTab = MainTab.TRANSFER
                requestedTransferType = TransactionType.BLEND
                moreSubScreen = null
              }
              MenuDestination.PRODUCTION_REPORT -> {
                selectedTab = MainTab.PRODUCTION
                moreSubScreen = null
              }
              MenuDestination.PROCESS_LOSS -> {
                selectedTab = MainTab.LOSS
                moreSubScreen = null
              }
              MenuDestination.DAILY_INJECTION -> {
                selectedTab = MainTab.MORE
                moreSubScreen = MoreSubScreen.INJECTION
              }
              MenuDestination.SOLUTION_MAKING -> {
                selectedTab = MainTab.MORE
                moreSubScreen = MoreSubScreen.SOLUTION
              }
              MenuDestination.CRU_WAIT -> {
                selectedTab = MainTab.MORE
                moreSubScreen = MoreSubScreen.CRU_WAIT
              }
              MenuDestination.N2_CALC -> {
                selectedTab = MainTab.MORE
                moreSubScreen = MoreSubScreen.N2_CALC
              }
              MenuDestination.VCF_CALCULATOR -> {
                selectedTab = MainTab.MORE
                moreSubScreen = MoreSubScreen.VCF_CALC
              }
              MenuDestination.TANK_MASTER -> {
                selectedTab = MainTab.MORE
                moreSubScreen = MoreSubScreen.TANKS
              }
              MenuDestination.CALIBRATION_EDITOR -> {
                selectedTab = MainTab.MORE
                moreSubScreen = MoreSubScreen.CALIBRATION
              }
              MenuDestination.PRODUCT_MASTER -> {
                showProductManagerFromMenu = true
              }
              MenuDestination.CLOUD_IMPORT -> {
                globalCloudInitialUrl = null
                globalRestoreFromOnboarding = false
                showGlobalCloudImportDialog = true
              }
              MenuDestination.SAVED_DRAFTS -> {
                selectedTab = MainTab.MORE
                moreSubScreen = MoreSubScreen.DRAFTS
              }
              MenuDestination.SETTINGS_BACKUP -> {
                selectedTab = MainTab.MORE
                moreSubScreen = MoreSubScreen.SETTINGS
              }
            }
          }

          // Navigation handler for resuming drafts
          val onOpenDraft: (String, String) -> Unit = { moduleId, _ ->
            when (moduleId) {
              "dip_calc" -> {
                selectedTab = MainTab.DIP
                requestedDipSubMode = 0
                moreSubScreen = null
              }
              "vol_to_dip" -> {
                selectedTab = MainTab.DIP
                requestedDipSubMode = 1
                moreSubScreen = null
              }
              "transfer_blend" -> {
                selectedTab = MainTab.TRANSFER
                moreSubScreen = null
              }
              "loss_calc" -> {
                selectedTab = MainTab.LOSS
                moreSubScreen = null
              }
              "production_report" -> {
                selectedTab = MainTab.PRODUCTION
                moreSubScreen = null
              }
              "n2_calc" -> {
                selectedTab = MainTab.MORE
                moreSubScreen = MoreSubScreen.N2_CALC
              }
              "injection" -> {
                selectedTab = MainTab.MORE
                moreSubScreen = MoreSubScreen.INJECTION
              }
              "solution" -> {
                selectedTab = MainTab.MORE
                moreSubScreen = MoreSubScreen.SOLUTION
              }
              "cru_wait" -> {
                selectedTab = MainTab.MORE
                moreSubScreen = MoreSubScreen.CRU_WAIT
              }
              "vcf" -> {
                selectedTab = MainTab.MORE
                moreSubScreen = MoreSubScreen.VCF_CALC
              }
              else -> {
                selectedTab = MainTab.MORE
                moreSubScreen = null
              }
            }
          }

          Box(modifier = Modifier.fillMaxSize()) {
            // Main content area - keeping all 5 tabs mounted for instant switching
            Box(
              modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 64.dp)
            ) {
              // Tab 0: Dip (Single / Vol→Dip / Bulk Matrix)
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .graphicsLayer {
                    alpha = if (selectedTab == MainTab.DIP) 1f else 0f
                    translationX = if (selectedTab == MainTab.DIP) 0f else 100000f
                  }
              ) {
                DipCalcScreen(
                  repository = repository,
                  requestedSubMode = requestedDipSubMode,
                  onSubModeHandled = { requestedDipSubMode = null }
                )
              }

              // Tab 1: Transfer & Blending
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .graphicsLayer {
                    alpha = if (selectedTab == MainTab.TRANSFER) 1f else 0f
                    translationX = if (selectedTab == MainTab.TRANSFER) 0f else 100000f
                  }
              ) {
                TransferScreen(
                  repository = repository,
                  requestedTxType = requestedTransferType,
                  onTxTypeHandled = { requestedTransferType = null }
                )
              }

              // Tab 2: Loss
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .graphicsLayer {
                    alpha = if (selectedTab == MainTab.LOSS) 1f else 0f
                    translationX = if (selectedTab == MainTab.LOSS) 0f else 100000f
                  }
              ) {
                LossScreen(repository)
              }

              // Tab 3: Production
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .graphicsLayer {
                    alpha = if (selectedTab == MainTab.PRODUCTION) 1f else 0f
                    translationX = if (selectedTab == MainTab.PRODUCTION) 0f else 100000f
                  }
              ) {
                ProductionReportScreen(repository)
              }

              // Tab 4: Categorized Operations Menu Hub & Sub-Modules
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .graphicsLayer {
                    alpha = if (selectedTab == MainTab.MORE) 1f else 0f
                    translationX = if (selectedTab == MainTab.MORE) 0f else 100000f
                  }
              ) {
                MoreNavigationContainer(
                  isActiveTab = selectedTab == MainTab.MORE,
                  subScreen = moreSubScreen,
                  onNavigate = onMenuNavigate,
                  onBack = { moreSubScreen = null },
                  repository = repository,
                  onOpenDraft = onOpenDraft
                )
              }
            }

            // Bottom navigation bar (strictly 5 items, 64dp height + navigationBars inset)
            Surface(
              color = if (settings.yardSunlightMode) Color.White else MaterialTheme.colorScheme.surfaceContainer,
              tonalElevation = if (settings.yardSunlightMode) 0.dp else 3.dp,
              shadowElevation = if (settings.yardSunlightMode) 0.dp else 8.dp,
              modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .windowInsetsPadding(WindowInsets.navigationBars)
              ) {
                HorizontalDivider(
                  thickness = if (settings.yardSunlightMode) 1.5.dp else 0.5.dp,
                  color = if (settings.yardSunlightMode) Color.Black else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  MainTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                      modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                          interactionSource = remember { MutableInteractionSource() },
                          indication = ripple(bounded = true)
                        ) {
                          haptics.trigger(com.example.engine.HapticEvent.MODE_SWITCH)
                          if (tab == MainTab.MORE && selectedTab == MainTab.MORE && moreSubScreen != null) {
                            // Re-tapping Menu tab returns to Menu Hub root
                            moreSubScreen = null
                          } else {
                            selectedTab = tab
                          }
                        }
                        .testTag("nav_tab_${tab.id}"),
                      contentAlignment = Alignment.Center
                    ) {
                      Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                      ) {
                        Box(
                          modifier = Modifier
                            .width(48.dp)
                            .height(28.dp)
                            .background(
                              color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                              shape = RoundedCornerShape(14.dp)
                            ),
                          contentAlignment = Alignment.Center
                        ) {
                          Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                          )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                          text = tab.label,
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                          color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                      }
                    }
                  }
                }
              }
            }

            // Direct Product Master Sheet launched from Operations Menu
            if (showProductManagerFromMenu) {
              ProductManagerDialog(
                repository = repository,
                onDismiss = { showProductManagerFromMenu = false },
                requireAuth = { action -> requireMenuAuth(action) }
              )
            }

            if (showMenuPasswordDialog) {
              PasswordDialog(
                storedHash = settings.passwordHash,
                onDismiss = {
                  showMenuPasswordDialog = false
                  pendingMenuAuthAction = null
                },
                onSuccess = {
                  showMenuPasswordDialog = false
                  pendingMenuAuthAction?.invoke()
                  pendingMenuAuthAction = null
                }
              )
            }

            // Global Google Drive / Cloud URL Import Dialog
            if (showGlobalCloudImportDialog) {
              CloudImportDialog(
                repository = repository,
                mode = CloudImportMode.BACKUP_JSON,
                initialUrl = globalCloudInitialUrl,
                onDismiss = {
                  showGlobalCloudImportDialog = false
                  globalCloudInitialUrl = null
                },
                onBackupDownloaded = { json, preview, _, provider ->
                  showGlobalCloudImportDialog = false
                  globalCloudInitialUrl = null
                  globalPendingJson = json
                  globalRestorePreview = preview
                  globalRestoreSource = "Source: ${provider.displayName} direct download"
                }
              )
            }

            // Global Verified Restore Preview -> Auth -> Summary flow (for Cloud Sync, Share-to-App, and Onboarding)
            VerifiedBackupRestoreFlow(
              repository = repository,
              pendingJson = globalPendingJson,
              preview = globalRestorePreview,
              sourceDescription = globalRestoreSource,
              requirePassword = !globalRestoreFromOnboarding,
              onDismissAll = {
                globalPendingJson = null
                globalRestorePreview = null
                globalRestoreFromOnboarding = false
              },
              onRestoreSuccess = {
                if (showOnboarding) {
                  prefs.edit().putBoolean("onboarding_completed", true).apply()
                  showOnboarding = false
                }
              }
            )

            // Splash screen
            AnimatedVisibility(
              visible = showSplash,
              enter = fadeIn(),
              exit = fadeOut()
            ) {
              SplashScreen()
            }

            // First-launch onboarding dialog
            if (!showSplash && showOnboarding && !showGlobalCloudImportDialog && globalPendingJson == null) {
              AlertDialog(
                onDismissRequest = { /* Require choice */ },
                title = {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    RefineryLogo(modifier = Modifier.size(40.dp), withBackground = false)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Welcome to Tank Calc", fontWeight = FontWeight.Bold)
                  }
                },
                text = {
                  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Condensate fractionation refinery volume & loss reconciliation calculator.")
                    Text("Choose how you would like to set up your workspace:", fontWeight = FontWeight.SemiBold)
                    Text("• Google Drive / Cloud Link: Paste a shared Google Drive, Dropbox, or HTTPS backup link for instant setup.", style = MaterialTheme.typography.bodySmall)
                    Text("• Device Backup File: Pick a local .json backup file from storage.", style = MaterialTheme.typography.bodySmall)
                    Text("• Start New: Initializes with standard demo refinery tanks (T-101, T-102, N-1).", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                      onClick = {
                        globalCloudInitialUrl = null
                        globalRestoreFromOnboarding = true
                        showGlobalCloudImportDialog = true
                      },
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Import from Google Drive / Cloud Link")
                    }
                    OutlinedButton(
                      onClick = {
                        onboardingRestoreLauncher.launch(arrayOf("application/json", "*/*"))
                      },
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Import Local File from Device")
                    }
                  }
                },
                confirmButton = {
                  TextButton(onClick = {
                    prefs.edit().putBoolean("onboarding_completed", true).apply()
                    showOnboarding = false
                  }) {
                    Text("Start New (Demo Tanks)")
                  }
                }
              )
            }

            // Global Native In-App Update Dialog
            if (!showSplash && availableUpdate != null) {
              val upd = availableUpdate!!
              com.example.ui.components.AppUpdateDialog(
                manifest = upd.manifest,
                currentVersionName = upd.currentVersionName,
                currentBuild = upd.currentBuild,
                onDismiss = {
                  if (!upd.manifest.forceUpdate) {
                    availableUpdate = null
                  }
                }
              )
            }
          }
          }
        }
      }
    }
  }
}

@Composable
fun MoreNavigationContainer(
  isActiveTab: Boolean,
  subScreen: MoreSubScreen?,
  onNavigate: (MenuDestination) -> Unit,
  onBack: () -> Unit,
  repository: TankRepository,
  onOpenDraft: (String, String) -> Unit
) {
  BackHandler(enabled = isActiveTab && subScreen != null, onBack = onBack)
  if (subScreen != null) {
    when (subScreen) {
      MoreSubScreen.TANKS -> TanksScreen(repository, onBack = onBack)
      MoreSubScreen.CALIBRATION -> CalibrationInspectorScreen(repository = repository, onBack = onBack)
      MoreSubScreen.INJECTION -> InjectionScreen(repository, onBack = onBack)
      MoreSubScreen.SOLUTION -> SolutionScreen(repository, onBack = onBack)
      MoreSubScreen.CRU_WAIT -> CruWaitScreen(repository, onBack = onBack)
      MoreSubScreen.N2_CALC -> N2CalcScreen(repository, onBack = onBack)
      MoreSubScreen.VCF_CALC -> VcfSettingsScreen(
        repository = repository,
        onBack = onBack,
        sectionMode = SettingsSectionMode.VCF_CALCULATOR_ONLY
      )
      MoreSubScreen.SETTINGS -> VcfSettingsScreen(
        repository = repository,
        onBack = onBack,
        sectionMode = SettingsSectionMode.SETTINGS_AND_BACKUP
      )
      MoreSubScreen.DRAFTS -> DraftsScreen(repository, onBack = onBack, onOpenDraft = onOpenDraft)
    }
  } else {
    AppScreen(
      title = "Operations Menu",
      subtitle = "PARTEX PETRO LTD. • Refinery Suite",
      showSunlightToggle = false
    ) {
      MenuHubScreen(
        repository = repository,
        onNavigate = onNavigate
      )
    }
  }
}

@Composable
fun SplashScreen() {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF0A192F)),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(24.dp)
    ) {
      RefineryLogo(
        modifier = Modifier.size(130.dp),
        withBackground = true
      )
      Spacer(modifier = Modifier.height(20.dp))
      Text(
        text = "Tank Calc",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFF1F5F9)
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "Condensate Fractionation Refinery Process Calculator",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF94A3B8),
        textAlign = TextAlign.Center
      )
      Spacer(modifier = Modifier.height(32.dp))
      LinearProgressIndicator(
        modifier = Modifier.width(160.dp).height(4.dp),
        color = Color(0xFF14B8A6),
        trackColor = Color(0xFF1E293B)
      )
      Spacer(modifier = Modifier.height(48.dp))
      Text(
        text = "Developed by Meherab Hossain Jihad\nVersion 1.0.0",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF64748B),
        textAlign = TextAlign.Center
      )
    }
  }
}
