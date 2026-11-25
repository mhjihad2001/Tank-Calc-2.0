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

  const val DEFAULT_GITHUB_OWNER = "mhjihad2001"
  const val DEFAULT_GITHUB_REPO = "Tank-Calc-2.0"
  const val DEFAULT_MANIFEST_URL =
    "https://raw.githubusercontent.com/$DEFAULT_GITHUB_OWNER/$DEFAULT_GITHUB_REPO/main/version.json"

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

  private data class GitHubReleaseAssetJson(
    val name: String? = null,
    val browser_download_url: String? = null
  )

  private data class GitHubReleaseJson(
    val tag_name: String? = null,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean? = false,
    val assets: List<GitHubReleaseAssetJson>? = emptyList()
  )

  /**
   * Normalizes user-entered GitHub repo/blob links into a direct raw `version.json` URL.
   */
  fun normalizeManifestUrl(input: String?): String {
    val trimmed = input?.trim().orEmpty()
    if (trimmed.isBlank()) return DEFAULT_MANIFEST_URL

    // Handle https://github.com/owner/repo/blob/<branch>/version.json
    val blobRegex = Regex("""^https?://github\.com/([^/]+)/([^/]+)/blob/(.+)$""", RegexOption.IGNORE_CASE)
    blobRegex.matchEntire(trimmed)?.let { m ->
      val owner = m.groupValues[1]
      val repo = m.groupValues[2].removeSuffix(".git")
      val path = m.groupValues[3]
      return "https://raw.githubusercontent.com/$owner/$repo/$path"
    }

    // Handle bare https://github.com/owner/repo (or with trailing slash/.git)
    val repoRegex = Regex("""^https?://github\.com/([^/]+)/([^/?#]+)/?$""", RegexOption.IGNORE_CASE)
    repoRegex.matchEntire(trimmed)?.let { m ->
      val owner = m.groupValues[1]
      val repo = m.groupValues[2].removeSuffix(".git")
      return "https://raw.githubusercontent.com/$owner/$repo/main/version.json"
    }

    return trimmed
  }

  /**
   * Extracts GitHub (owner, repo) from raw.githubusercontent.com, github.com, or api.github.com URLs.
   */
  fun extractGitHubOwnerRepo(url: String): Pair<String, String>? {
    val rawRegex = Regex("""^https?://raw\.githubusercontent\.com/([^/]+)/([^/]+)/""", RegexOption.IGNORE_CASE)
    rawRegex.find(url)?.let { m ->
      return Pair(m.groupValues[1], m.groupValues[2].removeSuffix(".git"))
    }
    val ghRegex = Regex("""^https?://github\.com/([^/]+)/([^/?#]+)""", RegexOption.IGNORE_CASE)
    ghRegex.find(url)?.let { m ->
      return Pair(m.groupValues[1], m.groupValues[2].removeSuffix(".git"))
    }
    val apiRegex = Regex("""^https?://api\.github\.com/repos/([^/]+)/([^/?#]+)""", RegexOption.IGNORE_CASE)
    apiRegex.find(url)?.let { m ->
      return Pair(m.groupValues[1], m.groupValues[2].removeSuffix(".git"))
    }
    return null
  }

  /**
   * Compares two semantic version strings (e.g., "1.0.2" vs "1.0.0", ignoring leading "v").
   * Returns >0 if [remote] is newer than [current], 0 if equal, <0 if older.
   */
  fun compareSemanticVersions(remote: String, current: String): Int {
    val rParts = remote.trim().removePrefix("v").removePrefix("V")
      .split(".", "-", "+")
      .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
    val cParts = current.trim().removePrefix("v").removePrefix("V")
      .split(".", "-", "+")
      .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
    val maxLen = maxOf(rParts.size, cParts.size)
    for (i in 0 until maxLen) {
      val r = rParts.getOrElse(i) { 0 }
      val c = cParts.getOrElse(i) { 0 }
      if (r != c) return r.compareTo(c)
    }
    return 0
  }

  fun parseManifestJson(jsonText: String): AppVersionManifest {
    val clean = jsonText.trim().trimStart('\uFEFF')
    val adapter = moshi.adapter(RawVersionJson::class.java)
    val parsed = adapter.fromJson(clean)
      ?: throw IllegalArgumentException("Invalid version.json format")
    val latestBuild = parsed.latest_build ?: 1
    val versionName = (parsed.version_name ?: "1.0.0").ifBlank { "1.0.0" }
    val apkUrl = (parsed.apk_url ?: "").ifBlank {
      "https://github.com/$DEFAULT_GITHUB_OWNER/$DEFAULT_GITHUB_REPO/releases/latest/download/app-release.apk"
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
   * Parses GitHub Releases API `/releases/latest` JSON as a fallback when `version.json`
   * is not yet present on `main`/`master`.
   */
  fun parseGitHubReleaseJson(
    jsonText: String,
    owner: String,
    repo: String,
    currentBuild: Int,
    currentVersionName: String
  ): AppVersionManifest {
    val clean = jsonText.trim().trimStart('\uFEFF')
    val adapter = moshi.adapter(GitHubReleaseJson::class.java)
    val parsed = adapter.fromJson(clean)
      ?: throw IllegalArgumentException("Invalid GitHub release metadata")
    val rawTag = (parsed.tag_name ?: parsed.name ?: currentVersionName).trim()
    val versionName = rawTag.removePrefix("v").removePrefix("V").ifBlank { currentVersionName }
    val apkAssetUrl = parsed.assets
      ?.firstOrNull { it.name?.endsWith(".apk", ignoreCase = true) == true && !it.browser_download_url.isNullOrBlank() }
      ?.browser_download_url
      ?: "https://github.com/$owner/$repo/releases/latest/download/app-release.apk"
    val changelog = parsed.body?.trim().takeUnless { it.isNullOrBlank() }
      ?: "New release $rawTag available."
    val isNewerBySemver = compareSemanticVersions(versionName, currentVersionName) > 0
    val inferredBuild = if (isNewerBySemver) currentBuild + 1 else currentBuild
    return AppVersionManifest(
      latestBuild = inferredBuild,
      versionName = versionName,
      apkUrl = apkAssetUrl,
      forceUpdate = false,
      changelog = changelog
    )
  }

  private fun buildManifestCandidateUrls(primaryUrl: String, ownerRepo: Pair<String, String>?): List<String> {
    val candidates = linkedSetOf<String>()
    candidates.add(primaryUrl)
    if (primaryUrl.contains("/main/version.json", ignoreCase = true)) {
      candidates.add(primaryUrl.replace("/main/version.json", "/master/version.json", ignoreCase = true))
    } else if (primaryUrl.contains("/master/version.json", ignoreCase = true)) {
      candidates.add(primaryUrl.replace("/master/version.json", "/main/version.json", ignoreCase = true))
    }
    if (ownerRepo != null) {
      val (owner, repo) = ownerRepo
      candidates.add("https://github.com/$owner/$repo/releases/latest/download/version.json")
    }
    return candidates.toList()
  }

  private fun evaluateManifest(
    manifest: AppVersionManifest,
    currentBuild: Int,
    currentVerName: String
  ): UpdateCheckResult {
    val hasNewerBuild = manifest.latestBuild > currentBuild
    val hasNewerSemver =
      manifest.latestBuild >= currentBuild && compareSemanticVersions(manifest.versionName, currentVerName) > 0
    return if (hasNewerBuild || hasNewerSemver) {
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

  /**
   * Checks remote `version.json?t=<timestamp>` on GitHub with automatic branch (`main` -> `master`)
   * and GitHub Releases API fallback.
   * Wrapped in try/catch so offline usage or network failures never crash or block the app.
   */
  suspend fun checkForUpdates(
    context: Context,
    manifestBaseUrl: String = DEFAULT_MANIFEST_URL
  ): UpdateCheckResult = withContext(Dispatchers.IO) {
    try {
      val (currentBuild, currentVerName) = getInstalledVersionInfo(context)
      val primaryUrl = normalizeManifestUrl(manifestBaseUrl)
      val ownerRepo = extractGitHubOwnerRepo(primaryUrl)
      val candidateUrls = buildManifestCandidateUrls(primaryUrl, ownerRepo)

      var lastHttpCode = 0
      val ts = System.currentTimeMillis()

      // 1. Try version.json candidates (main -> master -> release asset)
      for (candidate in candidateUrls) {
        val separator = if (candidate.contains("?")) "&" else "?"
        val bustUrl = "${candidate}${separator}t=$ts"

        val request = Request.Builder()
          .url(bustUrl)
          .header("Cache-Control", "no-cache, no-store, must-revalidate")
          .header("Pragma", "no-cache")
          .header("User-Agent", "TankCalc-Android-Updater/2.0")
          .get()
          .build()

        httpClient.newCall(request).execute().use { response ->
          lastHttpCode = response.code
          if (response.isSuccessful) {
            val bodyStr = response.body?.string()
            if (!bodyStr.isNullOrBlank()) {
              val manifest = parseManifestJson(bodyStr)
              return@withContext evaluateManifest(manifest, currentBuild, currentVerName)
            }
          }
        }
      }

      // 2. Fallback to GitHub Releases API (/releases/latest) if version.json returned 404
      if (ownerRepo != null) {
        val (owner, repo) = ownerRepo
        val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val apiRequest = Request.Builder()
          .url(apiUrl)
          .header("Accept", "application/vnd.github+json")
          .header("User-Agent", "TankCalc-Android-Updater/2.0")
          .get()
          .build()

        httpClient.newCall(apiRequest).execute().use { response ->
          if (response.isSuccessful) {
            val bodyStr = response.body?.string()
            if (!bodyStr.isNullOrBlank()) {
              val releaseManifest = parseGitHubReleaseJson(
                jsonText = bodyStr,
                owner = owner,
                repo = repo,
                currentBuild = currentBuild,
                currentVersionName = currentVerName
              )
              return@withContext evaluateManifest(releaseManifest, currentBuild, currentVerName)
            }
          } else if (lastHttpCode == 0) {
            lastHttpCode = response.code
          }
        }
      }

      // 3. Produce a clear, actionable diagnostic message
      val msg = when (lastHttpCode) {
        404 -> {
          if (ownerRepo != null) {
            val (owner, repo) = ownerRepo
            "version.json not found on GitHub ($owner/$repo). Push this code to the public GitHub 'main' branch or publish a Release first."
          } else {
            "Update manifest (version.json) not found (HTTP 404). Check the Update Source URL in Settings."
          }
        }
        403, 429 -> "GitHub rate limit or access denied (HTTP $lastHttpCode). Please try again shortly."
        0 -> "Unable to reach update server. Check your internet connection."
        else -> "HTTP $lastHttpCode: Unable to check update manifest."
      }
      UpdateCheckResult.Error(msg)
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
