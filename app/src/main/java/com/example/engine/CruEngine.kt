package com.example.engine

import com.example.data.*
import java.util.Locale

data class ChangeEstimatorResult(
  val whsvDelta: Double,
  val pressureDelta: Double,
  val ronDelta: Double,
  val anDelta: Double,
  val mabpDelta: Double,
  val totalWaitDelta: Double,
  val targetWait: Double?,
  val whsvWarn: Boolean,
  val pressureWarn: Boolean,
  val ronWarn: Boolean,
  val anWarn: Boolean,
  val mabpWarn: Boolean,
  val fWhsvBase: Double,
  val fWhsvNew: Double,
  val fPressBase: Double,
  val fPressNew: Double,
  val fRonBase: Double,
  val fRonNew: Double,
  val fAnBase: Double,
  val fAnNew: Double,
  val fMabpBase: Double,
  val fMabpNew: Double
) {
  val deltaWhsv: Double get() = whsvDelta
  val deltaPressure: Double get() = pressureDelta
  val deltaRon: Double get() = ronDelta
  val deltaNaphthenesAromatics: Double get() = anDelta
  val deltaMabp: Double get() = mabpDelta
  val totalDeltaWait: Double get() = totalWaitDelta

  val warnings: List<String>
    get() {
      val list = mutableListOf<String>()
      if (whsvWarn) list.add("WHSV outside Fig 16 chart range")
      if (pressureWarn) list.add("Pressure outside Fig 17 chart range")
      if (ronWarn) list.add("RON outside Fig 18 chart range")
      if (anWarn) list.add("A+0.85N outside Fig 19 chart range")
      if (mabpWarn) list.add("MABP outside Fig 20 chart range")
      return list
    }
}

data class SorWaitActivityResult(
  val sorWaitBase: Double,
  val f17PressActual: Double,
  val f17PressRef: Double,
  val pressCorrection: Double,
  val f16WhsvActual: Double,
  val f16WhsvRef: Double,
  val whsvCorrection: Double,
  val f20MabpActual: Double,
  val f20MabpRef: Double,
  val mabpCorrection: Double,
  val correctedSorWait: Double,
  val activityLoss: Double,
  val isOutOfRange: Boolean,
  val warnings: List<String>,
  val actualWait: Double = 0.0
) {
  val deltaPressure: Double get() = pressCorrection
  val deltaWhsv: Double get() = whsvCorrection
  val deltaMabp: Double get() = mabpCorrection
}

object CruEngine {

  const val BBL_TO_M3 = 0.158987295

  fun pressureToKgCm2(value: Double, unit: PressureUnit): Double {
    return when (unit) {
      PressureUnit.KG_CM2_G -> value
      PressureUnit.BAR_G -> value * 1.0197162129779282
      PressureUnit.PSIG -> value * 0.070306957964
    }
  }

  fun pressureFromKgCm2(valueKgCm2: Double, targetUnit: PressureUnit): Double {
    return when (targetUnit) {
      PressureUnit.KG_CM2_G -> valueKgCm2
      PressureUnit.BAR_G -> valueKgCm2 / 1.0197162129779282
      PressureUnit.PSIG -> valueKgCm2 / 0.070306957964
    }
  }

  fun linearInterpolate(x: Double, xList: List<Double>, yList: List<Double>): Pair<Double, Boolean> {
    if (xList.isEmpty() || yList.isEmpty()) return Pair(0.0, false)
    if (xList.size == 1) return Pair(yList[0], false)
    val isClamped = x < xList.first() || x > xList.last()
    val xClamped = x.coerceIn(xList.first(), xList.last())

    for (i in 0 until xList.size - 1) {
      val x0 = xList[i]
      val x1 = xList[i + 1]
      if (xClamped in x0..x1 || (x0 == x1)) {
        val y0 = yList[i]
        val y1 = yList[i + 1]
        val fraction = if (x1 != x0) (xClamped - x0) / (x1 - x0) else 0.0
        val y = y0 + fraction * (y1 - y0)
        return Pair(y, isClamped)
      }
    }
    return Pair(yList.last(), isClamped)
  }

  fun calculateFig22(ron: Double, an: Double, tables: CruLookupTables): Pair<Double, Boolean> {
    val (y25, clamp25) = linearInterpolate(ron, tables.fig22RonX, tables.fig22An25)
    val (y35, clamp35) = linearInterpolate(ron, tables.fig22RonX, tables.fig22An35)
    val (y45, clamp45) = linearInterpolate(ron, tables.fig22RonX, tables.fig22An45)
    val (y55, clamp55) = linearInterpolate(ron, tables.fig22RonX, tables.fig22An55)

    val ronClamped = clamp25 || clamp35 || clamp45 || clamp55
    val anClamped = an < 25.0 || an > 55.0
    val anEff = an.coerceIn(25.0, 55.0)

    val sorWait = when {
      anEff <= 35.0 -> {
        val frac = (anEff - 25.0) / (35.0 - 25.0)
        y25 + frac * (y35 - y25)
      }
      anEff <= 45.0 -> {
        val frac = (anEff - 35.0) / (45.0 - 35.0)
        y35 + frac * (y45 - y35)
      }
      else -> {
        val frac = (anEff - 45.0) / (55.0 - 45.0)
        y45 + frac * (y55 - y45)
      }
    }
    return Pair(sorWait, ronClamped || anClamped)
  }

  fun calculateChangeEstimator(
    baseWhsv: Double, newWhsv: Double, useWhsv: Boolean,
    basePressureKgCm2: Double, newPressureKgCm2: Double, usePressure: Boolean,
    baseRon: Double, newRon: Double, useRon: Boolean,
    baseAn: Double, newAn: Double, useAn: Boolean,
    baseMabp: Double, newMabp: Double, useMabp: Boolean,
    currentWait: Double?,
    tables: CruLookupTables
  ): ChangeEstimatorResult {
    val (fWhsvBase, wClampBase) = linearInterpolate(baseWhsv, tables.fig16WhsvX, tables.fig16WhsvY)
    val (fWhsvNew, wClampNew) = linearInterpolate(newWhsv, tables.fig16WhsvX, tables.fig16WhsvY)
    val whsvDelta = fWhsvNew - fWhsvBase

    val (fPressBase, pClampBase) = linearInterpolate(basePressureKgCm2, tables.fig17PressureX, tables.fig17PressureY)
    val (fPressNew, pClampNew) = linearInterpolate(newPressureKgCm2, tables.fig17PressureX, tables.fig17PressureY)
    val pressDelta = fPressNew - fPressBase

    val (fRonBase, rClampBase) = linearInterpolate(baseRon, tables.fig18RonX, tables.fig18RonY)
    val (fRonNew, rClampNew) = linearInterpolate(newRon, tables.fig18RonX, tables.fig18RonY)
    val ronDelta = fRonNew - fRonBase

    val (fAnBase, anClampBase) = linearInterpolate(baseAn, tables.fig19AnX, tables.fig19AnY)
    val (fAnNew, anClampNew) = linearInterpolate(newAn, tables.fig19AnX, tables.fig19AnY)
    val anDelta = fAnNew - fAnBase

    val (fMabpBase, mClampBase) = linearInterpolate(baseMabp, tables.fig20MabpX, tables.fig20MabpY)
    val (fMabpNew, mClampNew) = linearInterpolate(newMabp, tables.fig20MabpX, tables.fig20MabpY)
    val mabpDelta = fMabpNew - fMabpBase

    var totalDelta = 0.0
    if (useWhsv) totalDelta += whsvDelta
    if (usePressure) totalDelta += pressDelta
    if (useRon) totalDelta += ronDelta
    if (useAn) totalDelta += anDelta
    if (useMabp) totalDelta += mabpDelta

    val targetWait = currentWait?.let { it + totalDelta }

    return ChangeEstimatorResult(
      whsvDelta = whsvDelta,
      pressureDelta = pressDelta,
      ronDelta = ronDelta,
      anDelta = anDelta,
      mabpDelta = mabpDelta,
      totalWaitDelta = totalDelta,
      targetWait = targetWait,
      whsvWarn = wClampBase || wClampNew,
      pressureWarn = pClampBase || pClampNew,
      ronWarn = rClampBase || rClampNew,
      anWarn = anClampBase || anClampNew,
      mabpWarn = mClampBase || mClampNew,
      fWhsvBase = fWhsvBase,
      fWhsvNew = fWhsvNew,
      fPressBase = fPressBase,
      fPressNew = fPressNew,
      fRonBase = fRonBase,
      fRonNew = fRonNew,
      fAnBase = fAnBase,
      fAnNew = fAnNew,
      fMabpBase = fMabpBase,
      fMabpNew = fMabpNew
    )
  }

  fun calculateSorWaitAndActivity(
    ron: Double,
    an: Double,
    pressureKgCm2: Double,
    whsv: Double,
    mabp: Double,
    actualWait: Double,
    tables: CruLookupTables
  ): SorWaitActivityResult {
    val warnings = mutableListOf<String>()

    // Fig 22 base
    val (sorBase, fig22Warn) = calculateFig22(ron, an, tables)
    if (fig22Warn) {
      if (ron < tables.fig22RonX.first() || ron > tables.fig22RonX.last()) {
        warnings.add("RON ($ron) is outside Fig 22 chart range (92.0 - 102.0)")
      }
      if (an < 25.0 || an > 55.0) {
        warnings.add("A+0.85N ($an) is outside Fig 22 chart range (25 - 55 vol %)")
      }
    }

    // Pressure correction: f17(P) - f17(P_ref)
    val refPressKgCm2 = pressureToKgCm2(tables.refPressureBarG, PressureUnit.BAR_G)
    val (f17P, pWarn) = linearInterpolate(pressureKgCm2, tables.fig17PressureX, tables.fig17PressureY)
    val (f17PRef, _) = linearInterpolate(refPressKgCm2, tables.fig17PressureX, tables.fig17PressureY)
    val pressCorr = f17P - f17PRef
    if (pWarn) warnings.add("Pressure is outside Fig 17 chart range")

    // WHSV correction: f16(WHSV) - f16(WHSV_ref)
    val (f16W, wWarn) = linearInterpolate(whsv, tables.fig16WhsvX, tables.fig16WhsvY)
    val (f16WRef, _) = linearInterpolate(tables.refWhsv, tables.fig16WhsvX, tables.fig16WhsvY)
    val whsvCorr = f16W - f16WRef
    if (wWarn) warnings.add("WHSV is outside Fig 16 chart range")

    // MABP correction: f20(MABP) - f20(MABP_ref)
    val (f20M, mWarn) = linearInterpolate(mabp, tables.fig20MabpX, tables.fig20MabpY)
    val (f20MRef, _) = linearInterpolate(tables.refMabp, tables.fig20MabpX, tables.fig20MabpY)
    val mabpCorr = f20M - f20MRef
    if (mWarn) warnings.add("MABP is outside Fig 20 chart range")

    val correctedSorWait = sorBase + pressCorr + whsvCorr + mabpCorr
    val activityLoss = actualWait - correctedSorWait

    return SorWaitActivityResult(
      sorWaitBase = sorBase,
      f17PressActual = f17P,
      f17PressRef = f17PRef,
      pressCorrection = pressCorr,
      f16WhsvActual = f16W,
      f16WhsvRef = f16WRef,
      whsvCorrection = whsvCorr,
      f20MabpActual = f20M,
      f20MabpRef = f20MRef,
      mabpCorrection = mabpCorr,
      correctedSorWait = correctedSorWait,
      activityLoss = activityLoss,
      isOutOfRange = warnings.isNotEmpty(),
      warnings = warnings,
      actualWait = actualWait
    )
  }

  fun interpolateFig22(ron: Double, naphthenesAromatics: Double, tables: CruLookupTables): Pair<Double, Boolean> {
    return calculateFig22(ron, naphthenesAromatics, tables)
  }

  fun calculateChangeEstimator(
    baseWhsv: Double,
    basePressure: Double,
    baseRon: Double,
    baseNaphthenesAromatics: Double,
    baseMabp: Double,
    newWhsv: Double,
    newPressure: Double,
    newRon: Double,
    newNaphthenesAromatics: Double,
    newMabp: Double,
    pressureUnit: PressureUnit,
    includeWhsv: Boolean,
    includePressure: Boolean,
    includeRon: Boolean,
    includeNaphthenesAromatics: Boolean,
    includeMabp: Boolean,
    currentWait: Double? = null,
    tables: CruLookupTables
  ): ChangeEstimatorResult {
    val basePressKg = pressureToKgCm2(basePressure, pressureUnit)
    val newPressKg = pressureToKgCm2(newPressure, pressureUnit)
    return calculateChangeEstimator(
      baseWhsv = baseWhsv, newWhsv = newWhsv, useWhsv = includeWhsv,
      basePressureKgCm2 = basePressKg, newPressureKgCm2 = newPressKg, usePressure = includePressure,
      baseRon = baseRon, newRon = newRon, useRon = includeRon,
      baseAn = baseNaphthenesAromatics, newAn = newNaphthenesAromatics, useAn = includeNaphthenesAromatics,
      baseMabp = baseMabp, newMabp = newMabp, useMabp = includeMabp,
      currentWait = currentWait,
      tables = tables
    )
  }

  fun calculateSorWaitAndActivity(
    ron: Double,
    naphthenesAromatics: Double,
    pressure: Double,
    whsv: Double,
    mabp: Double,
    actualWait: Double,
    pressureUnit: PressureUnit,
    tables: CruLookupTables
  ): SorWaitActivityResult {
    val pressKgCm2 = pressureToKgCm2(pressure, pressureUnit)
    return calculateSorWaitAndActivity(
      ron = ron,
      an = naphthenesAromatics,
      pressureKgCm2 = pressKgCm2,
      whsv = whsv,
      mabp = mabp,
      actualWait = actualWait,
      tables = tables
    )
  }

  // WAIT and WABT from reactors
  fun calculateWait(beds: List<CruReactorBed>): Double {
    val totalWeight = beds.sumOf { it.catalystWeightTonnes }
    if (totalWeight <= 0.0) return 0.0
    val weightedInlet = beds.sumOf { it.catalystWeightTonnes * it.inletTempC }
    return weightedInlet / totalWeight
  }

  fun calculateWait(weights: List<Double>, tins: List<Double>): Double {
    val totalWeight = weights.sum()
    if (totalWeight <= 0.0) return 0.0
    val weightedInlet = weights.zip(tins).sumOf { (w, t) -> w * t }
    return weightedInlet / totalWeight
  }

  fun calculateWabt(beds: List<CruReactorBed>): Double {
    val totalWeight = beds.sumOf { it.catalystWeightTonnes }
    if (totalWeight <= 0.0) return 0.0
    val weightedAvg = beds.sumOf { it.catalystWeightTonnes * ((it.inletTempC + it.outletTempC) / 2.0) }
    return weightedAvg / totalWeight
  }

  fun calculateWabt(weights: List<Double>, tins: List<Double>, touts: List<Double>): Double {
    val totalWeight = weights.sum()
    if (totalWeight <= 0.0) return 0.0
    val weightedAvg = weights.indices.sumOf { i ->
      val w = weights[i]
      val tin = tins.getOrElse(i) { 0.0 }
      val tout = touts.getOrElse(i) { 0.0 }
      w * ((tin + tout) / 2.0)
    }
    return weightedAvg / totalWeight
  }

  // WHSV Tool
  enum class FeedRateUnit(val label: String) {
    TONNES_HR("t/h"),
    KG_HR("kg/h"),
    M3_HR("m³/h"),
    BBL_HR("bbl/h")
  }

  enum class DensityMode {
    SG,
    API
  }

  fun calculateWhsv(
    feedRate: Double,
    feedUnit: FeedRateUnit,
    densityMode: DensityMode,
    densityVal: Double,
    catalystTonnes: Double
  ): Double {
    if (catalystTonnes <= 0.0) return 0.0
    val sg = if (densityMode == DensityMode.API) {
      if (densityVal + 131.5 > 0) 141.5 / (densityVal + 131.5) else 0.75
    } else {
      densityVal.coerceAtLeast(0.1)
    }

    val feedTonnesPerHour = when (feedUnit) {
      FeedRateUnit.TONNES_HR -> feedRate
      FeedRateUnit.KG_HR -> feedRate / 1000.0
      FeedRateUnit.M3_HR -> feedRate * sg
      FeedRateUnit.BBL_HR -> feedRate * BBL_TO_M3 * sg
    }

    return feedTonnesPerHour / catalystTonnes
  }

  // MABP Tool: (T10 + 2*T50 + T90) / 4
  fun calculateMabp(t10: Double, t50: Double, t90: Double): Double {
    return (t10 + 2.0 * t50 + t90) / 4.0
  }

  // H2/HC ratio Tool: (R_Nm3/h / 22.4 * Y) / (F_kg/h / m)
  fun calculateH2HcRatio(
    recycleGasNm3h: Double,
    h2PurityFraction: Double,
    feedKgH: Double,
    feedMolecularWeight: Double
  ): Double {
    if (feedKgH <= 0.0 || feedMolecularWeight <= 0.0) return 0.0
    val molH2PerHour = (recycleGasNm3h / 22.414) * h2PurityFraction * 1000.0 // mol/h
    val molHcPerHour = (feedKgH / feedMolecularWeight) * 1000.0 // mol/h
    return if (molHcPerHour > 0) molH2PerHour / molHcPerHour else 0.0
  }

  fun calculateH2HcRatio(
    recycleGasNm3h: Double,
    h2PurityVolPercent: Double,
    feedTonnesPerHour: Double
  ): Double {
    val feedKgH = feedTonnesPerHour * 1000.0
    val h2Fraction = h2PurityVolPercent / 100.0
    return calculateH2HcRatio(recycleGasNm3h, h2Fraction, feedKgH, 100.0)
  }

  // Summary Text
  fun formatActivitySummary(rec: CruActivityRecord): String {
    return String.format(
      Locale.US,
      "CRU WAIT Activity [%s] | Target RON: %.1f | Actual WAIT: %.1f °C | Corrected SOR WAIT: %.1f °C | Activity Loss: %.1f °C | WHSV: %.2f | P: %.1f %s | MABP: %.1f °C",
      rec.dateTime,
      rec.ronClear,
      rec.actualWait,
      rec.correctedSorWait,
      rec.activityLoss,
      rec.whsv,
      rec.pressureVal,
      rec.pressureUnit.label,
      rec.mabp
    )
  }
}
