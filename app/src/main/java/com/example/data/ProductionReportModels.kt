package com.example.data

import com.squareup.moshi.JsonClass
import java.util.UUID

enum class TankRole {
  FEED,
  PRODUCT,
  BLENDING,
  EXCLUDED
}

enum class ProductionReportTankGroup {
  RAW_MATERIAL,
  PRODUCT
}

enum class WasteCategory {
  FLARE,
  KNHT_H2,
  FUEL
}

enum class CalculationBasis {
  NATURAL,
  TEMP_30C
}

@JsonClass(generateAdapter = true)
data class DensityCoefficientRow(
  val lowerBound: Double,
  val coefficient: Double
)

@JsonClass(generateAdapter = true)
data class ProductionReportTankMap(
  val tankName: String,
  val group: ProductionReportTankGroup,
  val productCode: String,
  val subProductDesc: String = "",
  val coefficientSource: CoefficientSourceType,
  val isBlendingTank: Boolean = false,
  val defaultDensity: Double? = null,
  val linkedTankId: String? = null,
  val defaultRole: TankRole = if (group == ProductionReportTankGroup.RAW_MATERIAL) TankRole.FEED else TankRole.PRODUCT,
  val defaultProductGroup: String = productCode
)

@JsonClass(generateAdapter = true)
data class ProductionReportConstants(
  val basisTempC: Double = 30.0,
  val processWasteDensityKgL: Double = 0.74,
  val minimumReserveStockL: Double = 840_000.0,
  val designLoadCfuBblDay: Double = 10_000.0,
  val designLoadKnhtBblDay: Double = 8_000.0,
  val designLoadCruBblDay: Double = 5_600.0,
  val reportTime: String = "10:00",
  val includeJetAndSkoInYield: Boolean = true
)

@JsonClass(generateAdapter = true)
data class ProcessWasteMeter(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val tag: String,
  val category: WasteCategory
)

enum class ErpRowType {
  STANDARD, // "Standard product"
  BLENDING, // "Blending product"
  CONSUMED_AS_FEED // "Consumed as feed (like DSN)"
}

@JsonClass(generateAdapter = true)
data class ErpRowConfig(
  val id: String = UUID.randomUUID().toString(),
  val itemCode: String = "",
  val itemName: String,
  val isRaw: Boolean = false,
  val rowType: ErpRowType = ErpRowType.STANDARD,
  val assignedTankNames: List<String> = emptyList(),
  val note: String = "",
  val isHidden: Boolean = false,
  val sortOrder: Int = 0
)

@JsonClass(generateAdapter = true)
data class RawMaterialRowConfig(
  val id: String = UUID.randomUUID().toString(),
  val itemCode: String = "",
  val itemName: String,
  val tankSplits: Map<String, Double> = emptyMap(), // TankName -> Percentage (e.g. "T-501A" -> 75.0)
  val isHidden: Boolean = false,
  val sortOrder: Int = 0
)

@JsonClass(generateAdapter = true)
data class ProductionReportSetup(
  val tableVersionId: String = UUID.randomUUID().toString(),
  val tankMaps: List<ProductionReportTankMap> = emptyList(),
  val vccTable: List<DensityCoefficientRow> = defaultVccTable(),
  val vcf30Table: List<DensityCoefficientRow> = defaultVcf30Table(),
  val constants: ProductionReportConstants = ProductionReportConstants(),
  val processWasteMeters: List<ProcessWasteMeter> = defaultProcessWasteMeters(),
  val erpRowConfigs: List<ErpRowConfig> = defaultErpRowConfigs(),
  val rawMaterialRowConfigs: List<RawMaterialRowConfig> = defaultRawMaterialRowConfigs()
) {
  companion object {
    fun defaultErpRowConfigs(): List<ErpRowConfig> = listOf(
      ErpRowConfig(itemCode = "DIESEL", itemName = "Diesel", rowType = ErpRowType.STANDARD, assignedTankNames = listOf("T-506", "T-507", "T-508"), sortOrder = 1),
      ErpRowConfig(itemCode = "JBO", itemName = "JBO", rowType = ErpRowType.STANDARD, assignedTankNames = listOf("T-505A"), sortOrder = 2),
      ErpRowConfig(itemCode = "MTT", itemName = "MTT", rowType = ErpRowType.STANDARD, assignedTankNames = listOf("T-505B"), sortOrder = 3),
      ErpRowConfig(itemCode = "LN", itemName = "Light Naphtha", rowType = ErpRowType.STANDARD, assignedTankNames = listOf("T-502A", "T-502B"), sortOrder = 4),
      ErpRowConfig(itemCode = "SKO", itemName = "SKO", rowType = ErpRowType.STANDARD, assignedTankNames = emptyList(), sortOrder = 5),
      ErpRowConfig(itemCode = "JET", itemName = "Jet A-1", rowType = ErpRowType.STANDARD, assignedTankNames = listOf("T-504A", "T-504B"), sortOrder = 6),
      ErpRowConfig(itemCode = "REFORMATE", itemName = "Reformate", rowType = ErpRowType.STANDARD, assignedTankNames = listOf("T-503A", "T-503B"), sortOrder = 7),
      ErpRowConfig(itemCode = "LPG", itemName = "LPG", rowType = ErpRowType.STANDARD, assignedTankNames = listOf("T-512A", "T-512B", "T-512C", "T-512D"), sortOrder = 8),
      ErpRowConfig(itemCode = "MS", itemName = "Petrol (MS)", rowType = ErpRowType.BLENDING, assignedTankNames = listOf("T-509"), sortOrder = 9),
      ErpRowConfig(itemCode = "OCTANE", itemName = "Octane (HOBC)", rowType = ErpRowType.BLENDING, assignedTankNames = listOf("T-510"), sortOrder = 10),
      ErpRowConfig(itemCode = "DSN", itemName = "DSN", rowType = ErpRowType.CONSUMED_AS_FEED, assignedTankNames = listOf("T-511"), sortOrder = 11)
    )

    fun defaultRawMaterialRowConfigs(): List<RawMaterialRowConfig> = listOf(
      RawMaterialRowConfig(itemCode = "COND_EXP", itemName = "Condensate (Export)", tankSplits = mapOf("T-501A" to 75.0, "T-501B" to 100.0, "T-501C" to 100.0, "T-501D" to 70.0), sortOrder = 1),
      RawMaterialRowConfig(itemCode = "COND_LOC", itemName = "Condensate (Local)", tankSplits = mapOf("T-501D" to 30.0), sortOrder = 2),
      RawMaterialRowConfig(itemCode = "NAPH_LOC", itemName = "Naphtha (Local)", tankSplits = mapOf("T-501A" to 25.0), sortOrder = 3),
      RawMaterialRowConfig(itemCode = "NAPH_EXP", itemName = "Naphtha (Export)", tankSplits = emptyMap(), sortOrder = 4)
    )
    fun defaultTankMaps(): List<ProductionReportTankMap> = listOf(
      // Raw materials
      ProductionReportTankMap("T-501A", ProductionReportTankGroup.RAW_MATERIAL, "NGC", "NGC / Condensate", CoefficientSourceType.VCF30_BY_DENSITY15, defaultDensity = 0.73618),
      ProductionReportTankMap("T-501B", ProductionReportTankGroup.RAW_MATERIAL, "NGC", "NGC / Condensate", CoefficientSourceType.VCF30_BY_DENSITY15, defaultDensity = 0.73618),
      ProductionReportTankMap("T-501C", ProductionReportTankGroup.RAW_MATERIAL, "NGC", "NGC / Condensate", CoefficientSourceType.VCF30_BY_DENSITY15, defaultDensity = 0.73618),
      ProductionReportTankMap("T-501D", ProductionReportTankGroup.RAW_MATERIAL, "NGC", "NGC / Condensate", CoefficientSourceType.VCF30_BY_DENSITY15, defaultDensity = 0.73618),
      ProductionReportTankMap("T-511", ProductionReportTankGroup.RAW_MATERIAL, "DSN", "DSN Raw Material", CoefficientSourceType.VCF30_BY_DENSITY15, defaultDensity = 0.750),
      ProductionReportTankMap("T-508", ProductionReportTankGroup.RAW_MATERIAL, "Mixed feed", "Mixed Feed", CoefficientSourceType.VCF30_BY_DENSITY15, defaultDensity = 0.820),

      // Products
      ProductionReportTankMap("T-502A", ProductionReportTankGroup.PRODUCT, "LN", "Light Naphtha", CoefficientSourceType.VCC_BY_DENSITY30, defaultDensity = 0.65059),
      ProductionReportTankMap("T-502B", ProductionReportTankGroup.PRODUCT, "LN", "Light Naphtha", CoefficientSourceType.VCC_BY_DENSITY30, defaultDensity = 0.65059),
      ProductionReportTankMap("T-503A", ProductionReportTankGroup.PRODUCT, "REFORMATE", "Reformate", CoefficientSourceType.VCC_BY_DENSITY30, defaultDensity = 0.760),
      ProductionReportTankMap("T-503B", ProductionReportTankGroup.PRODUCT, "REFORMATE", "Reformate", CoefficientSourceType.VCC_BY_DENSITY30, defaultDensity = 0.760),
      ProductionReportTankMap("T-504A", ProductionReportTankGroup.PRODUCT, "JET", "Kerosene / Jet A-1", CoefficientSourceType.VCC_BY_DENSITY30, defaultDensity = 0.790),
      ProductionReportTankMap("T-504B", ProductionReportTankGroup.PRODUCT, "JET", "Kerosene / Jet A-1", CoefficientSourceType.VCC_BY_DENSITY30, defaultDensity = 0.790),
      ProductionReportTankMap("T-505A", ProductionReportTankGroup.PRODUCT, "SKO", "JBO", CoefficientSourceType.VCC_BY_DENSITY30, defaultDensity = 0.810),
      ProductionReportTankMap("T-505B", ProductionReportTankGroup.PRODUCT, "SKO", "MTT", CoefficientSourceType.VCC_BY_DENSITY30, defaultDensity = 0.810),
      ProductionReportTankMap("T-506", ProductionReportTankGroup.PRODUCT, "DIESEL", "Light Diesel", CoefficientSourceType.VCC_BY_DENSITY30, defaultDensity = 0.825),
      ProductionReportTankMap("T-507", ProductionReportTankGroup.PRODUCT, "DIESEL", "Heavy Diesel", CoefficientSourceType.VCC_BY_DENSITY30, defaultDensity = 0.840),
      ProductionReportTankMap("T-508", ProductionReportTankGroup.PRODUCT, "DIESEL", "Diesel blending", CoefficientSourceType.VCC_BY_DENSITY30, defaultDensity = 0.830),
      ProductionReportTankMap("T-509", ProductionReportTankGroup.PRODUCT, "MS", "MS (Petrol)", CoefficientSourceType.VCC_BY_DENSITY30, isBlendingTank = true, defaultDensity = 0.72692),
      ProductionReportTankMap("T-510", ProductionReportTankGroup.PRODUCT, "OCTANE", "Octane / HOBC", CoefficientSourceType.VCC_BY_DENSITY30, isBlendingTank = true, defaultDensity = 0.745),
      ProductionReportTankMap("T-511", ProductionReportTankGroup.PRODUCT, "DSN", "DSN Product", CoefficientSourceType.VCC_BY_DENSITY30, defaultDensity = 0.750),
      ProductionReportTankMap("T-512A", ProductionReportTankGroup.PRODUCT, "LPG", "LPG Tank A", CoefficientSourceType.NONE, defaultDensity = 0.540),
      ProductionReportTankMap("T-512B", ProductionReportTankGroup.PRODUCT, "LPG", "LPG Tank B", CoefficientSourceType.NONE, defaultDensity = 0.540),
      ProductionReportTankMap("T-512C", ProductionReportTankGroup.PRODUCT, "LPG", "LPG Tank C", CoefficientSourceType.NONE, defaultDensity = 0.540),
      ProductionReportTankMap("T-512D", ProductionReportTankGroup.PRODUCT, "LPG", "LPG Tank D", CoefficientSourceType.NONE, defaultDensity = 0.540)
    )

    fun defaultVccTable(): List<DensityCoefficientRow> = listOf(
      DensityCoefficientRow(0.627, 0.00152), DensityCoefficientRow(0.640, 0.00150),
      DensityCoefficientRow(0.643, 0.00148), DensityCoefficientRow(0.644, 0.00146),
      DensityCoefficientRow(0.655, 0.00144), DensityCoefficientRow(0.661, 0.00142),
      DensityCoefficientRow(0.665, 0.00140), DensityCoefficientRow(0.673, 0.00138),
      DensityCoefficientRow(0.679, 0.00136), DensityCoefficientRow(0.687, 0.00134),
      DensityCoefficientRow(0.694, 0.00132), DensityCoefficientRow(0.702, 0.00130),
      DensityCoefficientRow(0.708, 0.00128), DensityCoefficientRow(0.716, 0.00126),
      DensityCoefficientRow(0.724, 0.00124), DensityCoefficientRow(0.732, 0.00122),
      DensityCoefficientRow(0.740, 0.00120), DensityCoefficientRow(0.751, 0.00118),
      DensityCoefficientRow(0.759, 0.00116), DensityCoefficientRow(0.760, 0.00114),
      DensityCoefficientRow(0.761, 0.00112), DensityCoefficientRow(0.763, 0.00110),
      DensityCoefficientRow(0.765, 0.00108), DensityCoefficientRow(0.767, 0.00106),
      DensityCoefficientRow(0.769, 0.00104), DensityCoefficientRow(0.770, 0.00103),
      DensityCoefficientRow(0.771, 0.00101), DensityCoefficientRow(0.773, 0.00099),
      DensityCoefficientRow(0.775, 0.00097), DensityCoefficientRow(0.779, 0.00095),
      DensityCoefficientRow(0.788, 0.00093), DensityCoefficientRow(0.796, 0.00091),
      DensityCoefficientRow(0.806, 0.00089), DensityCoefficientRow(0.816, 0.00087),
      DensityCoefficientRow(0.824, 0.00085), DensityCoefficientRow(0.838, 0.00083),
      DensityCoefficientRow(0.855, 0.00081), DensityCoefficientRow(0.873, 0.00079),
      DensityCoefficientRow(0.891, 0.00077), DensityCoefficientRow(0.909, 0.00076),
      DensityCoefficientRow(0.918, 0.00075), DensityCoefficientRow(0.927, 0.00074),
      DensityCoefficientRow(0.938, 0.00073), DensityCoefficientRow(0.949, 0.00071),
      DensityCoefficientRow(0.961, 0.00070), DensityCoefficientRow(0.971, 0.00069),
      DensityCoefficientRow(0.993, 0.00068)
    )

    fun defaultVcf30Table(): List<DensityCoefficientRow> = listOf(
      DensityCoefficientRow(0.6600, 0.00142), DensityCoefficientRow(0.6606, 0.00141),
      DensityCoefficientRow(0.6633, 0.00140), DensityCoefficientRow(0.6658, 0.00139),
      DensityCoefficientRow(0.6676, 0.00138), DensityCoefficientRow(0.6695, 0.00137),
      DensityCoefficientRow(0.6720, 0.00136), DensityCoefficientRow(0.6748, 0.00135),
      DensityCoefficientRow(0.6774, 0.00134), DensityCoefficientRow(0.6799, 0.00133),
      DensityCoefficientRow(0.6828, 0.00132), DensityCoefficientRow(0.6854, 0.00131),
      DensityCoefficientRow(0.6881, 0.00130), DensityCoefficientRow(0.6907, 0.00129),
      DensityCoefficientRow(0.6929, 0.00128), DensityCoefficientRow(0.6951, 0.00127),
      DensityCoefficientRow(0.6984, 0.00126), DensityCoefficientRow(0.7015, 0.00125),
      DensityCoefficientRow(0.7043, 0.00124), DensityCoefficientRow(0.7067, 0.00123),
      DensityCoefficientRow(0.7091, 0.00122), DensityCoefficientRow(0.7116, 0.00121),
      DensityCoefficientRow(0.7141, 0.00120), DensityCoefficientRow(0.7168, 0.00119),
      DensityCoefficientRow(0.7196, 0.00118), DensityCoefficientRow(0.7229, 0.00117),
      DensityCoefficientRow(0.7261, 0.00116), DensityCoefficientRow(0.7291, 0.00115),
      DensityCoefficientRow(0.7318, 0.00114), DensityCoefficientRow(0.7346, 0.00113),
      DensityCoefficientRow(0.7373, 0.00112), DensityCoefficientRow(0.7409, 0.00111),
      DensityCoefficientRow(0.7438, 0.00110), DensityCoefficientRow(0.7470, 0.00109),
      DensityCoefficientRow(0.7504, 0.00108), DensityCoefficientRow(0.7531, 0.00107),
      DensityCoefficientRow(0.7557, 0.00106), DensityCoefficientRow(0.7582, 0.00105),
      DensityCoefficientRow(0.7608, 0.00104), DensityCoefficientRow(0.7636, 0.00103),
      DensityCoefficientRow(0.7662, 0.00102), DensityCoefficientRow(0.7685, 0.00101),
      DensityCoefficientRow(0.7711, 0.00100), DensityCoefficientRow(0.7741, 0.00099),
      DensityCoefficientRow(0.7768, 0.00098), DensityCoefficientRow(0.7794, 0.00097),
      DensityCoefficientRow(0.7828, 0.00096), DensityCoefficientRow(0.7862, 0.00095),
      DensityCoefficientRow(0.7892, 0.00094), DensityCoefficientRow(0.7920, 0.00093),
      DensityCoefficientRow(0.7947, 0.00092), DensityCoefficientRow(0.7984, 0.00091),
      DensityCoefficientRow(0.8022, 0.00090), DensityCoefficientRow(0.8060, 0.00089),
      DensityCoefficientRow(0.8100, 0.00088), DensityCoefficientRow(0.8141, 0.00087),
      DensityCoefficientRow(0.8179, 0.00086), DensityCoefficientRow(0.8228, 0.00085),
      DensityCoefficientRow(0.8273, 0.00084), DensityCoefficientRow(0.8311, 0.00083),
      DensityCoefficientRow(0.8362, 0.00082), DensityCoefficientRow(0.8425, 0.00081),
      DensityCoefficientRow(0.8484, 0.00080), DensityCoefficientRow(0.8532, 0.00079),
      DensityCoefficientRow(0.8584, 0.00078), DensityCoefficientRow(0.8632, 0.00077),
      DensityCoefficientRow(0.8694, 0.00076), DensityCoefficientRow(0.8775, 0.00075),
      DensityCoefficientRow(0.8856, 0.00074), DensityCoefficientRow(0.8930, 0.00073),
      DensityCoefficientRow(0.9000, 0.00072), DensityCoefficientRow(0.9121, 0.00071),
      DensityCoefficientRow(0.9237, 0.00070), DensityCoefficientRow(0.9345, 0.00069),
      DensityCoefficientRow(0.9398, 0.00068), DensityCoefficientRow(0.9560, 0.00067),
      DensityCoefficientRow(0.9662, 0.00066), DensityCoefficientRow(0.9795, 0.00065),
      DensityCoefficientRow(0.9911, 0.00064)
    )

    fun defaultProcessWasteMeters(): List<ProcessWasteMeter> = listOf(
      ProcessWasteMeter(name = "D-101 to flare", tag = "FI-1022", category = WasteCategory.FLARE),
      ProcessWasteMeter(name = "D-206 to flare", tag = "FI-2023", category = WasteCategory.FLARE),
      ProcessWasteMeter(name = "D-208 to flare", tag = "FI-2054", category = WasteCategory.FLARE),
      ProcessWasteMeter(name = "D-209 to flare", tag = "FI-2036", category = WasteCategory.FLARE),
      ProcessWasteMeter(name = "D-211 to flare", tag = "FI-2063", category = WasteCategory.FLARE),
      ProcessWasteMeter(name = "CRU to flare", tag = "FI-3008", category = WasteCategory.FLARE),
      ProcessWasteMeter(name = "CRU to flare", tag = "FI-3007", category = WasteCategory.FLARE),
      ProcessWasteMeter(name = "D-303 to flare", tag = "FI-3038", category = WasteCategory.FLARE),
      ProcessWasteMeter(name = "D-304 to KHNT", tag = "FI-3013", category = WasteCategory.KNHT_H2),
      ProcessWasteMeter(name = "D-304 to fuel", tag = "FI-3012", category = WasteCategory.FUEL),
      ProcessWasteMeter(name = "D-601 to flare", tag = "FI-6025", category = WasteCategory.FLARE),
      ProcessWasteMeter(name = "D-305 to flare/fuel", tag = "FI-3015", category = WasteCategory.FUEL),
      ProcessWasteMeter(name = "D-307 to fuel", tag = "FI-3026", category = WasteCategory.FUEL)
    )
  }
}

@JsonClass(generateAdapter = true)
data class MovementTransactionEntry(
  val id: String = UUID.randomUUID().toString(),
  val volumeNatural: Double = 0.0,
  val temperatureC: Double? = null,
  val density: Double? = null, // density15 for Feed, density30 for Product
  val note: String = ""
)

@JsonClass(generateAdapter = true)
data class BulkDipEntry(
  val tankId: String,
  val tankName: String,
  val dipMm: Double? = null,
  val waterDipMm: Double? = null,
  val calculatedVolume: Double? = null,
  val waterVolume: Double? = null,
  val netVolume: Double? = null,
  val isInvalid: Boolean = false,
  val calibrationStamp: String? = null
)

@JsonClass(generateAdapter = true)
data class BulkDipSheet(
  val dateStr: String, // "yyyy-MM-dd"
  val entries: Map<String, BulkDipEntry> = emptyMap(), // tankId or tankName -> BulkDipEntry
  val lastModified: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class ProductionReportTankEntry(
  val tankName: String,
  val roleOverride: TankRole? = null,
  val productGroupOverride: String? = null,
  val useVolumeModePrev: Boolean = true,
  val dipMmPrev: Double? = null,
  val volumePrevInput: Double? = null,
  val useVolumeModeToday: Boolean = true,
  val dipMmToday: Double? = null,
  val volumeTodayInput: Double? = null,
  val tempPrevC: Double? = null,
  val tempTodayC: Double? = null,
  val densityPrev15: Double? = null,
  val densityPrev30: Double? = null,
  val densityToday15: Double? = null,
  val densityToday30: Double? = null,
  val dipMm: Double? = null,
  val volumeInput: Double? = null,
  val useVolumeDirectly: Boolean = true,
  val temperatureC: Double? = null,
  val density15: Double? = null,
  val density30: Double? = null,
  val adjustmentL: Double = 0.0,
  val adjustmentNote: String = "",
  val savedNaturalVolume: Double? = null,
  val calibrationVersionStamp: String? = null,
  val useSavedVolumeOverride: Boolean = false,
  val receivedEntries: List<MovementTransactionEntry> = emptyList(),
  val deliveredEntries: List<MovementTransactionEntry> = emptyList(),
  val isFromDipSheetToday: Boolean = false,
  val isFromDipSheetPrev: Boolean = false,
  val dipSheetDipToday: Double? = null,
  val dipSheetDipPrev: Double? = null,
  val volumePrevOverride: Double? = null,
  val tempPrevOverride: Double? = null,
  val densityPrevOverride15: Double? = null,
  val densityPrevOverride30: Double? = null
)

@JsonClass(generateAdapter = true)
data class BlendingUseData(
  val lnHobc: Double = 0.0,
  val lnMs: Double = 0.0,
  val reformateHobc: Double = 0.0,
  val reformateMs: Double = 0.0,
  val lnHobcSource: String = "T-502A/B",
  val lnMsSource: String = "T-502A/B",
  val reformateHobcSource: String = "T-503A/B",
  val reformateMsSource: String = "T-503A/B",
  val isAutoFilledFromTransfers: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ProductDeliveryData(
  val deliveredNatural: Double = 0.0,
  val deliveryTempC: Double = 30.0,
  val delivered30Override: Double? = null,
  val internalNatural: Double = 0.0,
  val internal30Override: Double? = null
)

@JsonClass(generateAdapter = true)
data class RawCategorySplit(
  val tankName: String,
  val condensateExportPct: Double = 100.0,
  val condensateLocalPct: Double = 0.0,
  val naphthaExportPct: Double = 0.0,
  val naphthaLocalPct: Double = 0.0
)

enum class OperatingLoadMode {
  KG_HR,
  BBL_DAY,
  M3_HR
}

@JsonClass(generateAdapter = true)
data class OperatingUnitData(
  val mode: OperatingLoadMode = OperatingLoadMode.KG_HR,
  val flowValue: Double = 0.0,
  val feedDensity: Double = 0.74
)

@JsonClass(generateAdapter = true)
data class OperatingLoads(
  val cfu: OperatingUnitData = OperatingUnitData(flowValue = 40000.0, feedDensity = 0.736),
  val knht: OperatingUnitData = OperatingUnitData(flowValue = 28000.0, feedDensity = 0.790),
  val cru: OperatingUnitData = OperatingUnitData(flowValue = 18000.0, feedDensity = 0.760),
  val cfuBblDay: Double = 10_000.0,
  val knhtBblDay: Double = 8_000.0,
  val cruBblDay: Double = 5_600.0
)

@JsonClass(generateAdapter = true)
data class ProductionReportOtherData(
  val ngcReceivedNatural: Map<String, Double> = emptyMap(), // Tank -> Litres
  val blendingUse: BlendingUseData = BlendingUseData(),
  val productDeliveries: Map<String, ProductDeliveryData> = defaultProductDeliveries(),
  val rawCategorySplits: Map<String, RawCategorySplit> = defaultRawCategorySplits(),
  val rawCategoryReceipts: Map<String, Double> = emptyMap(),
  val processWasteKgH: Map<String, Double> = emptyMap(), // meter.tag or id -> kg/h
  val operatingLoads: OperatingLoads = OperatingLoads(),
  val feedCompositionText: String = "Temane (73%)+ERL(22%)+Local(3%)+DSN(2%)",
  val tanksUsedText: String = "",
  val rawCategoryOpenings: Map<String, Double> = emptyMap(),
  val octaneBalancingOverride: Double? = null,
  val msBalancingOverride: Double? = null
) {
  companion object {
    fun defaultProductDeliveries(): Map<String, ProductDeliveryData> = mapOf(
      "OCTANE" to ProductDeliveryData(),
      "MS" to ProductDeliveryData(),
      "SOLVENT" to ProductDeliveryData(),
      "DIESEL" to ProductDeliveryData(),
      "JET" to ProductDeliveryData(),
      "SKO" to ProductDeliveryData(),
      "LPG" to ProductDeliveryData()
    )

    fun defaultRawCategorySplits(): Map<String, RawCategorySplit> = mapOf(
      "T-501A" to RawCategorySplit("T-501A", condensateExportPct = 75.0, naphthaLocalPct = 25.0, condensateLocalPct = 0.0, naphthaExportPct = 0.0),
      "T-501B" to RawCategorySplit("T-501B", condensateExportPct = 100.0, condensateLocalPct = 0.0, naphthaExportPct = 0.0, naphthaLocalPct = 0.0),
      "T-501C" to RawCategorySplit("T-501C", condensateExportPct = 100.0, condensateLocalPct = 0.0, naphthaExportPct = 0.0, naphthaLocalPct = 0.0),
      "T-501D" to RawCategorySplit("T-501D", condensateExportPct = 70.0, condensateLocalPct = 30.0, naphthaExportPct = 0.0, naphthaLocalPct = 0.0)
    )
  }
}

@JsonClass(generateAdapter = true)
data class ProductionReportDaySnapshot(
  val dateStr: String, // "yyyy-MM-dd" or "dd-MM-yyyy"
  val reportTime: String = "10:00",
  val tankEntries: Map<String, ProductionReportTankEntry> = emptyMap(),
  val otherData: ProductionReportOtherData = ProductionReportOtherData(),
  val lastModified: Long = System.currentTimeMillis(),
  val tableVersionId: String? = null,
  val vccTableSnapshot: List<DensityCoefficientRow>? = null,
  val vcf30TableSnapshot: List<DensityCoefficientRow>? = null,
  val useCurrentTablesOverride: Boolean = false,
  val erpRowConfigsSnapshot: List<ErpRowConfig>? = null,
  val rawMaterialRowConfigsSnapshot: List<RawMaterialRowConfig>? = null,
  val useCurrentErpRowsOverride: Boolean = false
)

// Calculated result classes
data class CalculatedRawTank(
  val tankName: String,
  val productCode: String,
  val previousVolume: Double,
  val todayVolume: Double,
  val received: Double,
  val processed: Double, // Previous - Today + Received
  val coefficient: Double? = null,
  val tempC: Double? = null,
  val density15: Double? = null,
  val inputMode: InputMode = InputMode.DIP,
  val hasAdjustment: Boolean = false,
  val adjustmentL: Double = 0.0
)

data class CalculatedProductTank(
  val tankName: String,
  val productCode: String,
  val subProductDesc: String,
  val previousVolume: Double,
  val todayVolume: Double,
  val changeD: Double, // Today - Previous
  val delivered: Double = 0.0,
  val coefficient: Double? = null,
  val tempC: Double? = null,
  val density30: Double? = null,
  val inputMode: InputMode = InputMode.DIP,
  val hasAdjustment: Boolean = false,
  val adjustmentL: Double = 0.0
)

data class CalculatedBlendingTank(
  val tankName: String,
  val productCode: String, // e.g. "OCTANE" or "MS"
  val productName: String, // e.g. "Octane / HOBC" or "MS (Petrol)"
  val previousVolume: Double,
  val todayVolume: Double,
  val changeD: Double, // Today - Previous
  val delivered: Double = 0.0,
  val internalUsed: Double = 0.0,
  val production: Double = 0.0, // changeD + delivered + internalUsed
  val coefficient: Double? = null,
  val tempC: Double? = null,
  val density30: Double? = null,
  val inputMode: InputMode = InputMode.DIP,
  val hasAdjustment: Boolean = false,
  val adjustmentL: Double = 0.0
)

data class CalculatedProductGroup(
  val productCode: String,
  val productName: String,
  val tanks: List<CalculatedProductTank>,
  val totalTodayVolume: Double,
  val totalPreviousVolume: Double,
  val totalChangeD: Double,
  val usedForBlending: Double,
  val delivered: Double,
  val internalUsed: Double,
  val groupProduction: Double,
  val percentOfFeed: Double
)

data class ProductProductionSummary(
  val productCode: String,
  val productName: String,
  val tankChange: Double,
  val usedForBlending: Double,
  val delivered: Double,
  val internalUsed: Double,
  val productionH: Double,
  val percentOfFeed: Double
)

data class CalculatedWasteItem(
  val id: String,
  val name: String,
  val tag: String,
  val category: WasteCategory,
  val kgH: Double,
  val litres24h: Double,
  val percentOfFeed: Double
) {
  val kgDay: Double get() = kgH * 24.0
}

data class ErpRowData(
  val itemCode: String,
  val itemName: String,
  val openingStock: Double,
  val productionOrReceived: Double,
  val usedForBlending: Double,
  val delivered: Double,
  val internalUseOrEvap: Double,
  val closingStock: Double,
  val physicalStock: Double,
  val difference: Double,
  val isRaw: Boolean = false,
  val rowType: ErpRowType = ErpRowType.STANDARD,
  val assignedTankNames: List<String> = emptyList(),
  val note: String = ""
)

data class ProductionReportCalculationResult(
  val basis: CalculationBasis,
  val reportDate: String,
  val previousDate: String,
  val durationText: String,
  val rawTanks: List<CalculatedRawTank>,
  val totalFeed: Double,
  val productTanks: List<CalculatedProductTank>,
  val productSummaries: List<ProductProductionSummary>,
  val productSubTotal: Double,
  val soldInternalSubTotal: Double,
  val totalProduced: Double,
  val totalLoss: Double,
  val lossPercent: Double,
  val wasteItems: List<CalculatedWasteItem>,
  val totalMeasuredLossPct: Double,
  val unmeasuredLossPct: Double,
  val fuelForHeatersPct: Double,
  val h2MakeUpKnhtPct: Double,
  val actualFlaringPct: Double,
  val isWasteEntered: Boolean = true,
  val totalWasteKgH: Double = 0.0,
  val totalWasteKgDay: Double = 0.0,
  val totalWasteLitres: Double = 0.0,
  val dryOutStockL: Double,
  val dryOutConsumableL: Double,
  val dryOutDays: Double,
  val dryOutDateStr: String,
  val erpRows: List<ErpRowData> = emptyList(),
  val plantEfficiency: Map<String, Double> = emptyMap(),
  val warnings: List<String> = emptyList(),
  val missingTanks: List<String> = emptyList(),
  val isBlocked: Boolean = false,
  val totalRawToday: Double = 0.0,
  val totalRawPrevious: Double = 0.0,
  val totalRawReceived: Double = 0.0,
  val productGroups: List<CalculatedProductGroup> = emptyList(),
  val totalProductToday: Double = 0.0,
  val totalProductPrevious: Double = 0.0,
  val blendingTanks: List<CalculatedBlendingTank> = emptyList(),
  val totalBlendingToday: Double = 0.0,
  val totalBlendingPrevious: Double = 0.0,
  val totalBlendingDelivered: Double = 0.0,
  val totalBlendingProduction: Double = 0.0,
  // Option A & Option B dual loss
  val totalProductA: Double = 0.0,
  val yieldOptionA: Double = 0.0,
  val lossOptionA: Double = 0.0,
  val lossPercentOptionA: Double = 0.0,
  val totalChangeB: Double = 0.0,
  val yieldOptionB: Double = 0.0,
  val lossOptionB: Double = 0.0,
  val lossPercentOptionB: Double = 0.0,
  val diffAB: Double = 0.0,
  val blendingTanksNet: Double = 0.0,
  val isDiffExplainedByBlending: Boolean = true,
  val diffExplanation: String = "",
  val actualFlaringPctOptionA: Double = 0.0,
  val actualFlaringPctOptionB: Double = 0.0,
  val tovChangeProductTanks: Double = 0.0,
  val tovChangeBlendingTanks: Double = 0.0,
  val tovChangeAllTanks: Double = 0.0,
  val totalProducedOptionB: Double = 0.0,
  val productYieldOptionA: Double = 0.0,
  val productYieldOptionB: Double = 0.0,
  val diffLossMethods: Double = 0.0
)

