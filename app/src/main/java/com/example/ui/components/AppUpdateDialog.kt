package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.engine.AppUpdateManager
import com.example.engine.AppVersionManifest
import com.example.engine.HapticEvent
import com.example.engine.rememberHapticController
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AppUpdateDialog(
  manifest: AppVersionManifest,
  currentVersionName: String,
  currentBuild: Int,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val haptics = rememberHapticController()

  var isDownloading by remember { mutableStateOf(false) }
  var downloadProgress by remember { mutableIntStateOf(0) }
  var downloadedApkFile by remember { mutableStateOf<File?>(null) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val isForce = manifest.forceUpdate

  AlertDialog(
    onDismissRequest = {
      if (!isForce && !isDownloading) {
        onDismiss()
      }
    },
    properties = DialogProperties(
      dismissOnBackPress = !isForce && !isDownloading,
      dismissOnClickOutside = !isForce && !isDownloading
    ),
    icon = {
      Icon(
        imageVector = Icons.Default.SystemUpdate,
        contentDescription = "Update Available",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(32.dp)
      )
    },
    title = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "Update Available • v${manifest.versionName}",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.ExtraBold
        )
        Text(
          text = "Installed: v$currentVersionName (Build $currentBuild) → Build ${manifest.latestBuild}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        if (isForce) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = "Mandatory refinery update required to continue.",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
              )
            }
          }
        }

        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = "What's New (Changelog):",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = manifest.changelog.ifBlank { "Performance and stability improvements." },
              style = MaterialTheme.typography.bodyMedium
            )
          }
        }

        if (isDownloading) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Downloading update package...",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = "$downloadProgress%",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
              )
            }
            LinearProgressIndicator(
              progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .testTag("update_download_progress")
            )
          }
        }

        if (errorMessage != null) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = errorMessage!!,
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(10.dp)
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        enabled = !isDownloading,
        onClick = {
          haptics.trigger(HapticEvent.MODE_SWITCH)
          errorMessage = null

          // If APK was already downloaded (e.g. returning from Unknown Sources permission toggle), install immediately
          val readyFile = downloadedApkFile
          if (readyFile != null && readyFile.exists()) {
            val installRes = AppUpdateManager.installDownloadedApk(context, readyFile)
            installRes.onFailure { err ->
              errorMessage = err.localizedMessage ?: "Failed to launch installer."
            }
            return@Button
          }

          isDownloading = true
          downloadProgress = 0
          scope.launch {
            val res = AppUpdateManager.downloadApkWithProgress(
              context = context,
              apkUrl = manifest.apkUrl,
              onProgress = { pct -> downloadProgress = pct }
            )
            isDownloading = false
            res.fold(
              onSuccess = { apkFile ->
                downloadedApkFile = apkFile
                haptics.trigger(HapticEvent.SUCCESS)
                val installRes = AppUpdateManager.installDownloadedApk(context, apkFile)
                installRes.onFailure { err ->
                  errorMessage = err.localizedMessage ?: "Failed to launch installer."
                }
              },
              onFailure = { err ->
                haptics.trigger(HapticEvent.WARNING_ALERT)
                errorMessage = "Download failed: ${err.localizedMessage ?: "Check network connection"}"
              }
            )
          }
        },
        modifier = Modifier.testTag("update_now_button")
      ) {
        Icon(
          imageVector = Icons.Default.CloudDownload,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          when {
            isDownloading -> "Downloading ($downloadProgress%)..."
            downloadedApkFile?.exists() == true -> "Install Update Now"
            else -> "Update Now"
          }
        )
      }
    },
    dismissButton = if (!isForce) {
      {
        TextButton(
          enabled = !isDownloading,
          onClick = {
            haptics.trigger(HapticEvent.NAV_STEP)
            onDismiss()
          },
          modifier = Modifier.testTag("update_later_button")
        ) {
          Text("Later")
        }
      }
    } else null
  )
}
