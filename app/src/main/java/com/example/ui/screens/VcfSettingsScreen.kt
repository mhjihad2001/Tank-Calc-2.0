package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.engine.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.components.AppScreen
import com.example.ui.components.AuthSession
import com.example.ui.components.CloudImportDialog
import com.example.ui.components.CloudImportMode
import com.example.ui.components.DensityRefAndMethodSelector
import com.example.ui.components.PasswordDialog
import com.example.ui.components.RefineryLogo
import com.example.ui.components.VcfVccComparisonCard
import com.example.ui.components.VcfVccTableEditorDialog
import com.example.ui.components.formatCloudSyncTimestamp
import com.example.ui.components.shortLabel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class SettingsSectionMode {
  ALL,
  VCF_CALCULATOR_ONLY,
  SETTINGS_AND_BACKUP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VcfSettingsScreen(
  repository: TankRepository,
  onBack: (() -> Unit)? = null,
  sectionMode: SettingsSectionMode = SettingsSectionMode.SETTINGS_AND_BACKUP
) {
  val context = LocalContext.current
  val settings by repository.settings.collectAsState()
  val tanks by repository.tanks.collectAsState()
  val prodSetup by repository.productionReportSetup.collectAsState()

  var densityInput by remember { mutableStateOf("750.0") }
  var tempInput by remember { mutableStateOf("25.0") }
  var observedVolInput by remember { mutableStateOf("10000.0") }
  var showTableEditor by remember { mutableStateOf(false) }

  var showChangePasswordDialog by remember { mutableStateOf(false) }
  var showAuthForPassword by remember { mutableStateOf(false) }

  // Export verification dialog state (HIDDEN by default; unlocked ONLY via Master Password)
  var backupExportUnlocked by remember { mutableStateOf(false) }
  var showUnlockBackupExportDialog by remember { mutableStateOf(false) }
  var exportVerification by remember { mutableStateOf<ExportVerificationResult?>(null) }
  var showExportDialog by remember { mutableStateOf(false) }
  var includeDraftsInBackup by remember { mutableStateOf(false) }

  // Self-test dialog state
  var selfTestResult by remember { mutableStateOf<SelfTestResult?>(null) }
  var showSelfTestDialog by remember { mutableStateOf(false) }

  // Restore states
  val scope = rememberCoroutineScope()
  var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
  var restorePreview by remember { mutableStateOf<RestorePreview?>(null) }
  var showRestorePreviewDialog by remember { mutableStateOf(false) }
  var showRestoreAuthDialog by remember { mutableStateOf(false) }
  var restoreModeChoice by remember { mutableStateOf(RestoreMode.REPLACE) }
  var restoreResultDialog by remember { mutableStateOf<RestoreResult?>(null) }
  var showCloudImportDialog by remember { mutableStateOf(false) }
  var isQuickCloudSyncing by remember { mutableStateOf(false) }

  var showDiagnosticsDialog by remember { mutableStateOf(false) }
  var diagnosticsReport by remember { mutableStateOf<DiagnosticsReport?>(null) }

  // In-app update check state
  val installedVerInfo = remember(context) { AppUpdateManager.getInstalledVersionInfo(context) }
  var isCheckingUpdate by remember { mutableStateOf(false) }
  var updateCheckStatusText by remember { mutableStateOf<String?>(null) }
  var updateCheckIsError by remember { mutableStateOf(false) }
  var showUpdateSourceConfig by remember { mutableStateOf(false) }
  var updateManifestUrlDraft by remember(settings.customUpdateManifestUrl) {
    mutableStateOf(settings.customUpdateManifestUrl)
  }
  var availableUpdateResult by remember { mutableStateOf<UpdateCheckResult.UpdateAvailable?>(null) }
  val haptics = com.example.engine.rememberHapticController()

  val restoreFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let {
      try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val json = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
        val result = BackupManager.previewBackup(json)
        result.fold(
          onSuccess = { preview ->
            pendingRestoreJson = json
            restorePreview = preview
            showRestorePreviewDialog = true
          },
          onFailure = { err ->
            Toast.makeText(context, "Invalid backup file: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
          }
        )
      } catch (e: Exception) {
        Toast.makeText(context, "Error reading file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
      }
    }
  }

  val inputDensity = densityInput.toDoubleOrNull() ?: 750.0
  val temp = tempInput.toDoubleOrNull() ?: 25.0
  val obsVol = observedVolInput.toDoubleOrNull() ?: 10000.0

  val dualRes = remember(inputDensity, temp, obsVol, settings, prodSetup) {
    VcfEngine.calculateDual30C(
      observedVolume = obsVol,
      observedTemp = temp,
      inputDensity = inputDensity,
      densityRefTemp = settings.densityInputRefTemp,
      tableType = settings.vcfTableType,
      round4 = settings.roundVcfTo4Decimals,
      roundTemp025 = settings.roundTempTo025,
      vccTable = prodSetup.vccTable,
      vcf30Table = prodSetup.vcf30Table,
      customBands = settings.customVcfBandParams,
      preferredMethod = settings.tempCorrectionMethod
    )
  }

  val daysSinceLastBackup = if (settings.lastBackupTimestamp > 0L) {
    (System.currentTimeMillis() - settings.lastBackupTimestamp) / (1000 * 60 * 60 * 24)
  } else {
    999L
  }

  val showSettings = sectionMode != SettingsSectionMode.VCF_CALCULATOR_ONLY
  val showVcfCalc = sectionMode != SettingsSectionMode.SETTINGS_AND_BACKUP

  AppScreen(
    title = if (sectionMode == SettingsSectionMode.VCF_CALCULATOR_ONLY) "VCF & VCC Calculator" else "App Settings",
    onBack = onBack,
    actions = {
      if (sectionMode == SettingsSectionMode.VCF_CALCULATOR_ONLY) {
        UnitQuickSwitchChip(
          currentUnit = settings.unit,
          onUnitChange = { repository.updateSettings(settings.copy(unit = it)) }
        )
      }
    }
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Unit Settings Section
      if (showSettings) {
      item {
        Text("Global Unit & Precision", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
      }

      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Selected Active Unit:", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              UnitType.values().forEach { u ->
                FilterChip(
                  selected = settings.unit == u,
                  onClick = { repository.updateSettings(settings.copy(unit = u)) },
                  label = { Text("${u.name} (${UnitUtils.unitLabel(u)})") }
                )
              }
            }
            Text(
              "Note: All volumes are stored internally in litres. Switching units converts display and input seamlessly without corrupting saved data.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            Divider()
            Text("Decimal Precision Settings:", fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = settings.decimalPlacesL.toString(),
                onValueChange = {
                  val v = it.toIntOrNull() ?: 0
                  repository.updateSettings(settings.copy(decimalPlacesL = v))
                },
                label = { Text("Litres (L)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
              )
              OutlinedTextField(
                value = settings.decimalPlacesM3.toString(),
                onValueChange = {
                  val v = it.toIntOrNull() ?: 3
                  repository.updateSettings(settings.copy(decimalPlacesM3 = v))
                },
                label = { Text("Cubic Metres (m³)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
              )
              OutlinedTextField(
                value = settings.decimalPlacesBbl.toString(),
                onValueChange = {
                  val v = it.toIntOrNull() ?: 2
                  repository.updateSettings(settings.copy(decimalPlacesBbl = v))
                },
                label = { Text("Barrels (bbl)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }

      // Diagnostics & Performance Section
      item {
        Divider()
        Text("Diagnostics & Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
      }

      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text("Performance Mode", fontWeight = FontWeight.Bold)
                Text("Reduce animations & heavier calculations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Switch(
                checked = settings.performanceMode,
                onCheckedChange = { repository.updateSettings(settings.copy(performanceMode = it)) }
              )
            }

            Divider()

            Button(
              onClick = {
                diagnosticsReport = repository.runDiagnostics()
                showDiagnosticsDialog = true
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(Icons.Default.HealthAndSafety, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Run Consistency Check (Diagnostics)")
            }
          }
        }
      }

      // 1B. Tactile Haptics & Built-In Industrial Keyboard Section
      item {
        Divider()
        Text(
          "Tactile Haptics & Built-In Keyboard",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )
      }

      item {
        val haptics = com.example.engine.rememberHapticController()
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Built-In Dual-Mode Keyboard (123 + ABC)", fontWeight = FontWeight.Bold)
                Text(
                  "Use Tank Calc's own industrial keypad for all number & text fields (shift between 123 and ABC anytime)",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Switch(
                checked = settings.useInAppKeyboard,
                onCheckedChange = {
                  haptics.trigger(com.example.engine.HapticEvent.MODE_SWITCH)
                  repository.updateSettings(settings.copy(useInAppKeyboard = it))
                }
              )
            }

            Divider()

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Tactile Haptic Feedback", fontWeight = FontWeight.Bold)
                Text(
                  "Physical vibration on key presses, cell jumps, formula evaluation, and outlier alerts",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Switch(
                checked = settings.hapticsEnabled,
                onCheckedChange = {
                  repository.updateSettings(settings.copy(hapticsEnabled = it))
                  if (it) haptics.trigger(com.example.engine.HapticEvent.SUCCESS)
                }
              )
            }

            if (settings.hapticsEnabled) {
              Text("Vibration Strength Profile:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                val levels = listOf(
                  1 to "Light",
                  2 to "Medium (Default)",
                  3 to "Strong (Field Glove)"
                )
                levels.forEach { (lvl, label) ->
                  FilterChip(
                    selected = settings.hapticIntensity == lvl,
                    onClick = {
                      repository.updateSettings(settings.copy(hapticIntensity = lvl))
                      haptics.trigger(com.example.engine.HapticEvent.KEY_TAP)
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OutlinedButton(
                  onClick = { haptics.trigger(com.example.engine.HapticEvent.KEY_TAP) },
                  modifier = Modifier.weight(1f)
                ) {
                  Text("Test Key Tap", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                  onClick = { haptics.trigger(com.example.engine.HapticEvent.SUCCESS) },
                  modifier = Modifier.weight(1f)
                ) {
                  Text("Test Confirm", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                  onClick = { haptics.trigger(com.example.engine.HapticEvent.WARNING_ALERT) },
                  modifier = Modifier.weight(1f)
                ) {
                  Text("Test Alert", style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }
        }
      }

      // 2. Cloud Sync & Data Recovery Section (Backup File Export is HIDDEN behind Master Password below)
      item {
        Divider()
        Text("Cloud Sync & Data Recovery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          // Direct Google Drive / Cloud Import Card
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    "Direct Google Drive / Cloud Sync",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                  )
                  Text(
                    "Import calibration backups directly from Google Drive, Dropbox, OneDrive, GitHub, or HTTPS URL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              if (settings.savedCloudBackupUrl.isNotBlank()) {
                val provider = remember(settings.savedCloudBackupUrl) {
                  CloudBackupImporter.detectProvider(settings.savedCloudBackupUrl)
                }
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.surface
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        text = settings.savedCloudBackupLabel.ifBlank { "Saved Cloud Source" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                      )
                      Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                      ) {
                        Text(
                          text = provider.displayName,
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = FontWeight.SemiBold,
                          color = MaterialTheme.colorScheme.onSecondaryContainer,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                      }
                    }
                    Text(
                      text = "Last synced: ${formatCloudSyncTimestamp(settings.lastCloudSyncTimestamp)}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Button(
                        onClick = {
                          isQuickCloudSyncing = true
                          scope.launch {
                            val res = CloudBackupImporter.downloadTextFromCloud(settings.savedCloudBackupUrl)
                            if (!res.success) {
                              isQuickCloudSyncing = false
                              Toast.makeText(context, res.errorMessage ?: "Cloud sync failed", Toast.LENGTH_LONG).show()
                              return@launch
                            }
                            val previewRes = BackupManager.previewBackup(res.content)
                            isQuickCloudSyncing = false
                            previewRes.fold(
                              onSuccess = { preview ->
                                repository.updateSettings(
                                  repository.settings.value.copy(lastCloudSyncTimestamp = System.currentTimeMillis())
                                )
                                pendingRestoreJson = res.content
                                restorePreview = preview
                                showRestorePreviewDialog = true
                              },
                              onFailure = { err ->
                                Toast.makeText(context, "Invalid backup JSON: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                              }
                            )
                          }
                        },
                        enabled = !isQuickCloudSyncing,
                        modifier = Modifier.weight(1f)
                      ) {
                        if (isQuickCloudSyncing) {
                          CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                          )
                          Spacer(modifier = Modifier.width(6.dp))
                          Text("Syncing...")
                        } else {
                          Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(6.dp))
                          Text("1-Tap Sync Now")
                        }
                      }
                      OutlinedButton(
                        onClick = { showCloudImportDialog = true },
                        enabled = !isQuickCloudSyncing
                      ) {
                        Text("Edit Link")
                      }
                    }
                  }
                }
              } else {
                Button(
                  onClick = { showCloudImportDialog = true },
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Icon(Icons.Default.CloudDownload, contentDescription = null)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Import from Google Drive / Cloud Link")
                }
              }
            }
          }

          // Import / Restore local file button
          OutlinedButton(
            onClick = {
              restoreFileLauncher.launch(arrayOf("application/json", "*/*"))
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import Local Backup File (Device Storage)")
          }
        }
      }
      } // end showSettings (Units, Diagnostics, Backup)

      // 3. Dual ASTM VCF & VCC Calculator Section
      if (showVcfCalc) {
      item {
        if (showSettings) Divider()
        Text("Dual VCF & VCC Calculator (@30 °C)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
      }

      item {
        DensityRefAndMethodSelector(
          densityRefTemp = settings.densityInputRefTemp,
          onDensityRefTempChange = { ref ->
            repository.updateSettings(settings.copy(densityInputRefTemp = ref))
          },
          activeMethod = settings.tempCorrectionMethod,
          onMethodChange = { method ->
            repository.updateSettings(settings.copy(tempCorrectionMethod = method))
          },
          onOpenTableEditor = { showTableEditor = true }
        )
      }

      item {
        Text("ASTM Table Standard", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          VcfTableType.values().forEach { table ->
            FilterChip(
              selected = settings.vcfTableType == table,
              onClick = {
                repository.updateSettings(settings.copy(vcfTableType = table))
              },
              label = { Text(table.name.replace("_", " ")) }
            )
          }
        }
      }

      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = densityInput,
            onValueChange = { densityInput = it },
            label = { Text("Density ${settings.densityInputRefTemp.shortLabel()} (kg/m³ or kg/L)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1.25f)
          )
          OutlinedTextField(
            value = tempInput,
            onValueChange = { tempInput = it },
            label = { Text("Obs. Temp (°C)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(0.95f)
          )
        }
      }

      item {
        OutlinedTextField(
          value = observedVolInput,
          onValueChange = { observedVolInput = it },
          label = { Text("Observed Volume (Natural L)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }

      item {
        VcfVccComparisonCard(
          result = dualRes,
          settings = settings,
          onSelectMethod = { method ->
            repository.updateSettings(settings.copy(tempCorrectionMethod = method))
          },
          onOpenTableEditor = { showTableEditor = true },
          title = "VCF vs VCC Results & Deviation (@30 °C)"
        )
      }

      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ASTM D1250-80 Engineering Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            Text("Density Band: ${dualRes.vcfObserved.bandName}", style = MaterialTheme.typography.bodySmall)
            Text("Thermal Expansion (α15): ${String.format(Locale.US, "%.8f", dualRes.vcfObserved.alpha15)} /°C", style = MaterialTheme.typography.bodySmall)
            Text("Temperature Diff (ΔT vs 15°C): ${dualRes.vcfObserved.dT} °C  •  (ΔT vs 30°C): ${String.format(Locale.US, "%.2f", temp - 30.0)} °C", style = MaterialTheme.typography.bodySmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Active Method (${dualRes.selectedMethod.shortLabel()}):", fontWeight = FontWeight.Bold)
              Text(
                UnitUtils.formatVolume(dualRes.selectedVolume30, settings.unit, settings = settings),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }
      }
      } // end showVcfCalc

      // 4. VCF Rounding & Calculation Constants
      item {
        if (showSettings) {
          Divider()
          Text("Calculation Constants & Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        } else {
          Text("ASTM Rounding Options", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
      }

      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Round VCF to 4 decimals (Standard)")
          Switch(
            checked = settings.roundVcfTo4Decimals,
            onCheckedChange = { repository.updateSettings(settings.copy(roundVcfTo4Decimals = it)) }
          )
        }
      }

      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Round temperature to nearest 0.25 °C")
          Switch(
            checked = settings.roundTempTo025,
            onCheckedChange = { repository.updateSettings(settings.copy(roundTempTo025 = it)) }
          )
        }
      }

      if (showSettings) {

      item {
        OutlinedButton(
          onClick = {
            haptics.trigger(HapticEvent.MODE_SWITCH)
            showTableEditor = true
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_edit_vcf_vcc_tables_button")
        ) {
          Icon(Icons.Default.TableChart, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Edit VCC, VCF30 & ASTM K0/K1 Data Tables")
        }
      }

      item {
        OutlinedTextField(
          value = settings.invalidInputThreshold.toString(),
          onValueChange = {
            val v = it.toDoubleOrNull() ?: 1.0
            repository.updateSettings(settings.copy(invalidInputThreshold = v))
          },
          label = { Text("Invalid Input Volume Threshold (L)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth()
        )
      }

      item {
        OutlinedTextField(
          value = settings.lossThresholdPercent.toString(),
          onValueChange = {
            val v = it.toDoubleOrNull() ?: 1.5
            repository.updateSettings(settings.copy(lossThresholdPercent = v))
          },
          label = { Text("Loss % Threshold for RED Flag") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth()
        )
      }

      // Plant Yard Usability (Outdoor Sunlight Mode)
      item {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (settings.yardSunlightMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Yard / Sunlight Mode", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
              }
              Text(
                "Ultra-high-contrast pure white backgrounds, bold jet-black metrics, and saturated borders for direct outdoor sunlight.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = settings.yardSunlightMode,
              onCheckedChange = { repository.updateSettings(settings.copy(yardSunlightMode = it)) }
            )
          }
        }
      }

      item {
        OutlinedTextField(
          value = settings.dipOutlierThresholdMm.toString(),
          onValueChange = {
            val v = it.toDoubleOrNull() ?: 500.0
            repository.updateSettings(settings.copy(dipOutlierThresholdMm = v))
          },
          label = { Text("Bulk Dip Outlier / Anomaly Threshold (Δ mm vs Previous Day)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth()
        )
      }

      // Plant & Production Report / ERP Settings
      item {
        Divider()
        Text("Plant & Production Report (ERP)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
      }

      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = settings.companyName,
              onValueChange = { repository.updateSettings(settings.copy(companyName = it)) },
              label = { Text("Corporate Name in Reports") },
              modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
              value = settings.plantName,
              onValueChange = { repository.updateSettings(settings.copy(plantName = it)) },
              label = { Text("Plant / Refinery Unit Name") },
              modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
              value = settings.erpToleranceL.toString(),
              onValueChange = {
                val v = it.toDoubleOrNull() ?: 5.0
                repository.updateSettings(settings.copy(erpToleranceL = v))
              },
              label = { Text("ERP Stock Balance Tolerance (L)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth()
            )

            Text("Report Signature Block", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
              value = settings.signaturePreparedBy,
              onValueChange = { repository.updateSettings(settings.copy(signaturePreparedBy = it)) },
              label = { Text("Prepared by") },
              modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
              value = settings.signatureCheckedBy,
              onValueChange = { repository.updateSettings(settings.copy(signatureCheckedBy = it)) },
              label = { Text("Checked by") },
              modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
              value = settings.signatureApprovedBy,
              onValueChange = { repository.updateSettings(settings.copy(signatureApprovedBy = it)) },
              label = { Text("Approved by") },
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }

      item {
        OutlinedButton(
          onClick = {
            if (AuthSession.isUnlocked()) {
              showChangePasswordDialog = true
            } else {
              showAuthForPassword = true
            }
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Icon(Icons.Default.Lock, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Change Master Password")
        }
      }

      // Hidden Admin Backup File Export Gate (Always hidden by default; requires Master Password to unhide)
      item {
        if (!backupExportUnlocked) {
          OutlinedButton(
            onClick = {
              haptics.trigger(com.example.engine.HapticEvent.MODE_SWITCH)
              showUnlockBackupExportDialog = true
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("unlock_backup_export_button")
          ) {
            Icon(Icons.Default.AdminPanelSettings, contentDescription = "Master Password Required")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Admin Backup Export (Master Password)")
          }
        } else {
          val lastDateStr = if (settings.lastBackupTimestamp > 0L) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(settings.lastBackupTimestamp))
          } else {
            "Never"
          }
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("unlocked_backup_export_panel")
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(Icons.Default.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Column {
                    Text(
                      "Backup File Export (Unlocked)",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                      "Last Backup: $lastDateStr",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
                TextButton(
                  onClick = {
                    haptics.trigger(com.example.engine.HapticEvent.MODE_SWITCH)
                    backupExportUnlocked = false
                  },
                  modifier = Modifier.testTag("lock_backup_export_button")
                ) {
                  Icon(Icons.Default.VisibilityOff, contentDescription = "Hide Export Options", modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Hide")
                }
              }

              if (daysSinceLastBackup > 7L) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                  Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      "Reminder: No full backup made in over 7 days.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onErrorContainer
                    )
                  }
                }
              }

              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
              ) {
                Checkbox(
                  checked = includeDraftsInBackup,
                  onCheckedChange = { includeDraftsInBackup = it }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  "Include drafts in backup (excluded by default)",
                  style = MaterialTheme.typography.bodySmall
                )
              }

              Button(
                onClick = {
                  val result = BackupManager.exportAndVerify(context, repository, isSetupOnly = false, includeDrafts = includeDraftsInBackup)
                  exportVerification = result
                  showExportDialog = true
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("export_full_backup_button")
              ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Full Backup (All Data & Calibration)")
              }

              OutlinedButton(
                onClick = {
                  val result = BackupManager.exportAndVerify(context, repository, isSetupOnly = true)
                  exportVerification = result
                  showExportDialog = true
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("export_setup_file_button")
              ) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Setup File (No History, Ready to Use)")
              }

              OutlinedButton(
                onClick = {
                  val test = BackupManager.runSelfTest(context, repository)
                  selfTestResult = test
                  showSelfTestDialog = true
                },
                modifier = Modifier.fillMaxWidth()
              ) {
                Icon(Icons.Default.FactCheck, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Run Backup Self-Test (Verify Calibration Tables)")
              }

              OutlinedButton(
                onClick = {
                  val uri = BackupManager.exportAllDataExcelCsv(context, repository)
                  if (uri != null) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                      type = "text/csv"
                      putExtra(Intent.EXTRA_STREAM, uri)
                      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share TankCalc Data Report"))
                  } else {
                    Toast.makeText(context, "Failed to export report.", Toast.LENGTH_SHORT).show()
                  }
                },
                modifier = Modifier.fillMaxWidth()
              ) {
                Icon(Icons.Default.Description, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export All Data to Excel / CSV")
              }
            }
          }
        }
      }

      // 5. About, Version & In-App Self-Update Section
      item {
        Divider()
        Text("About, Version & App Updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
      }

      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              RefineryLogo(modifier = Modifier.size(64.dp), withBackground = true)
              Spacer(modifier = Modifier.width(16.dp))
              val installedSig = remember(context) {
                AppUpdateManager.shortFingerprint(AppUpdateManager.getInstalledSigningSha256(context))
              }
              Column {
                Text(
                  text = "Tank Calc v${installedVerInfo.second} (Build ${installedVerInfo.first})",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold
                )
                Text("Refinery tank volume & loss calculator", style = MaterialTheme.typography.bodyMedium)
                Text("Developed by Meherab Hossain Jihad", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                  text = "Signing Key: $installedSig",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Button(
              enabled = !isCheckingUpdate,
              onClick = {
                haptics.trigger(com.example.engine.HapticEvent.MODE_SWITCH)
                isCheckingUpdate = true
                updateCheckStatusText = null
                updateCheckIsError = false
                scope.launch {
                  val manifestUrl = settings.customUpdateManifestUrl
                    .ifBlank { AppUpdateManager.DEFAULT_MANIFEST_URL }
                  when (val res = AppUpdateManager.checkForUpdates(context, manifestUrl)) {
                    is UpdateCheckResult.UpdateAvailable -> {
                      isCheckingUpdate = false
                      haptics.trigger(com.example.engine.HapticEvent.SUCCESS)
                      availableUpdateResult = res
                      updateCheckIsError = false
                      updateCheckStatusText = "Update available: v${res.manifest.versionName} (Build ${res.manifest.latestBuild})"
                    }
                    is UpdateCheckResult.UpToDate -> {
                      isCheckingUpdate = false
                      haptics.trigger(com.example.engine.HapticEvent.SUCCESS)
                      updateCheckIsError = false
                      updateCheckStatusText = "You are on the latest version (v${res.currentVersionName}, Build ${res.currentBuild})."
                      Toast.makeText(context, "App is up to date (v${res.currentVersionName})", Toast.LENGTH_SHORT).show()
                    }
                    is UpdateCheckResult.Error -> {
                      isCheckingUpdate = false
                      haptics.trigger(com.example.engine.HapticEvent.WARNING_ALERT)
                      updateCheckIsError = true
                      updateCheckStatusText = res.message
                      Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                    }
                  }
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_check_updates_button")
            ) {
              if (isCheckingUpdate) {
                CircularProgressIndicator(
                  modifier = Modifier.size(16.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Checking GitHub Release...")
              } else {
                Icon(Icons.Default.SystemUpdate, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Check for Updates")
              }
            }

            if (updateCheckStatusText != null) {
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (updateCheckIsError) {
                  MaterialTheme.colorScheme.errorContainer
                } else {
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                },
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = updateCheckStatusText!!,
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.SemiBold,
                  color = if (updateCheckIsError) {
                    MaterialTheme.colorScheme.onErrorContainer
                  } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                  },
                  modifier = Modifier.padding(10.dp)
                )
              }
            }

            // Optional configurable Update Source URL (in case repo/branch differs)
            TextButton(
              onClick = { showUpdateSourceConfig = !showUpdateSourceConfig },
              contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
              Icon(
                imageVector = if (showUpdateSourceConfig) Icons.Default.ExpandLess else Icons.Default.Link,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (showUpdateSourceConfig) "Hide Update Source Settings" else "Configure GitHub Update Source",
                style = MaterialTheme.typography.labelMedium
              )
            }

            if (showUpdateSourceConfig) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = updateManifestUrlDraft,
                  onValueChange = { updateManifestUrlDraft = it },
                  label = { Text("GitHub Repo or version.json URL") },
                  placeholder = { Text(AppUpdateManager.DEFAULT_MANIFEST_URL) },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(
                    onClick = {
                      val normalized = if (updateManifestUrlDraft.isBlank()) {
                        ""
                      } else {
                        AppUpdateManager.normalizeManifestUrl(updateManifestUrlDraft)
                      }
                      repository.updateSettings(settings.copy(customUpdateManifestUrl = normalized))
                      updateManifestUrlDraft = normalized
                      Toast.makeText(context, "Update source saved.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                  ) {
                    Text("Save Source")
                  }
                  OutlinedButton(
                    onClick = {
                      repository.updateSettings(settings.copy(customUpdateManifestUrl = ""))
                      updateManifestUrlDraft = ""
                      Toast.makeText(context, "Reset to default GitHub repo.", Toast.LENGTH_SHORT).show()
                    }
                  ) {
                    Text("Reset Default")
                  }
                }
              }
            }

            Text("Build Date: October 2026", style = MaterialTheme.typography.bodySmall)
            Divider()
            Text("Features: Dip-to-volume engine with compact calibration, N2 water column interpolation, 30°C temperature-corrected loss reconciliation, editable chemical dosing tables, ASTM D1250-80 VCF tables, and verified cloud/local calibration sync.", style = MaterialTheme.typography.bodySmall)
            Text("Note: Always cross-check results with official calibration charts and standards.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text("© 2026 Meherab Hossain Jihad. All rights reserved.", style = MaterialTheme.typography.bodySmall)
            Text("Generated by Tank Calc | Developed by Meherab Hossain Jihad", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
          }
        }
      }
      } // end showSettings (Constants, Yard Mode, Report Settings, About)
    }

    if (availableUpdateResult != null) {
      val upd = availableUpdateResult!!
      com.example.ui.components.AppUpdateDialog(
        manifest = upd.manifest,
        currentVersionName = upd.currentVersionName,
        currentBuild = upd.currentBuild,
        onDismiss = {
          if (!upd.manifest.forceUpdate) {
            availableUpdateResult = null
          }
        }
      )
    }

    // Export Verification Dialog
    if (showExportDialog && exportVerification != null) {
      val ev = exportVerification!!
      AlertDialog(
        onDismissRequest = { showExportDialog = false },
        title = {
          Text(if (ev.selfTestPassed) "Backup Verified & Ready" else "Backup Verification FAILED", color = if (ev.selfTestPassed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
            Text("Type: ${ev.manifest.exportType}", fontWeight = FontWeight.Bold)
            Text("File size: ${String.format("%.2f MB", ev.fileSizeMb)} (${ev.file.name})")
            Text("Tanks: ${ev.manifest.tanksCount} | Calibrated: ${ev.manifest.calibrationTanksCount}")
            Text("Total Dip Rows: ${ev.manifest.totalDipRows} | Fraction Rows: ${ev.manifest.totalFractionRows} | N2 Rows: ${ev.manifest.totalN2Rows}")
            Text("Transactions: ${ev.manifest.transactionsCount} | Loss Reports: ${ev.manifest.lossReportsCount} | Injection: ${ev.manifest.dailyRecordsCount}")
            Divider()
            Text("Tank Calibration Breakdown (From Generated File):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
              items(ev.perTankCounts) { tc ->
                Text("• ${tc.tankName}: Dip: ${tc.dipRows} rows, Frac: ${tc.fractionRows} rows, N2: ${tc.n2Rows} rows ${if (tc.isValid) "✓" else "⚠️"}", style = MaterialTheme.typography.bodySmall)
              }
            }
            if (!ev.selfTestPassed) {
              Text(ev.errorMessage ?: "Discrepancy detected!", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
          }
        },
        confirmButton = {
          if (ev.selfTestPassed) {
            Button(onClick = {
              showExportDialog = false
              val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, ev.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
              }
              context.startActivity(Intent.createChooser(intent, "Share TankCalc File"))
            }) {
              Text("Share File")
            }
          }
        },
        dismissButton = {
          TextButton(onClick = { showExportDialog = false }) { Text("Close") }
        }
      )
    }

    // Self-Test Dialog
    if (showSelfTestDialog && selfTestResult != null) {
      val st = selfTestResult!!
      AlertDialog(
        onDismissRequest = { showSelfTestDialog = false },
        title = {
          Text(if (st.passed) "Self-Test PASSED ✓" else "Self-Test FAILED ⚠️", color = if (st.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
            Text(st.details, fontWeight = FontWeight.Medium)
            Divider()
            Text("Tank Verification Checklist:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
              items(st.perTankCounts) { tc ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(tc.tankName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                  Text("Dip: ${tc.dipRows} | Frac: ${tc.fractionRows} | N2: ${tc.n2Rows} ${if (tc.isValid) "✓" else "FAIL"}", style = MaterialTheme.typography.bodySmall)
                }
              }
            }
          }
        },
        confirmButton = {
          Button(onClick = { showSelfTestDialog = false }) { Text("OK") }
        }
      )
    }

    // Direct Cloud / Google Drive Import Dialog
    if (showCloudImportDialog) {
      CloudImportDialog(
        repository = repository,
        mode = CloudImportMode.BACKUP_JSON,
        onDismiss = { showCloudImportDialog = false },
        onBackupDownloaded = { json, preview, _, _ ->
          showCloudImportDialog = false
          pendingRestoreJson = json
          restorePreview = preview
          showRestorePreviewDialog = true
        }
      )
    }

    // Restore Preview Dialog
    if (showRestorePreviewDialog && restorePreview != null) {
      val rp = restorePreview!!
      AlertDialog(
        onDismissRequest = { showRestorePreviewDialog = false },
        title = { Text("Restore File Preview (${rp.exportType})") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 450.dp)) {
            Text("Created: ${rp.createdAt} | Device: ${rp.deviceInfo}")
            Text("Developer: ${rp.developer}")
            Text("Tanks: ${rp.manifest.tanksCount} | Total Dip: ${rp.manifest.totalDipRows} | Frac: ${rp.manifest.totalFractionRows}")
            Text("Transactions: ${rp.manifest.transactionsCount} | Reports: ${rp.manifest.lossReportsCount}")
            if (rp.hasCalibration) {
              Text("Calibration Data: PRESENT ✓ (Full Dip/Fraction/N2 tables)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            } else {
              Text("⚠️ WARNING: This file has NO calibration data! You can still import it, but calibration Excel/CSV will be needed.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            if (rp.checksumValid) {
              Text("Checksum: VALID ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            } else {
              Text("Checksum: WARNING (Corrupted or modified) ⚠️", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Select Restore Mode:", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = restoreModeChoice == RestoreMode.REPLACE,
                onClick = { restoreModeChoice = RestoreMode.REPLACE },
                label = { Text("Replace All") }
              )
              FilterChip(
                selected = restoreModeChoice == RestoreMode.MERGE,
                onClick = { restoreModeChoice = RestoreMode.MERGE },
                label = { Text("Merge Missing") }
              )
            }
          }
        },
        confirmButton = {
          Button(onClick = {
            showRestorePreviewDialog = false
            showRestoreAuthDialog = true
          }) {
            Text("Proceed to Restore")
          }
        },
        dismissButton = {
          TextButton(onClick = { showRestorePreviewDialog = false }) { Text("Cancel") }
        }
      )
    }

    // Restore Auth Dialog
    if (showRestoreAuthDialog && pendingRestoreJson != null) {
      PasswordDialog(
        title = "Restore Authorization",
        message = "Enter master password to authorize restoring data:",
        storedHash = settings.passwordHash,
        onDismiss = { showRestoreAuthDialog = false; pendingRestoreJson = null },
        onSuccess = {
          showRestoreAuthDialog = false
          pendingRestoreJson?.let { json ->
            val res = BackupManager.restoreBackup(repository, json, restoreModeChoice)
            restoreResultDialog = res
          }
          pendingRestoreJson = null
        }
      )
    }

    // Post-Restore Checklist Summary Dialog
    if (restoreResultDialog != null) {
      val res = restoreResultDialog!!
      AlertDialog(
        onDismissRequest = { restoreResultDialog = null },
        title = {
          Text(if (res.success) "Restore Success Summary" else "Restore Failed", color = if (res.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
            Text(res.message, fontWeight = FontWeight.SemiBold)
            Divider()
            Text("Component Checklist:", fontWeight = FontWeight.Bold)
            Text("• Settings Loaded: ${if (res.settingsLoaded) "✓" else "✗"}")
            Text("• Injection Setup Loaded: ${if (res.injectionSetupLoaded) "✓" else "✗"}")
            Text("• Calibration Tables Present: ${if (res.hasCalibration) "✓" else "⚠️ Missing"}")
            Divider()
            Text("Tank Calibration Checklist:", fontWeight = FontWeight.Bold)
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
              items(res.perTankChecklist) { tc ->
                Text("• ${tc.tankName}: Dip: ${tc.dipRows} rows, Frac: ${tc.fractionRows} rows, N2: ${tc.n2Rows} rows ${if (tc.isValid) "✓" else "⚠️"}", style = MaterialTheme.typography.bodySmall)
              }
            }
            if (res.sanityCheckErrors.isNotEmpty()) {
              Text("Sanity Warnings: ${res.sanityCheckErrors.joinToString("; ")}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
          }
        },
        confirmButton = {
          Button(onClick = { restoreResultDialog = null }) { Text("Done") }
        }
      )
    }

    // Master Password Dialog to Unhide Backup File Export Options
    if (showUnlockBackupExportDialog) {
      PasswordDialog(
        title = "Master Password Required",
        message = "Enter master password to unhide backup file export options:",
        storedHash = settings.passwordHash,
        onDismiss = { showUnlockBackupExportDialog = false },
        onSuccess = {
          showUnlockBackupExportDialog = false
          backupExportUnlocked = true
          haptics.trigger(com.example.engine.HapticEvent.SUCCESS)
          Toast.makeText(context, "Backup export options unhidden.", Toast.LENGTH_SHORT).show()
        }
      )
    }

    // Change Password Dialog
    if (showAuthForPassword) {
      PasswordDialog(
        title = "Authentication Required",
        message = "Enter current password to change master password:",
        storedHash = settings.passwordHash,
        onDismiss = { showAuthForPassword = false },
        onSuccess = {
          showAuthForPassword = false
          AuthSession.unlock()
          showChangePasswordDialog = true
        }
      )
    }

    if (showChangePasswordDialog) {
      var newPass by remember { mutableStateOf("") }
      var confirmPass by remember { mutableStateOf("") }
      var err by remember { mutableStateOf<String?>(null) }

      AlertDialog(
        onDismissRequest = { showChangePasswordDialog = false },
        title = { Text("Change Master Password") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = newPass,
              onValueChange = { newPass = it; err = null },
              label = { Text("New Password") },
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = confirmPass,
              onValueChange = { confirmPass = it; err = null },
              label = { Text("Confirm New Password") },
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              modifier = Modifier.fillMaxWidth()
            )
            if (err != null) {
              Text(err!!, color = MaterialTheme.colorScheme.error)
            }
          }
        },
        confirmButton = {
          Button(onClick = {
            if (newPass.length < 3) {
              err = "Password must be at least 3 characters."
              return@Button
            }
            if (newPass != confirmPass) {
              err = "Passwords do not match."
              return@Button
            }
            val newHash = SecurityUtils.hashPassword(newPass)
            repository.updateSettings(settings.copy(passwordHash = newHash))
            showChangePasswordDialog = false
            Toast.makeText(context, "Master password updated successfully!", Toast.LENGTH_SHORT).show()
          }) {
            Text("Update Password")
          }
        },
        dismissButton = {
          TextButton(onClick = { showChangePasswordDialog = false }) { Text("Cancel") }
        }
      )
    }

    // Diagnostics Dialog
    if (showDiagnosticsDialog && diagnosticsReport != null) {
      val diag = diagnosticsReport!!
      val hasIssues = diag.tanksWithoutProduct.isNotEmpty() || diag.productsWithoutTanks.isNotEmpty() || diag.staleTankLinks.isNotEmpty() || diag.staleProductLinks.isNotEmpty()
      AlertDialog(
        onDismissRequest = { showDiagnosticsDialog = false },
        title = {
          Text(if (hasIssues) "Consistency Check Issues Found ⚠️" else "All Checks Passed ✓", color = if (hasIssues) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
            if (!hasIssues) {
              Text("No inconsistencies or stale links found in Tank and Product masters.", fontWeight = FontWeight.Medium)
            } else {
              if (diag.tanksWithoutProduct.isNotEmpty()) {
                Text("• Tanks without Product (${diag.tanksWithoutProduct.size}): ${diag.tanksWithoutProduct.joinToString(", ") { it.name }}", color = MaterialTheme.colorScheme.error)
              }
              if (diag.productsWithoutTanks.isNotEmpty()) {
                Text("• Products without Tanks (${diag.productsWithoutTanks.size}): ${diag.productsWithoutTanks.joinToString(", ") { it.name }}", color = MaterialTheme.colorScheme.error)
              }
              if (diag.staleTankLinks.isNotEmpty()) {
                Text("• Stale Tank Links (${diag.staleTankLinks.size}): ${diag.staleTankLinks.size} tanks point to non-existent products.", color = MaterialTheme.colorScheme.error)
              }
              if (diag.staleProductLinks.isNotEmpty()) {
                Text("• Stale Product Links (${diag.staleProductLinks.size}): ${diag.staleProductLinks.size} products reference non-existent tanks.", color = MaterialTheme.colorScheme.error)
              }
            }

            // Hidden debug line for layout insets and screen size
            val density = LocalDensity.current
            val config = LocalConfiguration.current
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            val topDp = insets.calculateTopPadding()
            val bottomDp = insets.calculateBottomPadding()
            val leftDp = insets.calculateStartPadding(LocalLayoutDirection.current)
            val rightDp = insets.calculateEndPadding(LocalLayoutDirection.current)
            val topPx = with(density) { topDp.roundToPx() }
            val bottomPx = with(density) { bottomDp.roundToPx() }
            val leftPx = with(density) { leftDp.roundToPx() }
            val rightPx = with(density) { rightDp.roundToPx() }
            val screenWidthDp = config.screenWidthDp
            val screenHeightDp = config.screenHeightDp
            val screenWidthPx = with(density) { screenWidthDp.dp.roundToPx() }
            val screenHeightPx = with(density) { screenHeightDp.dp.roundToPx() }

            Divider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
              "Detected Layout Insets & Screen Size:\n" +
              "• Insets (dp): Top: ${topDp.value.toInt()}dp, Bottom: ${bottomDp.value.toInt()}dp, Left: ${leftDp.value.toInt()}dp, Right: ${rightDp.value.toInt()}dp\n" +
              "• Insets (px): Top: ${topPx}px, Bottom: ${bottomPx}px, Left: ${leftPx}px, Right: ${rightPx}px\n" +
              "• Display: ${screenWidthDp}×${screenHeightDp} dp (${screenWidthPx}×${screenHeightPx} px)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline
            )
          }
        },
        confirmButton = {
          if (hasIssues) {
            Button(onClick = {
              repository.cleanStaleLinks()
              diagnosticsReport = repository.runDiagnostics()
              Toast.makeText(context, "Stale links cleaned successfully!", Toast.LENGTH_SHORT).show()
            }) {
              Text("Clean Stale Links")
            }
          }
        },
        dismissButton = {
          TextButton(onClick = { showDiagnosticsDialog = false }) { Text("Close") }
        }
      )
    }

    if (showTableEditor) {
      VcfVccTableEditorDialog(
        repository = repository,
        onDismiss = { showTableEditor = false }
      )
    }
  }
}
