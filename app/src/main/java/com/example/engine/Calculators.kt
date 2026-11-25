package com.example.engine

import com.example.data.*
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object Calculators {

  private data class TankCacheKey(
    val id: String,
    val name: String,
    val dipCalibrationMode: DipCalibrationMode,
    val dipSize: Int,
    val fracSize: Int,
    val n2Size: Int,
    val firstDipMm: Double,
    val lastDipMm: Double,
    val firstDipVol: Double,
    val lastDipVol: Double
  )

  private class IndexedTankCalibration(
    val dipMms: DoubleArray,
    val dipVolumes: DoubleArray,
    val hasDipVolume: BooleanArray,
    val maxColDipVol: Double,
    val hasNonTenMmRows: Boolean,
    val isDirectPerMm: Boolean,
    val validSortedVols: DoubleArray,
    val validSortedDipMms: DoubleArray,
    val hasFracRow: BooleanArray,
    val hasFracVolume: BooleanArray,
    val fracVolumes: DoubleArray,
    val maxColFracVol: Double,
    val n2Heights: DoubleArray,
    val n2Volumes: DoubleArray,
    val hasN2Volume: BooleanArray
  )

  private val calibrationCache = ConcurrentHashMap<TankCacheKey, IndexedTankCalibration>()

  fun invalidateCache() {
    calibrationCache.clear()
  }

  fun warmUpCache(tanks: List<Tank>) {
    for (tank in tanks) {
      getOrCreateIndex(tank)
    }
  }

  /**
   * Returns true if this tank uses direct per-mm calibration (no 0..9mm fraction table applied),
   * such as LPG spheres/bullets (512A, 512B, 512C, 512D) or any tank with 1mm dip rows.
   */
  fun isDirectPerMmTank(tank: Tank): Boolean {
    if (tank.type == TankType.N2_STORAGE) return false
    return getOrCreateIndex(tank).isDirectPerMm
  }

  private fun isLikelyDirectLpgTankName(name: String): Boolean {
    val norm = name.uppercase(java.util.Locale.US)
    return norm.contains("512A") || norm.contains("512B") || norm.contains("512C") || norm.contains("512D") ||
      norm.contains("512-A") || norm.contains("512-B") || norm.contains("512-C") || norm.contains("512-D") ||
      norm.contains("LPG")
  }

  private fun buildCacheKey(tank: Tank): TankCacheKey {
    val firstDip = tank.dipTable.firstOrNull()
    val lastDip = tank.dipTable.lastOrNull()
    val firstVol = if (firstDip != null) TankNameMatcher.findVolume(firstDip.volumes, tank.name) ?: -1.0 else -1.0
    val lastVol = if (lastDip != null) TankNameMatcher.findVolume(lastDip.volumes, tank.name) ?: -1.0 else -1.0
    return TankCacheKey(
      id = tank.id,
      name = tank.name,
      dipCalibrationMode = tank.dipCalibrationMode,
      dipSize = tank.dipTable.size,
      fracSize = tank.fractionTable.size,
      n2Size = tank.n2Table.size,
      firstDipMm = firstDip?.dipMm ?: -1.0,
      lastDipMm = lastDip?.dipMm ?: -1.0,
      firstDipVol = firstVol,
      lastDipVol = lastVol
    )
  }

  private fun getOrCreateIndex(tank: Tank): IndexedTankCalibration {
    val key = buildCacheKey(tank)
    calibrationCache[key]?.let { return it }

    val sortedDip = tank.dipTable.sortedBy { it.dipMm }
    val dipCount = sortedDip.size
    val dipMms = DoubleArray(dipCount)
    val dipVolumes = DoubleArray(dipCount)
    val hasDipVolume = BooleanArray(dipCount)
    var maxColDipVol = 0.0
    var hasNonTenMmRows = false

    for (i in 0 until dipCount) {
      val row = sortedDip[i]
      dipMms[i] = row.dipMm
      val mod10 = abs(row.dipMm % 10.0)
      if (mod10 > 0.001 && abs(mod10 - 10.0) > 0.001) {
        hasNonTenMmRows = true
      }
      val v = TankNameMatcher.findVolume(row.volumes, tank.name)
      if (v != null) {
        hasDipVolume[i] = true
        dipVolumes[i] = v
        if (v > maxColDipVol) maxColDipVol = v
      } else {
        hasDipVolume[i] = false
        dipVolumes[i] = 0.0
      }
    }

    // Pre-sort valid volume rows for fast binary search in Volume-to-Dip
    val minDip = if (dipCount > 0) dipMms[0] else 0.0
    val validPairs = ArrayList<Pair<Double, Double>>(dipCount)
    for (i in 0 until dipCount) {
      if (hasDipVolume[i]) {
        val v = dipVolumes[i]
        val d = dipMms[i]
        if (v > 1.0 || d == minDip) {
          validPairs.add(Pair(d, v))
        }
      }
    }
    validPairs.sortBy { it.second }
    val validSortedVols = DoubleArray(validPairs.size) { validPairs[it].second }
    val validSortedDipMms = DoubleArray(validPairs.size) { validPairs[it].first }

    // Index fraction rows 0..9
    val hasFracRow = BooleanArray(10)
    val hasFracVolume = BooleanArray(10)
    val fracVolumes = DoubleArray(10)
    var maxColFracVol = 0.0

    for (row in tank.fractionTable) {
      val mm = row.mm
      if (mm in 0..9) {
        hasFracRow[mm] = true
        val v = TankNameMatcher.findVolume(row.volumes, tank.name)
        if (v != null) {
          hasFracVolume[mm] = true
          fracVolumes[mm] = v
          if (v > maxColFracVol) maxColFracVol = v
        }
      }
    }

    val hasAnyFracForTank = (1..9).any { hasFracRow[it] && hasFracVolume[it] && fracVolumes[it] > 0.0 }
    val isDirectPerMm = when (tank.dipCalibrationMode) {
      DipCalibrationMode.DIRECT_1MM -> true
      DipCalibrationMode.BASE_10MM_PLUS_FRACTION -> false
      DipCalibrationMode.AUTO -> hasNonTenMmRows || (dipCount > 0 && !hasAnyFracForTank && isLikelyDirectLpgTankName(tank.name))
    }

    // Index N2 rows
    val sortedN2 = tank.n2Table.sortedBy { it.heightMm }
    val n2Count = sortedN2.size
    val n2Heights = DoubleArray(n2Count)
    val n2Volumes = DoubleArray(n2Count)
    val hasN2Volume = BooleanArray(n2Count)
    for (i in 0 until n2Count) {
      val row = sortedN2[i]
      n2Heights[i] = row.heightMm
      val v = TankNameMatcher.findVolume(row.volumes, tank.name)
      if (v != null) {
        hasN2Volume[i] = true
        n2Volumes[i] = v
      }
    }

    val indexed = IndexedTankCalibration(
      dipMms = dipMms,
      dipVolumes = dipVolumes,
      hasDipVolume = hasDipVolume,
      maxColDipVol = maxColDipVol,
      hasNonTenMmRows = hasNonTenMmRows,
      isDirectPerMm = isDirectPerMm,
      validSortedVols = validSortedVols,
      validSortedDipMms = validSortedDipMms,
      hasFracRow = hasFracRow,
      hasFracVolume = hasFracVolume,
      fracVolumes = fracVolumes,
      maxColFracVol = maxColFracVol,
      n2Heights = n2Heights,
      n2Volumes = n2Volumes,
      hasN2Volume = hasN2Volume
    )
    if (calibrationCache.size > 256) {
      calibrationCache.clear()
    }
    calibrationCache[key] = indexed
    return indexed
  }

  private fun findDipRowIndex(dipMms: DoubleArray, targetMm: Double): Int {
    val idx = Arrays.binarySearch(dipMms, targetMm)
    if (idx >= 0) return idx
    val ins = -idx - 1
    if (ins < dipMms.size && abs(dipMms[ins] - targetMm) < 0.001) return ins
    if (ins > 0 && abs(dipMms[ins - 1] - targetMm) < 0.001) return ins - 1
    return -1
  }

  fun calculateDipVolume(
    tank: Tank,
    inputDip: Double,
    threshold: Double = 1.0,
    interpolateDecimal: Boolean = false
  ): DipCalculationResult {
    return computeDipVolumeInternal(tank, inputDip, threshold, interpolateDecimal)
  }

  private fun computeDipVolumeInternal(
    tank: Tank,
    dipMm: Double,
    invalidThreshold: Double,
    interpolateDecimal: Boolean
  ): DipCalculationResult {
    val idx = getOrCreateIndex(tank)
    val cap = if (tank.maxVolumeLiters > 0.0) tank.maxVolumeLiters else idx.maxColDipVol.coerceAtLeast(100000.0)

    if (idx.dipMms.isEmpty()) {
      return DipCalculationResult(
        inputDip = dipMm,
        effectiveDip = dipMm,
        baseDip = 0,
        baseVolume = 0.0,
        fracMm = 0,
        fractionVolume = 0.0,
        totalVolume = 0.0,
        capacity = cap,
        ullage = cap,
        percentFull = 0.0,
        warning = "Missing Dip Table for ${tank.name}",
        invalidInput = true,
        usedDirectMmLookup = idx.isDirectPerMm
      )
    }

    val minDip = idx.dipMms.first()
    val maxDip = idx.dipMms.last()
    val upperLimit = if (idx.isDirectPerMm) maxDip + 0.001 else maxDip + 9.999

    if (dipMm < minDip || dipMm > upperLimit) {
      val displayMax = if (idx.isDirectPerMm) maxDip else maxDip + 9.0
      return DipCalculationResult(
        inputDip = dipMm,
        effectiveDip = dipMm,
        baseDip = 0,
        baseVolume = 0.0,
        fracMm = 0,
        fractionVolume = 0.0,
        totalVolume = 0.0,
        capacity = cap,
        ullage = cap,
        percentFull = 0.0,
        warning = "Dip ($dipMm mm) out of calibration range ($minDip - $displayMax mm)",
        invalidInput = true,
        usedDirectMmLookup = idx.isDirectPerMm
      )
    }

    // Optional decimal mm linear interpolation when requested
    val floorInt = kotlin.math.floor(dipMm).toInt()
    val ceilInt = kotlin.math.ceil(dipMm).toInt()
    if (interpolateDecimal && ceilInt > floorInt && abs(dipMm - floorInt.toDouble()) > 1e-4) {
      val lowRes = computeExactIntDip(tank, idx, cap, dipMm, floorInt, invalidThreshold)
      val highRes = computeExactIntDip(tank, idx, cap, dipMm, ceilInt, invalidThreshold)
      if (!lowRes.invalidInput && !highRes.invalidInput) {
        val ratio = dipMm - floorInt.toDouble()
        val interpTotal = lowRes.totalVolume + ratio * (highRes.totalVolume - lowRes.totalVolume)
        val interpFrac = if (idx.isDirectPerMm) 0.0 else (interpTotal - lowRes.baseVolume).coerceAtLeast(0.0)
        val ull = (cap - interpTotal).coerceAtLeast(0.0)
        val pct = if (cap > 0.0) ((interpTotal / cap) * 100.0).coerceIn(0.0, 100.0) else 0.0
        return lowRes.copy(
          inputDip = dipMm,
          effectiveDip = dipMm,
          baseVolume = if (idx.isDirectPerMm) interpTotal else lowRes.baseVolume,
          fractionVolume = interpFrac,
          totalVolume = interpTotal,
          ullage = ull,
          percentFull = pct,
          interpolationNote = String.format(
            java.util.Locale.US,
            "Linearly interpolated decimal %.2f mm between %d mm and %d mm",
            dipMm,
            floorInt,
            ceilInt
          ),
          usedDirectMmLookup = idx.isDirectPerMm
        )
      }
    }

    val roundedDip = Math.round(dipMm).toInt()
    val note = if (abs(dipMm - roundedDip.toDouble()) > 1e-4) {
      "Rounded ${dipMm} mm to ${roundedDip} mm"
    } else {
      null
    }
    return computeExactIntDip(tank, idx, cap, dipMm, roundedDip, invalidThreshold, note)
  }

  private fun computeExactIntDip(
    tank: Tank,
    idx: IndexedTankCalibration,
    cap: Double,
    rawInputDip: Double,
    intDip: Int,
    invalidThreshold: Double,
    interpolationNote: String? = null
  ): DipCalculationResult {
    val minDip = idx.dipMms.first()

    // Direct 1 mm per-dip mode (e.g. LPG bullets 512A, 512B, 512C, 512D — NO fraction table used)
    if (idx.isDirectPerMm) {
      val exactIdx = findDipRowIndex(idx.dipMms, intDip.toDouble())
      if (exactIdx >= 0) {
        if (!idx.hasDipVolume[exactIdx]) {
          return DipCalculationResult(
            inputDip = rawInputDip,
            effectiveDip = intDip.toDouble(),
            baseDip = intDip,
            baseVolume = 0.0,
            fracMm = 0,
            fractionVolume = 0.0,
            totalVolume = 0.0,
            capacity = cap,
            ullage = cap,
            percentFull = 0.0,
            warning = "Tank column '${tank.name}' not found in Dip table",
            interpolationNote = interpolationNote,
            invalidInput = true,
            usedDirectMmLookup = true
          )
        }
        val directVol = idx.dipVolumes[exactIdx]
        if (directVol <= invalidThreshold && intDip > minDip && idx.maxColDipVol > invalidThreshold) {
          return DipCalculationResult(
            inputDip = rawInputDip,
            effectiveDip = intDip.toDouble(),
            baseDip = intDip,
            baseVolume = directVol,
            fracMm = 0,
            fractionVolume = 0.0,
            totalVolume = 0.0,
            capacity = cap,
            ullage = cap,
            percentFull = 0.0,
            warning = "Invalid input: Dip $rawInputDip mm is beyond calibrated height for ${tank.name}.",
            interpolationNote = interpolationNote,
            invalidInput = true,
            usedDirectMmLookup = true
          )
        }
        val ull = (cap - directVol).coerceAtLeast(0.0)
        val pct = if (cap > 0.0) ((directVol / cap) * 100.0).coerceIn(0.0, 100.0) else 0.0
        return DipCalculationResult(
          inputDip = rawInputDip,
          effectiveDip = intDip.toDouble(),
          baseDip = intDip,
          baseVolume = directVol,
          fracMm = 0,
          fractionVolume = 0.0,
          totalVolume = directVol,
          capacity = cap,
          ullage = ull,
          percentFull = pct,
          warning = null,
          interpolationNote = interpolationNote,
          invalidInput = false,
          usedDirectMmLookup = true
        )
      }

      // If direct table has a small gap between calibrated mm rows, linearly interpolate without fraction table
      val bs = Arrays.binarySearch(idx.dipMms, intDip.toDouble())
      val ins = -bs - 1
      val lowerIdx = (ins - 1).coerceIn(0, idx.dipMms.lastIndex)
      val upperIdx = ins.coerceIn(0, idx.dipMms.lastIndex)
      if (lowerIdx != upperIdx && idx.hasDipVolume[lowerIdx] && idx.hasDipVolume[upperIdx]) {
        val dL = idx.dipMms[lowerIdx]
        val dU = idx.dipMms[upperIdx]
        val vL = idx.dipVolumes[lowerIdx]
        val vU = idx.dipVolumes[upperIdx]
        val ratio = if (abs(dU - dL) > 1e-9) (intDip.toDouble() - dL) / (dU - dL) else 0.0
        val interpVol = vL + ratio * (vU - vL)
        if (interpVol <= invalidThreshold && intDip > minDip && idx.maxColDipVol > invalidThreshold) {
          return DipCalculationResult(
            inputDip = rawInputDip,
            effectiveDip = intDip.toDouble(),
            baseDip = intDip,
            baseVolume = interpVol,
            fracMm = 0,
            fractionVolume = 0.0,
            totalVolume = 0.0,
            capacity = cap,
            ullage = cap,
            percentFull = 0.0,
            warning = "Invalid input: Dip $rawInputDip mm is beyond calibrated height for ${tank.name}.",
            interpolationNote = interpolationNote,
            invalidInput = true,
            usedDirectMmLookup = true
          )
        }
        val ull = (cap - interpVol).coerceAtLeast(0.0)
        val pct = if (cap > 0.0) ((interpVol / cap) * 100.0).coerceIn(0.0, 100.0) else 0.0
        val noteText = String.format(
          java.util.Locale.US,
          "Direct mm interpolation between %.0f mm and %.0f mm (no fraction)",
          dL,
          dU
        )
        return DipCalculationResult(
          inputDip = rawInputDip,
          effectiveDip = intDip.toDouble(),
          baseDip = intDip,
          baseVolume = interpVol,
          fracMm = 0,
          fractionVolume = 0.0,
          totalVolume = interpVol,
          capacity = cap,
          ullage = ull,
          percentFull = pct,
          warning = null,
          interpolationNote = interpolationNote?.let { "$it • $noteText" } ?: noteText,
          invalidInput = false,
          usedDirectMmLookup = true
        )
      }

      return DipCalculationResult(
        inputDip = rawInputDip,
        effectiveDip = intDip.toDouble(),
        baseDip = intDip,
        baseVolume = 0.0,
        fracMm = 0,
        fractionVolume = 0.0,
        totalVolume = 0.0,
        capacity = cap,
        ullage = cap,
        percentFull = 0.0,
        warning = "Dip $intDip mm not found in calibration table",
        interpolationNote = interpolationNote,
        invalidInput = true,
        usedDirectMmLookup = true
      )
    }

    // Standard 10mm Base + 0..9mm Fraction mode
    val baseMm = (intDip / 10) * 10
    val fracMm = intDip % 10

    val baseIdx = findDipRowIndex(idx.dipMms, baseMm.toDouble())
    if (baseIdx < 0) {
      return DipCalculationResult(
        inputDip = rawInputDip,
        effectiveDip = intDip.toDouble(),
        baseDip = baseMm,
        baseVolume = 0.0,
        fracMm = fracMm,
        fractionVolume = 0.0,
        totalVolume = 0.0,
        capacity = cap,
        ullage = cap,
        percentFull = 0.0,
        warning = "Base dip $baseMm mm not found in table",
        interpolationNote = interpolationNote,
        invalidInput = true
      )
    }

    if (!idx.hasDipVolume[baseIdx]) {
      return DipCalculationResult(
        inputDip = rawInputDip,
        effectiveDip = intDip.toDouble(),
        baseDip = baseMm,
        baseVolume = 0.0,
        fracMm = fracMm,
        fractionVolume = 0.0,
        totalVolume = 0.0,
        capacity = cap,
        ullage = cap,
        percentFull = 0.0,
        warning = "Tank column '${tank.name}' not found in Dip table",
        interpolationNote = interpolationNote,
        invalidInput = true
      )
    }
    val baseVol = idx.dipVolumes[baseIdx]

    if (baseVol <= invalidThreshold && baseMm > minDip) {
      if (idx.maxColDipVol > invalidThreshold) {
        return DipCalculationResult(
          inputDip = rawInputDip,
          effectiveDip = intDip.toDouble(),
          baseDip = baseMm,
          baseVolume = baseVol,
          fracMm = fracMm,
          fractionVolume = 0.0,
          totalVolume = 0.0,
          capacity = cap,
          ullage = cap,
          percentFull = 0.0,
          warning = "Invalid input: Dip $rawInputDip mm is beyond calibrated height for ${tank.name}.",
          interpolationNote = interpolationNote,
          invalidInput = true
        )
      }
    }

    var fracVol = 0.0
    var warning: String? = null

    if (fracMm > 0) {
      if (fracMm !in 0..9 || !idx.hasFracRow[fracMm]) {
        warning = "Fraction ${fracMm}mm not found in Fraction Table, using 0"
      } else {
        if (!idx.hasFracVolume[fracMm]) {
          warning = "Tank column '${tank.name}' missing in Fraction Table"
        } else {
          val fv = idx.fracVolumes[fracMm]
          if (fv <= invalidThreshold && idx.maxColFracVol > invalidThreshold) {
            warning = "Fraction value ($fv) <= threshold (${invalidThreshold}L) for ${fracMm}mm"
          } else {
            fracVol = fv
          }
        }
      }
    }

    val totalVol = baseVol + fracVol
    val ull = (cap - totalVol).coerceAtLeast(0.0)
    val pct = if (cap > 0.0) ((totalVol / cap) * 100.0).coerceIn(0.0, 100.0) else 0.0

    return DipCalculationResult(
      inputDip = rawInputDip,
      effectiveDip = intDip.toDouble(),
      baseDip = baseMm,
      baseVolume = baseVol,
      fracMm = fracMm,
      fractionVolume = fracVol,
      totalVolume = totalVol,
      capacity = cap,
      ullage = ull,
      percentFull = pct,
      warning = warning,
      interpolationNote = interpolationNote,
      invalidInput = false,
      usedDirectMmLookup = false
    )
  }

  /**
   * Binary-search inverse lookup (Volume -> Dip in mm).
   * Runs in <0.05ms using pre-sorted primitive arrays.
   */
  fun calculateVolumeToDip(tank: Tank, targetVolume: Double, invalidThreshold: Double = 1.0): Double? {
    val idx = getOrCreateIndex(tank)
    if (idx.dipMms.isEmpty() || targetVolume < 0.0) {
      return null
    }

    val validVols: DoubleArray
    val validDips: DoubleArray

    if (abs(invalidThreshold - 1.0) < 1e-6) {
      validVols = idx.validSortedVols
      validDips = idx.validSortedDipMms
    } else {
      val minDip = idx.dipMms.first()
      val pairs = ArrayList<Pair<Double, Double>>(idx.dipMms.size)
      for (i in idx.dipMms.indices) {
        if (idx.hasDipVolume[i]) {
          val v = idx.dipVolumes[i]
          val d = idx.dipMms[i]
          if (v > invalidThreshold || d == minDip) {
            pairs.add(Pair(d, v))
          }
        }
      }
      pairs.sortBy { it.second }
      validVols = DoubleArray(pairs.size) { pairs[it].second }
      validDips = DoubleArray(pairs.size) { pairs[it].first }
    }

    if (validVols.isEmpty()) {
      return null
    }

    // Binary search floor for largest row with vol <= targetVolume
    val bs = Arrays.binarySearch(validVols, targetVolume)
    val floorIdx = if (bs >= 0) {
      var k = bs
      while (k + 1 < validVols.size && validVols[k + 1] <= targetVolume) {
        k++
      }
      k
    } else {
      val ins = -bs - 1
      (ins - 1).coerceAtLeast(0)
    }

    // Direct 1 mm tanks: find closest calibrated mm directly without adding fraction table
    if (idx.isDirectPerMm) {
      if (floorIdx + 1 < validVols.size) {
        val diffLow = abs(targetVolume - validVols[floorIdx])
        val diffHigh = abs(validVols[floorIdx + 1] - targetVolume)
        return if (diffHigh < diffLow) validDips[floorIdx + 1] else validDips[floorIdx]
      }
      return validDips[floorIdx]
    }

    val baseMm = validDips[floorIdx].toInt()
    val baseVol = validVols[floorIdx]
    val remainder = (targetVolume - baseVol).coerceAtLeast(0.0)

    var bestFracMm = 0
    var minDiff = remainder

    for (f in 1..9) {
      if (idx.hasFracRow[f] && idx.hasFracVolume[f]) {
        val fv = idx.fracVolumes[f]
        if (fv > invalidThreshold) {
          val diff = abs(remainder - fv)
          if (diff < minDiff) {
            minDiff = diff
            bestFracMm = f
          }
        }
      }
    }

    return (baseMm + bestFracMm).toDouble()
  }

  /**
   * Binary-search N2 water column interpolation returning Triple(volume, breakdownText, warning).
   */
  fun calculateN2Volume(tank: Tank, heightMm: Double): Triple<Double, String, String?> {
    val idx = getOrCreateIndex(tank)
    if (idx.n2Heights.isEmpty()) {
      return Triple(0.0, "No N2 table rows", "Missing N2 Table for ${tank.name}")
    }

    val minH = idx.n2Heights.first()
    val maxH = idx.n2Heights.last()

    if (heightMm < minH || heightMm > maxH) {
      return Triple(0.0, "Out of bounds ($minH - $maxH mm)", "Height ($heightMm mm) out of range ($minH - $maxH mm)")
    }

    val bs = Arrays.binarySearch(idx.n2Heights, heightMm)
    val exactIdx = if (bs >= 0) {
      bs
    } else {
      val ins = -bs - 1
      when {
        ins < idx.n2Heights.size && abs(idx.n2Heights[ins] - heightMm) < 0.001 -> ins
        ins > 0 && abs(idx.n2Heights[ins - 1] - heightMm) < 0.001 -> ins - 1
        else -> -1
      }
    }

    if (exactIdx >= 0) {
      if (!idx.hasN2Volume[exactIdx]) {
        return Triple(0.0, "Missing tank column", "Tank column '${tank.name}' missing in N2 table")
      }
      val v0 = idx.n2Volumes[exactIdx]
      val hExact = idx.n2Heights[exactIdx]
      return Triple(v0, "Exact table match at $hExact mm", null)
    }

    val ins = -bs - 1
    val lowerIdx = (ins - 1).coerceAtLeast(0)
    val upperIdx = ins.coerceAtMost(idx.n2Heights.lastIndex)
    if (!idx.hasN2Volume[lowerIdx] || !idx.hasN2Volume[upperIdx]) {
      return Triple(0.0, "Missing volume data", "Missing volume data for interpolation")
    }
    val hL = idx.n2Heights[lowerIdx]
    val hU = idx.n2Heights[upperIdx]
    val vL = idx.n2Volumes[lowerIdx]
    val vU = idx.n2Volumes[upperIdx]
    val v0 = if (abs(hU - hL) < 1e-9) {
      vL
    } else {
      val ratio = (heightMm - hL) / (hU - hL)
      vL + ratio * (vU - vL)
    }
    val breakdown = String.format(
      java.util.Locale.US,
      "Interpolated between %.0f mm (%,.1f) & %.0f mm (%,.1f)",
      hL, vL, hU, vU
    )
    return Triple(v0, breakdown, null)
  }

  data class SolutionCalcResult(
    val requiredActiveVolumeL: Double,
    val requiredChemicalVolumeL: Double,
    val requiredChemicalMassKg: Double,
    val waterVolumeL: Double,
    val finalSolutionMassKg: Double,
    val finalStrengthWtPercent: Double
  )

  fun calculateSolutionBatch(
    batchVolumeL: Double,
    targetStrengthPercent: Double,
    chemicalPurityPercent: Double,
    chemicalDensityKgL: Double
  ): SolutionCalcResult {
    val safePurity = if (chemicalPurityPercent <= 0.0) 100.0 else chemicalPurityPercent
    val safeDensity = if (chemicalDensityKgL <= 0.0) 1.0 else chemicalDensityKgL

    val reqActiveVolL = batchVolumeL * (targetStrengthPercent / 100.0)
    val reqChemVolL = reqActiveVolL / (safePurity / 100.0)
    val reqChemMassKg = reqChemVolL * safeDensity
    val waterVolL = (batchVolumeL - reqChemVolL).coerceAtLeast(0.0)
    val waterMassKg = waterVolL * 1.0
    val totalMassKg = reqChemMassKg + waterMassKg
    val actualWtPercent = if (totalMassKg > 0) ((reqChemMassKg * (safePurity / 100.0)) / totalMassKg) * 100.0 else 0.0

    return SolutionCalcResult(
      requiredActiveVolumeL = reqActiveVolL,
      requiredChemicalVolumeL = reqChemVolL,
      requiredChemicalMassKg = reqChemMassKg,
      waterVolumeL = waterVolL,
      finalSolutionMassKg = totalMassKg,
      finalStrengthWtPercent = actualWtPercent
    )
  }

  data class DipEstimationResult(
    val initialDipMm: Double,
    val initialVolumeL: Double,
    val transferVolumeL: Double,
    val targetVolumeL: Double,
    val estimatedDipMm: Double?,
    val dipDeltaMm: Double?,
    val exceedsCapacity: Boolean,
    val warning: String?
  )

  /**
   * Estimates the target dip (mm) for a tank given an initial dip (mm) and required transfer volume (L).
   * Works seamlessly for both standard 10mm + fraction tanks and direct per-1mm calibrated tanks.
   */
  fun estimateTargetDip(
    tank: Tank,
    initialDipMm: Double,
    transferVolumeL: Double,
    invalidThreshold: Double = 1.0
  ): DipEstimationResult {
    val initRes = calculateDipVolume(tank, initialDipMm, invalidThreshold)
    if (initRes.invalidInput) {
      return DipEstimationResult(
        initialDipMm = initialDipMm,
        initialVolumeL = 0.0,
        transferVolumeL = transferVolumeL,
        targetVolumeL = 0.0,
        estimatedDipMm = null,
        dipDeltaMm = null,
        exceedsCapacity = false,
        warning = initRes.warning ?: "Invalid initial dip"
      )
    }

    val targetVolL = initRes.totalVolume + transferVolumeL
    if (targetVolL < 0.0) {
      return DipEstimationResult(
        initialDipMm = initialDipMm,
        initialVolumeL = initRes.totalVolume,
        transferVolumeL = transferVolumeL,
        targetVolumeL = targetVolL,
        estimatedDipMm = null,
        dipDeltaMm = null,
        exceedsCapacity = false,
        warning = "Target volume cannot be negative"
      )
    }

    val idx = getOrCreateIndex(tank)
    val maxCalibratedVol = if (idx.isDirectPerMm) {
      idx.maxColDipVol
    } else {
      idx.maxColDipVol + idx.maxColFracVol
    }
    val capacityLimit = if (maxCalibratedVol > 0.0) maxCalibratedVol else tank.maxVolumeLiters
    val exceedsCap = capacityLimit > 0.0 && targetVolL > capacityLimit + 1e-6

    val estDip = calculateVolumeToDip(tank, targetVolL, invalidThreshold)
    val minCalibratedVol = idx.validSortedVols.firstOrNull() ?: 0.0
    val belowMin = idx.validSortedVols.isNotEmpty() && targetVolL < minCalibratedVol - 1e-6

    val warning = when {
      exceedsCap -> "Target volume exceeds calibrated tank capacity"
      belowMin -> "Target volume is below minimum calibrated level"
      estDip == null -> "Unable to estimate dip from calibration table"
      else -> initRes.warning
    }

    return DipEstimationResult(
      initialDipMm = initialDipMm,
      initialVolumeL = initRes.totalVolume,
      transferVolumeL = transferVolumeL,
      targetVolumeL = targetVolL,
      estimatedDipMm = estDip,
      dipDeltaMm = estDip?.let { it - initialDipMm },
      exceedsCapacity = exceedsCap,
      warning = warning
    )
  }
}
