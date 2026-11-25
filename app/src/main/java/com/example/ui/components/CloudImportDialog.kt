package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.TankRepository
import com.example.engine.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class CloudImportMode {
  BACKUP_JSON,
  CSV_TABLE
}

/**
 * Reusable dialog to import a Tank Calc JSON backup/setup file or CSV calibration chart
 * directly from Google Drive, Google Sheets, Dropbox, OneDrive, GitHub, or any HTTPS link.
 */
@Composable
fun CloudImportDialog(
  repository: TankRepository,
  mode: CloudImportMode = CloudImportMode.BACKUP_JSON,
  initialUrl: String? = null,
  onDismiss: () -> Unit,
  onBackupDownloaded: (json: String, preview: RestorePreview, sourceUrl: String, provider: CloudProvider) -> Unit = { _, _, _, _ -> },
  onCsvDownloaded: (csvText: String, provider: CloudProvider) -> Unit = { _, _ -> }
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  val settings by repository.settings.collectAsState()

  var urlInput by remember {
    mutableStateOf(
      initialUrl?.takeIf { it.isNotBlank() }
        ?: if (mode == CloudImportMode.BACKUP_JSON) settings.savedCloudBackupUrl else ""
    )
  }
  var labelInput by remember {
    mutableStateOf(settings.savedCloudBackupLabel.ifBlank { "Refinery Master Calibration" })
  }
  var rememberLink by remember { mutableStateOf(mode == CloudImportMode.BACKUP_JSON) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var showDriveHelp by remember { mutableStateOf(false) }

  val detectedProvider = remember(urlInput) { CloudBackupImporter.detectProvider(urlInput) }

  fun startDownload(targetUrl: String) {
    val cleanUrl = targetUrl.trim()
    if (cleanUrl.isBlank()) {
      errorMessage = "Please paste a Google Drive or cloud HTTPS link first."
      return
    }
    errorMessage = null
    isLoading = true
    scope.launch {
      val result = CloudBackupImporter.downloadTextFromCloud(
        rawUrl = cleanUrl,
        preferCsv = (mode == CloudImportMode.CSV_TABLE)
      )
      if (!result.success) {
        isLoading = false
        errorMessage = result.errorMessage ?: "Download failed."
        return@launch
      }

      if (mode == CloudImportMode.BACKUP_JSON) {
        val previewRes = BackupManager.previewBackup(result.content)
        isLoading = false
        previewRes.fold(
          onSuccess = { preview ->
            if (rememberLink) {
              val cur = repository.settings.value
              repository.updateSettings(
                cur.copy(
                  savedCloudBackupUrl = cleanUrl,
                  savedCloudBackupLabel = labelInput.trim().ifBlank { "${result.provider.displayName} Backup" },
                  lastCloudSyncTimestamp = System.currentTimeMillis()
                )
              )
            }
            onBackupDownloaded(result.content, preview, cleanUrl, result.provider)
          },
          onFailure = { err ->
            errorMessage = "Downloaded ${(result.bytesRead / 1024).coerceAtLeast(1)} KB from ${result.provider.displayName}, but it is not a valid Tank Calc backup JSON: ${err.localizedMessage}"
          }
        )
      } else {
        isLoading = false
        onCsvDownloaded(result.content, result.provider)
      }
    }
  }

  AlertDialog(
    onDismissRequest = { if (!isLoading) onDismiss() },
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
          text = if (mode == CloudImportMode.BACKUP_JSON) "Import from Google Drive / Cloud" else "Fetch CSV from Google Sheets / Drive",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = if (mode == CloudImportMode.BACKUP_JSON) {
            "Paste a share link from Google Drive, Dropbox, OneDrive, GitHub, or any HTTPS server to import your calibration & backup directly—no manual file upload needed."
          } else {
            "Paste a Google Sheets link or Google Drive CSV share link to pull Dip, Fraction, or N2 calibration tables directly."
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
          value = urlInput,
          onValueChange = {
            urlInput = it
            errorMessage = null
          },
          label = { Text(if (mode == CloudImportMode.BACKUP_JSON) "Google Drive / Cloud File Link" else "Google Sheets / Drive CSV Link") },
          placeholder = { Text("https://drive.google.com/file/d/...") },
          singleLine = false,
          maxLines = 3,
          enabled = !isLoading,
          trailingIcon = {
            Row {
              if (urlInput.isNotEmpty()) {
                IconButton(onClick = { urlInput = ""; errorMessage = null }, enabled = !isLoading) {
                  Icon(Icons.Default.Clear, contentDescription = "Clear Link")
                }
              }
              IconButton(
                onClick = {
                  val clip = clipboard.getText()?.text?.trim()
                  if (!clip.isNullOrBlank()) {
                    urlInput = clip
                    errorMessage = null
                  } else {
                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                  }
                },
                enabled = !isLoading,
                modifier = Modifier.testTag("cloud_import_paste_button")
              ) {
                Icon(Icons.Default.ContentPaste, contentDescription = "Paste Link")
              }
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("cloud_import_url_input")
        )

        // Live provider detection pill
        if (urlInput.isNotBlank()) {
          val isValid = detectedProvider != CloudProvider.INVALID
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isValid) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.errorContainer,
            border = BorderStroke(
              1.dp,
              if (isValid) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            )
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = if (isValid) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = if (isValid) {
                  "Detected: ${detectedProvider.displayName} (auto direct download)"
                } else {
                  "Link must start with https://"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isValid) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
              )
            }
          }
        }

        if (mode == CloudImportMode.BACKUP_JSON) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Checkbox(
              checked = rememberLink,
              onCheckedChange = { rememberLink = it },
              enabled = !isLoading
            )
            Text(
              text = "Save link for 1-tap future sync",
              style = MaterialTheme.typography.bodySmall
            )
          }

          if (rememberLink) {
            OutlinedTextField(
              value = labelInput,
              onValueChange = { labelInput = it },
              label = { Text("Source Name (Optional)") },
              singleLine = true,
              enabled = !isLoading,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }

        // Expandable Google Drive guide
        TextButton(
          onClick = { showDriveHelp = !showDriveHelp },
          contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
        ) {
          Icon(
            imageVector = if (showDriveHelp) Icons.Default.ExpandLess else Icons.Default.HelpOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("How to get a Google Drive direct link?", style = MaterialTheme.typography.labelSmall)
        }

        if (showDriveHelp) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text("1. Upload your TankCalc .json backup to Google Drive.", style = MaterialTheme.typography.labelSmall)
              Text("2. Tap ⋮ → Share → General access → 'Anyone with the link' (Viewer).", style = MaterialTheme.typography.labelSmall)
              Text("3. Tap 'Copy link' and paste it above. Tank Calc converts it automatically!", style = MaterialTheme.typography.labelSmall)
              Text("Tip: You can also tap any .json file in Google Drive / WhatsApp and choose 'Share → Tank Calc'.", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
          }
        }

        if (isLoading) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Downloading & verifying calibration file...", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
          }
        }

        if (errorMessage != null) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.errorContainer
          ) {
            Text(
              text = errorMessage!!,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(10.dp)
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { startDownload(urlInput) },
        enabled = !isLoading && urlInput.isNotBlank() && detectedProvider != CloudProvider.INVALID,
        modifier = Modifier.testTag("cloud_import_fetch_button")
      ) {
        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(if (isLoading) "Fetching..." else "Fetch & Preview")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !isLoading) {
        Text("Cancel")
      }
    }
  )
}

/**
 * Reusable end-to-end verified Backup Preview -> Master Password Auth -> Restore Summary dialog chain.
 * Used for incoming Google Drive links, Operations Menu Cloud Sync, Onboarding, and Android "Open With / Share".
 */
@Composable
fun VerifiedBackupRestoreFlow(
  repository: TankRepository,
  pendingJson: String?,
  preview: RestorePreview?,
  sourceDescription: String,
  requirePassword: Boolean = true,
  onDismissAll: () -> Unit,
  onRestoreSuccess: () -> Unit = {}
) {
  val settings by repository.settings.collectAsState()
  var restoreModeChoice by remember(pendingJson) { mutableStateOf(RestoreMode.REPLACE) }
  var showAuthDialog by remember(pendingJson) { mutableStateOf(false) }
  var restoreResult by remember(pendingJson) { mutableStateOf<RestoreResult?>(null) }

  if (pendingJson != null && preview != null && !showAuthDialog && restoreResult == null) {
    AlertDialog(
      onDismissRequest = onDismissAll,
      title = {
        Column {
          Text("Verify Backup (${preview.exportType})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
            text = sourceDescription,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 450.dp)) {
          Text("Created: ${preview.createdAt}", style = MaterialTheme.typography.bodySmall)
          Text("Tanks: ${preview.manifest.tanksCount} | Dip: ${preview.manifest.totalDipRows} rows | Frac: ${preview.manifest.totalFractionRows} rows", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
          Text("Transactions: ${preview.manifest.transactionsCount} | Loss Reports: ${preview.manifest.lossReportsCount}", style = MaterialTheme.typography.bodySmall)

          if (preview.hasCalibration) {
            Text("Calibration Charts: PRESENT ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
          } else {
            Text("⚠️ No calibration tables found in this file.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
          }

          if (preview.checksumValid) {
            Text("SHA-256 Checksum: VALID ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
          } else {
            Text("⚠️ Checksum mismatch (file may be edited)", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
          }

          if (preview.perTankCounts.isNotEmpty()) {
            HorizontalDivider()
            LazyColumn(modifier = Modifier.heightIn(max = 120.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              items(preview.perTankCounts) { tc ->
                Text(
                  "• ${tc.tankName}: Dip ${tc.dipRows} | Frac ${tc.fractionRows} | N2 ${tc.n2Rows}",
                  style = MaterialTheme.typography.labelSmall
                )
              }
            }
          }

          HorizontalDivider()
          Text("Select Restore Mode:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
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
        Button(
          onClick = {
            if (requirePassword && !AuthSession.isUnlocked()) {
              showAuthDialog = true
            } else {
              val res = BackupManager.restoreBackup(repository, pendingJson, restoreModeChoice)
              restoreResult = res
              if (res.success) onRestoreSuccess()
            }
          }
        ) {
          Text("Restore Now")
        }
      },
      dismissButton = {
        TextButton(onClick = onDismissAll) { Text("Cancel") }
      }
    )
  }

  if (showAuthDialog && pendingJson != null) {
    PasswordDialog(
      title = "Restore Authorization",
      message = "Enter master password to import calibration & backup data:",
      storedHash = settings.passwordHash,
      onDismiss = {
        showAuthDialog = false
        onDismissAll()
      },
      onSuccess = {
        showAuthDialog = false
        val res = BackupManager.restoreBackup(repository, pendingJson, restoreModeChoice)
        restoreResult = res
        if (res.success) onRestoreSuccess()
      }
    )
  }

  if (restoreResult != null) {
    val res = restoreResult!!
    AlertDialog(
      onDismissRequest = {
        restoreResult = null
        onDismissAll()
      },
      title = {
        Text(
          if (res.success) "Import Completed ✓" else "Import Failed",
          color = if (res.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 380.dp)) {
          Text(res.message, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
          HorizontalDivider()
          Text("Tank Calibration Summary:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
          LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(res.perTankChecklist) { tc ->
              Text(
                "• ${tc.tankName}: Dip ${tc.dipRows} | Frac ${tc.fractionRows} | N2 ${tc.n2Rows} ${if (tc.isValid) "✓" else "⚠️"}",
                style = MaterialTheme.typography.labelSmall
              )
            }
          }
        }
      },
      confirmButton = {
        Button(onClick = {
          restoreResult = null
          onDismissAll()
        }) {
          Text("Done")
        }
      }
    )
  }
}

fun formatCloudSyncTimestamp(timestamp: Long): String {
  if (timestamp <= 0L) return "Not synced yet"
  return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
}
