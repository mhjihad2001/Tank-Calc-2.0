package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppVersionManifest(
  val latestBuild: Int,
  val versionName: String,
  val apkUrl: String,
  val forceUpdate: Boolean,
  val changelog: String
)

sealed class UpdateCheckResult {
  data class UpdateAvailable(
    val manifest: AppVersionManifest,
    val currentBuild: Int,
    val currentVersionName: String
  ) : UpdateCheckResult()

  data class UpToDate(
    val currentBuild: Int,
    val currentVersionName: String,
    val remoteManifest: AppVersionManifest? = null
  ) : UpdateCheckResult()

  data class Error(val message: String) : UpdateCheckResult()
}

object AppUpdateManager {

  const val DEFAULT_MANIFEST_URL =
    "https://raw.githubusercontent.com/mhjihad2001/Tank-Calc-2.0/main/version.json"

  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .build()
  }

  fun getInstalledVersionInfo(context: Context): Pair<Int, String> {
    return try {
      val pm = context.packageManager
      val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, 0)
      }
      val buildCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkgInfo.longVersionCode.toInt()
      } else {
        @Suppress("DEPRECATION")
        pkgInfo.versionCode
      }
      val verName = pkgInfo.versionName ?: "1.0.0"
      Pair(buildCode, verName)
    } catch (_: Exception) {
      Pair(1, "1.0.0")
    }
  }

  private val moshi by lazy {
    com.squareup.moshi.Moshi.Builder()
      .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
      .build()
  }

  private data class RawVersionJson(
    val latest_build: Int? = 1,
    val version_name: String? = "1.0.0",
    val apk_url: String? = null,
    val force_update: Boolean? = false,
    val changelog: String? = null
  )

  fun parseManifestJson(jsonText: String): AppVersionManifest {
    val clean = jsonText.trim().trimStart('\uFEFF')
    val adapter = moshi.adapter(RawVersionJson::class.java)
    val parsed = adapter.fromJson(clean)
      ?: throw IllegalArgumentException("Invalid version.json format")
    val latestBuild = parsed.latest_build ?: 1
    val versionName = (parsed.version_name ?: "1.0.0").ifBlank { "1.0.0" }
    val apkUrl = (parsed.apk_url ?: "").ifBlank {
      "https://github.com/mhjihad2001/Tank-Calc-2.0/releases/latest/download/app-release.apk"
    }
    val forceUpdate = parsed.force_update ?: false
    val changelog = (parsed.changelog ?: "").ifBlank { "Bug fixes and performance improvements." }
    return AppVersionManifest(
      latestBuild = latestBuild,
      versionName = versionName,
      apkUrl = apkUrl,
      forceUpdate = forceUpdate,
      changelog = changelog
    )
  }

  /**
   * Checks remote `version.json?t=<timestamp>` on GitHub.
   * Wrapped in try/catch so offline usage or network failures never crash or block the app.
   */
  suspend fun checkForUpdates(
    context: Context,
    manifestBaseUrl: String = DEFAULT_MANIFEST_URL
  ): UpdateCheckResult = withContext(Dispatchers.IO) {
    try {
      val (currentBuild, currentVerName) = getInstalledVersionInfo(context)
      val separator = if (manifestBaseUrl.contains("?")) "&" else "?"
      val bustUrl = "${manifestBaseUrl}${separator}t=${System.currentTimeMillis()}"

      val request = Request.Builder()
        .url(bustUrl)
        .header("Cache-Control", "no-cache, no-store, must-revalidate")
        .header("Pragma", "no-cache")
        .header("User-Agent", "TankCalc-Android-Updater/2.0")
        .get()
        .build()

      httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          return@withContext UpdateCheckResult.Error("HTTP ${response.code}: Unable to check update manifest.")
        }
        val bodyStr = response.body?.string()
          ?: return@withContext UpdateCheckResult.Error("Empty response from update server.")

        val manifest = parseManifestJson(bodyStr)
        if (manifest.latestBuild > currentBuild) {
          UpdateCheckResult.UpdateAvailable(
            manifest = manifest,
            currentBuild = currentBuild,
            currentVersionName = currentVerName
          )
        } else {
          UpdateCheckResult.UpToDate(
            currentBuild = currentBuild,
            currentVersionName = currentVerName,
            remoteManifest = manifest
          )
        }
      }
    } catch (e: Exception) {
      UpdateCheckResult.Error(e.localizedMessage ?: "Network unavailable")
    }
  }

  /**
   * Downloads the release APK with real-time progress (0..100%) into the app's cache/updates folder.
   */
  suspend fun downloadApkWithProgress(
    context: Context,
    apkUrl: String,
    onProgress: (Int) -> Unit
  ): Result<File> = withContext(Dispatchers.IO) {
    try {
      withContext(Dispatchers.Main) { onProgress(0) }

      val updateDir = File(context.cacheDir, "updates").apply {
        if (!exists()) mkdirs()
      }
      val targetApk = File(updateDir, "TankCalc-update.apk")
      if (targetApk.exists()) {
        targetApk.delete()
      }

      val request = Request.Builder()
        .url(apkUrl)
        .header("User-Agent", "TankCalc-Android-Updater/2.0")
        .get()
        .build()

      httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          return@withContext Result.failure(
            IllegalStateException("Download failed with HTTP ${response.code}")
          )
        }
        val body = response.body
          ?: return@withContext Result.failure(IllegalStateException("Empty APK file response"))

        val totalBytes = body.contentLength()
        var downloadedBytes = 0L
        var lastReportedPercent = -1

        body.byteStream().use { input ->
          FileOutputStream(targetApk).use { output ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
              val read = input.read(buffer)
              if (read == -1) break
              output.write(buffer, 0, read)
              downloadedBytes += read
              if (totalBytes > 0) {
                val pct = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                if (pct != lastReportedPercent) {
                  lastReportedPercent = pct
                  withContext(Dispatchers.Main) { onProgress(pct) }
                }
              }
            }
            output.flush()
          }
        }
      }

      if (!targetApk.exists() || targetApk.length() < 1024L) {
        return@withContext Result.failure(
          IllegalStateException("Downloaded APK file is incomplete or invalid.")
        )
      }

      withContext(Dispatchers.Main) { onProgress(100) }
      Result.success(targetApk)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Launches Android's native package installer via FileProvider URI.
   * On Android 8.0+ (API 26+), checks `canRequestPackageInstalls()` and opens system permission screen if needed.
   */
  fun installDownloadedApk(context: Context, apkFile: File): Result<Unit> {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (!context.packageManager.canRequestPackageInstalls()) {
          val settingsIntent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
          ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          context.startActivity(settingsIntent)
          return Result.failure(
            IllegalStateException("Please allow 'Install unknown apps' for Tank Calc, then tap Install again.")
          )
        }
      }

      val apkUri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
      )

      val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(apkUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(installIntent)
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
