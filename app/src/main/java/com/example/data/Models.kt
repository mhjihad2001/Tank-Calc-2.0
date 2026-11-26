package com.example.data

import java.util.UUID

enum class InputMode { DIP, VOLUME }

enum class CoefficientSourceType {
  VCF30_BY_DENSITY15,
  VCC_BY_DENSITY30,
  NONE
}

enum class TankType { FEED, PRODUCT, BLENDING, N2_STORAGE }
enum class TankShape { VERTICAL, HORIZONTAL }

enum class DipCalibrationMode {
  AUTO,
  BASE_10MM_PLUS_FRACTION,
  DIRECT_1MM
}

data class DipRow(
  val dipMm: Double,
  val volumes: Map<String, Double> // TankName -> Volume in Litres
)

data class FractionRow(
  val mm: Int, // 0 to 9
  val volumes: Map<String, Double> // TankName -> Volume in Litres
)

data class N2Row(
  val heightMm: Double,
  val volumes: Map<String, Double> // TankName -> Volume
)

data class Tank(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val type: TankType,
  val shape: TankShape = TankShape.VERTICAL,
  val maxVolumeLiters: Double = 100000.0,
  val dipTable: List<DipRow> = emptyList(),
  val fractionTable: List<FractionRow> = emptyList(),
  val n2Table: List<N2Row> = emptyList(),
  val productId: String? = null,
  val blendedProductId: String? = null,
  val includeInProductionReport: Boolean = true,
  val coefficientSource: CoefficientSourceType = CoefficientSourceType.VCF30_BY_DENSITY15,
  val defaultInputMode: InputMode = InputMode.DIP,
  val reportOrder: Int = 0,
  val dipCalibrationMode: DipCalibrationMode = DipCalibrationMode.AUTO
) {
  val linkedProductId: String get() = productId ?: blendedProductId ?: ""
}

enum class ProductCategory { PRODUCT, INTERMEDIATE }

data class Product(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val shortCode: String,
  val category: ProductCategory = ProductCategory.PRODUCT,
  val defaultDensity15: Double? = null,
  val colorHex: String = "#1565C0",
  val isActive: Boolean = true,
  val tankIds: List<String> = emptyList(),
  val erpReportGroup: String = "Other",
  val groupOrder: Int = 0
)

data class ProductCandidate(
  val source: String,
  val productId: String,
  val productName: String,
  val shortCode: String
)

data class LinkConflict(
  val tank: Tank,
  val candidates: List<ProductCandidate>,
  val defaultChoiceProductId: String?
)

data class DiagnosticsReport(
  val tanksWithoutProduct: List<Tank>,
  val productsWithoutTanks: List<Product>,
  val staleTankLinks: List<Pair<Tank, String>>,
  val staleProductLinks: List<Pair<Product, String>>
)

data class BlendingLine(
  val id: String = UUID.randomUUID().toString(),
  val amount: Double = 0.0,
  val destinationTankName: String? = null,
  val time: String? = null
)

data class ProductYieldEntry(
  val productId: String? = null,
  val productName: String,
  val shortCode: String,
  val colorHex: String,
  val category: ProductCategory = ProductCategory.PRODUCT,
  val tankNames: List<String> = emptyList(),
  val productMade: Double = 0.0,
  val percentOfFeed: Double? = null,
  val percentOfTotalProduct: Double = 0.0,
  val tankBreakdown: Map<String, Double> = emptyMap()
)

data class FeedTankRatioEntry(
  val tankName: String,
  val feedConsumed: Double = 0.0,
  val percentOfTotalFeed: Double? = null
)

enum class TransactionType { RECEIVE, DELIVERY, BLEND }
enum class CalculationMode { NORMAL, TEMP_30C }

data class TransactionRecord(
  val id: String = UUID.randomUUID().toString(),
  val tankName: String,
  val type: TransactionType,
  val productName: String,
  val dateTime: String,
  val initialDip: Double,
  val finalDip: Double,
  val initialVolume: Double,
  val finalVolume: Double,
  val quantity: Double,
  val mode: CalculationMode,
  val density15: Double? = null,
  val initialTemp: Double? = null,
  val finalTemp: Double? = null,
  val vcfNatural: Double? = null,
  val vcf30: Double? = null,
  val volume15: Double? = null,
  val volume30: Double? = null
)

data class PeriodReceiptOrDelivery(
  val id: String = UUID.randomUUID().toString(),
  val quantity: Double,
  val time: String
)

data class LossTankEntry(
  val tankName: String,
  val initialTov: Double,
  val finalTov: Double,
  val receivingAmount: Double = 0.0,
  val blendingAmount: Double = 0.0,
  val deliveryAmount: Double = 0.0,
  val density15: Double? = null,
  val initialTemp: Double? = null,
  val finalTemp: Double? = null,
  val initialVcf: Double? = null,
  val finalVcf: Double? = null,
  val initialTov30: Double? = null,
  val finalTov30: Double? = null,
  val netQuantity: Double = 0.0,
  val isInitialSynced: Boolean = false,
  val isFinalSynced: Boolean = false,
  val blendingLines: List<BlendingLine> = emptyList(),
  val effectiveInitialTov: Double? = null,
  val effectiveInitialTov30: Double? = null,
  val blendedInAmount: Double = 0.0,
  val blendedInSources: List<String> = emptyList(),
  val effectiveInitialNote: String? = null,
  val productId: String? = null,
  val productName: String? = null,
  val productShortCode: String? = null,
  val productColorHex: String? = null
)

data class LossReport(
  val id: String = UUID.randomUUID().toString(),
  val mode: CalculationMode = CalculationMode.NORMAL,
  val startTime: String,
  val endTime: String,
  val durationHours: Double,
  val feedTanks: List<LossTankEntry>,
  val productTanks: List<LossTankEntry>,
  val blendingTanks: List<LossTankEntry> = emptyList(),
  val totalFeed: Double,
  val totalProduct: Double,
  val loss: Double,
  val lossPercent: Double,
  val yieldPercent: Double,
  val feedRatePerHour: Double,
  val productRatePerHour: Double,
  val lossRatePerHour: Double,
  val flag: String, // GREEN, AMBER, RED
  val timestamp: String,
  val productYields: List<ProductYieldEntry> = emptyList(),
  val feedTankRatios: List<FeedTankRatioEntry> = emptyList(),
  val totalProductPercent: Double? = null,
  val lossPercentOnFeed: Double? = null,
  val productTankLinksSnapshot: Map<String, String> = emptyMap(),
  val warnings: List<String> = emptyList()
)

data class TankReadingLogEntry(
  val id: String = UUID.randomUUID().toString(),
  val tankName: String,
  val timestamp: String,
  val tov: Double,
  val density15: Double? = null,
  val temp: Double? = null,
  val reportId: String? = null
)

enum class UnitType { LITRES, M3, BBL }
enum class VcfTableType { TABLE_54A, TABLE_54B, TABLE_54D }
enum class DensityRefTemp { AT_15C, AT_30C }
enum class TempCorrectionMethod { VCF_ASTM, VCC_TABLE, VCF30_TABLE }

data class VcfBandParam(
  val id: String,
  val tableType: VcfTableType,
  val bandName: String,
  val minDensity15: Double,
  val maxDensity15: Double,
  val k0: Double,
  val k1: Double,
  val k2Offset: Double = 0.0
)

data class AppSettings(
  val unit: UnitType = UnitType.LITRES,
  val decimalPlacesL: Int = 0,
  val decimalPlacesM3: Int = 3,
  val decimalPlacesBbl: Int = 2,
  val decimalPlaces: Int = 2,
  val vcfTableType: VcfTableType = VcfTableType.TABLE_54B,
  val roundVcfTo4Decimals: Boolean = true,
  val roundTempTo025: Boolean = false,
  val invalidInputThreshold: Double = 1.0,
  val lossThresholdPercent: Double = 1.5,
  val passwordHash: String = "b328131362e84897f9035a9ea2ff12ebbf7e8284534f3b018598a27ee2ecfa19",
  val customConstants: Map<String, Double> = emptyMap(),
  val lastBackupTimestamp: Long = 0L,
  val plantName: String = "Condensate Fractionation Plant",
  val companyName: String = "PARTEX PETRO LTD.",
  val erpToleranceL: Double = 5.0,
  val signaturePreparedBy: String = "Shift Engineer",
  val signatureCheckedBy: String = "Operations Manager",
  val signatureApprovedBy: String = "Plant General Manager",
  val performanceMode: Boolean = false,
  val yardSunlightMode: Boolean = false,
  val dipOutlierThresholdMm: Double = 500.0,
  val savedCloudBackupUrl: String = "",
  val savedCloudBackupLabel: String = "",
  val lastCloudSyncTimestamp: Long = 0L,
  val hapticsEnabled: Boolean = true,
  val hapticIntensity: Int = 2, // 1 = Light, 2 = Medium (Industrial), 3 = Strong (Field Glove)
  val useInAppKeyboard: Boolean = true,
  val customUpdateManifestUrl: String = "",
  val densityInputRefTemp: DensityRefTemp = DensityRefTemp.AT_15C,
  val tempCorrectionMethod: TempCorrectionMethod = TempCorrectionMethod.VCF_ASTM,
  val customVcfBandParams: List<VcfBandParam> = emptyList()
)

data class BlendStage(
  val id: String = UUID.randomUUID().toString(),
  val stageName: String,
  val componentName: String = "",
  val orderIndex: Int = 0,
  val productId: String? = null
)

data class BlendProgram(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val tankName: String,
  val stages: List<BlendStage> = emptyList(),
  val isDefault: Boolean = false
)

data class BlendStageReading(
  val stageId: String,
  val stageName: String,
  val componentName: String = "",
  val isVolumeInput: Boolean = false,
  val dipMm: Double = 0.0,
  val volumeNaturalL: Double = 0.0,
  val dateTime: String = "",
  val density15: Double? = null,
  val temp: Double? = null,
  val vcfNatural: Double? = null,
  val vcf30: Double? = null,
  val volume30L: Double? = null
)

data class BlendComponentResult(
  val stageName: String,
  val componentName: String,
  val transferredL: Double,
  val naturalTransferredL: Double,
  val transferred30L: Double? = null,
  val warning: String? = null
)

data class BlendRecord(
  val id: String = UUID.randomUUID().toString(),
  val programId: String,
  val programName: String,
  val tankName: String,
  val dateTime: String,
  val mode: CalculationMode = CalculationMode.NORMAL,
  val stageReadings: List<BlendStageReading> = emptyList(),
  val finalVolumeL: Double = 0.0,
  val finalVolume30L: Double? = null,
  val componentTransfers: List<BlendComponentResult> = emptyList(),
  val batchNumber: String = "",
  val remarks: String = ""
)

data class EditLogEntry(
  val id: String = UUID.randomUUID().toString(),
  val timestamp: String,
  val action: String,
  val targetName: String,
  val details: String
)

data class DipCalculationResult(
  val inputDip: Double,
  val effectiveDip: Double,
  val baseDip: Int,
  val baseVolume: Double,
  val fracMm: Int,
  val fractionVolume: Double,
  val totalVolume: Double,
  val capacity: Double = 100000.0,
  val ullage: Double = 0.0,
  val percentFull: Double = 0.0,
  val warning: String? = null,
  val interpolationNote: String? = null,
  val invalidInput: Boolean = false,
  val usedDirectMmLookup: Boolean = false
) {
  val fracVolume: Double get() = fractionVolume
}

