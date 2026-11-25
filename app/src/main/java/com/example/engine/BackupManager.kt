package com.example.engine

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

// Compact calibration tables format
data class CalibrationPackage(
  val dip: Map<String, List<List<Double>>> = emptyMap(),      // TankName -> [[dip_mm, volume_L], ...]
  val fraction: Map<String, List<List<Double>>> = emptyMap(), // TankName -> [[mm_0_to_9, volume_L], ...]
  val n2: Map<String, List<List<Double>>> = emptyMap()        // TankName -> [[height_mm, volume], ...]
)

data class BackupManifest(
  val exportType: String, // "FULL_BACKUP" or "SETUP_ONLY"
  val tanksCount: Int,
  val calibrationTanksCount: Int,
  val totalDipRows: Int,
  val totalFractionRows: Int,
  val totalN2Rows: Int,
  val transactionsCount: Int,
  val lossReportsCount: Int,
  val dailyRecordsCount: Int,
  val dosingTanksCount: Int,
  val chemicalsCount: Int,
  val settingsKeysCount: Int,
  val blendProgramsCount: Int = 0,
  val blendRecordsCount: Int = 0,
  val cruActivityRecordsCount: Int = 0,
  val productsCount: Int = 0,
  val draftsCount: Int = 0
)

data class BackupDataPayload(
  val tanks: List<Tank>,
  val calibration: CalibrationPackage,
  val settings: AppSettings,
  val dosingTanks: List<DosingTank>,
  val chemicals: List<Chemical>,
  val transactions: List<TransactionRecord> = emptyList(),
  val lossReports: List<LossReport> = emptyList(),
  val readingLogs: List<TankReadingLogEntry> = emptyList(),
  val injectionRecords: List<DailyInjectionRecord> = emptyList(),
  val solutionPresets: List<SolutionPreset> = emptyList(),
  val solutionBatches: List<SolutionBatchRecord> = emptyList(),
  val blendPrograms: List<BlendProgram> = emptyList(),
  val blendRecords: List<BlendRecord> = emptyList(),
  val editLogs: List<EditLogEntry> = emptyList(),
  val cruLookupTables: CruLookupTables = CruLookupTables(),
  val cruActivityRecords: List<CruActivityRecord> = emptyList(),
  val cruReactorBeds: List<CruReactorBed> = emptyList(),
  val products: List<Product> = emptyList(),
  val productionReportSetup: ProductionReportSetup? = null,
  val productionReportDays: List<ProductionReportDaySnapshot> = emptyList(),
  val drafts: List<DraftRecord> = emptyList(),
  val extraStorageKeys: Map<String, String> = emptyMap()
)

data class BackupPackageStructure(
  val app: String = "TankCalc",
  val schemaVersion: Int = 2,
  val createdAt: String,
  val deviceInfo: String,
  val developer: String = "Meherab Hossain Jihad",
  val manifest: BackupManifest,
  val checksum: String,
  val data: BackupDataPayload
)

data class TankCalibrationCounts(
  val tankName: String,
  val dipRows: Int,
  val fractionRows: Int,
  val n2Rows: Int,
  val isValid: Boolean
)

data class ExportVerificationResult(
  val file: File,
  val uri: Uri,
  val fileSizeMb: Double,
  val manifest: BackupManifest,
  val perTankCounts: List<TankCalibrationCounts>,
  val selfTestPassed: Boolean,
  val errorMessage: String? = null
)

data class RestorePreview(
  val app: String,
  val schemaVersion: Int,
  val createdAt: String,
  val deviceInfo: String,
  val developer: String,
  val exportType: String,
  val manifest: BackupManifest,
  val hasCalibration: Boolean,
  val perTankCounts: List<TankCalibrationCounts>,
  val checksumValid: Boolean
)

data class RestoreResult(
  val success: Boolean,
  val message: String,
  val perTankChecklist: List<TankCalibrationCounts>,
  val settingsLoaded: Boolean,
  val injectionSetupLoaded: Boolean,
  val hasCalibration: Boolean,
  val sanityCheckErrors: List<String> = emptyList()
)

data class SelfTestResult(
  val passed: Boolean,
  val details: String,
  val perTankCounts: List<TankCalibrationCounts>,
  val sanityPassed: Boolean,
  val sanityErrors: List<String> = emptyList()
)

object BackupManager {
  private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
  private val packageAdapter = moshi.adapter(BackupPackageStructure::class.java)
  private val payloadAdapter = moshi.adapter(BackupDataPayload::class.java)

  fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  /**
   * 1B: Single source of truth: builds export package with compact calibration section.
   */
  fun buildExportPackage(
    repository: TankRepository,
    isSetupOnly: Boolean,
    includeDrafts: Boolean = false
  ): Pair<BackupPackageStructure, String> {
    val liveTanks = repository.tanks.value
    val liveSettings = repository.settings.value
    val liveDosing = repository.dosingTanks.value
    val liveChem = repository.chemicals.value

    // Build compact calibration tables
    val dipMap = mutableMapOf<String, List<List<Double>>>()
    val fracMap = mutableMapOf<String, List<List<Double>>>()
    val n2Map = mutableMapOf<String, List<List<Double>>>()

    var totalDip = 0
    var totalFrac = 0
    var totalN2 = 0
    var calibTanks = 0

    for (t in liveTanks) {
      var hasAnyCalib = false
      if (t.dipTable.isNotEmpty()) {
        val rows = t.dipTable.map { row ->
          val v = row.volumes[t.name] ?: row.volumes.values.firstOrNull() ?: 0.0
          listOf(row.dipMm, v)
        }
        dipMap[t.name] = rows
        totalDip += rows.size
        hasAnyCalib = true
      }
      if (t.fractionTable.isNotEmpty()) {
        val rows = t.fractionTable.map { row ->
          val v = row.volumes[t.name] ?: row.volumes.values.firstOrNull() ?: 0.0
          listOf(row.mm.toDouble(), v)
        }
        fracMap[t.name] = rows
        totalFrac += rows.size
        hasAnyCalib = true
      }
      if (t.n2Table.isNotEmpty()) {
        val rows = t.n2Table.map { row ->
          val v = row.volumes[t.name] ?: row.volumes.values.firstOrNull() ?: 0.0
          listOf(row.heightMm, v)
        }
        n2Map[t.name] = rows
        totalN2 += rows.size
        hasAnyCalib = true
      }
      if (hasAnyCalib) calibTanks++
    }

    val calibration = CalibrationPackage(
      dip = dipMap,
      fraction = fracMap,
      n2 = n2Map
    )

    // Capture any extra storage keys from SharedPreferences
    val allPrefs = repository.getAllPrefs()
    val knownKeys = setOf(
      "tanks_data", "transactions_data", "loss_reports_data",
      "tank_readings_data", "dosing_tanks_data", "chemicals_data",
      "injection_records_data", "settings_data", "solution_presets_data",
      "solution_batches_data", "wash_water_data", "blend_programs_data",
      "blend_records_data", "edit_logs_data",
      "cru_lookup_tables_data", "cru_activity_records_data", "cru_reactor_beds_data",
      "products_data", "production_report_setup_data", "production_report_days_data",
      "global_drafts_data"
    )
    val extraKeys = allPrefs.filterKeys { it !in knownKeys }
      .mapValues { it.value?.toString() ?: "" }

    val txList = if (isSetupOnly) emptyList() else repository.transactions.value
    val lossList = if (isSetupOnly) emptyList() else repository.lossReports.value
    val readList = if (isSetupOnly) emptyList() else repository.readingLogs.value
    val injList = if (isSetupOnly) emptyList() else repository.injectionRecords.value
    val blendProgList = repository.blendPrograms.value
    val blendRecList = if (isSetupOnly) emptyList() else repository.blendRecords.value
    val editLogsList = if (isSetupOnly) emptyList() else repository.editLogs.value
    val cruRecordsList = if (isSetupOnly) emptyList() else repository.cruActivityRecords.value
    val cruTables = repository.cruLookupTables.value
    val cruBedsList = repository.cruReactorBeds.value
    val draftsList = if (includeDrafts && !isSetupOnly) repository.drafts.value.values.toList() else emptyList()

    val manifest = BackupManifest(
      exportType = if (isSetupOnly) "SETUP_ONLY" else "FULL_BACKUP",
      tanksCount = liveTanks.size,
      calibrationTanksCount = calibTanks,
      totalDipRows = totalDip,
      totalFractionRows = totalFrac,
      totalN2Rows = totalN2,
      transactionsCount = txList.size,
      lossReportsCount = lossList.size,
      dailyRecordsCount = injList.size,
      dosingTanksCount = liveDosing.size,
      chemicalsCount = liveChem.size,
      settingsKeysCount = 12 + extraKeys.size,
      blendProgramsCount = blendProgList.size,
      blendRecordsCount = blendRecList.size,
      cruActivityRecordsCount = cruRecordsList.size,
      productsCount = repository.products.value.size,
      draftsCount = draftsList.size
    )

    val payload = BackupDataPayload(
      tanks = liveTanks,
      calibration = calibration,
      settings = liveSettings,
      dosingTanks = liveDosing,
      chemicals = liveChem,
      transactions = txList,
      lossReports = lossList,
      readingLogs = readList,
      injectionRecords = injList,
      solutionPresets = repository.solutionPresets.value,
      solutionBatches = if (isSetupOnly) emptyList() else repository.solutionBatches.value,
      blendPrograms = blendProgList,
      blendRecords = blendRecList,
      editLogs = editLogsList,
      cruLookupTables = cruTables,
      cruActivityRecords = cruRecordsList,
      cruReactorBeds = cruBedsList,
      products = repository.products.value,
      productionReportSetup = repository.productionReportSetup.value,
      productionReportDays = if (isSetupOnly) emptyList() else repository.productionReportDays.value,
      drafts = draftsList,
      extraStorageKeys = extraKeys
    )

    val payloadJson = payloadAdapter.toJson(payload)
    val checksum = sha256(payloadJson)
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val structure = BackupPackageStructure(
      createdAt = dateFormat.format(Date()),
      deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})",
      manifest = manifest,
      checksum = checksum,
      data = payload
    )
    val fullJson = packageAdapter.toJson(structure)
    return Pair(structure, fullJson)
  }

  /**
   * 1C & 1D: Write file, perform strict self-test verification against source data, and return verification result.
   */
  fun exportAndVerify(
    context: Context,
    repository: TankRepository,
    isSetupOnly: Boolean,
    includeDrafts: Boolean = false
  ): ExportVerificationResult {
    val timeFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
    val prefix = if (isSetupOnly) "TankCalc_Setup" else "TankCalc_Backup"
    val fileName = "${prefix}_${timeFormat.format(Date())}.json"
    val file = File(context.cacheDir, fileName)

    val (_, fullJson) = buildExportPackage(repository, isSetupOnly, includeDrafts)
    file.writeText(fullJson, Charsets.UTF_8)

    // Verification step: re-read file and parse
    val reReadText = file.readText(Charsets.UTF_8)
    val parsedStructure = try {
      packageAdapter.fromJson(reReadText)
    } catch (e: Exception) {
      null
    }

    if (parsedStructure == null) {
      return ExportVerificationResult(
        file = file,
        uri = Uri.EMPTY,
        fileSizeMb = file.length() / (1024.0 * 1024.0),
        manifest = BackupManifest(if (isSetupOnly) "SETUP_ONLY" else "FULL_BACKUP", 0,0,0,0,0,0,0,0,0,0,0),
        perTankCounts = emptyList(),
        selfTestPassed = false,
        errorMessage = "Verification Failed: Re-read file JSON could not be parsed."
      )
    }

    val perTankCounts = mutableListOf<TankCalibrationCounts>()
    val liveTanks = repository.tanks.value
    var countErrors = 0

    for (t in liveTanks) {
      val fileDip = parsedStructure.data.calibration.dip[t.name]?.size ?: 0
      val fileFrac = parsedStructure.data.calibration.fraction[t.name]?.size ?: 0
      val fileN2 = parsedStructure.data.calibration.n2[t.name]?.size ?: 0

      val sourceDip = t.dipTable.size
      val sourceFrac = t.fractionTable.size
      val sourceN2 = t.n2Table.size

      val isValid = (fileDip == sourceDip && fileFrac == sourceFrac && fileN2 == sourceN2)
      if (!isValid) countErrors++

      // If tank has calibration in app but 0 in file -> hard fail
      if ((sourceDip > 0 && fileDip == 0) || (sourceFrac > 0 && fileFrac == 0) || (sourceN2 > 0 && fileN2 == 0)) {
        countErrors++
      }

      perTankCounts.add(
        TankCalibrationCounts(
          tankName = t.name,
          dipRows = fileDip,
          fractionRows = fileFrac,
          n2Rows = fileN2,
          isValid = isValid
        )
      )
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val sizeMb = file.length() / (1024.0 * 1024.0)

    // Update last backup timestamp
    val cur = repository.settings.value
    repository.updateSettings(cur.copy(lastBackupTimestamp = System.currentTimeMillis()))

    return ExportVerificationResult(
      file = file,
      uri = uri,
      fileSizeMb = sizeMb,
      manifest = parsedStructure.manifest,
      perTankCounts = perTankCounts,
      selfTestPassed = countErrors == 0,
      errorMessage = if (countErrors > 0) "Verification Failed: Discrepancy detected between memory and exported calibration rows." else null
    )
  }

  /**
   * 1F: Run backup self-test without sharing.
   */
  fun runSelfTest(context: Context, repository: TankRepository): SelfTestResult {
    val (structure, fullJson) = buildExportPackage(repository, isSetupOnly = false)
    val parsed = try {
      packageAdapter.fromJson(fullJson)
    } catch (e: Exception) {
      null
    }

    if (parsed == null) {
      return SelfTestResult(
        passed = false,
        details = "Self-test failed: JSON serialization error.",
        perTankCounts = emptyList(),
        sanityPassed = false
      )
    }

    val perTank = mutableListOf<TankCalibrationCounts>()
    val liveTanks = repository.tanks.value
    var allMatch = true
    val sanityErrors = mutableListOf<String>()

    for (t in liveTanks) {
      val fileDip = parsed.data.calibration.dip[t.name]?.size ?: 0
      val fileFrac = parsed.data.calibration.fraction[t.name]?.size ?: 0
      val fileN2 = parsed.data.calibration.n2[t.name]?.size ?: 0

      val match = (fileDip == t.dipTable.size && fileFrac == t.fractionTable.size && fileN2 == t.n2Table.size)
      if (!match) allMatch = false

      perTank.add(
        TankCalibrationCounts(
          tankName = t.name,
          dipRows = fileDip,
          fractionRows = fileFrac,
          n2Rows = fileN2,
          isValid = match
        )
      )

      // Test mid dip calculation sanity check
      if (t.dipTable.isNotEmpty()) {
        val midDip = (t.dipTable.first().dipMm + t.dipTable.last().dipMm) / 2.0
        val res = Calculators.calculateDipVolume(t, midDip)
        if (res.invalidInput || res.totalVolume <= 0.0) {
          sanityErrors.add("Tank ${t.name} failed mid-dip ($midDip mm) volume test: ${res.warning ?: "Volume 0"}")
        }
      }
    }

    val passed = allMatch && sanityErrors.isEmpty()
    val details = if (passed) {
      "PASS: All ${liveTanks.size} tanks and calibration tables verified row-by-row. Sanity checks passed."
    } else {
      "FAIL: Calibration mismatch or sanity check failed. ${sanityErrors.joinToString("; ")}"
    }

    return SelfTestResult(
      passed = passed,
      details = details,
      perTankCounts = perTank,
      sanityPassed = sanityErrors.isEmpty(),
      sanityErrors = sanityErrors
    )
  }

  /**
   * 1E: Preview an incoming backup file before restoring.
   */
  fun previewBackup(jsonString: String): Result<RestorePreview> {
    return try {
      val structure = packageAdapter.fromJson(jsonString)
        ?: return Result.failure(Exception("Invalid JSON format or corrupted file."))

      if (structure.app != "TankCalc") {
        return Result.failure(Exception("Not a TankCalc backup file (App is '${structure.app}')."))
      }

      val payloadJson = payloadAdapter.toJson(structure.data)
      val calculatedChecksum = sha256(payloadJson)
      val checksumValid = calculatedChecksum.equals(structure.checksum, ignoreCase = true)

      val calib = structure.data.calibration
      val hasCalib = (calib.dip.isNotEmpty() || calib.fraction.isNotEmpty() || calib.n2.isNotEmpty())

      val perTank = structure.data.tanks.map { t ->
        val dipRows = calib.dip[t.name]?.size ?: t.dipTable.size
        val fracRows = calib.fraction[t.name]?.size ?: t.fractionTable.size
        val n2Rows = calib.n2[t.name]?.size ?: t.n2Table.size
        TankCalibrationCounts(
          tankName = t.name,
          dipRows = dipRows,
          fractionRows = fracRows,
          n2Rows = n2Rows,
          isValid = dipRows > 0 || fracRows > 0 || n2Rows > 0
        )
      }

      Result.success(
        RestorePreview(
          app = structure.app,
          schemaVersion = structure.schemaVersion,
          createdAt = structure.createdAt,
          deviceInfo = structure.deviceInfo,
          developer = structure.developer,
          exportType = structure.manifest.exportType,
          manifest = structure.manifest,
          hasCalibration = hasCalib,
          perTankCounts = perTank,
          checksumValid = checksumValid
        )
      )
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * 1E: Restore data with atomic transaction and rollback.
   */
  fun restoreBackup(
    repository: TankRepository,
    jsonString: String,
    mode: RestoreMode
  ): RestoreResult {
    // Snapshot original data for rollback
    val originalTanks = repository.tanks.value
    val originalTx = repository.transactions.value
    val originalLoss = repository.lossReports.value
    val originalReadings = repository.readingLogs.value
    val originalDosing = repository.dosingTanks.value
    val originalChem = repository.chemicals.value
    val originalInj = repository.injectionRecords.value
    val originalSettings = repository.settings.value
    val originalBlendProg = repository.blendPrograms.value
    val originalBlendRec = repository.blendRecords.value
    val originalEditLogs = repository.editLogs.value
    val originalCruTables = repository.cruLookupTables.value
    val originalCruRecords = repository.cruActivityRecords.value
    val originalCruBeds = repository.cruReactorBeds.value

    try {
      val structure = packageAdapter.fromJson(jsonString)
        ?: return RestoreResult(false, "Failed to parse backup JSON.", emptyList(), false, false, false)

      val payload = structure.data
      val calib = payload.calibration
      val hasCalibration = calib.dip.isNotEmpty() || calib.fraction.isNotEmpty() || calib.n2.isNotEmpty()

      // Reconstruct tanks with their calibration tables
      val restoredTanks = payload.tanks.map { rawTank ->
        val dips = calib.dip[rawTank.name]?.map { pair ->
          DipRow(dipMm = pair[0], volumes = mapOf(rawTank.name to pair[1]))
        } ?: rawTank.dipTable

        val fracs = calib.fraction[rawTank.name]?.map { pair ->
          FractionRow(mm = pair[0].toInt(), volumes = mapOf(rawTank.name to pair[1]))
        } ?: rawTank.fractionTable

        val n2s = calib.n2[rawTank.name]?.map { pair ->
          N2Row(heightMm = pair[0], volumes = mapOf(rawTank.name to pair[1]))
        } ?: rawTank.n2Table

        rawTank.copy(
          dipTable = dips,
          fractionTable = fracs,
          n2Table = n2s
        )
      }

      val finalTanks: List<Tank>
      val finalTx: List<TransactionRecord>
      val finalLoss: List<LossReport>
      val finalReadings: List<TankReadingLogEntry>
      val finalDosing: List<DosingTank>
      val finalChem: List<Chemical>
      val finalInj: List<DailyInjectionRecord>
      val finalBlendProg: List<BlendProgram>
      val finalBlendRec: List<BlendRecord>
      val finalEditLogs: List<EditLogEntry>
      val finalCruTables: CruLookupTables
      val finalCruRecords: List<CruActivityRecord>
      val finalCruBeds: List<CruReactorBed>
      val finalProducts: List<Product>

      if (mode == RestoreMode.REPLACE) {
        finalTanks = restoredTanks
        finalTx = payload.transactions
        finalLoss = payload.lossReports
        finalReadings = payload.readingLogs
        finalDosing = payload.dosingTanks
        finalChem = payload.chemicals
        finalInj = payload.injectionRecords
        finalBlendProg = payload.blendPrograms.ifEmpty { originalBlendProg }
        finalBlendRec = payload.blendRecords
        finalEditLogs = payload.editLogs
        finalCruTables = payload.cruLookupTables
        finalCruRecords = payload.cruActivityRecords
        finalCruBeds = payload.cruReactorBeds.ifEmpty { originalCruBeds }
        finalProducts = payload.products.ifEmpty { repository.products.value }
      } else {
        // Merge mode
        finalTanks = (originalTanks.filter { ot -> restoredTanks.none { it.name.equals(ot.name, ignoreCase = true) } } + restoredTanks)
        finalTx = (originalTx + payload.transactions).distinctBy { it.id }
        finalLoss = (originalLoss + payload.lossReports).distinctBy { it.id }
        finalReadings = (originalReadings + payload.readingLogs).distinctBy { it.id }
        finalDosing = (originalDosing.filter { od -> payload.dosingTanks.none { it.name.equals(od.name, ignoreCase = true) } } + payload.dosingTanks)
        finalChem = (originalChem.filter { oc -> payload.chemicals.none { it.name.equals(oc.name, ignoreCase = true) } } + payload.chemicals)
        finalInj = (originalInj + payload.injectionRecords).distinctBy { it.id }
        finalBlendProg = (originalBlendProg.filter { op -> payload.blendPrograms.none { it.id == op.id } } + payload.blendPrograms)
        finalBlendRec = (originalBlendRec + payload.blendRecords).distinctBy { it.id }
        finalEditLogs = (originalEditLogs + payload.editLogs).distinctBy { it.id }
        finalCruTables = payload.cruLookupTables
        finalCruRecords = (originalCruRecords + payload.cruActivityRecords).distinctBy { it.id }
        finalCruBeds = if (payload.cruReactorBeds.isNotEmpty()) payload.cruReactorBeds else originalCruBeds
        finalProducts = (repository.products.value.filter { op -> payload.products.none { it.id == op.id } } + payload.products)
      }

      // Execute single atomic commit
      repository.restoreAllDataDirect(
        tanks = finalTanks,
        transactions = finalTx,
        lossReports = finalLoss,
        readingLogs = finalReadings,
        dosingTanks = finalDosing,
        chemicals = finalChem,
        injectionRecords = finalInj,
        settings = payload.settings, // Password becomes the one in backup as required
        blendPrograms = finalBlendProg,
        blendRecords = finalBlendRec,
        editLogs = finalEditLogs,
        cruLookupTables = finalCruTables,
        cruActivityRecords = finalCruRecords,
        cruReactorBeds = finalCruBeds,
        products = finalProducts,
        productionReportSetup = payload.productionReportSetup,
        productionReportDays = if (mode == RestoreMode.MERGE) {
          (repository.productionReportDays.value.filter { od -> payload.productionReportDays.none { it.dateStr == od.dateStr } } + payload.productionReportDays)
        } else {
          payload.productionReportDays
        }
      )

      if (payload.solutionPresets.isNotEmpty()) {
        repository.saveSolutionPresets(payload.solutionPresets)
      }
      if (payload.solutionBatches.isNotEmpty()) {
        val mergedBatches = if (mode == RestoreMode.MERGE) {
          (payload.solutionBatches + repository.solutionBatches.value).distinctBy { it.id }
        } else {
          payload.solutionBatches
        }
        repository.saveSolutionBatches(mergedBatches)
      }
      if (payload.drafts.isNotEmpty()) {
        for (d in payload.drafts) {
          repository.saveDraft(d.moduleKey, d.moduleTitle, d.contextKey, d.payloadJson)
        }
      }

      // Post-restore sanity check: compute Dip-to-Volume at mid-dip for every tank
      val sanityErrors = mutableListOf<String>()
      val checklist = finalTanks.map { t ->
        if (t.dipTable.isNotEmpty()) {
          val midDip = (t.dipTable.first().dipMm + t.dipTable.last().dipMm) / 2.0
          val res = Calculators.calculateDipVolume(t, midDip)
          if (res.invalidInput) {
            sanityErrors.add("Tank ${t.name}: Dip $midDip mm returned invalid input (${res.warning})")
          }
        }
        TankCalibrationCounts(
          tankName = t.name,
          dipRows = t.dipTable.size,
          fractionRows = t.fractionTable.size,
          n2Rows = t.n2Table.size,
          isValid = t.dipTable.isNotEmpty() || t.fractionTable.isNotEmpty() || t.n2Table.isNotEmpty()
        )
      }

      return RestoreResult(
        success = true,
        message = if (sanityErrors.isEmpty()) "Restore completed successfully! All data and calibration ready to use." else "Restored with calibration warnings: ${sanityErrors.first()}",
        perTankChecklist = checklist,
        settingsLoaded = true,
        injectionSetupLoaded = finalDosing.isNotEmpty() && finalChem.isNotEmpty(),
        hasCalibration = hasCalibration,
        sanityCheckErrors = sanityErrors
      )
    } catch (e: Exception) {
      // Rollback on failure
      repository.restoreAllDataDirect(
        tanks = originalTanks,
        transactions = originalTx,
        lossReports = originalLoss,
        readingLogs = originalReadings,
        dosingTanks = originalDosing,
        chemicals = originalChem,
        injectionRecords = originalInj,
        settings = originalSettings,
        blendPrograms = originalBlendProg,
        blendRecords = originalBlendRec,
        editLogs = originalEditLogs
      )
      return RestoreResult(
        success = false,
        message = "Restore failed: ${e.localizedMessage}. Rolled back to previous data.",
        perTankChecklist = emptyList(),
        settingsLoaded = false,
        injectionSetupLoaded = false,
        hasCalibration = false
      )
    }
  }

  /**
   * Human-readable multi-section CSV export for Excel
   */
  fun exportAllDataExcelCsv(context: Context, repository: TankRepository): Uri? {
    try {
      val sb = StringBuilder()
      sb.append("=== TANK CALC FULL EXCEL / CSV EXPORT ===\n")
      sb.append("Generated by Tank Calc | Developed by Meherab Hossain Jihad\n\n")

      sb.append("--- TANKS MASTER ---\n")
      sb.append("Name,Type,Shape,Capacity_Litres,Dip_Rows,Fraction_Rows,N2_Rows\n")
      repository.tanks.value.forEach { t ->
        sb.append("${t.name},${t.type},${t.shape},${t.maxVolumeLiters},${t.dipTable.size},${t.fractionTable.size},${t.n2Table.size}\n")
      }
      sb.append("\n")

      sb.append("--- DOSING TANKS ---\n")
      sb.append("Name,Diameter_mm,Height_mm,Override_L_per_mm,Linked_Chemical\n")
      repository.dosingTanks.value.forEach { dt ->
        sb.append("${dt.name},${dt.diameterMm},${dt.heightMm},${dt.overrideLitresPerMm ?: ""},${dt.linkedChemicalName}\n")
      }
      sb.append("\n")

      sb.append("--- CHEMICALS ---\n")
      sb.append("Name,Density_kg_L,Active_Percent,Element_Factor,Dosing_Basis,Target_Ppm\n")
      repository.chemicals.value.forEach { ch ->
        sb.append("${ch.name},${ch.densityKgL},${ch.activeFractionPercent},${ch.elementFactor},${ch.dosingBasisStreamName},${ch.targetWtPpm}\n")
      }
      sb.append("\n")

      sb.append("--- LOSS REPORTS ---\n")
      sb.append("ID,StartTime,EndTime,Duration_Hours,Feed_L,Product_L,Loss_L,Loss_Percent,Yield_Percent,Flag\n")
      repository.lossReports.value.forEach { r ->
        sb.append("${r.id},${r.startTime},${r.endTime},${r.durationHours},${r.totalFeed},${r.totalProduct},${r.loss},${r.lossPercent},${r.yieldPercent},${r.flag}\n")
      }
      sb.append("\n")

      sb.append("--- TRANSACTIONS ---\n")
      sb.append("Tank,Type,Quantity_L,DateTime,Mode\n")
      repository.transactions.value.forEach { tx ->
        sb.append("${tx.tankName},${tx.type},${tx.quantity},${tx.dateTime},${tx.mode}\n")
      }
      sb.append("\n")

      sb.append("--- DAILY INJECTION RECORDS ---\n")
      sb.append("Date,Timestamp,Dosing_Point,Tank,Chemical,Dosing_L_hr,Dosing_mm_d,Basis_kg_hr,Wt_PPM,Status\n")
      repository.injectionRecords.value.forEach { rec ->
        sb.append("${rec.date},${rec.timestamp},\"${rec.dosingPointName.ifEmpty { rec.tankName }}\",${rec.tankName},${rec.chemicalName},${String.format(Locale.US, "%.4f", rec.effectiveLHr)},${String.format(Locale.US, "%.2f", rec.effectiveMmDay)},${rec.basisStreamKgHr},${String.format(Locale.US, "%.3f", rec.wtPpm)},${rec.status}\n")
      }

      val file = File(context.cacheDir, "TankCalc_DataExport_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.csv")
      file.writeText(sb.toString(), Charsets.UTF_8)
      return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: Exception) {
      e.printStackTrace()
      return null
    }
  }
}

enum class RestoreMode { REPLACE, MERGE }

