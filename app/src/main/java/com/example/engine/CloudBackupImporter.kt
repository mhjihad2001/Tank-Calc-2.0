package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.Locale

enum class CloudProvider(val displayName: String) {
  GOOGLE_DRIVE("Google Drive"),
  GOOGLE_SHEETS("Google Sheets"),
  DROPBOX("Dropbox"),
  ONEDRIVE("OneDrive"),
  GITHUB("GitHub"),
  DIRECT_HTTPS("Direct HTTPS"),
  INVALID("Unsupported Link")
}

data class NormalizedCloudLink(
  val provider: CloudProvider,
  val primaryUrl: String,
  val fallbackUrl: String? = null
)

data class CloudDownloadResult(
  val success: Boolean,
  val content: String = "",
  val provider: CloudProvider = CloudProvider.DIRECT_HTTPS,
  val resolvedUrl: String = "",
  val bytesRead: Int = 0,
  val errorMessage: String? = null
)

/**
 * Direct Cloud & Google Drive calibration/backup downloader.
 * Supports Google Drive share links, Google Sheets CSV export links,
 * Dropbox, OneDrive, GitHub raw/blob links, and standard HTTPS URLs.
 */
object CloudBackupImporter {
  private const val MAX_DOWNLOAD_BYTES = 15 * 1024 * 1024 // 15 MB hard ceiling
  private const val MAX_REDIRECTS = 5
  private const val CONNECT_TIMEOUT_MS = 15_000
  private const val READ_TIMEOUT_MS = 20_000

  private val DRIVE_FILE_D_REGEX = Regex("""/file/d/([a-zA-Z0-9_-]{10,})""")
  private val DRIVE_QUERY_ID_REGEX = Regex("""[?&]id=([a-zA-Z0-9_-]{10,})""")
  private val SHEETS_D_REGEX = Regex("""/spreadsheets/d/([a-zA-Z0-9_-]{10,})""")
  private val SHEETS_GID_REGEX = Regex("""[#?&]gid=(\d+)""")
  private val GITHUB_BLOB_REGEX = Regex("""^https://github\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.*)$""")
  private val DRIVE_CONFIRM_TOKEN_REGEX = Regex("""name="confirm"\s+value="([^"]+)"""")
  private val DRIVE_UUID_TOKEN_REGEX = Regex("""name="uuid"\s+value="([^"]+)"""")

  fun detectProvider(rawInput: String): CloudProvider {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return CloudProvider.INVALID
    val lower = trimmed.lowercase(Locale.US)
    if (!lower.startsWith("https://")) return CloudProvider.INVALID

    val host = try {
      URL(trimmed).host.lowercase(Locale.US)
    } catch (_: Exception) {
      return CloudProvider.INVALID
    }

    return when {
      host == "docs.google.com" && lower.contains("/spreadsheets/") -> CloudProvider.GOOGLE_SHEETS
      host == "drive.google.com" || host == "drive.usercontent.google.com" || host == "docs.google.com" -> CloudProvider.GOOGLE_DRIVE
      host == "dropbox.com" || host.endsWith(".dropbox.com") || host == "dl.dropboxusercontent.com" -> CloudProvider.DROPBOX
      host == "1drv.ms" || host.endsWith("onedrive.live.com") || host.endsWith(".sharepoint.com") -> CloudProvider.ONEDRIVE
      host == "github.com" || host == "raw.githubusercontent.com" || host == "gist.githubusercontent.com" -> CloudProvider.GITHUB
      else -> CloudProvider.DIRECT_HTTPS
    }
  }

  /**
   * Converts user-pasted share URLs from Google Drive, Google Sheets, Dropbox,
   * OneDrive, or GitHub into direct raw file download URLs.
   */
  fun normalizeDownloadUrl(rawInput: String, preferCsv: Boolean = false): Result<NormalizedCloudLink> {
    val trimmed = rawInput.trim()
    if (trimmed.isBlank()) {
      return Result.failure(IllegalArgumentException("Please paste a valid HTTPS link."))
    }
    if (!trimmed.lowercase(Locale.US).startsWith("https://")) {
      return Result.failure(IllegalArgumentException("Only secure HTTPS links (https://...) are supported."))
    }

    val provider = detectProvider(trimmed)
    if (provider == CloudProvider.INVALID) {
      return Result.failure(IllegalArgumentException("Invalid URL format. Please check the link and try again."))
    }

    return when (provider) {
      CloudProvider.GOOGLE_DRIVE -> {
        val fileId = DRIVE_FILE_D_REGEX.find(trimmed)?.groupValues?.get(1)
          ?: DRIVE_QUERY_ID_REGEX.find(trimmed)?.groupValues?.get(1)
        if (fileId.isNullOrBlank()) {
          Result.failure(
            IllegalArgumentException("Could not find a Google Drive file ID in this link. Copy the share link of the specific file (not a folder).")
          )
        } else {
          Result.success(
            NormalizedCloudLink(
              provider = CloudProvider.GOOGLE_DRIVE,
              primaryUrl = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t",
              fallbackUrl = "https://drive.google.com/uc?export=download&id=$fileId"
            )
          )
        }
      }

      CloudProvider.GOOGLE_SHEETS -> {
        val sheetId = SHEETS_D_REGEX.find(trimmed)?.groupValues?.get(1)
        if (sheetId.isNullOrBlank()) {
          Result.failure(IllegalArgumentException("Could not find a Google Sheets document ID in this link."))
        } else {
          val gid = SHEETS_GID_REGEX.find(trimmed)?.groupValues?.get(1) ?: "0"
          val exportUrl = "https://docs.google.com/spreadsheets/d/$sheetId/export?format=csv&gid=$gid"
          Result.success(
            NormalizedCloudLink(
              provider = CloudProvider.GOOGLE_SHEETS,
              primaryUrl = exportUrl
            )
          )
        }
      }

      CloudProvider.DROPBOX -> {
        val direct = when {
          trimmed.contains("dl=0") -> trimmed.replace("dl=0", "dl=1")
          trimmed.contains("dl=1") -> trimmed
          trimmed.contains("?") -> "$trimmed&dl=1"
          else -> "$trimmed?dl=1"
        }
        Result.success(NormalizedCloudLink(provider = CloudProvider.DROPBOX, primaryUrl = direct))
      }

      CloudProvider.ONEDRIVE -> {
        val direct = when {
          trimmed.contains("download=1") -> trimmed
          trimmed.contains("?") -> "$trimmed&download=1"
          else -> "$trimmed?download=1"
        }
        Result.success(NormalizedCloudLink(provider = CloudProvider.ONEDRIVE, primaryUrl = direct))
      }

      CloudProvider.GITHUB -> {
        val blobMatch = GITHUB_BLOB_REGEX.find(trimmed)
        val direct = if (blobMatch != null) {
          val (owner, repo, branch, path) = blobMatch.destructured
          "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
        } else {
          trimmed
        }
        Result.success(NormalizedCloudLink(provider = CloudProvider.GITHUB, primaryUrl = direct))
      }

      CloudProvider.DIRECT_HTTPS -> {
        Result.success(NormalizedCloudLink(provider = CloudProvider.DIRECT_HTTPS, primaryUrl = trimmed))
      }

      CloudProvider.INVALID -> {
        Result.failure(IllegalArgumentException("Unsupported URL."))
      }
    }
  }

  /**
   * Downloads text content (JSON backup or CSV calibration table) safely over HTTPS.
   */
  suspend fun downloadTextFromCloud(
    rawUrl: String,
    preferCsv: Boolean = false
  ): CloudDownloadResult = withContext(Dispatchers.IO) {
    val normalized = normalizeDownloadUrl(rawUrl, preferCsv).getOrElse { err ->
      return@withContext CloudDownloadResult(
        success = false,
        errorMessage = err.message ?: "Invalid link."
      )
    }

    val primaryAttempt = fetchUrlWithSafeRedirects(normalized.primaryUrl, normalized.provider, preferCsv)
    if (primaryAttempt.success || normalized.fallbackUrl == null) {
      return@withContext primaryAttempt
    }

    // Try fallback URL (e.g., standard drive.google.com/uc endpoint)
    val fallbackAttempt = fetchUrlWithSafeRedirects(normalized.fallbackUrl, normalized.provider, preferCsv)
    if (fallbackAttempt.success) fallbackAttempt else primaryAttempt
  }

  private fun validatePublicHttpsHost(url: URL) {
    if (!url.protocol.equals("https", ignoreCase = true)) {
      throw SecurityException("Only HTTPS downloads are permitted.")
    }
    val host = url.host?.trim()?.lowercase(Locale.US) ?: ""
    if (host.isEmpty() || host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) {
      throw SecurityException("Local or internal hosts are not allowed.")
    }

    val addresses = InetAddress.getAllByName(host)
    if (addresses.isEmpty()) {
      throw SecurityException("Could not resolve host '$host'.")
    }
    for (addr in addresses) {
      if (addr.isLoopbackAddress ||
        addr.isSiteLocalAddress ||
        addr.isLinkLocalAddress ||
        addr.isAnyLocalAddress ||
        addr.isMulticastAddress
      ) {
        throw SecurityException("Private or loopback network addresses are blocked for security.")
      }
      val raw = addr.address
      // Block IPv6 Unique Local Addresses (fc00::/7) and carrier-grade NAT (100.64.0.0/10)
      if (raw.size == 16 && (raw[0].toInt() and 0xFE) == 0xFC) {
        throw SecurityException("Private IPv6 addresses are blocked for security.")
      }
      if (raw.size == 4) {
        val b0 = raw[0].toInt() and 0xFF
        val b1 = raw[1].toInt() and 0xFF
        if (b0 == 100 && b1 in 64..127) {
          throw SecurityException("Private network addresses are blocked for security.")
        }
      }
    }
  }

  private fun fetchUrlWithSafeRedirects(
    startUrl: String,
    provider: CloudProvider,
    preferCsv: Boolean
  ): CloudDownloadResult {
    var currentUrlStr = startUrl
    var hops = 0

    try {
      while (hops <= MAX_REDIRECTS) {
        val url = URL(currentUrlStr)
        validatePublicHttpsHost(url)

        val conn = (url.openConnection() as HttpURLConnection).apply {
          requestMethod = "GET"
          instanceFollowRedirects = false
          connectTimeout = CONNECT_TIMEOUT_MS
          readTimeout = READ_TIMEOUT_MS
          setRequestProperty("User-Agent", "TankCalc-Android/1.0")
          setRequestProperty("Accept", if (preferCsv) "text/csv,text/plain,*/*" else "application/json,text/plain,*/*")
        }

        try {
          val code = conn.responseCode
          if (code in listOf(
              HttpURLConnection.HTTP_MOVED_PERM,
              HttpURLConnection.HTTP_MOVED_TEMP,
              HttpURLConnection.HTTP_SEE_OTHER,
              307,
              308
            )
          ) {
            val location = conn.getHeaderField("Location")
            if (location.isNullOrBlank()) {
              return CloudDownloadResult(
                success = false,
                provider = provider,
                resolvedUrl = currentUrlStr,
                errorMessage = "Server redirected without a target address."
              )
            }
            val nextUrl = URL(url, location)
            currentUrlStr = nextUrl.toString()
            hops++
            continue
          }

          if (code !in 200..299) {
            val hint = when (code) {
              401, 403 -> if (provider == CloudProvider.GOOGLE_DRIVE || provider == CloudProvider.GOOGLE_SHEETS) {
                "Access denied (HTTP $code). Open Google Drive → Share → set General access to 'Anyone with the link (Viewer)'."
              } else {
                "Access denied (HTTP $code). Make sure the link is publicly readable."
              }
              404 -> "File not found (HTTP 404). Verify the link was copied completely."
              else -> "Download failed with HTTP status $code."
            }
            return CloudDownloadResult(
              success = false,
              provider = provider,
              resolvedUrl = currentUrlStr,
              errorMessage = hint
            )
          }

          val contentLength = conn.contentLengthLong
          if (contentLength > MAX_DOWNLOAD_BYTES) {
            return CloudDownloadResult(
              success = false,
              provider = provider,
              resolvedUrl = currentUrlStr,
              errorMessage = "File is too large (${contentLength / (1024 * 1024)} MB). Maximum supported size is 15 MB."
            )
          }

          val bytes = conn.inputStream.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (true) {
              val read = input.read(buf)
              if (read == -1) break
              total += read
              if (total > MAX_DOWNLOAD_BYTES) {
                return CloudDownloadResult(
                  success = false,
                  provider = provider,
                  resolvedUrl = currentUrlStr,
                  errorMessage = "File exceeded the 15 MB safety download limit."
                )
              }
              out.write(buf, 0, read)
            }
            out.toByteArray()
          }

          val text = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF')
          val trimmedStart = text.trimStart().lowercase(Locale.US)

          // Detect HTML page returned instead of raw JSON/CSV (common with private Drive links or large-file confirm pages)
          if (trimmedStart.startsWith("<!doctype html") || trimmedStart.startsWith("<html")) {
            // Check if Google Drive returned a virus-scan confirmation page for a large file
            if (provider == CloudProvider.GOOGLE_DRIVE && hops < MAX_REDIRECTS) {
              val confirmVal = DRIVE_CONFIRM_TOKEN_REGEX.find(text)?.groupValues?.get(1)
              val uuidVal = DRIVE_UUID_TOKEN_REGEX.find(text)?.groupValues?.get(1)
              val fileId = DRIVE_QUERY_ID_REGEX.find(currentUrlStr)?.groupValues?.get(1)
                ?: DRIVE_FILE_D_REGEX.find(startUrl)?.groupValues?.get(1)
              if (!confirmVal.isNullOrBlank() && !fileId.isNullOrBlank()) {
                val uuidPart = if (!uuidVal.isNullOrBlank()) "&uuid=$uuidVal" else ""
                currentUrlStr = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=$confirmVal$uuidPart"
                hops++
                continue
              }
            }

            val htmlHint = if (provider == CloudProvider.GOOGLE_DRIVE || provider == CloudProvider.GOOGLE_SHEETS) {
              "Google Drive returned a web/login page instead of the file. In Google Drive, tap Share → General access → 'Anyone with the link (Viewer)', then try again."
            } else {
              "This link returned an HTML web page instead of a raw backup/CSV file. Please use a direct file download link."
            }
            return CloudDownloadResult(
              success = false,
              provider = provider,
              resolvedUrl = currentUrlStr,
              errorMessage = htmlHint
            )
          }

          if (text.isBlank()) {
            return CloudDownloadResult(
              success = false,
              provider = provider,
              resolvedUrl = currentUrlStr,
              errorMessage = "Downloaded file is empty."
            )
          }

          return CloudDownloadResult(
            success = true,
            content = text,
            provider = provider,
            resolvedUrl = currentUrlStr,
            bytesRead = bytes.size
          )
        } finally {
          conn.disconnect()
        }
      }

      return CloudDownloadResult(
        success = false,
        provider = provider,
        resolvedUrl = currentUrlStr,
        errorMessage = "Too many redirects while downloading file."
      )
    } catch (e: SecurityException) {
      return CloudDownloadResult(
        success = false,
        provider = provider,
        resolvedUrl = currentUrlStr,
        errorMessage = e.message ?: "Blocked unsafe URL."
      )
    } catch (e: Exception) {
      return CloudDownloadResult(
        success = false,
        provider = provider,
        resolvedUrl = currentUrlStr,
        errorMessage = "Network error: ${e.localizedMessage ?: "Please check your internet connection."}"
      )
    }
  }
}
