package com.example

import com.example.engine.CloudBackupImporter
import com.example.engine.CloudProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBackupImporterTest {

  @Test
  fun normalizesGoogleDriveShareLink() {
    val shareUrl = "https://drive.google.com/file/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/view?usp=sharing"
    val res = CloudBackupImporter.normalizeDownloadUrl(shareUrl).getOrThrow()
    assertEquals(CloudProvider.GOOGLE_DRIVE, res.provider)
    assertEquals(
      "https://drive.usercontent.google.com/download?id=1AbCdEfGhIjKlMnOpQrStUvWxYz&export=download&confirm=t",
      res.primaryUrl
    )
    assertEquals(
      "https://drive.google.com/uc?export=download&id=1AbCdEfGhIjKlMnOpQrStUvWxYz",
      res.fallbackUrl
    )
  }

  @Test
  fun normalizesGoogleSheetsLinkToCsvExport() {
    val sheetUrl = "https://docs.google.com/spreadsheets/d/1XyZ987654321AbCdEf/edit#gid=456789"
    val res = CloudBackupImporter.normalizeDownloadUrl(sheetUrl, preferCsv = true).getOrThrow()
    assertEquals(CloudProvider.GOOGLE_SHEETS, res.provider)
    assertEquals(
      "https://docs.google.com/spreadsheets/d/1XyZ987654321AbCdEf/export?format=csv&gid=456789",
      res.primaryUrl
    )
  }

  @Test
  fun normalizesDropboxAndGitHubLinks() {
    val dropbox = CloudBackupImporter.normalizeDownloadUrl("https://www.dropbox.com/s/abc123/backup.json?dl=0").getOrThrow()
    assertEquals(CloudProvider.DROPBOX, dropbox.provider)
    assertTrue(dropbox.primaryUrl.endsWith("dl=1"))

    val github = CloudBackupImporter.normalizeDownloadUrl("https://github.com/partex/tankcalc/blob/main/calib/master.json").getOrThrow()
    assertEquals(CloudProvider.GITHUB, github.provider)
    assertEquals("https://raw.githubusercontent.com/partex/tankcalc/main/calib/master.json", github.primaryUrl)
  }

  @Test
  fun rejectsInsecureOrNonHttpsLinks() {
    assertTrue(CloudBackupImporter.normalizeDownloadUrl("http://drive.google.com/file/d/1234567890/view").isFailure)
    assertTrue(CloudBackupImporter.normalizeDownloadUrl("file:///etc/passwd").isFailure)
    assertTrue(CloudBackupImporter.normalizeDownloadUrl("javascript:alert(1)").isFailure)
    assertEquals(CloudProvider.INVALID, CloudBackupImporter.detectProvider("http://example.com/file.json"))
  }
}
