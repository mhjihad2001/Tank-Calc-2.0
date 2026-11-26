package com.example.engine

import com.example.data.DensityCoefficientRow
import com.example.data.DensityRefTemp
import com.example.data.ProductionReportSetup
import com.example.data.TempCorrectionMethod
import com.example.data.VcfBandParam
import com.example.data.VcfTableType
import kotlin.math.abs
import kotlin.math.exp

data class VcfResult(
  val vcf: Double,
  val alpha15: Double,
  val dT: Double,
  val bandName: String,
  val warning: String?
)

/**
 * Side-by-side 30°C temperature correction result comparing ASTM VCF, VCC Table (@30°C density),
 * and Refinery VCF30 Table (@15°C density), with resolved @15°C & @30°C densities and deviation metrics.
 */
data class DualTempCorrectionResult(
  val observedVolume: Double,
  val observedTemp: Double,
  val inputDensityRefTemp: DensityRefTemp,
  val rho15KgM3: Double,
  val rho30KgM3: Double,
  // 1. ASTM VCF (D1250-80)
  val vcfObserved: VcfResult,
  val vcfAt30: VcfResult,
  val volume15Vcf: Double,
  val volume30Vcf: Double,
  // 2. VCC Table (Linear coefficient by Density @30°C)
  val vccCoefficient: Double,
  val vccMatchedBoundKgL: Double?,
  val vccMultiplier: Double,
  val volume30Vcc: Double,
  // 3. VCF30 Table (Linear coefficient by Density @15°C)
  val vcf30Coefficient: Double,
  val vcf30MatchedBoundKgL: Double?,
  val vcf30Multiplier: Double,
  val volume30Vcf30Table: Double,
  // Deviation between ASTM VCF and VCC Table
  val deviationVcfVsVccL: Double,
  val deviationVcfVsVccPercent: Double,
  val deviationStatus: String,
  // Selected / active acceptable method volume
  val selectedMethod: TempCorrectionMethod,
  val selectedVolume30: Double
) {
  fun volumeForMethod(method: TempCorrectionMethod): Double = when (method) {
    TempCorrectionMethod.VCF_ASTM -> volume30Vcf
    TempCorrectionMethod.VCC_TABLE -> volume30Vcc
    TempCorrectionMethod.VCF30_TABLE -> volume30Vcf30Table
  }
}

object VcfEngine {

  /**
   * Standard ASTM D1250-80 K0 / K1 / K2Offset band parameters — exposed as editable defaults
   * so refinery engineers can tune or audit ASTM VCF constants if needed.
   */
  fun defaultVcfBandParams(): List<VcfBandParam> = listOf(
    VcfBandParam(
      id = "54A_CRUDE",
      tableType = VcfTableType.TABLE_54A,
      bandName = "Table 54A (Crude Oil)",
      minDensity15 = 653.0,
      maxDensity15 = 1074.0,
      k0 = 613.9723,
      k1 = 0.0,
      k2Offset = 0.0
    ),
    VcfBandParam(
      id = "54B_GASOLINE",
      tableType = VcfTableType.TABLE_54B,
      bandName = "Table 54B - Gasoline Range",
      minDensity15 = 653.0,
      maxDensity15 = 770.5,
      k0 = 346.4228,
      k1 = 0.4388,
      k2Offset = 0.0
    ),
    VcfBandParam(
      id = "54B_TRANSITION",
      tableType = VcfTableType.TABLE_54B,
      bandName = "Table 54B - Transition Range",
      minDensity15 = 770.5001,
      maxDensity15 = 787.4999,
      k0 = 2680.3206,
      k1 = 0.0,
      k2Offset = -0.00336312
    ),
    VcfBandParam(
      id = "54B_JET",
      tableType = VcfTableType.TABLE_54B,
      bandName = "Table 54B - Jet / Kerosene",
      minDensity15 = 787.5,
      maxDensity15 = 838.5,
      k0 = 594.5418,
      k1 = 0.0,
      k2Offset = 0.0
    ),
    VcfBandParam(
      id = "54B_FUELOIL",
      tableType = VcfTableType.TABLE_54B,
      bandName = "Table 54B - Fuel Oil / Diesel",
      minDensity15 = 838.5001,
      maxDensity15 = 1163.5,
      k0 = 186.9696,
      k1 = 0.4862,
      k2Offset = 0.0
    ),
    VcfBandParam(
      id = "54D_LUBE",
      tableType = VcfTableType.TABLE_54D,
      bandName = "Table 54D (Lubricating Oils)",
      minDensity15 = 800.0,
      maxDensity15 = 1163.0,
      k0 = 0.0,
      k1 = 0.0,
      k2Offset = 0.00045
    )
  )

  fun effectiveBandParams(customBands: List<VcfBandParam>): List<VcfBandParam> =
    if (customBands.isEmpty()) defaultVcfBandParams() else customBands

  /**
   * Accepts density entered in either kg/L (e.g. 0.745) or kg/m³ (e.g. 745.0)
   * and normalizes cleanly to kg/m³.
   */
  fun normalizeDensityKgM3(rawDensity: Double): Double {
    if (!rawDensity.isFinite() || rawDensity <= 0.0) return 750.0
    return if (rawDensity in 0.1..2.5) rawDensity * 1000.0 else rawDensity
  }

  /**
   * Accepts density in either kg/L or kg/m³ and normalizes cleanly to kg/L (g/mL).
   */
  fun normalizeDensityKgL(rawDensity: Double): Double {
    if (!rawDensity.isFinite() || rawDensity <= 0.0) return 0.750
    return if (rawDensity > 2.5) rawDensity / 1000.0 else rawDensity
  }

  fun calculateVcf(
    rho15: Double, // Density at 15 °C (kg/m3 or kg/L auto-normalized)
    observedTemp: Double, // Temperature in °C
    tableType: VcfTableType = VcfTableType.TABLE_54B,
    round4: Boolean = true,
    roundTemp025: Boolean = false,
    customBands: List<VcfBandParam> = emptyList()
  ): VcfResult {
    val rho15Norm = normalizeDensityKgM3(rho15)
    val t = if (roundTemp025) {
      Math.round(observedTemp * 4.0) / 4.0
    } else {
      observedTemp
    }

    val dT = t - 15.0
    val bandsForTable = effectiveBandParams(customBands).filter { it.tableType == tableType }
    val matchedBand = bandsForTable.firstOrNull { rho15Norm >= it.minDensity15 && rho15Norm <= it.maxDensity15 }
    val activeBand = matchedBand ?: bandsForTable.firstOrNull()

    var warning: String? = null
    val bandName: String
    val alpha15: Double

    if (activeBand != null) {
      if (matchedBand == null) {
        val minAll = bandsForTable.minOfOrNull { it.minDensity15 } ?: 653.0
        val maxAll = bandsForTable.maxOfOrNull { it.maxDensity15 } ?: 1163.5
        warning = "Density ${String.format("%.1f", rho15Norm)} is outside ${tableType.name.replace("_", " ")} range ($minAll - $maxAll kg/m³)."
        bandName = "${activeBand.bandName} (Out of Range)"
      } else {
        bandName = "${activeBand.bandName} (${activeBand.minDensity15} - ${activeBand.maxDensity15})"
      }
      alpha15 = activeBand.k2Offset +
        (activeBand.k0 / (rho15Norm * rho15Norm)) +
        (activeBand.k1 / rho15Norm)
    } else {
      bandName = "Table 54B - Generalized"
      alpha15 = 346.4228 / (rho15Norm * rho15Norm) + 0.4388 / rho15Norm
    }

    // VCF formula: exp( -alpha15 * dT * (1 + 0.8 * alpha15 * dT) )
    val exponent = -alpha15 * dT * (1.0 + 0.8 * alpha15 * dT)
    var vcf = exp(exponent)

    if (round4) {
      vcf = Math.round(vcf * 10000.0) / 10000.0
    }

    return VcfResult(
      vcf = vcf,
      alpha15 = alpha15,
      dT = dT,
      bandName = bandName,
      warning = warning
    )
  }

  /**
   * Converts Density @15°C (kg/m³) -> Density @30°C (kg/m³) via unrounded ASTM VCF(30).
   */
  fun convertDensity15To30(
    rho15Input: Double,
    tableType: VcfTableType = VcfTableType.TABLE_54B,
    customBands: List<VcfBandParam> = emptyList()
  ): Double {
    val rho15 = normalizeDensityKgM3(rho15Input)
    val vcf30Unrounded = calculateVcf(
      rho15 = rho15,
      observedTemp = 30.0,
      tableType = tableType,
      round4 = false,
      roundTemp025 = false,
      customBands = customBands
    ).vcf
    return rho15 * vcf30Unrounded
  }

  /**
   * Converts Density @30°C (kg/m³) -> Density @15°C (kg/m³) via fixed-point iteration
   * solving rho30 = rho15 * VCF(rho15, 30°C). Converges to < 1e-8 kg/m³ within 10 iterations.
   */
  fun convertDensity30To15(
    rho30Input: Double,
    tableType: VcfTableType = VcfTableType.TABLE_54B,
    customBands: List<VcfBandParam> = emptyList()
  ): Double {
    val rho30 = normalizeDensityKgM3(rho30Input)
    var guess = rho30 / 0.985
    repeat(12) {
      val vcf30 = calculateVcf(
        rho15 = guess,
        observedTemp = 30.0,
        tableType = tableType,
        round4 = false,
        roundTemp025 = false,
        customBands = customBands
      ).vcf
      val next = if (vcf30 > 0.5) rho30 / vcf30 else guess
      if (abs(next - guess) < 1e-8) return next
      guess = next
    }
    return guess
  }

  /**
   * Resolves both (rho15KgM3, rho30KgM3) from whichever reference temperature the user entered.
   */
  fun resolveBothDensities(
    inputDensity: Double,
    refTemp: DensityRefTemp,
    tableType: VcfTableType = VcfTableType.TABLE_54B,
    customBands: List<VcfBandParam> = emptyList()
  ): Pair<Double, Double> {
    val normalized = normalizeDensityKgM3(inputDensity)
    return when (refTemp) {
      DensityRefTemp.AT_15C -> {
        val rho30 = convertDensity15To30(normalized, tableType, customBands)
        Pair(normalized, rho30)
      }
      DensityRefTemp.AT_30C -> {
        val rho15 = convertDensity30To15(normalized, tableType, customBands)
        Pair(rho15, normalized)
      }
    }
  }

  /**
   * Computes 30°C corrected volumes simultaneously using:
   * 1. ASTM D1250-80 VCF (exponential formula with editable K0/K1 band constants)
   * 2. Refinery VCC Table (linear coefficient looked up by Density @30°C)
   * 3. Refinery VCF30 Table (linear coefficient looked up by Density @15°C)
   * and returns the exact deviation between VCF and VCC along with the selected acceptable volume.
   */
  fun calculateDual30C(
    observedVolume: Double,
    observedTemp: Double,
    inputDensity: Double,
    densityRefTemp: DensityRefTemp = DensityRefTemp.AT_15C,
    tableType: VcfTableType = VcfTableType.TABLE_54B,
    round4: Boolean = true,
    roundTemp025: Boolean = false,
    vccTable: List<DensityCoefficientRow> = ProductionReportSetup.defaultVccTable(),
    vcf30Table: List<DensityCoefficientRow> = ProductionReportSetup.defaultVcf30Table(),
    customBands: List<VcfBandParam> = emptyList(),
    preferredMethod: TempCorrectionMethod = TempCorrectionMethod.VCF_ASTM
  ): DualTempCorrectionResult {
    val (rho15KgM3, rho30KgM3) = resolveBothDensities(inputDensity, densityRefTemp, tableType, customBands)
    val rho15KgL = rho15KgM3 / 1000.0
    val rho30KgL = rho30KgM3 / 1000.0
    val effectiveTemp = if (roundTemp025) Math.round(observedTemp * 4.0) / 4.0 else observedTemp

    // 1. ASTM VCF calculation
    val vcfObs = calculateVcf(rho15KgM3, observedTemp, tableType, round4, roundTemp025, customBands)
    val vcf30 = calculateVcf(rho15KgM3, 30.0, tableType, round4, roundTemp025, customBands)
    val vol15Vcf = observedVolume * vcfObs.vcf
    val vol30Vcf = if (vcf30.vcf > 0.0) observedVolume * vcfObs.vcf / vcf30.vcf else observedVolume

    // 2. VCC Table calculation (Density @30°C in kg/L)
    val activeVcc = if (vccTable.isEmpty()) ProductionReportSetup.defaultVccTable() else vccTable
    val (vccCoeff, vccRow) = ProductionReportEngine.findCoefficient(rho30KgL, activeVcc)
    val vccMult = 1.0 - vccCoeff * (effectiveTemp - 30.0)
    val vol30Vcc = observedVolume * vccMult

    // 3. VCF30 Table calculation (Density @15°C in kg/L)
    val activeVcf30 = if (vcf30Table.isEmpty()) ProductionReportSetup.defaultVcf30Table() else vcf30Table
    val (vcf30Coeff, vcf30Row) = ProductionReportEngine.findCoefficient(rho15KgL, activeVcf30)
    val vcf30Mult = 1.0 - vcf30Coeff * (effectiveTemp - 30.0)
    val vol30Vcf30 = observedVolume * vcf30Mult

    // Deviation between ASTM VCF and VCC Table
    val diffL = vol30Vcf - vol30Vcc
    val denom = maxOf(abs(vol30Vcf), abs(vol30Vcc), 1.0)
    val devPct = if (observedVolume == 0.0) 0.0 else (abs(diffL) / denom) * 100.0
    val devStatus = when {
      devPct <= 0.05 -> "Tight Match (<= 0.05%)"
      devPct <= 0.20 -> "Minor Deviation (0.05-0.20%)"
      else -> "High Deviation (> 0.20%)"
    }

    val selectedVol = when (preferredMethod) {
      TempCorrectionMethod.VCF_ASTM -> vol30Vcf
      TempCorrectionMethod.VCC_TABLE -> vol30Vcc
      TempCorrectionMethod.VCF30_TABLE -> vol30Vcf30
    }

    return DualTempCorrectionResult(
      observedVolume = observedVolume,
      observedTemp = effectiveTemp,
      inputDensityRefTemp = densityRefTemp,
      rho15KgM3 = rho15KgM3,
      rho30KgM3 = rho30KgM3,
      vcfObserved = vcfObs,
      vcfAt30 = vcf30,
      volume15Vcf = vol15Vcf,
      volume30Vcf = vol30Vcf,
      vccCoefficient = vccCoeff,
      vccMatchedBoundKgL = vccRow?.lowerBound,
      vccMultiplier = vccMult,
      volume30Vcc = vol30Vcc,
      vcf30Coefficient = vcf30Coeff,
      vcf30MatchedBoundKgL = vcf30Row?.lowerBound,
      vcf30Multiplier = vcf30Mult,
      volume30Vcf30Table = vol30Vcf30,
      deviationVcfVsVccL = diffL,
      deviationVcfVsVccPercent = devPct,
      deviationStatus = devStatus,
      selectedMethod = preferredMethod,
      selectedVolume30 = selectedVol
    )
  }
}
