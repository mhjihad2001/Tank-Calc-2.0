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
}
