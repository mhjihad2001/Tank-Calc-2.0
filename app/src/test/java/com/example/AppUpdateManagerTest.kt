package com.example

import com.example.engine.AppUpdateManager
import org.junit.Assert.*
import org.junit.Test

class AppUpdateManagerTest {

  @Test
  fun parseManifestJson_parsesExactRootVersionJsonStructure() {
    val json = """
      {
        "latest_build": 5,
        "version_name": "2.1.0",
        "apk_url": "https://github.com/mhjihad2001/Tank-Calc-2.0/releases/latest/download/app-release.apk",
        "force_update": true,
        "changelog": "Added automated self-update and direct 1mm LPG tank support."
      }
    """.trimIndent()

    val manifest = AppUpdateManager.parseManifestJson(json)
    assertEquals(5, manifest.latestBuild)
    assertEquals("2.1.0", manifest.versionName)
    assertEquals(
      "https://github.com/mhjihad2001/Tank-Calc-2.0/releases/latest/download/app-release.apk",
      manifest.apkUrl
    )
    assertTrue(manifest.forceUpdate)
    assertEquals("Added automated self-update and direct 1mm LPG tank support.", manifest.changelog)
  }

  @Test
  fun parseManifestJson_handlesDefaultValuesGracefully() {
    val json = """
      {
        "latest_build": 1,
        "version_name": "1.0.0",
        "apk_url": "https://github.com/mhjihad2001/Tank-Calc-2.0/releases/latest/download/app-release.apk",
        "force_update": false,
        "changelog": "Initial release."
      }
    """.trimIndent()

    val manifest = AppUpdateManager.parseManifestJson(json)
    assertEquals(1, manifest.latestBuild)
    assertEquals("1.0.0", manifest.versionName)
    assertFalse(manifest.forceUpdate)
    assertEquals("Initial release.", manifest.changelog)
  }

  @Test
  fun normalizeManifestUrl_convertsGitHubRepoAndBlobUrlsToRaw() {
    assertEquals(
      AppUpdateManager.DEFAULT_MANIFEST_URL,
      AppUpdateManager.normalizeManifestUrl("")
    )
    assertEquals(
      "https://raw.githubusercontent.com/mhjihad2001/Tank-Calc-2.0/main/version.json",
      AppUpdateManager.normalizeManifestUrl("https://github.com/mhjihad2001/Tank-Calc-2.0")
    )
    assertEquals(
      "https://raw.githubusercontent.com/mhjihad2001/Tank-Calc-2.0/main/version.json",
      AppUpdateManager.normalizeManifestUrl("https://github.com/mhjihad2001/Tank-Calc-2.0/blob/main/version.json")
    )
  }

  @Test
  fun extractGitHubOwnerRepo_parsesRawAndWebUrls() {
    assertEquals(
      Pair("mhjihad2001", "Tank-Calc-2.0"),
      AppUpdateManager.extractGitHubOwnerRepo(AppUpdateManager.DEFAULT_MANIFEST_URL)
    )
    assertEquals(
      Pair("mhjihad2001", "Tank-Calc-2.0"),
      AppUpdateManager.extractGitHubOwnerRepo("https://github.com/mhjihad2001/Tank-Calc-2.0")
    )
  }

  @Test
  fun parseGitHubReleaseJson_fallbackExtractsVersionAndApkAsset() {
    val releaseJson = """
      {
        "tag_name": "v1.2.0",
        "name": "v1.2.0",
        "body": "Fixed blending dip estimation and added dark haptics.",
        "draft": false,
        "assets": [
          {
            "name": "app-release.apk",
            "browser_download_url": "https://github.com/mhjihad2001/Tank-Calc-2.0/releases/download/v1.2.0/app-release.apk"
          }
        ]
      }
    """.trimIndent()

    val manifest = AppUpdateManager.parseGitHubReleaseJson(
      jsonText = releaseJson,
      owner = "mhjihad2001",
      repo = "Tank-Calc-2.0",
      currentBuild = 1,
      currentVersionName = "1.0.0"
    )
    assertEquals(2, manifest.latestBuild)
    assertEquals("1.2.0", manifest.versionName)
    assertEquals(
      "https://github.com/mhjihad2001/Tank-Calc-2.0/releases/download/v1.2.0/app-release.apk",
      manifest.apkUrl
    )
    assertEquals("Fixed blending dip estimation and added dark haptics.", manifest.changelog)
  }

  @Test
  fun compareSemanticVersions_handlesSemverOrdering() {
    assertTrue(AppUpdateManager.compareSemanticVersions("1.0.1", "1.0.0") > 0)
    assertTrue(AppUpdateManager.compareSemanticVersions("v2.0.0", "1.9.9") > 0)
    assertEquals(0, AppUpdateManager.compareSemanticVersions("v1.0.0", "1.0.0"))
    assertTrue(AppUpdateManager.compareSemanticVersions("1.0.0", "1.1.0") < 0)
  }
}
