package com.example.data

import android.content.Context
import com.example.data.db.*
import com.example.engine.Calculators
import com.example.engine.TankNameMatcher
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TankRepository(context: Context) {
  companion object {
    const val PROD_REF_ID = "prod_ref_seed"
    const val PROD_LN_ID = "prod_ln_seed"
    const val PROD_HOBC_ID = "prod_hobc_seed"
    const val PROD_MS_ID = "prod_ms_seed"
  }

  private val prefs = context.getSharedPreferences("tank_calc_prefs", Context.MODE_PRIVATE)
  private val db = TankCalcDatabase.getDatabase(context)
  private val dao = db.tankCalcDao()
  private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

  private val singleTankAdapter = moshi.adapter(Tank::class.java)
  private val singleBulkDipSheetAdapter = moshi.adapter(BulkDipSheet::class.java)
  private val singleProdReportDayAdapter = moshi.adapter(ProductionReportDaySnapshot::class.java)
  private val singleLossReportAdapter = moshi.adapter(LossReport::class.java)
  private val singleInjectionRecordAdapter = moshi.adapter(DailyInjectionRecord::class.java)
  private val singleTransactionAdapter = moshi.adapter(TransactionRecord::class.java)
  private val singleBlendRecordAdapter = moshi.adapter(BlendRecord::class.java)

  private val tanksAdapter = moshi.adapter<List<Tank>>(
    Types.newParameterizedType(List::class.java, Tank::class.java)
  )
  private val transactionsAdapter = moshi.adapter<List<TransactionRecord>>(
    Types.newParameterizedType(List::class.java, TransactionRecord::class.java)
  )
  private val lossReportsAdapter = moshi.adapter<List<LossReport>>(
    Types.newParameterizedType(List::class.java, LossReport::class.java)
  )
  private val readingsAdapter = moshi.adapter<List<TankReadingLogEntry>>(
    Types.newParameterizedType(List::class.java, TankReadingLogEntry::class.java)
  )
  private val dosingTanksAdapter = moshi.adapter<List<DosingTank>>(
    Types.newParameterizedType(List::class.java, DosingTank::class.java)
  )
  private val chemicalsAdapter = moshi.adapter<List<Chemical>>(
    Types.newParameterizedType(List::class.java, Chemical::class.java)
  )
  private val injectionRecordsAdapter = moshi.adapter<List<DailyInjectionRecord>>(
    Types.newParameterizedType(List::class.java, DailyInjectionRecord::class.java)
  )
  private val solutionPresetsAdapter = moshi.adapter<List<SolutionPreset>>(
    Types.newParameterizedType(List::class.java, SolutionPreset::class.java)
  )
  private val solutionBatchesAdapter = moshi.adapter<List<SolutionBatchRecord>>(
    Types.newParameterizedType(List::class.java, SolutionBatchRecord::class.java)
  )
  private val washWaterAdapter = moshi.adapter<List<WashWaterRecord>>(
    Types.newParameterizedType(List::class.java, WashWaterRecord::class.java)
  )
  private val blendProgramsAdapter = moshi.adapter<List<BlendProgram>>(
    Types.newParameterizedType(List::class.java, BlendProgram::class.java)
  )
  private val blendRecordsAdapter = moshi.adapter<List<BlendRecord>>(
    Types.newParameterizedType(List::class.java, BlendRecord::class.java)
  )
  private val editLogsAdapter = moshi.adapter<List<EditLogEntry>>(
    Types.newParameterizedType(List::class.java, EditLogEntry::class.java)
  )
  private val cruLookupTablesAdapter = moshi.adapter(CruLookupTables::class.java)
  private val cruActivityRecordsAdapter = moshi.adapter<List<CruActivityRecord>>(
    Types.newParameterizedType(List::class.java, CruActivityRecord::class.java)
  )
  private val cruReactorBedsAdapter = moshi.adapter<List<CruReactorBed>>(
    Types.newParameterizedType(List::class.java, CruReactorBed::class.java)
  )
  private val productsAdapter = moshi.adapter<List<Product>>(
    Types.newParameterizedType(List::class.java, Product::class.java)
  )
  private val prodReportSetupAdapter = moshi.adapter(ProductionReportSetup::class.java)
  private val prodReportDaysAdapter = moshi.adapter<List<ProductionReportDaySnapshot>>(
    Types.newParameterizedType(List::class.java, ProductionReportDaySnapshot::class.java)
  )
  private val settingsAdapter = moshi.adapter(AppSettings::class.java)
  private val draftsAdapter = moshi.adapter<List<DraftRecord>>(
    Types.newParameterizedType(List::class.java, DraftRecord::class.java)
  )
  private val bulkDipSheetsAdapter = moshi.adapter<List<BulkDipSheet>>(
    Types.newParameterizedType(List::class.java, BulkDipSheet::class.java)
  )

  private val _tanks = MutableStateFlow<List<Tank>>(emptyList())
  val tanks: StateFlow<List<Tank>> = _tanks.asStateFlow()

  private val _drafts = MutableStateFlow<Map<String, DraftRecord>>(emptyMap())
  val drafts: StateFlow<Map<String, DraftRecord>> = _drafts.asStateFlow()

  private val _bulkDipSheets = MutableStateFlow<List<BulkDipSheet>>(emptyList())
  val bulkDipSheets: StateFlow<List<BulkDipSheet>> = _bulkDipSheets.asStateFlow()

  private val _productionReportSetup = MutableStateFlow(ProductionReportSetup())
  val productionReportSetup: StateFlow<ProductionReportSetup> = _productionReportSetup.asStateFlow()

  private val _productionReportDays = MutableStateFlow<List<ProductionReportDaySnapshot>>(emptyList())
  val productionReportDays: StateFlow<List<ProductionReportDaySnapshot>> = _productionReportDays.asStateFlow()

  private val _products = MutableStateFlow<List<Product>>(emptyList())
  val products: StateFlow<List<Product>> = _products.asStateFlow()

  private val _transactions = MutableStateFlow<List<TransactionRecord>>(emptyList())
  val transactions: StateFlow<List<TransactionRecord>> = _transactions.asStateFlow()

  private val _lossReports = MutableStateFlow<List<LossReport>>(emptyList())
  val lossReports: StateFlow<List<LossReport>> = _lossReports.asStateFlow()

  private val _readingLogs = MutableStateFlow<List<TankReadingLogEntry>>(emptyList())
  val readingLogs: StateFlow<List<TankReadingLogEntry>> = _readingLogs.asStateFlow()

  private val _dosingTanks = MutableStateFlow<List<DosingTank>>(emptyList())
  val dosingTanks: StateFlow<List<DosingTank>> = _dosingTanks.asStateFlow()

  private val _chemicals = MutableStateFlow<List<Chemical>>(emptyList())
  val chemicals: StateFlow<List<Chemical>> = _chemicals.asStateFlow()

  private val _injectionRecords = MutableStateFlow<List<DailyInjectionRecord>>(emptyList())
  val injectionRecords: StateFlow<List<DailyInjectionRecord>> = _injectionRecords.asStateFlow()

  private val _solutionPresets = MutableStateFlow<List<SolutionPreset>>(emptyList())
  val solutionPresets: StateFlow<List<SolutionPreset>> = _solutionPresets.asStateFlow()

  private val _solutionBatches = MutableStateFlow<List<SolutionBatchRecord>>(emptyList())
  val solutionBatches: StateFlow<List<SolutionBatchRecord>> = _solutionBatches.asStateFlow()

  private val _washWaterRecords = MutableStateFlow<List<WashWaterRecord>>(emptyList())
  val washWaterRecords: StateFlow<List<WashWaterRecord>> = _washWaterRecords.asStateFlow()

  private val _blendPrograms = MutableStateFlow<List<BlendProgram>>(emptyList())
  val blendPrograms: StateFlow<List<BlendProgram>> = _blendPrograms.asStateFlow()

  private val _blendRecords = MutableStateFlow<List<BlendRecord>>(emptyList())
  val blendRecords: StateFlow<List<BlendRecord>> = _blendRecords.asStateFlow()

  private val _editLogs = MutableStateFlow<List<EditLogEntry>>(emptyList())
  val editLogs: StateFlow<List<EditLogEntry>> = _editLogs.asStateFlow()

  private val _cruLookupTables = MutableStateFlow(CruLookupTables())
  val cruLookupTables: StateFlow<CruLookupTables> = _cruLookupTables.asStateFlow()

  private val _cruActivityRecords = MutableStateFlow<List<CruActivityRecord>>(emptyList())
  val cruActivityRecords: StateFlow<List<CruActivityRecord>> = _cruActivityRecords.asStateFlow()

  private val _cruReactorBeds = MutableStateFlow<List<CruReactorBed>>(emptyList())
  val cruReactorBeds: StateFlow<List<CruReactorBed>> = _cruReactorBeds.asStateFlow()

  private val _settings = MutableStateFlow(AppSettings())
  val settings: StateFlow<AppSettings> = _settings.asStateFlow()

  init {
    loadData()
  }

  private fun loadTanksFromRoomOrPrefs(): List<Tank> {
    return try {
      val roomMetas = dao.getAllTankMetasSync()
      if (roomMetas.isNotEmpty()) {
        val dipByTank = dao.getAllDipRowsSync().groupBy { it.tankId }
        val fracByTank = dao.getAllFractionRowsSync().groupBy { it.tankId }
        val n2ByTank = dao.getAllN2RowsSync().groupBy { it.tankId }
        roomMetas.mapNotNull { meta ->
          val baseTank = try {
            singleTankAdapter.fromJson(meta.customPropsJson)
          } catch (_: Exception) {
            null
          } ?: Tank(
            id = meta.id,
            name = meta.name,
            type = try { TankType.valueOf(meta.type) } catch (_: Exception) { TankType.PRODUCT },
            shape = try { TankShape.valueOf(meta.shape) } catch (_: Exception) { TankShape.VERTICAL },
            maxVolumeLiters = meta.maxVolumeLiters,
            productId = meta.productId
          )
          val dips = dipByTank[meta.id]?.map { r ->
            DipRow(dipMm = r.dipMm, volumes = mapOf(meta.name to r.volumeL))
          } ?: emptyList()
          val fracs = fracByTank[meta.id]?.map { r ->
            FractionRow(mm = r.mm, volumes = mapOf(meta.name to r.volumeL))
          } ?: emptyList()
          val n2s = n2ByTank[meta.id]?.map { r ->
            N2Row(heightMm = r.heightMm, volumes = mapOf(meta.name to r.volumeL))
          } ?: emptyList()
          baseTank.copy(
            dipTable = if (dips.isNotEmpty()) dips else baseTank.dipTable,
            fractionTable = if (fracs.isNotEmpty()) fracs else baseTank.fractionTable,
            n2Table = if (n2s.isNotEmpty()) n2s else baseTank.n2Table
          )
        }
      } else {
        val tanksJson = prefs.getString("tanks_data", null)
        val loaded = if (tanksJson != null) {
          try {
            tanksAdapter.fromJson(tanksJson) ?: getSeededTanks()
          } catch (_: Exception) {
            getSeededTanks()
          }
        } else {
          getSeededTanks()
        }
        persistTanksToRoomAsync(loaded)
        loaded
      }
    } catch (_: Exception) {
      val tanksJson = prefs.getString("tanks_data", null)
      if (tanksJson != null) {
        try { tanksAdapter.fromJson(tanksJson) ?: getSeededTanks() } catch (_: Exception) { getSeededTanks() }
      } else {
        getSeededTanks()
      }
    }
  }

  private fun persistTanksToRoomAsync(list: List<Tank>) {
    ioScope.launch {
      try {
        val metas = ArrayList<TankMetaEntity>(list.size)
        val dips = ArrayList<CalibrationDipEntity>()
        val fracs = ArrayList<CalibrationFractionEntity>()
        val n2s = ArrayList<CalibrationN2Entity>()

        for (t in list) {
          val stripped = t.copy(dipTable = emptyList(), fractionTable = emptyList(), n2Table = emptyList())
          metas.add(
            TankMetaEntity(
              id = t.id,
              name = t.name,
              type = t.type.name,
              shape = t.shape.name,
              maxVolumeLiters = t.maxVolumeLiters,
              hasN2Factor = t.n2Table.isNotEmpty(),
              productId = t.productId,
              customPropsJson = singleTankAdapter.toJson(stripped)
            )
          )
          for (r in t.dipTable) {
            val v = TankNameMatcher.findVolume(r.volumes, t.name) ?: r.volumes.values.firstOrNull() ?: 0.0
            dips.add(CalibrationDipEntity(tankId = t.id, tankName = t.name, dipMm = r.dipMm, volumeL = v))
          }
          for (r in t.fractionTable) {
            val v = TankNameMatcher.findVolume(r.volumes, t.name) ?: r.volumes.values.firstOrNull() ?: 0.0
            fracs.add(CalibrationFractionEntity(tankId = t.id, tankName = t.name, mm = r.mm, volumeL = v))
          }
          for (r in t.n2Table) {
            val v = TankNameMatcher.findVolume(r.volumes, t.name) ?: r.volumes.values.firstOrNull() ?: 0.0
            n2s.add(CalibrationN2Entity(tankId = t.id, tankName = t.name, heightMm = r.heightMm, volumeL = v))
          }
        }
        dao.replaceAllTanksAndCalibrationsSync(metas, dips, fracs, n2s)
      } catch (_: Exception) {}
    }
  }

  private fun loadData() {
    _tanks.value = loadTanksFromRoomOrPrefs()
    if (prefs.getString("tanks_data", null) == null) {
      prefs.edit().putString("tanks_data", tanksAdapter.toJson(_tanks.value)).apply()
    }
    Calculators.warmUpCache(_tanks.value)

    // Transactions (Room + SharedPreferences fallback/migration)
    val roomTx = try { dao.getAllTransactionsSync() } catch (_: Exception) { emptyList() }
    if (roomTx.isNotEmpty()) {
      _transactions.value = roomTx.mapNotNull {
        try { singleTransactionAdapter.fromJson(it.payloadJson) } catch (_: Exception) { null }
      }
    } else {
      val txJson = prefs.getString("transactions_data", null)
      if (txJson != null) {
        try {
          _transactions.value = transactionsAdapter.fromJson(txJson) ?: emptyList()
          if (_transactions.value.isNotEmpty()) {
            val toMigrate = _transactions.value
            ioScope.launch {
              try {
                dao.replaceAllTransactionsSync(
                  toMigrate.map { TransactionEntity(it.id, it.dateTime, it.tankName, singleTransactionAdapter.toJson(it)) }
                )
              } catch (_: Exception) {}
            }
          }
        } catch (e: Exception) {
          _transactions.value = emptyList()
        }
      }
    }

    // Loss Reports (Room + SharedPreferences fallback/migration)
    val roomLoss = try { dao.getAllLossReportsSync() } catch (_: Exception) { emptyList() }
    if (roomLoss.isNotEmpty()) {
      _lossReports.value = roomLoss.mapNotNull {
        try { singleLossReportAdapter.fromJson(it.payloadJson) } catch (_: Exception) { null }
      }
    } else {
      val lossJson = prefs.getString("loss_reports_data", null)
      if (lossJson != null) {
        try {
          _lossReports.value = lossReportsAdapter.fromJson(lossJson) ?: emptyList()
          if (_lossReports.value.isNotEmpty()) {
            val toMigrate = _lossReports.value
            ioScope.launch {
              try {
                dao.replaceAllLossReportsSync(
                  toMigrate.map { LossReportEntity(it.id, it.startTime, it.endTime, it.timestamp, singleLossReportAdapter.toJson(it)) }
                )
              } catch (_: Exception) {}
            }
          }
        } catch (e: Exception) {
          _lossReports.value = emptyList()
        }
      }
    }

    val readingsJson = prefs.getString("tank_readings_data", null)
    if (readingsJson != null) {
      try {
        _readingLogs.value = readingsAdapter.fromJson(readingsJson) ?: emptyList()
      } catch (e: Exception) {
        _readingLogs.value = emptyList()
      }
    }

    // Dosing Tanks
    val dtJson = prefs.getString("dosing_tanks_data", null)
    if (dtJson != null) {
      try {
        _dosingTanks.value = dosingTanksAdapter.fromJson(dtJson) ?: getSeededDosingTanks()
      } catch (e: Exception) {
        _dosingTanks.value = getSeededDosingTanks()
      }
    } else {
      _dosingTanks.value = getSeededDosingTanks()
      saveDosingTanks(_dosingTanks.value)
    }

    // Chemicals
    val chemJson = prefs.getString("chemicals_data", null)
    if (chemJson != null) {
      try {
        _chemicals.value = chemicalsAdapter.fromJson(chemJson) ?: getSeededChemicals()
      } catch (e: Exception) {
        _chemicals.value = getSeededChemicals()
      }
    } else {
      _chemicals.value = getSeededChemicals()
      saveChemicals(_chemicals.value)
    }

    // Injection records (Room + SharedPreferences fallback/migration)
    val roomInj = try { dao.getAllDailyInjectionRecordsSync() } catch (_: Exception) { emptyList() }
    if (roomInj.isNotEmpty()) {
      _injectionRecords.value = roomInj.mapNotNull {
        try { singleInjectionRecordAdapter.fromJson(it.payloadJson) } catch (_: Exception) { null }
      }
    } else {
      val injJson = prefs.getString("injection_records_data", null)
      if (injJson != null) {
        try {
          _injectionRecords.value = injectionRecordsAdapter.fromJson(injJson) ?: emptyList()
          if (_injectionRecords.value.isNotEmpty()) {
            val toMigrate = _injectionRecords.value
            ioScope.launch {
              try {
                dao.replaceAllDailyInjectionRecordsSync(
                  toMigrate.map { DailyInjectionRecordEntity(it.id, it.date, it.timestamp, it.tankName, singleInjectionRecordAdapter.toJson(it)) }
                )
              } catch (_: Exception) {}
            }
          }
        } catch (e: Exception) {
          _injectionRecords.value = emptyList()
        }
      }
    }

    // Solution Presets
    val presetsJson = prefs.getString("solution_presets_data", null)
    if (presetsJson != null) {
      try {
        _solutionPresets.value = solutionPresetsAdapter.fromJson(presetsJson) ?: getSeededSolutionPresets()
      } catch (e: Exception) {
        _solutionPresets.value = getSeededSolutionPresets()
      }
    } else {
      _solutionPresets.value = getSeededSolutionPresets()
      saveSolutionPresets(_solutionPresets.value)
    }

    // Solution Batches
    val batchesJson = prefs.getString("solution_batches_data", null)
    if (batchesJson != null) {
      try {
        _solutionBatches.value = solutionBatchesAdapter.fromJson(batchesJson) ?: emptyList()
      } catch (e: Exception) {
        _solutionBatches.value = emptyList()
      }
    }

    // Wash Water Records
    val washJson = prefs.getString("wash_water_data", null)
    if (washJson != null) {
      try {
        _washWaterRecords.value = washWaterAdapter.fromJson(washJson) ?: emptyList()
      } catch (e: Exception) {
        _washWaterRecords.value = emptyList()
      }
    }

    val settingsJson = prefs.getString("settings_data", null)
    if (settingsJson != null) {
      try {
        _settings.value = settingsAdapter.fromJson(settingsJson) ?: AppSettings()
      } catch (e: Exception) {
        _settings.value = AppSettings()
      }
    }

    val draftsJson = prefs.getString("global_drafts_data", null)
    if (draftsJson != null) {
      try {
        val list = draftsAdapter.fromJson(draftsJson) ?: emptyList()
        _drafts.value = list.associateBy { it.id }
      } catch (_: Exception) {
        _drafts.value = emptyMap()
      }
    }
    autoCleanupDrafts(30)

    // Ensure T-510 and T-509 are available in tanks and configured as Blending tanks
    val currentTanks = _tanks.value.toMutableList()
    var tanksUpdated = false
    val seededTanks = getSeededTanks()
    if (currentTanks.none { it.name.equals("T-510", ignoreCase = true) }) {
      seededTanks.find { it.name.equals("T-510", ignoreCase = true) }?.let {
        currentTanks.add(it)
        tanksUpdated = true
      }
    }
    if (currentTanks.none { it.name.equals("T-509", ignoreCase = true) }) {
      seededTanks.find { it.name.equals("T-509", ignoreCase = true) }?.let {
        currentTanks.add(it)
        tanksUpdated = true
      }
    }
    // Update existing T-510 and T-509 if they are not yet marked as Blending tanks
    for (i in currentTanks.indices) {
      val t = currentTanks[i]
      if (t.name.equals("T-510", ignoreCase = true) && t.type != TankType.BLENDING) {
        currentTanks[i] = t.copy(type = TankType.BLENDING, blendedProductId = PROD_HOBC_ID)
        tanksUpdated = true
      } else if (t.name.equals("T-509", ignoreCase = true) && t.type != TankType.BLENDING) {
        currentTanks[i] = t.copy(type = TankType.BLENDING, blendedProductId = PROD_MS_ID)
        tanksUpdated = true
      }
    }
    if (tanksUpdated) {
      saveTanks(currentTanks)
    }

    // Blend Programs
    val blendProgJson = prefs.getString("blend_programs_data", null)
    if (blendProgJson != null) {
      try {
        _blendPrograms.value = blendProgramsAdapter.fromJson(blendProgJson) ?: getSeededBlendPrograms()
      } catch (e: Exception) {
        _blendPrograms.value = getSeededBlendPrograms()
      }
    } else {
      _blendPrograms.value = getSeededBlendPrograms()
      saveBlendPrograms(_blendPrograms.value)
    }

    // Blend Records (Room + SharedPreferences fallback/migration)
    val roomBlendRecs = try { dao.getAllBlendRecordsSync() } catch (_: Exception) { emptyList() }
    if (roomBlendRecs.isNotEmpty()) {
      _blendRecords.value = roomBlendRecs.mapNotNull {
        try { singleBlendRecordAdapter.fromJson(it.payloadJson) } catch (_: Exception) { null }
      }
    } else {
      val blendRecJson = prefs.getString("blend_records_data", null)
      if (blendRecJson != null) {
        try {
          _blendRecords.value = blendRecordsAdapter.fromJson(blendRecJson) ?: emptyList()
          if (_blendRecords.value.isNotEmpty()) {
            val toMigrate = _blendRecords.value
            ioScope.launch {
              try {
                dao.replaceAllBlendRecordsSync(
                  toMigrate.map { BlendRecordEntity(it.id, it.dateTime, it.tankName, singleBlendRecordAdapter.toJson(it)) }
                )
              } catch (_: Exception) {}
            }
          }
        } catch (e: Exception) {
          _blendRecords.value = emptyList()
        }
      }
    }

    // Edit Logs
    val editLogsJson = prefs.getString("edit_logs_data", null)
    if (editLogsJson != null) {
      try {
        _editLogs.value = editLogsAdapter.fromJson(editLogsJson) ?: emptyList()
      } catch (e: Exception) {
        _editLogs.value = emptyList()
      }
    }

    // CRU Lookup Tables
    val cruTablesJson = prefs.getString("cru_lookup_tables_data", null)
    if (cruTablesJson != null) {
      try {
        _cruLookupTables.value = cruLookupTablesAdapter.fromJson(cruTablesJson) ?: CruLookupTables()
      } catch (e: Exception) {
        _cruLookupTables.value = CruLookupTables()
      }
    }

    // CRU Activity Records
    val cruRecordsJson = prefs.getString("cru_activity_records_data", null)
    if (cruRecordsJson != null) {
      try {
        _cruActivityRecords.value = cruActivityRecordsAdapter.fromJson(cruRecordsJson) ?: emptyList()
      } catch (e: Exception) {
        _cruActivityRecords.value = emptyList()
      }
    }

    // CRU Reactor Beds
    val cruBedsJson = prefs.getString("cru_reactor_beds_data", null)
    if (cruBedsJson != null) {
      try {
        _cruReactorBeds.value = cruReactorBedsAdapter.fromJson(cruBedsJson) ?: getSeededReactorBeds()
      } catch (e: Exception) {
        _cruReactorBeds.value = getSeededReactorBeds()
      }
    } else {
      _cruReactorBeds.value = getSeededReactorBeds()
      saveCruReactorBeds(_cruReactorBeds.value)
    }

    // Products Master List
    val productsJson = prefs.getString("products_data", null)
    if (productsJson != null) {
      try {
        _products.value = productsAdapter.fromJson(productsJson) ?: getSeededProducts()
      } catch (e: Exception) {
        _products.value = getSeededProducts()
      }
    } else {
      _products.value = getSeededProducts()
      saveProducts(_products.value)
    }

    // Production Report Setup & Migration
    val prodReportSetupJson = prefs.getString("production_report_setup_data", null)
    if (prodReportSetupJson != null) {
      try {
        val setup = prodReportSetupAdapter.fromJson(prodReportSetupJson)
        if (setup != null) {
          if (setup.tankMaps.isNotEmpty()) {
            val currentTanks = _tanks.value.toMutableList()
            var tanksChanged = false
            for (tm in setup.tankMaps) {
              val idx = currentTanks.indexOfFirst { it.name.equals(tm.tankName, ignoreCase = true) }
              if (idx >= 0) {
                val t = currentTanks[idx]
                val tType = when (tm.group) {
                  ProductionReportTankGroup.RAW_MATERIAL -> TankType.FEED
                  ProductionReportTankGroup.PRODUCT -> if (tm.isBlendingTank) TankType.BLENDING else TankType.PRODUCT
                }
                val prod = _products.value.find { it.shortCode.equals(tm.productCode, ignoreCase = true) }
                currentTanks[idx] = t.copy(
                  type = tType,
                  productId = prod?.id ?: t.productId,
                  coefficientSource = tm.coefficientSource
                )
                tanksChanged = true
              }
            }
            if (tanksChanged) {
              saveTanks(currentTanks)
            }
            val cleanedSetup = setup.copy(tankMaps = emptyList())
            saveProductionReportSetup(cleanedSetup, logAction = false)
          } else {
            _productionReportSetup.value = setup
          }
        }
      } catch (e: Exception) {
        _productionReportSetup.value = ProductionReportSetup()
      }
    } else {
      _productionReportSetup.value = ProductionReportSetup()
      saveProductionReportSetup(_productionReportSetup.value, logAction = false)
    }

    // Production Report Saved Days (Room + SharedPreferences fallback/migration)
    val roomProdDays = try { dao.getAllProductionReportDaysSync() } catch (_: Exception) { emptyList() }
    if (roomProdDays.isNotEmpty()) {
      _productionReportDays.value = roomProdDays.mapNotNull {
        try { singleProdReportDayAdapter.fromJson(it.payloadJson) } catch (_: Exception) { null }
      }
    } else {
      val prodReportDaysJson = prefs.getString("production_report_days_data", null)
      if (prodReportDaysJson != null) {
        try {
          _productionReportDays.value = prodReportDaysAdapter.fromJson(prodReportDaysJson) ?: emptyList()
          if (_productionReportDays.value.isNotEmpty()) {
            val toMigrate = _productionReportDays.value
            ioScope.launch {
              try {
                dao.upsertProductionReportDaysSync(
                  toMigrate.map { ProductionReportDayEntity(it.dateStr, singleProdReportDayAdapter.toJson(it), it.lastModified) }
                )
              } catch (_: Exception) {}
            }
          }
        } catch (e: Exception) {
          _productionReportDays.value = emptyList()
        }
      }
    }

    // Bulk Dip Sheets (Room + SharedPreferences fallback/migration)
    val roomBulkSheets = try { dao.getAllBulkDipSheetsSync() } catch (_: Exception) { emptyList() }
    if (roomBulkSheets.isNotEmpty()) {
      _bulkDipSheets.value = roomBulkSheets.mapNotNull {
        try { singleBulkDipSheetAdapter.fromJson(it.payloadJson) } catch (_: Exception) { null }
      }
    } else {
      val bulkDipJson = prefs.getString("bulk_dip_sheets_data", null)
      if (bulkDipJson != null) {
        try {
          _bulkDipSheets.value = bulkDipSheetsAdapter.fromJson(bulkDipJson) ?: emptyList()
          if (_bulkDipSheets.value.isNotEmpty()) {
            val toMigrate = _bulkDipSheets.value
            ioScope.launch {
              try {
                dao.upsertBulkDipSheetsSync(
                  toMigrate.map { BulkDipSheetEntity(it.dateStr, singleBulkDipSheetAdapter.toJson(it), it.lastModified) }
                )
              } catch (_: Exception) {}
            }
          }
        } catch (e: Exception) {
          _bulkDipSheets.value = emptyList()
        }
      }
    }
    prefs.edit().putBoolean("room_migration_v1_complete", true).apply()
  }

  fun saveBulkDipSheets(list: List<BulkDipSheet>) {
    _bulkDipSheets.value = list
    prefs.edit().putString("bulk_dip_sheets_data", bulkDipSheetsAdapter.toJson(list)).apply()
    ioScope.launch {
      try {
        dao.upsertBulkDipSheetsSync(
          list.map { BulkDipSheetEntity(it.dateStr, singleBulkDipSheetAdapter.toJson(it), it.lastModified) }
        )
      } catch (_: Exception) {}
    }
  }

  fun saveBulkDipSheet(sheet: BulkDipSheet) {
    val current = _bulkDipSheets.value.toMutableList()
    val idx = current.indexOfFirst { it.dateStr == sheet.dateStr }
    if (idx >= 0) {
      current[idx] = sheet
    } else {
      current.add(sheet)
    }
    _bulkDipSheets.value = current
    prefs.edit().putString("bulk_dip_sheets_data", bulkDipSheetsAdapter.toJson(current)).apply()
    ioScope.launch {
      try {
        dao.upsertBulkDipSheetSync(
          BulkDipSheetEntity(sheet.dateStr, singleBulkDipSheetAdapter.toJson(sheet), sheet.lastModified)
        )
      } catch (_: Exception) {}
    }
  }

  fun getBulkDipSheet(dateStr: String): BulkDipSheet? {
    return _bulkDipSheets.value.find { it.dateStr == dateStr }
  }

  /**
   * Returns previous day's dip reading (in mm) for each tank (keyed by both tankId and tankName)
   * to power real-time Outlier / Anomaly Detection in the Bulk Dip Matrix.
   */
  fun getPreviousDayDipReadings(currentDateStr: String): Map<String, Double> {
    val result = mutableMapOf<String, Double>()
    val allTanks = _tanks.value
    val threshold = _settings.value.invalidInputThreshold

    // 1. Check most recent BulkDipSheet strictly before currentDateStr
    val prevBulkSheet = _bulkDipSheets.value
      .filter { it.dateStr < currentDateStr && it.entries.isNotEmpty() }
      .maxByOrNull { it.dateStr }

    if (prevBulkSheet != null) {
      for ((key, entry) in prevBulkSheet.entries) {
        val d = entry.dipMm
        if (d != null && d > 0.0) {
          result[key] = d
          result[entry.tankName] = d
        }
      }
    }

    // 2. Also check ProductionReportDaySnapshot strictly before currentDateStr
    val prevProdDay = _productionReportDays.value
      .filter { it.dateStr < currentDateStr && it.tankEntries.isNotEmpty() }
      .maxByOrNull { it.dateStr }

    if (prevProdDay != null) {
      for ((_, entry) in prevProdDay.tankEntries) {
        if (entry.tankName !in result) {
          val directDip = entry.dipMmToday ?: entry.dipSheetDipToday ?: entry.dipMm
          val tankObj = allTanks.find { it.name.equals(entry.tankName, ignoreCase = true) }
          if (directDip != null && directDip > 0.0) {
            result[entry.tankName] = directDip
            if (tankObj != null) result[tankObj.id] = directDip
          } else {
            val vol = entry.volumeTodayInput ?: entry.savedNaturalVolume ?: entry.volumeInput
            if (vol != null && vol > 0.0 && tankObj != null && tankObj.dipTable.isNotEmpty()) {
              val revDip = Calculators.calculateVolumeToDip(tankObj, vol, threshold)
              if (revDip != null && revDip > 0.0) {
                result[entry.tankName] = revDip
                result[tankObj.id] = revDip
              }
            }
          }
        }
      }
    }

    // 3. Fallback to last transfer/blend dip for any remaining tanks
    for (tank in allTanks) {
      if (tank.name !in result && tank.id !in result) {
        val synced = getLastSavedFinalDip(tank.name)
        if (synced != null && synced.first > 0.0) {
          result[tank.name] = synced.first
          result[tank.id] = synced.first
        }
      }
    }

    return result
  }

  fun saveProductionReportSetup(setup: ProductionReportSetup, logAction: Boolean = true) {
    prefs.edit().putString("production_report_setup_data", prodReportSetupAdapter.toJson(setup)).apply()
    _productionReportSetup.value = setup
    if (logAction) {
      logEditAction("PRODUCTION_REPORT_SETUP", "Setup", "Updated production report setup configuration")
    }
  }

  fun saveProductionReportDays(list: List<ProductionReportDaySnapshot>) {
    _productionReportDays.value = list
    prefs.edit().putString("production_report_days_data", prodReportDaysAdapter.toJson(list)).apply()
    ioScope.launch {
      try {
        dao.clearProductionReportDaysSync()
        dao.upsertProductionReportDaysSync(
          list.map { ProductionReportDayEntity(it.dateStr, singleProdReportDayAdapter.toJson(it), it.lastModified) }
        )
      } catch (_: Exception) {}
    }
  }

  fun saveProductionReportDay(snapshot: ProductionReportDaySnapshot) {
    val current = _productionReportDays.value.toMutableList()
    val idx = current.indexOfFirst { it.dateStr == snapshot.dateStr }
    if (idx >= 0) {
      current[idx] = snapshot
    } else {
      current.add(snapshot)
    }
    _productionReportDays.value = current
    prefs.edit().putString("production_report_days_data", prodReportDaysAdapter.toJson(current)).apply()
    ioScope.launch {
      try {
        dao.upsertProductionReportDaySync(
          ProductionReportDayEntity(snapshot.dateStr, singleProdReportDayAdapter.toJson(snapshot), snapshot.lastModified)
        )
      } catch (_: Exception) {}
    }
  }

  fun deleteProductionReportDay(dateStr: String) {
    val current = _productionReportDays.value.filter { it.dateStr != dateStr }
    _productionReportDays.value = current
    prefs.edit().putString("production_report_days_data", prodReportDaysAdapter.toJson(current)).apply()
    ioScope.launch {
      try {
        dao.deleteProductionReportDaySync(dateStr)
      } catch (_: Exception) {}
    }
  }

  fun getProductionReportDay(dateStr: String): ProductionReportDaySnapshot? {
    return _productionReportDays.value.find { it.dateStr == dateStr }
  }

  fun getLatestSavedDay(): ProductionReportDaySnapshot? {
    return _productionReportDays.value.maxByOrNull { it.lastModified }
  }

  fun saveProducts(list: List<Product>) {
    prefs.edit().putString("products_data", productsAdapter.toJson(list)).apply()
    _products.value = list
  }

  fun saveTanks(list: List<Tank>) {
    _tanks.value = list
    Calculators.warmUpCache(list)
    prefs.edit().putString("tanks_data", tanksAdapter.toJson(list)).apply()
    persistTanksToRoomAsync(list)
  }

  fun saveTransactions(list: List<TransactionRecord>) {
    _transactions.value = list
    prefs.edit().putString("transactions_data", transactionsAdapter.toJson(list)).apply()
    ioScope.launch {
      try {
        dao.replaceAllTransactionsSync(
          list.map { TransactionEntity(it.id, it.dateTime, it.tankName, singleTransactionAdapter.toJson(it)) }
        )
      } catch (_: Exception) {}
    }
  }

  fun saveLossReports(list: List<LossReport>) {
    _lossReports.value = list
    prefs.edit().putString("loss_reports_data", lossReportsAdapter.toJson(list)).apply()
    ioScope.launch {
      try {
        dao.replaceAllLossReportsSync(
          list.map { LossReportEntity(it.id, it.startTime, it.endTime, it.timestamp, singleLossReportAdapter.toJson(it)) }
        )
      } catch (_: Exception) {}
    }
  }

  private fun saveReadingLogs(list: List<TankReadingLogEntry>) {
    prefs.edit().putString("tank_readings_data", readingsAdapter.toJson(list)).apply()
    _readingLogs.value = list
  }

  fun saveDosingTanks(list: List<DosingTank>) {
    prefs.edit().putString("dosing_tanks_data", dosingTanksAdapter.toJson(list)).apply()
    _dosingTanks.value = list
  }

  fun saveChemicals(list: List<Chemical>) {
    prefs.edit().putString("chemicals_data", chemicalsAdapter.toJson(list)).apply()
    _chemicals.value = list
  }

  fun saveInjectionRecords(list: List<DailyInjectionRecord>) {
    _injectionRecords.value = list
    prefs.edit().putString("injection_records_data", injectionRecordsAdapter.toJson(list)).apply()
    ioScope.launch {
      try {
        dao.replaceAllDailyInjectionRecordsSync(
          list.map { DailyInjectionRecordEntity(it.id, it.date, it.timestamp, it.tankName, singleInjectionRecordAdapter.toJson(it)) }
        )
      } catch (_: Exception) {}
    }
  }

  fun saveSolutionPresets(list: List<SolutionPreset>) {
    prefs.edit().putString("solution_presets_data", solutionPresetsAdapter.toJson(list)).apply()
    _solutionPresets.value = list
  }

  fun saveSolutionBatches(list: List<SolutionBatchRecord>) {
    prefs.edit().putString("solution_batches_data", solutionBatchesAdapter.toJson(list)).apply()
    _solutionBatches.value = list
  }

  fun saveBlendPrograms(list: List<BlendProgram>) {
    prefs.edit().putString("blend_programs_data", blendProgramsAdapter.toJson(list)).apply()
    _blendPrograms.value = list
  }

  fun addOrUpdateBlendProgram(program: BlendProgram) {
    val current = _blendPrograms.value.toMutableList()
    val idx = current.indexOfFirst { it.id == program.id }
    if (idx >= 0) {
      current[idx] = program
    } else {
      current.add(program)
    }
    saveBlendPrograms(current)
  }

  fun deleteBlendProgram(id: String) {
    val current = _blendPrograms.value.filter { it.id != id }
    saveBlendPrograms(current)
  }

  fun duplicateBlendProgram(program: BlendProgram): BlendProgram {
    val copy = program.copy(
      id = UUID.randomUUID().toString(),
      name = "${program.name} (Copy)",
      isDefault = false,
      stages = program.stages.map { it.copy(id = UUID.randomUUID().toString()) }
    )
    addOrUpdateBlendProgram(copy)
    return copy
  }

  fun saveBlendRecords(list: List<BlendRecord>) {
    _blendRecords.value = list
    prefs.edit().putString("blend_records_data", blendRecordsAdapter.toJson(list)).apply()
    ioScope.launch {
      try {
        dao.replaceAllBlendRecordsSync(
          list.map { BlendRecordEntity(it.id, it.dateTime, it.tankName, singleBlendRecordAdapter.toJson(it)) }
        )
      } catch (_: Exception) {}
    }
  }

  fun addOrUpdateBlendRecord(record: BlendRecord) {
    val current = _blendRecords.value.toMutableList()
    val idx = current.indexOfFirst { it.id == record.id }
    if (idx >= 0) {
      current[idx] = record
    } else {
      current.add(0, record)
    }
    saveBlendRecords(current)
  }

  fun deleteBlendRecord(id: String) {
    val current = _blendRecords.value.filter { it.id != id }
    saveBlendRecords(current)
  }

  fun saveEditLogs(list: List<EditLogEntry>) {
    prefs.edit().putString("edit_logs_data", editLogsAdapter.toJson(list)).apply()
    _editLogs.value = list
  }

  fun addEditLog(action: String, targetName: String, details: String) {
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    val entry = EditLogEntry(
      timestamp = time,
      action = action,
      targetName = targetName,
      details = details
    )
    val current = _editLogs.value.toMutableList()
    current.add(0, entry)
    saveEditLogs(current.take(200))
  }

  fun getLastSavedFinalDip(tankName: String): Pair<Double, String>? {
    val lastBlend = _blendRecords.value.filter { it.tankName.equals(tankName, ignoreCase = true) }
      .maxByOrNull { it.dateTime }
    val lastBlendDip = lastBlend?.stageReadings?.lastOrNull()?.dipMm

    val lastTx = _transactions.value.filter { it.tankName.equals(tankName, ignoreCase = true) }
      .maxByOrNull { it.dateTime }
    val lastTxDip = lastTx?.finalDip

    if (lastBlend != null && lastTx != null) {
      if (lastBlend.dateTime >= lastTx.dateTime) {
        if (lastBlendDip != null && lastBlendDip > 0.0) return Pair(lastBlendDip, "Synced from Blend (${lastBlend.dateTime})")
      } else {
        if (lastTxDip != null && lastTxDip > 0.0) return Pair(lastTxDip, "Synced from Transfer (${lastTx.dateTime})")
      }
    } else if (lastBlend != null && lastBlendDip != null && lastBlendDip > 0.0) {
      return Pair(lastBlendDip, "Synced from Blend (${lastBlend.dateTime})")
    } else if (lastTx != null && lastTxDip != null && lastTxDip > 0.0) {
      return Pair(lastTxDip, "Synced from Transfer (${lastTx.dateTime})")
    }
    return null
  }

  fun updateSettings(newSettings: AppSettings) {
    prefs.edit().putString("settings_data", settingsAdapter.toJson(newSettings)).apply()
    _settings.value = newSettings
  }

  fun saveCruLookupTables(tables: CruLookupTables) {
    prefs.edit().putString("cru_lookup_tables_data", cruLookupTablesAdapter.toJson(tables)).apply()
    _cruLookupTables.value = tables
  }

  fun resetCruLookupTablesToDefault() {
    val defaultTables = CruLookupTables()
    saveCruLookupTables(defaultTables)
    addEditLog("RESET", "CRU Lookup Tables", "Reset all CRU lookup tables and reference conditions to factory defaults")
  }

  fun saveCruActivityRecords(list: List<CruActivityRecord>) {
    prefs.edit().putString("cru_activity_records_data", cruActivityRecordsAdapter.toJson(list)).apply()
    _cruActivityRecords.value = list
  }

  fun addOrUpdateCruActivityRecord(record: CruActivityRecord) {
    val current = _cruActivityRecords.value.toMutableList()
    val idx = current.indexOfFirst { it.id == record.id }
    if (idx >= 0) {
      current[idx] = record
    } else {
      current.add(0, record)
    }
    saveCruActivityRecords(current)
  }

  fun deleteCruActivityRecord(id: String) {
    val current = _cruActivityRecords.value.filter { it.id != id }
    saveCruActivityRecords(current)
  }

  fun saveCruReactorBeds(list: List<CruReactorBed>) {
    prefs.edit().putString("cru_reactor_beds_data", cruReactorBedsAdapter.toJson(list)).apply()
    _cruReactorBeds.value = list
  }

  fun addOrUpdateCruReactorBed(bed: CruReactorBed) {
    val current = _cruReactorBeds.value.toMutableList()
    val idx = current.indexOfFirst { it.id == bed.id }
    if (idx >= 0) {
      current[idx] = bed
    } else {
      current.add(bed)
    }
    saveCruReactorBeds(current)
  }

  fun deleteCruReactorBed(id: String) {
    val current = _cruReactorBeds.value.filter { it.id != id }
    saveCruReactorBeds(current)
  }

  fun getSeededReactorBeds(): List<CruReactorBed> {
    return listOf(
      CruReactorBed(name = "Reactor 1 (R1)", catalystWeightTonnes = 8.5, inletTempC = 505.0, outletTempC = 445.0),
      CruReactorBed(name = "Reactor 2 (R2)", catalystWeightTonnes = 17.5, inletTempC = 503.0, outletTempC = 475.0),
      CruReactorBed(name = "Reactor 3 (R3)", catalystWeightTonnes = 27.48, inletTempC = 500.0, outletTempC = 490.0)
    )
  }

  fun restoreAllDataDirect(
    tanks: List<Tank>,
    transactions: List<TransactionRecord>,
    lossReports: List<LossReport>,
    readingLogs: List<TankReadingLogEntry>,
    dosingTanks: List<DosingTank>,
    chemicals: List<Chemical>,
    injectionRecords: List<DailyInjectionRecord>,
    settings: AppSettings,
    blendPrograms: List<BlendProgram> = _blendPrograms.value,
    blendRecords: List<BlendRecord> = _blendRecords.value,
    editLogs: List<EditLogEntry> = _editLogs.value,
    cruLookupTables: CruLookupTables = _cruLookupTables.value,
    cruActivityRecords: List<CruActivityRecord> = _cruActivityRecords.value,
    cruReactorBeds: List<CruReactorBed> = _cruReactorBeds.value,
    products: List<Product> = _products.value,
    productionReportSetup: ProductionReportSetup? = null,
    productionReportDays: List<ProductionReportDaySnapshot> = emptyList()
  ) {
    prefs.edit().apply {
      putString("tanks_data", tanksAdapter.toJson(tanks))
      putString("transactions_data", transactionsAdapter.toJson(transactions))
      putString("loss_reports_data", lossReportsAdapter.toJson(lossReports))
      putString("tank_readings_data", readingsAdapter.toJson(readingLogs))
      putString("dosing_tanks_data", dosingTanksAdapter.toJson(dosingTanks))
      putString("chemicals_data", chemicalsAdapter.toJson(chemicals))
      putString("injection_records_data", injectionRecordsAdapter.toJson(injectionRecords))
      putString("settings_data", settingsAdapter.toJson(settings))
      putString("blend_programs_data", blendProgramsAdapter.toJson(blendPrograms))
      putString("blend_records_data", blendRecordsAdapter.toJson(blendRecords))
      putString("edit_logs_data", editLogsAdapter.toJson(editLogs))
      putString("cru_lookup_tables_data", cruLookupTablesAdapter.toJson(cruLookupTables))
      putString("cru_activity_records_data", cruActivityRecordsAdapter.toJson(cruActivityRecords))
      putString("cru_reactor_beds_data", cruReactorBedsAdapter.toJson(cruReactorBeds))
      putString("products_data", productsAdapter.toJson(products))
      if (productionReportSetup != null) {
        putString("production_report_setup_data", prodReportSetupAdapter.toJson(productionReportSetup))
      }
      if (productionReportDays.isNotEmpty()) {
        putString("production_report_days_data", prodReportDaysAdapter.toJson(productionReportDays))
      }
      apply()
    }
    _tanks.value = tanks
    Calculators.warmUpCache(tanks)
    persistTanksToRoomAsync(tanks)
    _transactions.value = transactions
    _lossReports.value = lossReports
    _readingLogs.value = readingLogs
    _dosingTanks.value = dosingTanks
    _chemicals.value = chemicals
    _injectionRecords.value = injectionRecords
    _settings.value = settings
    _blendPrograms.value = blendPrograms
    _blendRecords.value = blendRecords
    _editLogs.value = editLogs
    _cruLookupTables.value = cruLookupTables
    _cruActivityRecords.value = cruActivityRecords
    _cruReactorBeds.value = cruReactorBeds
    _products.value = products
    if (productionReportSetup != null) {
      _productionReportSetup.value = productionReportSetup
    }
    if (productionReportDays.isNotEmpty()) {
      _productionReportDays.value = productionReportDays
    }
    ioScope.launch {
      try {
        dao.replaceAllTransactionsSync(
          transactions.map { TransactionEntity(it.id, it.dateTime, it.tankName, singleTransactionAdapter.toJson(it)) }
        )
        dao.replaceAllLossReportsSync(
          lossReports.map { LossReportEntity(it.id, it.startTime, it.endTime, it.timestamp, singleLossReportAdapter.toJson(it)) }
        )
        dao.replaceAllDailyInjectionRecordsSync(
          injectionRecords.map { DailyInjectionRecordEntity(it.id, it.date, it.timestamp, it.tankName, singleInjectionRecordAdapter.toJson(it)) }
        )
        dao.replaceAllBlendRecordsSync(
          blendRecords.map { BlendRecordEntity(it.id, it.dateTime, it.tankName, singleBlendRecordAdapter.toJson(it)) }
        )
        if (productionReportDays.isNotEmpty()) {
          dao.clearProductionReportDaysSync()
          dao.upsertProductionReportDaysSync(
            productionReportDays.map { ProductionReportDayEntity(it.dateStr, singleProdReportDayAdapter.toJson(it), it.lastModified) }
          )
        }
      } catch (_: Exception) {}
    }
  }

  fun getAllPrefs(): Map<String, *> {
    return prefs.all
  }

  fun logEditAction(action: String, targetName: String, details: String) {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    val entry = EditLogEntry(
      timestamp = timestamp,
      action = action,
      targetName = targetName,
      details = details
    )
    val current = _editLogs.value.toMutableList()
    current.add(0, entry)
    prefs.edit().putString("edit_logs_data", editLogsAdapter.toJson(current)).apply()
    _editLogs.value = current
  }

  fun addOrUpdateProduct(product: Product, logAction: Boolean = true) {
    val currentProds = _products.value.toMutableList()
    val idx = currentProds.indexOfFirst { it.id == product.id }
    if (idx >= 0) {
      currentProds[idx] = product
    } else {
      currentProds.add(product)
    }

    // Synchronize tanks:
    // Any tank whose ID is in product.tankIds gets tank.productId = product.id
    // Any tank previously associated with product.id but not in product.tankIds gets tank.productId = null
    // Other products must not contain product.tankIds
    val currentTanks = _tanks.value.map { tank ->
      if (product.tankIds.contains(tank.id)) {
        tank.copy(productId = product.id)
      } else if (tank.productId == product.id) {
        tank.copy(productId = null)
      } else {
        tank
      }
    }
    saveTanks(currentTanks)

    // Remove product.tankIds from other products
    val sanitizedProds = currentProds.map { p ->
      if (p.id != product.id) {
        p.copy(tankIds = p.tankIds.filter { !product.tankIds.contains(it) })
      } else {
        p
      }
    }
    saveProducts(sanitizedProds)

    if (logAction) {
      logEditAction(
        action = if (idx >= 0) "PRODUCT_EDIT" else "PRODUCT_ADD",
        targetName = product.name,
        details = "Code: ${product.shortCode}, Category: ${product.category}, Linked tanks: ${product.tankIds.size}"
      )
    }
  }

  fun deleteProduct(productId: String, logAction: Boolean = true) {
    val target = _products.value.find { it.id == productId } ?: return
    // Unlink all tanks
    val updatedTanks = _tanks.value.map { tank ->
      if (tank.productId == productId) tank.copy(productId = null) else tank
    }
    saveTanks(updatedTanks)

    val updatedProds = _products.value.filter { it.id != productId }
    saveProducts(updatedProds)

    if (logAction) {
      logEditAction("PRODUCT_DELETE", target.name, "Unlinked ${target.tankIds.size} tank(s)")
    }
  }

  fun linkTankToProduct(tankId: String, productId: String?, logAction: Boolean = true) {
    val tank = _tanks.value.find { it.id == tankId } ?: return
    val updatedTanks = _tanks.value.map {
      if (it.id == tankId) it.copy(productId = productId) else it
    }
    saveTanks(updatedTanks)

    val updatedProds = _products.value.map { prod ->
      val newTankIds = prod.tankIds.filter { it != tankId }.toMutableList()
      if (prod.id == productId) {
        newTankIds.add(tankId)
      }
      prod.copy(tankIds = newTankIds)
    }
    saveProducts(updatedProds)

    if (logAction) {
      val pName = productId?.let { pid -> _products.value.find { it.id == pid }?.name } ?: "None (Unassigned)"
      logEditAction("PRODUCT_TANK_LINK", tank.name, "Linked to product: $pName")
    }
  }

  fun checkLinkConflicts(): List<LinkConflict> {
    val conflicts = mutableListOf<LinkConflict>()
    val allTanks = _tanks.value
    val allProducts = _products.value
    val setup = _productionReportSetup.value

    for (tank in allTanks) {
      val masterProdId = tank.productId
      val prodMasterLinked = allProducts.filter { it.tankIds.contains(tank.id) }
      val legacyMap = setup.tankMaps.find { it.tankName.equals(tank.name, ignoreCase = true) || it.linkedTankId == tank.id }
      val legacyProd = legacyMap?.let { map ->
        allProducts.find { it.shortCode.equals(map.productCode, ignoreCase = true) || it.name.equals(map.productCode, ignoreCase = true) }
      }

      val distinctProdIds = mutableSetOf<String>()
      if (!masterProdId.isNullOrBlank()) distinctProdIds.add(masterProdId)
      prodMasterLinked.forEach { distinctProdIds.add(it.id) }
      if (legacyProd != null) distinctProdIds.add(legacyProd.id)

      if (distinctProdIds.size > 1) {
        val candidates = mutableListOf<ProductCandidate>()
        if (!masterProdId.isNullOrBlank()) {
          val p = allProducts.find { it.id == masterProdId }
          candidates.add(ProductCandidate("Tank Master", masterProdId, p?.name ?: masterProdId, p?.shortCode ?: ""))
        }
        for (p in prodMasterLinked) {
          if (candidates.none { it.productId == p.id }) {
            candidates.add(ProductCandidate("Products Manager", p.id, p.name, p.shortCode))
          }
        }
        if (legacyProd != null && candidates.none { it.productId == legacyProd.id }) {
          candidates.add(ProductCandidate("Production Report Setup", legacyProd.id, legacyProd.name, legacyProd.shortCode))
        }
        conflicts.add(
          LinkConflict(
            tank = tank,
            candidates = candidates,
            defaultChoiceProductId = masterProdId ?: prodMasterLinked.firstOrNull()?.id ?: legacyProd?.id
          )
        )
      }
    }
    return conflicts
  }

  fun resolveLinkConflict(tankId: String, selectedProductId: String?) {
    linkTankToProduct(tankId, selectedProductId, logAction = true)
  }

  fun runDiagnostics(): DiagnosticsReport {
    val tanksList = _tanks.value
    val productsList = _products.value

    val tanksWithoutProduct = tanksList.filter {
      (it.type == TankType.PRODUCT || it.type == TankType.BLENDING) &&
        (it.productId.isNullOrBlank() || productsList.none { p -> p.id == it.productId })
    }

    val productsWithoutTanks = productsList.filter { p ->
      p.isActive && tanksList.none { t -> t.productId == p.id }
    }

    val staleTankLinks = tanksList.filter {
      it.productId != null && productsList.none { p -> p.id == it.productId }
    }.map { Pair(it, it.productId!!) }

    val staleProductLinks = mutableListOf<Pair<Product, String>>()
    for (p in productsList) {
      for (tid in p.tankIds) {
        if (tanksList.none { it.id == tid }) {
          staleProductLinks.add(Pair(p, tid))
        }
      }
    }

    return DiagnosticsReport(
      tanksWithoutProduct = tanksWithoutProduct,
      productsWithoutTanks = productsWithoutTanks,
      staleTankLinks = staleTankLinks,
      staleProductLinks = staleProductLinks
    )
  }

  fun cleanStaleLinks() {
    val tanksList = _tanks.value
    val productsList = _products.value

    val cleanedTanks = tanksList.map { tank ->
      if (tank.productId != null && productsList.none { p -> p.id == tank.productId }) {
        tank.copy(productId = null)
      } else {
        tank
      }
    }
    saveTanks(cleanedTanks)

    val cleanedProducts = productsList.map { prod ->
      val validIds = prod.tankIds.filter { tid -> tanksList.any { t -> t.id == tid } }
      prod.copy(tankIds = validIds)
    }
    saveProducts(cleanedProducts)
    logEditAction("DIAGNOSTICS_CLEAN", "System", "Cleaned stale links between tanks and products")
  }

  fun saveTank(tank: Tank, logAction: Boolean = true) = addOrUpdateTank(tank, logAction)

  fun addOrUpdateTank(tank: Tank, logAction: Boolean = false) {
    val current = _tanks.value.toMutableList()
    val index = current.indexOfFirst { it.name.equals(tank.name, ignoreCase = true) || it.id == tank.id }
    val oldTank = if (index >= 0) current[index] else null
    val updatedTank = if (index >= 0) tank.copy(id = current[index].id) else tank
    if (index >= 0) {
      current[index] = updatedTank
    } else {
      current.add(updatedTank)
    }
    saveTanks(current)

    // Synchronize product links if productId changed
    if (oldTank?.productId != updatedTank.productId) {
      val curProds = _products.value.map { prod ->
        val updatedIds = prod.tankIds.filter { it != updatedTank.id }.toMutableList()
        if (prod.id == updatedTank.productId) {
          updatedIds.add(updatedTank.id)
        }
        prod.copy(tankIds = updatedIds)
      }
      saveProducts(curProds)
    }

    if (logAction) {
      val typeStr = "Type: ${updatedTank.type}" + if (updatedTank.type == TankType.BLENDING) ", Blended Product: ${updatedTank.blendedProductId?.let { pid -> _products.value.find { it.id == pid }?.name } ?: "None"}" else ""
      logEditAction(
        action = if (index >= 0) "TANK_EDIT" else "TANK_ADD",
        targetName = updatedTank.name,
        details = "$typeStr, Shape: ${updatedTank.shape}, Max: ${updatedTank.maxVolumeLiters.toInt()} L"
      )
    }
  }

  fun deleteTank(tankId: String) {
    val current = _tanks.value.filter { it.id != tankId }
    saveTanks(current)
    // Remove tank from products
    val updatedProds = _products.value.map { prod ->
      prod.copy(tankIds = prod.tankIds.filter { it != tankId })
    }
    saveProducts(updatedProds)
  }

  fun addTransaction(tx: TransactionRecord) {
    val current = _transactions.value.toMutableList()
    current.add(0, tx)
    saveTransactions(current)
  }

  fun addOrUpdateLossReport(report: LossReport) {
    val current = _lossReports.value.toMutableList()
    val index = current.indexOfFirst { it.id == report.id }
    if (index >= 0) {
      current[index] = report
    } else {
      current.add(0, report)
    }
    saveLossReports(current)
  }

  fun deleteLossReport(reportId: String) {
    val current = _lossReports.value.filter { it.id != reportId }
    saveLossReports(current)
  }

  fun addOrUpdateDosingTank(tank: DosingTank) {
    val current = _dosingTanks.value.toMutableList()
    val idx = current.indexOfFirst { it.id == tank.id || it.name.equals(tank.name, ignoreCase = true) }
    if (idx >= 0) {
      current[idx] = tank.copy(id = current[idx].id, isDefault = false)
    } else {
      current.add(tank.copy(isDefault = false))
    }
    saveDosingTanks(current)
  }

  fun deleteDosingTank(id: String) {
    val current = _dosingTanks.value.filter { it.id != id }
    saveDosingTanks(current)
  }

  fun addOrUpdateChemical(chem: Chemical) {
    val current = _chemicals.value.toMutableList()
    val idx = current.indexOfFirst { it.id == chem.id || it.name.equals(chem.name, ignoreCase = true) }
    if (idx >= 0) {
      current[idx] = chem.copy(id = current[idx].id, isDefault = false)
    } else {
      current.add(chem.copy(isDefault = false))
    }
    saveChemicals(current)
  }

  fun deleteChemical(id: String) {
    val current = _chemicals.value.filter { it.id != id }
    saveChemicals(current)
  }

  fun findReading(tankName: String, timestamp: String): TankReadingLogEntry? {
    val t = timestamp.trim()
    return _readingLogs.value.lastOrNull {
      it.tankName.equals(tankName, ignoreCase = true) && it.timestamp.trim() == t
    }
  }

  fun findChainedFinalReading(tankName: String, startTime: String): TankReadingLogEntry? {
    val t = startTime.trim()
    val matchingReport = _lossReports.value.firstOrNull { it.endTime.trim() == t }
    val entry = matchingReport?.let { (it.feedTanks + it.productTanks).firstOrNull { e -> e.tankName.equals(tankName, ignoreCase = true) } }
    if (entry != null) {
      return TankReadingLogEntry(
        tankName = tankName,
        timestamp = t,
        tov = entry.finalTov,
        density15 = entry.density15,
        temp = entry.finalTemp,
        reportId = matchingReport.id
      )
    }
    return findReading(tankName, startTime)
  }

  fun findExactReport(startTime: String, endTime: String): LossReport? {
    val s = startTime.trim()
    val e = endTime.trim()
    return _lossReports.value.firstOrNull { it.startTime.trim() == s && it.endTime.trim() == e }
  }

  fun getTankDensity(tankName: String): Double {
    return prefs.getFloat("density_${tankName.lowercase()}", 750.0f).toDouble()
  }

  fun setTankDensity(tankName: String, density: Double) {
    prefs.edit().putFloat("density_${tankName.lowercase()}", density.toFloat()).apply()
  }

  private fun getSeededTanks(): List<Tank> {
    val tankNames = listOf(
      "T-101", "T-102", "T-501A", "T-501B", "T-501C", "T-501D",
      "T-502A", "T-502B", "T-503A", "T-503B", "T-504A", "T-504B",
      "T-505A", "T-505B", "T-506", "T-507", "T-508", "T-509", "T-510", "T-511",
      "T-512A", "T-512B", "T-512C", "T-512D"
    )
    val dipRows = (0..5000 step 10).map { dip ->
      val dipMm = dip.toDouble()
      val vols = tankNames.associateWith { dipMm * 500.0 }
      DipRow(dipMm = dipMm, volumes = vols)
    }

    val fracRows = (0..9).map { mm ->
      val vols = tankNames.associateWith { mm.toDouble() * 50.0 }
      FractionRow(mm = mm, volumes = vols)
    }

    val n2Rows = (0..500 step 50).map { h ->
      val hMm = h.toDouble()
      val vN1 = hMm * 12.5
      N2Row(heightMm = hMm, volumes = mapOf("N-1" to vN1))
    }

    return listOf(
      Tank(name = "T-501A", type = TankType.FEED, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCF30_BY_DENSITY15),
      Tank(name = "T-501B", type = TankType.FEED, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCF30_BY_DENSITY15),
      Tank(name = "T-501C", type = TankType.FEED, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCF30_BY_DENSITY15),
      Tank(name = "T-501D", type = TankType.FEED, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCF30_BY_DENSITY15),
      Tank(name = "T-511", type = TankType.FEED, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCF30_BY_DENSITY15),
      Tank(name = "T-508", type = TankType.FEED, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCF30_BY_DENSITY15),
      Tank(name = "T-502A", type = TankType.PRODUCT, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCC_BY_DENSITY30, productId = PROD_LN_ID),
      Tank(name = "T-502B", type = TankType.PRODUCT, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCC_BY_DENSITY30, productId = PROD_LN_ID),
      Tank(name = "T-503A", type = TankType.PRODUCT, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCC_BY_DENSITY30, productId = PROD_REF_ID),
      Tank(name = "T-503B", type = TankType.PRODUCT, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCC_BY_DENSITY30, productId = PROD_REF_ID),
      Tank(name = "T-504A", type = TankType.PRODUCT, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCC_BY_DENSITY30),
      Tank(name = "T-504B", type = TankType.PRODUCT, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCC_BY_DENSITY30),
      Tank(name = "T-505A", type = TankType.PRODUCT, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCC_BY_DENSITY30),
      Tank(name = "T-505B", type = TankType.PRODUCT, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCC_BY_DENSITY30),
      Tank(name = "T-506", type = TankType.PRODUCT, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCC_BY_DENSITY30),
      Tank(name = "T-507", type = TankType.PRODUCT, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCC_BY_DENSITY30),
      Tank(name = "T-509", type = TankType.BLENDING, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCC_BY_DENSITY30, blendedProductId = PROD_MS_ID),
      Tank(name = "T-510", type = TankType.BLENDING, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.VCC_BY_DENSITY30, blendedProductId = PROD_HOBC_ID),
      Tank(name = "T-512A", type = TankType.PRODUCT, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.NONE),
      Tank(name = "T-512B", type = TankType.PRODUCT, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.NONE),
      Tank(name = "T-512C", type = TankType.PRODUCT, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.NONE),
      Tank(name = "T-512D", type = TankType.PRODUCT, shape = TankShape.VERTICAL, maxVolumeLiters = 500000.0, dipTable = dipRows, fractionTable = fracRows, coefficientSource = CoefficientSourceType.NONE),
      Tank(name = "N-1", type = TankType.N2_STORAGE, shape = TankShape.HORIZONTAL, maxVolumeLiters = 50000.0, n2Table = n2Rows)
    )
  }

  fun getSeededBlendPrograms(): List<BlendProgram> {
    return listOf(
      BlendProgram(
        name = "HOBC Blending",
        tankName = "T-510",
        isDefault = true,
        stages = listOf(
          BlendStage(stageName = "Initial", componentName = "", orderIndex = 0),
          BlendStage(stageName = "After Reformate", componentName = "Reformate", orderIndex = 1),
          BlendStage(stageName = "After Light Naphtha", componentName = "Light Naphtha", orderIndex = 2)
        )
      ),
      BlendProgram(
        name = "MS Blending",
        tankName = "T-509",
        isDefault = true,
        stages = listOf(
          BlendStage(stageName = "Initial", componentName = "", orderIndex = 0),
          BlendStage(stageName = "After Reformate", componentName = "Reformate", orderIndex = 1),
          BlendStage(stageName = "After Light Naphtha", componentName = "Light Naphtha", orderIndex = 2)
        )
      )
    )
  }

  private fun getSeededDosingTanks(): List<DosingTank> {
    return listOf(
      DosingTank(name = "Water Tank", diameterMm = 400.0, heightMm = 600.0, overrideLitresPerMm = 0.125664, linkedChemicalName = "DM Water", isDefault = true),
      DosingTank(name = "C2Cl4 Tank", diameterMm = 1200.0, heightMm = 1400.0, overrideLitresPerMm = 1.13, linkedChemicalName = "C2Cl4 solution", isDefault = true),
      DosingTank(name = "C.I Tank", diameterMm = 3000.0, heightMm = 3500.0, overrideLitresPerMm = 7.06, linkedChemicalName = "Nalco C.I", isDefault = true),
      DosingTank(name = "DMDS Tank", diameterMm = 1600.0, heightMm = 2000.0, overrideLitresPerMm = 2.0106, linkedChemicalName = "DMDS", isDefault = true)
    )
  }

  private fun getSeededChemicals(): List<Chemical> {
    return listOf(
      Chemical(name = "DM Water", densityKgL = 1.0, activeFractionPercent = 100.0, elementFactor = 1.0, dosingBasisStreamName = "CRU feed (kg/hr)", targetWtPpm = 5.0, minPpm = 4.0, maxPpm = 6.0, productHeaderType = "Water", isDefault = true),
      Chemical(name = "C2Cl4 solution", densityKgL = 0.778, activeFractionPercent = 5.0, elementFactor = 0.885, dosingBasisStreamName = "CRU feed (kg/hr)", targetWtPpm = 1.0, minPpm = 0.2, maxPpm = 1.0, productHeaderType = "C2Cl4", isDefault = true),
      Chemical(name = "Nalco C.I", densityKgL = 0.773, activeFractionPercent = 1.0, elementFactor = 1.0, dosingBasisStreamName = "Overhead product C-201 / C-203 / combined (kg/hr)", targetWtPpm = 8.0, minPpm = 7.0, maxPpm = 9.0, productHeaderType = "Nalco", isDefault = true),
      Chemical(name = "Chimec C.I", densityKgL = 0.773, activeFractionPercent = 10.0, elementFactor = 1.0, dosingBasisStreamName = "Overhead product C-201 / C-203 / combined (kg/hr)", targetWtPpm = 15.0, minPpm = 14.0, maxPpm = 16.0, productHeaderType = "Chimec", isDefault = true)
    )
  }

  private fun getSeededSolutionPresets(): List<SolutionPreset> {
    return listOf(
      SolutionPreset(
        name = "DMDS 16.5%",
        neatChemicalName = "DMDS",
        neatDensityKgL = 1.06,
        solventName = "HN+LN",
        strengthPercent = 16.5,
        basisString = "CRU feed (kg/hr)",
        defaultTankName = "DMDS Tank",
        drumSizeKg = 200.0,
        isDefault = true
      ),
      SolutionPreset(
        name = "C2Cl4 1%",
        neatChemicalName = "C2Cl4",
        neatDensityKgL = 1.62,
        solventName = "Reformate",
        strengthPercent = 1.0,
        basisString = "CRU feed (kg/hr)",
        defaultTankName = "C2Cl4 Tank",
        drumSizeKg = 200.0,
        isDefault = true
      ),
      SolutionPreset(
        name = "C.I 0.72%",
        neatChemicalName = "Nalco C.I",
        neatDensityKgL = 0.773,
        solventName = "LN+HN",
        strengthPercent = 0.72,
        basisString = "Overhead product C-201 / C-203 / combined (kg/hr)",
        defaultTankName = "C.I Tank",
        drumSizeKg = 200.0,
        isDefault = true
      )
    )
  }

  fun getSeededProducts(): List<Product> {
    return listOf(
      Product(
        id = PROD_REF_ID,
        name = "Reformate",
        shortCode = "REF",
        category = ProductCategory.PRODUCT,
        defaultDensity15 = 770.0,
        colorHex = "#2E7D32",
        erpReportGroup = "Reformate"
      ),
      Product(
        id = PROD_LN_ID,
        name = "Light Naphtha",
        shortCode = "LN",
        category = ProductCategory.INTERMEDIATE,
        defaultDensity15 = 680.0,
        colorHex = "#0288D1",
        erpReportGroup = "LN"
      ),
      Product(
        id = PROD_HOBC_ID,
        name = "HOBC",
        shortCode = "HOBC",
        category = ProductCategory.PRODUCT,
        defaultDensity15 = 755.0,
        colorHex = "#E65100",
        erpReportGroup = "Octane"
      ),
      Product(
        id = PROD_MS_ID,
        name = "MS",
        shortCode = "MS",
        category = ProductCategory.PRODUCT,
        defaultDensity15 = 745.0,
        colorHex = "#6A1B9A",
        erpReportGroup = "MS"
      ),
      Product(
        id = "prod_jet_seed",
        name = "Kerosene / Jet A-1",
        shortCode = "JET",
        category = ProductCategory.PRODUCT,
        defaultDensity15 = 790.0,
        colorHex = "#00897B",
        erpReportGroup = "Kerosene/Jet A-1"
      ),
      Product(
        id = "prod_jbo_seed",
        name = "JBO",
        shortCode = "JBO",
        category = ProductCategory.PRODUCT,
        defaultDensity15 = 810.0,
        colorHex = "#F57C00",
        erpReportGroup = "JBO"
      ),
      Product(
        id = "prod_mtt_seed",
        name = "MTT",
        shortCode = "MTT",
        category = ProductCategory.PRODUCT,
        defaultDensity15 = 810.0,
        colorHex = "#C2185B",
        erpReportGroup = "MTT"
      ),
      Product(
        id = "prod_diesel_seed",
        name = "Diesel",
        shortCode = "DIESEL",
        category = ProductCategory.PRODUCT,
        defaultDensity15 = 830.0,
        colorHex = "#3949AB",
        erpReportGroup = "Diesel"
      ),
      Product(
        id = "prod_dsn_seed",
        name = "DSN",
        shortCode = "DSN",
        category = ProductCategory.PRODUCT,
        defaultDensity15 = 750.0,
        colorHex = "#757575",
        erpReportGroup = "DSN"
      ),
      Product(
        id = "prod_lpg_seed",
        name = "LPG",
        shortCode = "LPG",
        category = ProductCategory.PRODUCT,
        defaultDensity15 = 540.0,
        colorHex = "#D81B60",
        erpReportGroup = "LPG"
      )
    )
  }

  // --- Global Persisted Drafts ---

  fun saveDraft(moduleKey: String, moduleTitle: String, contextKey: String = "", payloadJson: String): DraftRecord {
    val id = if (contextKey.isNotBlank()) "${moduleKey}_$contextKey" else moduleKey
    val draft = DraftRecord(
      id = id,
      moduleKey = moduleKey,
      moduleTitle = moduleTitle,
      contextKey = contextKey,
      payloadJson = payloadJson,
      lastModified = System.currentTimeMillis()
    )
    val updated = _drafts.value.toMutableMap()
    updated[id] = draft
    _drafts.value = updated
    persistDrafts(updated.values.toList())
    return draft
  }

  fun getDraft(moduleKey: String, contextKey: String = ""): DraftRecord? {
    val id = if (contextKey.isNotBlank()) "${moduleKey}_$contextKey" else moduleKey
    return _drafts.value[id]
  }

  fun deleteDraft(id: String) {
    val updated = _drafts.value.toMutableMap()
    if (updated.remove(id) != null) {
      _drafts.value = updated
      persistDrafts(updated.values.toList())
    }
  }

  fun deleteDraftByModule(moduleKey: String, contextKey: String = "") {
    val id = if (contextKey.isNotBlank()) "${moduleKey}_$contextKey" else moduleKey
    deleteDraft(id)
  }

  fun getAllDrafts(): List<DraftRecord> {
    return _drafts.value.values.sortedByDescending { it.lastModified }
  }

  fun autoCleanupDrafts(maxDays: Int = 30) {
    val cutoff = System.currentTimeMillis() - (maxDays.toLong() * 24L * 60L * 60L * 1000L)
    val remaining = _drafts.value.values.filter { it.lastModified >= cutoff }
    if (remaining.size != _drafts.value.size) {
      _drafts.value = remaining.associateBy { it.id }
      persistDrafts(remaining)
    }
  }

  private fun persistDrafts(list: List<DraftRecord>) {
    prefs.edit().putString("global_drafts_data", draftsAdapter.toJson(list)).apply()
  }
}
