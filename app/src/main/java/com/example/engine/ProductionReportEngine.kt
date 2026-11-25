package com.example.engine

import com.example.data.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

object ProductionReportEngine {

  /**
   * Normalizes tank names for robust auto-linking (strips spaces, dashes, underscores, non-alphanumeric).
   */
  fun normalizeTankName(name: String): String =
    name.trim().uppercase().replace("-", "").replace("_", "").replace(" ", "").replace(Regex("[^A-Z0-9]"), "")

  /**
   * Version stamp for a tank's calibration: captures ID, name, dip table rows/range, and fraction table rows/sum.
   */
  fun getTankCalibrationStamp(tank: Tank?): String {
    if (tank == null) return ""
    val dipCount = tank.dipTable.size
    val firstDip = tank.dipTable.firstOrNull()?.dipMm ?: 0.0
    val lastDip = tank.dipTable.lastOrNull()?.dipMm ?: 0.0
    val fracCount = tank.fractionTable.size
    val fracSum = tank.fractionTable.sumOf { it.volumes.values.sum() }
    return "${tank.id}_${tank.name}_${dipCount}_${firstDip}_${lastDip}_${fracCount}_${fracSum}"
  }

  /**
   * Auto-links report tank maps to existing tanks in Tank master.
   * Preserves existing valid linkedTankId; otherwise matches by normalized name.
   */
  fun autoLinkTankMaps(maps: List<ProductionReportTankMap>, tanks: List<Tank>): List<ProductionReportTankMap> {
    val normalizedMap = tanks.associateBy { normalizeTankName(it.name) }
    return maps.map { map ->
      if (map.linkedTankId != null && tanks.any { it.id == map.linkedTankId }) {
        map
      } else {
        val matched = normalizedMap[normalizeTankName(map.tankName)]
        if (matched != null) map.copy(linkedTankId = matched.id) else map
      }
    }
  }

  /**
   * Finds the Tank in Tank master linked to a report tank map.
   */
  fun findLinkedTank(map: ProductionReportTankMap, tanks: List<Tank>): Tank? {
    if (map.linkedTankId != null) {
      val byId = tanks.find { it.id == map.linkedTankId }
      if (byId != null) return byId
    }
    return tanks.find { normalizeTankName(it.name) == normalizeTankName(map.tankName) }
  }

  data class CalibrationCheckResult(
    val isOk: Boolean,
    val statusText: String,
    val isMissingTank: Boolean = false,
    val calculatedVolume: Double? = null
  )

  /**
   * Checks calibration status for a tank given optional dipMm.
   */
  fun checkCalibration(tank: Tank?, dipMm: Double?, threshold: Double = 5.0): CalibrationCheckResult {
    if (tank == null) {
      return CalibrationCheckResult(false, "Tank not found in master", isMissingTank = true)
    }
    if (tank.dipTable.isEmpty()) {
      return CalibrationCheckResult(false, "No calibration / invalid dip")
    }
    if (dipMm != null && dipMm > 0) {
      val calc = Calculators.calculateDipVolume(tank, dipMm, threshold)
      if (calc == null || (calc.totalVolume <= 0 && dipMm > 0)) {
        return CalibrationCheckResult(false, "No calibration / invalid dip")
      }
      return CalibrationCheckResult(true, "Calibration OK", calculatedVolume = calc.totalVolume)
    }
    return CalibrationCheckResult(true, "Calibration OK")
  }

  data class TableValidationResult(
    val errors: List<String>,
    val warnings: List<String>,
    val errorIndices: Set<Int>,
    val warningIndices: Set<Int>
  )

  /**
   * Full validation on coefficient tables: strictly ascending lower bounds, non-increasing coefficients.
   */
  fun validateCoefficientTable(table: List<DensityCoefficientRow>): TableValidationResult {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    val errorIndices = mutableSetOf<Int>()
    val warningIndices = mutableSetOf<Int>()

    for (i in 0 until table.size) {
      val row = table[i]
      if (row.lowerBound.isNaN() || row.coefficient.isNaN()) {
        errors.add("Row ${i + 1}: Values must be numeric.")
        errorIndices.add(i)
      }
      if (i > 0) {
        val prev = table[i - 1]
        if (row.lowerBound <= prev.lowerBound) {
          errors.add("Row ${i + 1}: Lower bound (${row.lowerBound}) must be strictly ascending (previous: ${prev.lowerBound}).")
          errorIndices.add(i)
        }
        if (row.coefficient > prev.coefficient) {
          warnings.add("Row ${i + 1}: Coefficient (${row.coefficient}) is greater than previous (${prev.coefficient}). Recommended non-increasing.")
          warningIndices.add(i)
        }
      }
    }
    return TableValidationResult(errors, warnings, errorIndices, warningIndices)
  }

  /**
   * Find coefficient in a table by density: takes the row with the largest lowerBound <= density.
   * If below first bound, returns first row.
   */
  fun findCoefficient(density: Double?, table: List<DensityCoefficientRow>): Pair<Double, DensityCoefficientRow?> {
    if (density == null || table.isEmpty()) return Pair(0.00100, null)
    val matching = table.filter { it.lowerBound <= density }
    val best = matching.maxByOrNull { it.lowerBound } ?: table.minByOrNull { it.lowerBound }
    return Pair(best?.coefficient ?: 0.00100, best)
  }

  /**
   * Volume at 30 °C:
   * Volume @30 = Natural - Natural x coefficient x (Temp - 30)
   */
  fun calculateVolumeAt30(naturalVolume: Double, tempC: Double?, coefficient: Double): Double {
    if (tempC == null) return naturalVolume
    val deltaT = tempC - 30.0
    return naturalVolume - (naturalVolume * coefficient * deltaT)
  }

  /**
   * Validates that table lower bounds are strictly ascending.
   * Returns list of 0-based indices that violate ascending order.
   */
  fun validateTableStrictlyAscending(table: List<DensityCoefficientRow>): List<Int> {
    val violations = mutableListOf<Int>()
    for (i in 1 until table.size) {
      if (table[i].lowerBound <= table[i - 1].lowerBound) {
        violations.add(i)
      }
    }
    return violations
  }

  fun getTankEffectiveRole(tank: Tank, entry: ProductionReportTankEntry?): TankRole {
    if (entry?.roleOverride != null) return entry.roleOverride
    return when (tank.type) {
      TankType.FEED -> TankRole.FEED
      TankType.PRODUCT -> TankRole.PRODUCT
      TankType.BLENDING -> TankRole.BLENDING
      TankType.N2_STORAGE -> TankRole.EXCLUDED
    }
  }

  fun getTankProductGroup(tank: Tank, entry: ProductionReportTankEntry?, products: List<Product>?): String {
    val pOverride = entry?.productGroupOverride
    if (!pOverride.isNullOrBlank()) return pOverride
    val pMatch = products?.find { it.id == tank.productId || it.name.equals(tank.productId, ignoreCase = true) || it.shortCode.equals(tank.productId, ignoreCase = true) }
    if (pMatch != null && pMatch.erpReportGroup.isNotBlank()) return pMatch.erpReportGroup
    return when {
      tank.name.contains("502") -> "LN"
      tank.name.contains("503") -> "Reformate"
      tank.name.contains("504") -> "Jet A-1"
      tank.name.contains("505A") -> "SKO/JBO/MTT"
      tank.name.contains("505B") -> "SKO/JBO/MTT"
      tank.name.contains("506") || tank.name.contains("507") || tank.name.contains("508") -> "Diesel"
      tank.name.contains("509") -> "MS"
      tank.name.contains("510") -> "Octane"
      tank.name.contains("511") -> "DSN"
      tank.name.contains("512") -> "LPG"
      tank.type == TankType.PRODUCT || tank.type == TankType.BLENDING -> "Other"
      else -> ""
    }
  }

  /**
   * Resolves effective volume for a tank on a given day (either Natural or @30).
   * Directly uses linked tank calibration (Dip table + Fraction table).
   */
  fun resolveTankVolume(
    entry: ProductionReportTankEntry?,
    tank: Tank,
    setup: ProductionReportSetup,
    basis: CalculationBasis,
    isForPrevious: Boolean = false,
    calibrationProvider: ((tankName: String, dipMm: Double) -> Double?)? = null,
    activeVcc: List<DensityCoefficientRow>? = null,
    activeVcf30: List<DensityCoefficientRow>? = null,
    invalidThreshold: Double = 5.0
  ): Pair<Double, Double?> { // (effectiveVolume, resolvedCoefficient)
    if (entry == null) return Pair(0.0, null)

    val effectiveRole = getTankEffectiveRole(tank, entry)
    if (effectiveRole == TankRole.EXCLUDED) {
      return Pair(0.0, null)
    }

    val vcc = activeVcc ?: setup.vccTable
    val vcf30 = activeVcf30 ?: setup.vcf30Table

    val useVolMode = if (isForPrevious) entry.useVolumeModePrev else entry.useVolumeModeToday
    val volInput = if (isForPrevious) (entry.volumePrevOverride ?: entry.volumePrevInput) else entry.volumeTodayInput
    val dipVal = if (isForPrevious) entry.dipMmPrev else entry.dipMmToday
    val tempVal = if (isForPrevious) (entry.tempPrevOverride ?: entry.tempPrevC) else (entry.tempTodayC ?: entry.temperatureC)
    val dens15Val = if (isForPrevious) (entry.densityPrevOverride15 ?: entry.densityPrev15) else (entry.densityToday15 ?: entry.density15)
    val dens30Val = if (isForPrevious) (entry.densityPrevOverride30 ?: entry.densityPrev30) else (entry.densityToday30 ?: entry.density30)

    // 1. Calculate raw measured natural volume
    val measuredNatural = if (!isForPrevious && entry.useSavedVolumeOverride && entry.savedNaturalVolume != null) {
      entry.savedNaturalVolume
    } else if (volInput != null) {
      volInput
    } else if (entry.volumeInput != null) {
      entry.volumeInput
    } else {
      val dip = dipVal ?: entry.dipMm
      if (dip != null && tank.dipTable.isNotEmpty()) {
        Calculators.calculateDipVolume(tank, dip, invalidThreshold)?.totalVolume ?: 0.0
      } else if (dip != null && calibrationProvider != null) {
        calibrationProvider(tank.name, dip) ?: 0.0
      } else {
        0.0
      }
    }

    // Adjusted natural volume = measured + adjustment
    val adjustedNatural = measuredNatural + entry.adjustmentL

    if (basis == CalculationBasis.NATURAL) {
      return Pair(adjustedNatural, null)
    }

    // Basis is 30 °C. Default coefficient source follows role if setup is NONE.
    val defaultCoeffSource = when (effectiveRole) {
      TankRole.FEED -> CoefficientSourceType.VCF30_BY_DENSITY15
      TankRole.PRODUCT -> CoefficientSourceType.VCC_BY_DENSITY30
      TankRole.BLENDING -> CoefficientSourceType.VCC_BY_DENSITY30
      TankRole.EXCLUDED -> CoefficientSourceType.NONE
    }
    val coeffSource = if (tank.coefficientSource != CoefficientSourceType.NONE) tank.coefficientSource else defaultCoeffSource

    return when (coeffSource) {
      CoefficientSourceType.NONE -> {
        Pair(adjustedNatural, null)
      }
      CoefficientSourceType.VCF30_BY_DENSITY15 -> {
        val density15 = dens15Val ?: entry.density15 ?: 0.750
        val (coeff, _) = findCoefficient(density15, vcf30)
        val temp = tempVal ?: entry.temperatureC ?: 30.0
        val vol30 = calculateVolumeAt30(adjustedNatural, temp, coeff)
        Pair(vol30, coeff)
      }
      CoefficientSourceType.VCC_BY_DENSITY30 -> {
        val density30 = dens30Val ?: entry.density30 ?: 0.750
        val (coeff, _) = findCoefficient(density30, vcc)
        val temp = tempVal ?: entry.temperatureC ?: 30.0
        val vol30 = calculateVolumeAt30(adjustedNatural, temp, coeff)
        Pair(vol30, coeff)
      }
    }
  }

  /**
   * Main calculation engine for a single basis (Natural or 30 °C).
   */
  fun computeReport(
    setup: ProductionReportSetup,
    todaySnapshot: ProductionReportDaySnapshot,
    previousSnapshot: ProductionReportDaySnapshot,
    basis: CalculationBasis,
    calibrationProvider: ((tankName: String, dipMm: Double) -> Double?)? = null,
    tanks: List<Tank>? = null,
    invalidThreshold: Double = 5.0,
    products: List<Product>? = null
  ): ProductionReportCalculationResult {
    val warnings = mutableListOf<String>()
    val activeTanks = if (tanks.isNullOrEmpty()) {
      setup.tankMaps.map { tm ->
        Tank(
          id = tm.tankName,
          name = tm.tankName,
          type = if (tm.group == ProductionReportTankGroup.RAW_MATERIAL) TankType.FEED else (if (tm.isBlendingTank) TankType.BLENDING else TankType.PRODUCT),
          includeInProductionReport = true,
          defaultInputMode = InputMode.VOLUME
        )
      }
    } else {
      tanks.filter { it.includeInProductionReport }
    }

    // Determine active tables: stored snapshot vs setup
    val (activeVcc, activeVcf30) = if (todaySnapshot.tableVersionId != null &&
      todaySnapshot.tableVersionId != setup.tableVersionId &&
      !todaySnapshot.useCurrentTablesOverride &&
      todaySnapshot.vccTableSnapshot != null &&
      todaySnapshot.vcf30TableSnapshot != null
    ) {
      Pair(todaySnapshot.vccTableSnapshot, todaySnapshot.vcf30TableSnapshot)
    } else {
      Pair(setup.vccTable, setup.vcf30Table)
    }

    // Check table ascending order
    val vccViolations = validateTableStrictlyAscending(activeVcc)
    if (vccViolations.isNotEmpty()) {
      warnings.add("VCC table has out-of-order rows at indices: ${vccViolations.joinToString()}")
    }
    val vcfViolations = validateTableStrictlyAscending(activeVcf30)
    if (vcfViolations.isNotEmpty()) {
      warnings.add("VCF30 table has out-of-order rows at indices: ${vcfViolations.joinToString()}")
    }

    val missingTanks = mutableListOf<String>()
    if (tanks != null) {
      for (entryName in todaySnapshot.tankEntries.keys) {
        if (tanks.none { it.name.equals(entryName, ignoreCase = true) }) {
          missingTanks.add(entryName)
        }
      }
    }
    for (t in activeTanks) {
      val effRole = getTankEffectiveRole(t, todaySnapshot.tankEntries[t.name])
      if (effRole == TankRole.PRODUCT || effRole == TankRole.BLENDING) {
        val grp = getTankProductGroup(t, todaySnapshot.tankEntries[t.name], products)
        if (grp.isBlank()) {
          missingTanks.add("${t.name}: Missing product group")
        }
      }
    }

    // 1. Resolve raw tanks, product tanks, and blending tanks from Tank Master
    val rawTanks = activeTanks.filter { tank ->
      getTankEffectiveRole(tank, todaySnapshot.tankEntries[tank.name]) == TankRole.FEED
    }
    val productTanks = activeTanks.filter { tank ->
      getTankEffectiveRole(tank, todaySnapshot.tankEntries[tank.name]) == TankRole.PRODUCT
    }
    val blendingTanksList = activeTanks.filter { tank ->
      getTankEffectiveRole(tank, todaySnapshot.tankEntries[tank.name]) == TankRole.BLENDING
    }

    // Source tanks for blending conversions: T-502 (LN) and T-503 (Reformate)
    val t502Entry = todaySnapshot.tankEntries["T-502A"] ?: todaySnapshot.tankEntries["T-502B"]
    val t502Density30 = t502Entry?.densityToday30 ?: t502Entry?.density30 ?: 0.65059
    val (lnCoeff, _) = findCoefficient(t502Density30, activeVcc)
    val lnTempC = t502Entry?.tempTodayC ?: t502Entry?.temperatureC ?: 30.0

    val t503Entry = todaySnapshot.tankEntries["T-503A"] ?: todaySnapshot.tankEntries["T-503B"]
    val t503Density30 = t503Entry?.densityToday30 ?: t503Entry?.density30 ?: 0.760
    val (refCoeff, _) = findCoefficient(t503Density30, activeVcc)
    val refTempC = t503Entry?.tempTodayC ?: t503Entry?.temperatureC ?: 30.0

    // Blending use amounts
    val rawBlend = todaySnapshot.otherData.blendingUse
    val (lnHobc, lnMs, refHobc, refMs) = if (basis == CalculationBasis.NATURAL) {
      listOf(rawBlend.lnHobc, rawBlend.lnMs, rawBlend.reformateHobc, rawBlend.reformateMs)
    } else {
      listOf(
        calculateVolumeAt30(rawBlend.lnHobc, lnTempC, lnCoeff),
        calculateVolumeAt30(rawBlend.lnMs, lnTempC, lnCoeff),
        calculateVolumeAt30(rawBlend.reformateHobc, refTempC, refCoeff),
        calculateVolumeAt30(rawBlend.reformateMs, refTempC, refCoeff)
      )
    }

    // Deliveries & Internal use
    val productDeliveries = todaySnapshot.otherData.productDeliveries
    fun getDeliveryAndInternal(code: String): Pair<Double, Double> {
      val data = productDeliveries[code] ?: ProductDeliveryData()
      if (basis == CalculationBasis.NATURAL || code.equals("LPG", ignoreCase = true)) {
        return Pair(data.deliveredNatural, data.internalNatural)
      } else {
        val groupTank = (productTanks + blendingTanksList).firstOrNull {
          getTankProductGroup(it, todaySnapshot.tankEntries[it.name], products).equals(code, ignoreCase = true)
        }
        val groupEntry = groupTank?.let { todaySnapshot.tankEntries[it.name] }
        val density30 = groupEntry?.densityToday30 ?: groupEntry?.density30 ?: 0.750
        val (coeff, _) = findCoefficient(density30, activeVcc)
        val del30 = data.delivered30Override ?: calculateVolumeAt30(data.deliveredNatural, data.deliveryTempC, coeff)
        val int30 = data.internal30Override ?: calculateVolumeAt30(data.internalNatural, data.deliveryTempC, coeff)
        return Pair(del30, int30)
      }
    }

    val (octDelivered, octInternal) = getDeliveryAndInternal("OCTANE")
    val (msDelivered, msInternal) = getDeliveryAndInternal("MS")
    val (solventDelivered, _) = getDeliveryAndInternal("SOLVENT")
    val (dieselDelivered, dieselInternal) = getDeliveryAndInternal("DIESEL")
    val (jetDelivered, jetInternal) = getDeliveryAndInternal("JET")
    val (jboDelivered, _) = getDeliveryAndInternal("SKO")
    val (lpgDelivered, lpgInternal) = getDeliveryAndInternal("LPG")

    // Process raw tanks
    val calculatedRawTanks = mutableListOf<CalculatedRawTank>()
    var totalFeed = 0.0

    for (tank in rawTanks) {
      val pEntry = previousSnapshot.tankEntries[tank.name]
      val tEntry = todaySnapshot.tankEntries[tank.name]

      val (pVol, _) = resolveTankVolume(pEntry, tank, setup, basis, true, calibrationProvider, activeVcc, activeVcf30, invalidThreshold)
      val (tVol, coeff) = resolveTankVolume(tEntry, tank, setup, basis, false, calibrationProvider, activeVcc, activeVcf30, invalidThreshold)

      val hasRecEntries = tEntry?.receivedEntries?.isNotEmpty() == true
      val naturalRec = if (hasRecEntries) {
        tEntry!!.receivedEntries.sumOf { it.volumeNatural }
      } else {
        todaySnapshot.otherData.ngcReceivedNatural[tank.name] ?: 0.0
      }
      val rec = if (basis == CalculationBasis.NATURAL || coeff == null) {
        naturalRec
      } else {
        if (hasRecEntries) {
          tEntry!!.receivedEntries.sumOf { entry ->
            val t = entry.temperatureC ?: tEntry.tempTodayC ?: tEntry.temperatureC ?: 30.0
            val d = entry.density ?: tEntry.densityToday15 ?: tEntry.density15 ?: 0.736
            val (c, _) = findCoefficient(d, activeVcf30)
            if (c == null) entry.volumeNatural else calculateVolumeAt30(entry.volumeNatural, t, c)
          }
        } else {
          calculateVolumeAt30(naturalRec, tEntry?.tempTodayC ?: tEntry?.temperatureC, coeff)
        }
      }

      val processed = if (tank.name == "T-508") {
        pVol - tVol
      } else {
        pVol - tVol + rec
      }

      totalFeed += processed

      val isVol = tEntry?.useVolumeModeToday == true || tank.dipTable.isEmpty() || tank.defaultInputMode == InputMode.VOLUME
      val inputMode = if (isVol) InputMode.VOLUME else InputMode.DIP
      val adj = tEntry?.adjustmentL ?: 0.0
      val hasAdj = abs(adj) > 0.001

      calculatedRawTanks.add(
        CalculatedRawTank(
          tankName = tank.name,
          productCode = tank.name,
          previousVolume = pVol,
          todayVolume = tVol,
          received = rec,
          processed = processed,
          coefficient = coeff,
          tempC = tEntry?.tempTodayC ?: tEntry?.temperatureC,
          density15 = tEntry?.densityToday15 ?: tEntry?.density15,
          inputMode = inputMode,
          hasAdjustment = hasAdj,
          adjustmentL = adj
        )
      )

      // Validation warnings
      if (tEntry != null) {
        val d30 = tEntry.densityToday30 ?: tEntry.density30
        val d15 = tEntry.densityToday15 ?: tEntry.density15
        if (d30 != null && d15 != null && d30 >= d15) {
          warnings.add("${tank.name}: Density @30 ($d30) >= Density @15 ($d15) is physically invalid.")
        }
      }
    }

    // Process product tanks
    val calculatedProductTanks = mutableListOf<CalculatedProductTank>()
    val tankChangesByTank = mutableMapOf<String, Double>()

    for (tank in productTanks) {
      val pEntry = previousSnapshot.tankEntries[tank.name]
      val tEntry = todaySnapshot.tankEntries[tank.name]

      val (pVol, _) = resolveTankVolume(pEntry, tank, setup, basis, true, calibrationProvider, activeVcc, activeVcf30, invalidThreshold)
      val (tVol, coeff) = resolveTankVolume(tEntry, tank, setup, basis, false, calibrationProvider, activeVcc, activeVcf30, invalidThreshold)

      val changeD = tVol - pVol
      tankChangesByTank[tank.name] = changeD

      val groupCode = getTankProductGroup(tank, tEntry, products).ifBlank { "Other" }

      val hasDelivEntries = tEntry?.deliveredEntries?.isNotEmpty() == true
      val tankDelivered = if (hasDelivEntries) {
        if (basis == CalculationBasis.NATURAL) {
          tEntry!!.deliveredEntries.sumOf { it.volumeNatural }
        } else {
          tEntry!!.deliveredEntries.sumOf { entry ->
            val t = entry.temperatureC ?: tEntry.tempTodayC ?: tEntry.temperatureC ?: 30.0
            val d = entry.density ?: tEntry.densityToday30 ?: tEntry.density30 ?: 0.750
            val (c, _) = findCoefficient(d, activeVcc)
            if (c == null) entry.volumeNatural else calculateVolumeAt30(entry.volumeNatural, t, c)
          }
        }
      } else {
        0.0
      }

      val isVol = tEntry?.useVolumeModeToday == true || tank.dipTable.isEmpty() || tank.defaultInputMode == InputMode.VOLUME
      val inputMode = if (isVol) InputMode.VOLUME else InputMode.DIP
      val adj = tEntry?.adjustmentL ?: 0.0
      val hasAdj = abs(adj) > 0.001

      calculatedProductTanks.add(
        CalculatedProductTank(
          tankName = tank.name,
          productCode = groupCode,
          subProductDesc = tank.linkedProductId.ifBlank { groupCode },
          previousVolume = pVol,
          todayVolume = tVol,
          changeD = changeD,
          delivered = tankDelivered,
          coefficient = coeff,
          tempC = tEntry?.tempTodayC ?: tEntry?.temperatureC,
          density30 = tEntry?.densityToday30 ?: tEntry?.density30,
          inputMode = inputMode,
          hasAdjustment = hasAdj,
          adjustmentL = adj
        )
      )
    }

    // Process blending tanks (T-509 MS, T-510 Octane, etc.)
    val calculatedBlendingTanks = mutableListOf<CalculatedBlendingTank>()
    for (tank in blendingTanksList) {
      val pEntry = previousSnapshot.tankEntries[tank.name]
      val tEntry = todaySnapshot.tankEntries[tank.name]

      val (pVol, _) = resolveTankVolume(pEntry, tank, setup, basis, true, calibrationProvider, activeVcc, activeVcf30, invalidThreshold)
      val (tVol, coeff) = resolveTankVolume(tEntry, tank, setup, basis, false, calibrationProvider, activeVcc, activeVcf30, invalidThreshold)

      val changeD = tVol - pVol

      val (bProdCode, bProdName, delVal, intVal) = when {
        tank.name.contains("510", ignoreCase = true) || tank.linkedProductId.contains("HOBC", ignoreCase = true) || tank.linkedProductId.contains("Octane", ignoreCase = true) -> {
          listOf("OCTANE", "Octane / HOBC", octDelivered, octInternal)
        }
        tank.name.contains("509", ignoreCase = true) || tank.linkedProductId.contains("MS", ignoreCase = true) -> {
          listOf("MS", "MS (Petrol)", msDelivered, msInternal)
        }
        else -> {
          val pGroup = getTankProductGroup(tank, tEntry, products).ifBlank { "Other" }
          val (d, i) = getDeliveryAndInternal(pGroup.uppercase())
          listOf(pGroup.uppercase(), pGroup, d, i)
        }
      }
      val hasBlendDelivEntries = tEntry?.deliveredEntries?.isNotEmpty() == true
      val blendTankDeliv = if (hasBlendDelivEntries) {
        if (basis == CalculationBasis.NATURAL) {
          tEntry!!.deliveredEntries.sumOf { it.volumeNatural }
        } else {
          tEntry!!.deliveredEntries.sumOf { entry ->
            val t = entry.temperatureC ?: tEntry.tempTodayC ?: tEntry.temperatureC ?: 30.0
            val d = entry.density ?: tEntry.densityToday30 ?: tEntry.density30 ?: 0.750
            val (c, _) = findCoefficient(d, activeVcc)
            if (c == null) entry.volumeNatural else calculateVolumeAt30(entry.volumeNatural, t, c)
          }
        }
      } else {
        (delVal as? Double) ?: 0.0
      }
      val dVal = blendTankDeliv
      val iVal = (intVal as? Double) ?: 0.0
      val prodVal = changeD + dVal + iVal

      val isVol = tEntry?.useVolumeModeToday == true || tank.dipTable.isEmpty() || tank.defaultInputMode == InputMode.VOLUME
      val inputMode = if (isVol) InputMode.VOLUME else InputMode.DIP
      val adj = tEntry?.adjustmentL ?: 0.0
      val hasAdj = abs(adj) > 0.001

      calculatedBlendingTanks.add(
        CalculatedBlendingTank(
          tankName = tank.name,
          productCode = bProdCode as String,
          productName = bProdName as String,
          previousVolume = pVol,
          todayVolume = tVol,
          changeD = changeD,
          delivered = dVal,
          internalUsed = iVal,
          production = prodVal,
          coefficient = coeff,
          tempC = tEntry?.tempTodayC ?: tEntry?.temperatureC,
          density30 = tEntry?.densityToday30 ?: tEntry?.density30,
          inputMode = inputMode,
          hasAdjustment = hasAdj,
          adjustmentL = adj
        )
      )
    }

    // Group tank changes by product group
    val groupChanges = mutableMapOf<String, Double>()
    for (cp in calculatedProductTanks) {
      val g = cp.productCode
      groupChanges[g] = (groupChanges[g] ?: 0.0) + cp.changeD
    }

    fun getGroupDelivered(gCode: String, fallback: Double): Double {
      val gTanks = calculatedProductTanks.filter { it.productCode == gCode }
      val sum = gTanks.sumOf { it.delivered }
      return if (sum > 0.0) sum else fallback
    }

    val delivLn = getGroupDelivered("LN", solventDelivered)
    val delivReformate = getGroupDelivered("Reformate", 0.0)
    val delivJet = getGroupDelivered("Jet A-1", jetDelivered)
    val delivSko = getGroupDelivered("SKO/JBO/MTT", jboDelivered)
    val delivDiesel = getGroupDelivered("Diesel", dieselDelivered)
    val delivLpg = getGroupDelivered("LPG", lpgDelivered)
    val delivOctane = calculatedBlendingTanks.filter { it.productCode == "OCTANE" }.sumOf { it.delivered }.let { if (it > 0.0) it else octDelivered }
    val delivMs = calculatedBlendingTanks.filter { it.productCode == "MS" }.sumOf { it.delivered }.let { if (it > 0.0) it else msDelivered }

    // Production per product group
    val prodLn = (groupChanges["LN"] ?: 0.0) + (lnHobc + lnMs) + delivLn
    val prodReformate = (groupChanges["Reformate"] ?: 0.0) + (refHobc + refMs) + delivReformate
    val prodJet = (groupChanges["Jet A-1"] ?: 0.0) + delivJet + jetInternal
    val prodSko = (groupChanges["SKO/JBO/MTT"] ?: 0.0) + delivSko
    val prodDiesel = (groupChanges["Diesel"] ?: 0.0) + delivDiesel + dieselInternal
    val prodLpg = (groupChanges["LPG"] ?: 0.0) + delivLpg + lpgInternal
    val prodDsn = (groupChanges["DSN"] ?: 0.0)
    val prodOctane = lnHobc + refHobc + (groupChanges["Octane"] ?: 0.0)
    val prodMs = lnMs + refMs + (groupChanges["MS"] ?: 0.0)
    val prodOther = (groupChanges["Other"] ?: 0.0)

    // Product groups with per-tank breakdowns and group totals
    val standardGroups = listOf(
      Pair("LN", "Light Naphtha"),
      Pair("Reformate", "Reformate"),
      Pair("Jet A-1", "Kerosene / Jet A-1"),
      Pair("SKO/JBO/MTT", "SKO (JBO+MTT)"),
      Pair("Diesel", "Diesel"),
      Pair("LPG", "LPG"),
      Pair("DSN", "DSN Product")
    )
    val groupCodesInTanks = calculatedProductTanks.map { it.productCode }.distinct()
    val allGroupDefs = mutableListOf<Pair<String, String>>()
    standardGroups.forEach { sg ->
      if (groupCodesInTanks.contains(sg.first)) allGroupDefs.add(sg)
    }
    groupCodesInTanks.filter { gc -> allGroupDefs.none { it.first == gc } }.forEach { gc ->
      allGroupDefs.add(Pair(gc, gc))
    }

    val calculatedProductGroups = allGroupDefs.map { (gCode, gName) ->
      val gTanks = calculatedProductTanks.filter { it.productCode == gCode }
      val totToday = gTanks.sumOf { it.todayVolume }
      val totPrev = gTanks.sumOf { it.previousVolume }
      val totChange = gTanks.sumOf { it.changeD }

      val usedBlend = when (gCode) {
        "LN" -> lnHobc + lnMs
        "Reformate" -> refHobc + refMs
        else -> 0.0
      }
      val deliv = when (gCode) {
        "LN" -> delivLn
        "Reformate" -> delivReformate
        "Jet A-1" -> delivJet
        "SKO/JBO/MTT" -> delivSko
        "Diesel" -> delivDiesel
        "LPG" -> delivLpg
        else -> gTanks.sumOf { it.delivered }
      }
      val internal = when (gCode) {
        "Jet A-1" -> jetInternal
        "Diesel" -> dieselInternal
        "LPG" -> lpgInternal
        else -> 0.0
      }
      val gProd = totChange + usedBlend + deliv + internal
      val pctFeed = if (totalFeed > 0) gProd / totalFeed * 100.0 else 0.0

      CalculatedProductGroup(
        productCode = gCode,
        productName = gName,
        tanks = gTanks,
        totalTodayVolume = totToday,
        totalPreviousVolume = totPrev,
        totalChangeD = totChange,
        usedForBlending = usedBlend,
        delivered = deliv,
        internalUsed = internal,
        groupProduction = gProd,
        percentOfFeed = pctFeed
      )
    }

    // Product summaries
    val productSummaries = mutableListOf(
      ProductProductionSummary("LN", "Light Naphtha", groupChanges["LN"] ?: 0.0, lnHobc + lnMs, delivLn, 0.0, prodLn, if (totalFeed > 0) prodLn / totalFeed * 100.0 else 0.0),
      ProductProductionSummary("REFORMATE", "Reformate", groupChanges["Reformate"] ?: 0.0, refHobc + refMs, delivReformate, 0.0, prodReformate, if (totalFeed > 0) prodReformate / totalFeed * 100.0 else 0.0),
      ProductProductionSummary("JET", "Kerosene / Jet A-1", groupChanges["Jet A-1"] ?: 0.0, 0.0, delivJet, jetInternal, prodJet, if (totalFeed > 0) prodJet / totalFeed * 100.0 else 0.0),
      ProductProductionSummary("SKO", "SKO (JBO+MTT)", groupChanges["SKO/JBO/MTT"] ?: 0.0, 0.0, delivSko, 0.0, prodSko, if (totalFeed > 0) prodSko / totalFeed * 100.0 else 0.0),
      ProductProductionSummary("DIESEL", "Diesel", groupChanges["Diesel"] ?: 0.0, 0.0, delivDiesel, dieselInternal, prodDiesel, if (totalFeed > 0) prodDiesel / totalFeed * 100.0 else 0.0),
      ProductProductionSummary("LPG", "LPG", groupChanges["LPG"] ?: 0.0, 0.0, delivLpg, lpgInternal, prodLpg, if (totalFeed > 0) prodLpg / totalFeed * 100.0 else 0.0),
      ProductProductionSummary("DSN", "DSN Product", groupChanges["DSN"] ?: 0.0, 0.0, 0.0, 0.0, prodDsn, if (totalFeed > 0) prodDsn / totalFeed * 100.0 else 0.0),
      ProductProductionSummary("OCTANE", "Octane / HOBC", groupChanges["Octane"] ?: 0.0, 0.0, delivOctane, octInternal, prodOctane, if (totalFeed > 0) prodOctane / totalFeed * 100.0 else 0.0),
      ProductProductionSummary("MS", "MS (Petrol)", groupChanges["MS"] ?: 0.0, 0.0, delivMs, msInternal, prodMs, if (totalFeed > 0) prodMs / totalFeed * 100.0 else 0.0)
    )

    if (prodOther != 0.0 || (groupChanges["Other"] != null && groupChanges["Other"] != 0.0)) {
      productSummaries.add(
        ProductProductionSummary("OTHER", "Other Products", prodOther, 0.0, 0.0, 0.0, prodOther, if (totalFeed > 0) prodOther / totalFeed * 100.0 else 0.0)
      )
    }

    // Sub totals
    val totalRawToday = calculatedRawTanks.sumOf { it.todayVolume }
    val totalRawPrevious = calculatedRawTanks.sumOf { it.previousVolume }
    val totalRawReceived = calculatedRawTanks.sumOf { it.received }

    val totalProductToday = calculatedProductTanks.sumOf { it.todayVolume }
    val totalProductPrevious = calculatedProductTanks.sumOf { it.previousVolume }
    val productSubTotal = calculatedProductTanks.sumOf { it.changeD }

    val totalBlendingToday = calculatedBlendingTanks.sumOf { it.todayVolume }
    val totalBlendingPrevious = calculatedBlendingTanks.sumOf { it.previousVolume }
    val totalBlendingDelivered = calculatedBlendingTanks.sumOf { it.delivered + it.internalUsed }
    val totalBlendingProduction = calculatedBlendingTanks.sumOf { it.production }

    // Sold / internal sub total = all deliveries + internal used
    val soldInternalSubTotal = solventDelivered +
      jetDelivered + jetInternal +
      jboDelivered +
      dieselDelivered + dieselInternal +
      lpgDelivered + lpgInternal +
      octDelivered + octInternal +
      msDelivered + msInternal

    // Option A: Total product (A) = sum of production of product groups, EXCLUDING blending tanks (Octane/HOBC, MS)
    // where group production = change of group tanks (Today - Previous) + quantities blended out + delivered + internal used
    val totalProductA = calculatedProductGroups.sumOf { it.groupProduction }
    val yieldOptionA = if (totalFeed > 0) (totalProductA / totalFeed) * 100.0 else 0.0
    val lossOptionA = totalFeed - totalProductA
    val lossPercentOptionA = if (totalFeed > 0) (lossOptionA / totalFeed) * 100.0 else 0.0

    // Option B: Total change (B) = sum of (Today - Previous) of ALL Product and Blending tanks + all sold/delivered + internal used
    val tovChangeProductTanks = calculatedProductTanks.sumOf { it.changeD }
    val tovChangeBlendingTanks = calculatedBlendingTanks.sumOf { it.changeD }
    val tovChangeAllTanks = tovChangeProductTanks + tovChangeBlendingTanks
    val allSoldAndInternal = calculatedProductGroups.sumOf { it.delivered + it.internalUsed } +
      calculatedBlendingTanks.sumOf { it.delivered + it.internalUsed }
    val totalChangeB = tovChangeAllTanks + allSoldAndInternal
    val yieldOptionB = if (totalFeed > 0) (totalChangeB / totalFeed) * 100.0 else 0.0
    val lossOptionB = totalFeed - totalChangeB
    val lossPercentOptionB = if (totalFeed > 0) (lossOptionB / totalFeed) * 100.0 else 0.0

    // Difference (A - B) = net gain/loss of blending tanks
    // Blending tank Net = Today - (Previous + blended in) + delivered + internal
    var totalBlendingTanksNet = 0.0
    for (bt in calculatedBlendingTanks) {
      val blendedIn = when {
        bt.productCode == "OCTANE" || bt.tankName.contains("510", ignoreCase = true) -> lnHobc + refHobc
        bt.productCode == "MS" || bt.tankName.contains("509", ignoreCase = true) -> lnMs + refMs
        else -> 0.0
      }
      val net = bt.todayVolume - (bt.previousVolume + blendedIn) + bt.delivered + bt.internalUsed
      totalBlendingTanksNet += net
    }

    val diffAB = totalChangeB - totalProductA
    val isDiffExplainedByBlending = abs(diffAB - totalBlendingTanksNet) <= 1.0

    val diffExplanation = if (isDiffExplainedByBlending) {
      if (abs(diffAB) < 0.01) {
        "Zero difference: Data is fully consistent across both loss methods."
      } else {
        "Difference is fully explained by net blending gain/loss (${String.format(Locale.US, "%,.2f", totalBlendingTanksNet)} L)."
      }
    } else {
      "Amber note: Difference (${String.format(Locale.US, "%,.2f", diffAB)} L) is not explained by blending tanks (${String.format(Locale.US, "%,.2f", totalBlendingTanksNet)} L). Check for excluded tanks or unallocated transfers."
    }

    // Standard report block uses Option B as base per specifications
    val totalProduced = totalChangeB
    val totalLoss = lossOptionB
    val lossPercent = lossPercentOptionB

    // Process waste calculations
    val wasteDensity = setup.constants.processWasteDensityKgL
    val calculatedWasteItems = setup.processWasteMeters.map { meter ->
      val kgH = todaySnapshot.otherData.processWasteKgH[meter.tag] ?: todaySnapshot.otherData.processWasteKgH[meter.id] ?: 0.0
      val litres24h = (kgH * 24.0) / wasteDensity
      val pct = if (totalFeed > 0) litres24h / totalFeed * 100.0 else 0.0
      CalculatedWasteItem(
        id = meter.id,
        name = meter.name,
        tag = meter.tag,
        category = meter.category,
        kgH = kgH,
        litres24h = litres24h,
        percentOfFeed = pct
      )
    }

    val totalMeasuredWasteLitres = calculatedWasteItems.sumOf { it.litres24h }
    val totalMeasuredLossPct = if (totalFeed > 0) totalMeasuredWasteLitres / totalFeed * 100.0 else 0.0
    val unmeasuredLossPct = lossPercent - totalMeasuredLossPct

    val fuelLitres = calculatedWasteItems.filter { it.category == WasteCategory.FUEL }.sumOf { it.litres24h }
    val fuelForHeatersPct = if (totalFeed > 0) fuelLitres / totalFeed * 100.0 else 0.0

    val knhtLitres = calculatedWasteItems.filter { it.category == WasteCategory.KNHT_H2 }.sumOf { it.litres24h }
    val h2MakeUpKnhtPct = if (totalFeed > 0) knhtLitres / totalFeed * 100.0 else 0.0

    val actualFlaringPctOptionA = lossPercentOptionA - fuelForHeatersPct - h2MakeUpKnhtPct
    val actualFlaringPctOptionB = lossPercentOptionB - fuelForHeatersPct - h2MakeUpKnhtPct
    val actualFlaringPct = actualFlaringPctOptionB

    // Dry-out
    val rawTanksToday = calculatedRawTanks.filter { it.tankName in listOf("T-501A", "T-501B", "T-501C", "T-501D") }
    val dryOutStockL = rawTanksToday.sumOf { it.todayVolume }
    val reserveStock = setup.constants.minimumReserveStockL
    val dryOutConsumableL = (dryOutStockL - reserveStock).coerceAtLeast(0.0)
    val dryOutDays = if (totalFeed > 0) dryOutConsumableL / totalFeed else 0.0

    val dryOutDateStr = try {
      val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
      val repDate = sdf.parse("${todaySnapshot.dateStr} ${todaySnapshot.reportTime}") ?: Date()
      val cal = Calendar.getInstance().apply {
        time = repDate
        add(Calendar.MINUTE, (dryOutDays * 24.0 * 60.0).toInt())
      }
      SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(cal.time)
    } catch (_: Exception) {
      "${String.format(Locale.US, "%.1f", dryOutDays)} days"
    }

    // Plant efficiency
    val loads = todaySnapshot.otherData.operatingLoads
    fun calcUnitBblDay(u: OperatingUnitData, designLoad: Double): Pair<Double, Double> {
      val bblDay = when (u.mode) {
        OperatingLoadMode.KG_HR -> if (u.feedDensity > 0) u.flowValue * 24.0 / u.feedDensity / 158.987 else 0.0
        OperatingLoadMode.M3_HR -> u.flowValue * 24.0 / 0.158987
        OperatingLoadMode.BBL_DAY -> u.flowValue
      }
      val eff = if (designLoad > 0) (bblDay / designLoad) * 100.0 else 0.0
      return Pair(bblDay, eff)
    }

    val plantEfficiency = mapOf(
      "CFU" to calcUnitBblDay(loads.cfu, setup.constants.designLoadCfuBblDay).second,
      "KNHT" to calcUnitBblDay(loads.knht, setup.constants.designLoadKnhtBblDay).second,
      "CRU" to calcUnitBblDay(loads.cru, setup.constants.designLoadCruBblDay).second
    )

    // Duration text
    val durationText = "${previousSnapshot.dateStr} 10:00 AM To ${todaySnapshot.dateStr} 10:00 AM"

    val isWasteEntered = todaySnapshot.otherData.processWasteKgH.values.any { it > 0.0 }
    val totalWasteKgH = calculatedWasteItems.sumOf { it.kgH }
    val totalWasteKgDay = calculatedWasteItems.sumOf { it.kgDay }
    val totalWasteLitres = calculatedWasteItems.sumOf { it.litres24h }

    val initialResult = ProductionReportCalculationResult(
      basis = basis,
      reportDate = todaySnapshot.dateStr,
      previousDate = previousSnapshot.dateStr,
      durationText = durationText,
      rawTanks = calculatedRawTanks,
      totalFeed = totalFeed,
      productTanks = calculatedProductTanks,
      productSummaries = productSummaries,
      productSubTotal = productSubTotal,
      soldInternalSubTotal = soldInternalSubTotal,
      totalProduced = totalProduced,
      totalLoss = totalLoss,
      lossPercent = lossPercent,
      wasteItems = calculatedWasteItems,
      totalMeasuredLossPct = totalMeasuredLossPct,
      unmeasuredLossPct = unmeasuredLossPct,
      fuelForHeatersPct = fuelForHeatersPct,
      h2MakeUpKnhtPct = h2MakeUpKnhtPct,
      actualFlaringPct = actualFlaringPct,
      isWasteEntered = isWasteEntered,
      totalWasteKgH = totalWasteKgH,
      totalWasteKgDay = totalWasteKgDay,
      totalWasteLitres = totalWasteLitres,
      dryOutStockL = dryOutStockL,
      dryOutConsumableL = dryOutConsumableL,
      dryOutDays = dryOutDays,
      dryOutDateStr = dryOutDateStr,
      plantEfficiency = plantEfficiency,
      warnings = warnings,
      missingTanks = missingTanks,
      isBlocked = missingTanks.isNotEmpty(),
      totalRawToday = totalRawToday,
      totalRawPrevious = totalRawPrevious,
      totalRawReceived = totalRawReceived,
      productGroups = calculatedProductGroups,
      totalProductToday = totalProductToday,
      totalProductPrevious = totalProductPrevious,
      blendingTanks = calculatedBlendingTanks,
      totalBlendingToday = totalBlendingToday,
      totalBlendingPrevious = totalBlendingPrevious,
      totalBlendingDelivered = totalBlendingDelivered,
      totalBlendingProduction = totalBlendingProduction,
      totalProductA = totalProductA,
      yieldOptionA = yieldOptionA,
      lossOptionA = lossOptionA,
      lossPercentOptionA = lossPercentOptionA,
      totalChangeB = totalChangeB,
      yieldOptionB = yieldOptionB,
      lossOptionB = lossOptionB,
      lossPercentOptionB = lossPercentOptionB,
      diffAB = diffAB,
      blendingTanksNet = totalBlendingTanksNet,
      isDiffExplainedByBlending = isDiffExplainedByBlending,
      diffExplanation = diffExplanation,
      actualFlaringPctOptionA = actualFlaringPctOptionA,
      actualFlaringPctOptionB = actualFlaringPctOptionB,
      tovChangeProductTanks = tovChangeProductTanks,
      tovChangeBlendingTanks = tovChangeBlendingTanks,
      tovChangeAllTanks = tovChangeAllTanks,
      totalProducedOptionB = totalChangeB,
      productYieldOptionA = yieldOptionA,
      productYieldOptionB = yieldOptionB,
      diffLossMethods = diffAB
    )
    return initialResult.copy(
      erpRows = computeErpRows(setup, todaySnapshot, previousSnapshot, initialResult)
    )
  }

  /**
   * Computes the ERP rows (30 °C basis only) using configurable ErpRowConfigs.
   */
  fun computeErpRows(
    setup: ProductionReportSetup,
    todaySnapshot: ProductionReportDaySnapshot,
    previousSnapshot: ProductionReportDaySnapshot,
    calc30Result: ProductionReportCalculationResult
  ): List<ErpRowData> {
    val rows = mutableListOf<ErpRowData>()

    // Maps of all available calculated tanks
    val allTanks = calc30Result.productTanks.map { Triple(it.tankName, it.previousVolume, it.todayVolume) } +
      calc30Result.blendingTanks.map { Triple(it.tankName, it.previousVolume, it.todayVolume) } +
      calc30Result.rawTanks.map { Triple(it.tankName, it.previousVolume, it.todayVolume) }

    fun getPrevStockForTanks(names: List<String>): Double {
      return allTanks.filter { it.first in names }.sumOf { it.second }
    }

    fun getTodayStockForTanks(names: List<String>): Double {
      return allTanks.filter { it.first in names }.sumOf { it.third }
    }

    val lnSummary = calc30Result.productSummaries.find { it.productCode == "LN" }
    val refSummary = calc30Result.productSummaries.find { it.productCode == "REFORMATE" }
    val lnBlendingUsed = lnSummary?.usedForBlending ?: 0.0
    val refBlendingUsed = refSummary?.usedForBlending ?: 0.0

    val activeVcc = if (todaySnapshot.tableVersionId != null &&
      todaySnapshot.tableVersionId != setup.tableVersionId &&
      !todaySnapshot.useCurrentTablesOverride &&
      todaySnapshot.vccTableSnapshot != null
    ) {
      todaySnapshot.vccTableSnapshot
    } else {
      setup.vccTable
    }
    val t502Entry = todaySnapshot.tankEntries["T-502A"] ?: todaySnapshot.tankEntries["T-502B"]
    val t502Density30 = t502Entry?.densityToday30 ?: t502Entry?.density30 ?: 0.65059
    val (lnCoeff, _) = findCoefficient(t502Density30, activeVcc)
    val lnTempC = t502Entry?.tempTodayC ?: t502Entry?.temperatureC ?: 30.0

    val t503Entry = todaySnapshot.tankEntries["T-503A"] ?: todaySnapshot.tankEntries["T-503B"]
    val t503Density30 = t503Entry?.densityToday30 ?: t503Entry?.density30 ?: 0.760
    val (refCoeff, _) = findCoefficient(t503Density30, activeVcc)
    val refTempC = t503Entry?.tempTodayC ?: t503Entry?.temperatureC ?: 30.0

    val rawBlend = todaySnapshot.otherData.blendingUse
    val (lnHobc, lnMs, refHobc, refMs) = if (calc30Result.basis == CalculationBasis.NATURAL) {
      listOf(rawBlend.lnHobc, rawBlend.lnMs, rawBlend.reformateHobc, rawBlend.reformateMs)
    } else {
      listOf(
        calculateVolumeAt30(rawBlend.lnHobc, lnTempC, lnCoeff),
        calculateVolumeAt30(rawBlend.lnMs, lnTempC, lnCoeff),
        calculateVolumeAt30(rawBlend.reformateHobc, refTempC, refCoeff),
        calculateVolumeAt30(rawBlend.reformateMs, refTempC, refCoeff)
      )
    }

    // Finished product configs: use snapshot from saved day if not overridden
    val activeFinishedRowConfigs = if (!todaySnapshot.useCurrentErpRowsOverride && !todaySnapshot.erpRowConfigsSnapshot.isNullOrEmpty()) {
      todaySnapshot.erpRowConfigsSnapshot
    } else {
      setup.erpRowConfigs.ifEmpty { ProductionReportSetup.defaultErpRowConfigs() }
    }.filter { !it.isHidden }.sortedBy { it.sortOrder }

    for (rowCfg in activeFinishedRowConfigs) {
      val assignedTanks = rowCfg.assignedTankNames
      val prevStock = getPrevStockForTanks(assignedTanks)
      val todayStock = getTodayStockForTanks(assignedTanks)

      val summary = calc30Result.productSummaries.find { it.productCode.equals(rowCfg.itemCode, ignoreCase = true) }
      val blendUsed = when {
        rowCfg.itemCode.equals("LN", ignoreCase = true) || assignedTanks.any { it.contains("502") } -> lnBlendingUsed
        rowCfg.itemCode.equals("REFORMATE", ignoreCase = true) || assignedTanks.any { it.contains("503") } -> refBlendingUsed
        else -> 0.0
      }

      val tankDeliveredSum = assignedTanks.sumOf { tName ->
        calc30Result.productTanks.find { it.tankName == tName }?.delivered
          ?: calc30Result.blendingTanks.find { it.tankName == tName }?.delivered
          ?: 0.0
      }
      val delivered = if (tankDeliveredSum > 0.0) tankDeliveredSum else (summary?.delivered ?: (todaySnapshot.otherData.productDeliveries[rowCfg.itemCode]?.deliveredNatural ?: 0.0))
      val internalFromSummary = summary?.internalUsed ?: (todaySnapshot.otherData.productDeliveries[rowCfg.itemCode]?.internalNatural ?: 0.0)

      when (rowCfg.rowType) {
        ErpRowType.STANDARD -> {
          // Standard product: Production/Received = Closing - Opening + Used for blending + Delivered + Internal
          val internalUseOrEvap = internalFromSummary
          val production = todayStock - prevStock + blendUsed + delivered + internalUseOrEvap
          val closingCalc = todayStock
          val diff = 0.0
          rows.add(
            ErpRowData(
              itemCode = rowCfg.itemCode,
              itemName = rowCfg.itemName,
              openingStock = prevStock,
              productionOrReceived = production,
              usedForBlending = blendUsed,
              delivered = delivered,
              internalUseOrEvap = internalUseOrEvap,
              closingStock = closingCalc,
              physicalStock = todayStock,
              difference = diff,
              isRaw = false,
              rowType = ErpRowType.STANDARD,
              assignedTankNames = assignedTanks,
              note = rowCfg.note
            )
          )
        }

        ErpRowType.BLENDING -> {
          // Blending product: Production/Received = blended components received into its tanks (from the blending lines of the day)
          // Internal use/Evaporation = Opening + Production - Delivered - Closing (balancing figure, editable override)
          val blendedIn = when {
            rowCfg.itemCode.equals("OCTANE", ignoreCase = true) || assignedTanks.any { it.contains("510") } || rowCfg.itemName.contains("Octane", ignoreCase = true) || rowCfg.itemName.contains("HOBC", ignoreCase = true) -> {
              lnHobc + refHobc
            }
            rowCfg.itemCode.equals("MS", ignoreCase = true) || assignedTanks.any { it.contains("509") } || rowCfg.itemName.contains("MS", ignoreCase = true) || rowCfg.itemName.contains("Petrol", ignoreCase = true) -> {
              lnMs + refMs
            }
            else -> 0.0
          }
          val production = blendedIn
          val rawInternalBalancing = prevStock + production - delivered - todayStock
          val internalUseOrEvap = when {
            rowCfg.itemCode.equals("OCTANE", ignoreCase = true) -> todaySnapshot.otherData.octaneBalancingOverride ?: rawInternalBalancing
            rowCfg.itemCode.equals("MS", ignoreCase = true) -> todaySnapshot.otherData.msBalancingOverride ?: rawInternalBalancing
            else -> rawInternalBalancing
          }
          val closingCalc = prevStock + production - delivered - internalUseOrEvap
          val diff = closingCalc - todayStock
          rows.add(
            ErpRowData(
              itemCode = rowCfg.itemCode,
              itemName = rowCfg.itemName,
              openingStock = prevStock,
              productionOrReceived = production,
              usedForBlending = 0.0,
              delivered = delivered,
              internalUseOrEvap = internalUseOrEvap,
              closingStock = closingCalc,
              physicalStock = todayStock,
              difference = diff,
              isRaw = false,
              rowType = ErpRowType.BLENDING,
              assignedTankNames = assignedTanks,
              note = rowCfg.note
            )
          )
        }

        ErpRowType.CONSUMED_AS_FEED -> {
          // Consumed as feed: Internal use = Previous - Today of its tanks; Production = 0
          val internalUse = (prevStock - todayStock).coerceAtLeast(0.0)
          val production = 0.0
          val closingCalc = todayStock
          rows.add(
            ErpRowData(
              itemCode = rowCfg.itemCode,
              itemName = rowCfg.itemName,
              openingStock = prevStock,
              productionOrReceived = production,
              usedForBlending = 0.0,
              delivered = 0.0,
              internalUseOrEvap = internalUse,
              closingStock = closingCalc,
              physicalStock = todayStock,
              difference = 0.0,
              isRaw = false,
              rowType = ErpRowType.CONSUMED_AS_FEED,
              assignedTankNames = assignedTanks,
              note = rowCfg.note
            )
          )
        }
      }
    }

    // Raw Materials block
    val activeRawConfigs = if (!todaySnapshot.useCurrentErpRowsOverride && !todaySnapshot.rawMaterialRowConfigsSnapshot.isNullOrEmpty()) {
      todaySnapshot.rawMaterialRowConfigsSnapshot
    } else {
      setup.rawMaterialRowConfigs.ifEmpty { ProductionReportSetup.defaultRawMaterialRowConfigs() }
    }.filter { !it.isHidden }.sortedBy { it.sortOrder }

    for (rawCfg in activeRawConfigs) {
      var usedFromSplits = 0.0
      var openingFromSplits = 0.0
      var receivedFromSplits = 0.0
      var closingFromSplits = 0.0

      for ((tankName, splitPct) in rawCfg.tankSplits) {
        val rt = calc30Result.rawTanks.find { it.tankName.equals(tankName, ignoreCase = true) }
        if (rt != null) {
          val frac = (splitPct / 100.0).coerceIn(0.0, 1.0)
          usedFromSplits += rt.processed * frac
          openingFromSplits += rt.previousVolume * frac
          receivedFromSplits += rt.received * frac
          closingFromSplits += rt.todayVolume * frac
        }
      }

      val op = todaySnapshot.otherData.rawCategoryOpenings[rawCfg.itemCode] ?: openingFromSplits
      val rec = todaySnapshot.otherData.rawCategoryReceipts[rawCfg.itemCode] ?: receivedFromSplits
      val cl = op + rec - usedFromSplits
      val phys = if (abs(closingFromSplits) > 0.001) closingFromSplits else cl

      rows.add(
        ErpRowData(
          itemCode = rawCfg.itemCode,
          itemName = rawCfg.itemName,
          openingStock = op,
          productionOrReceived = rec,
          usedForBlending = 0.0,
          delivered = 0.0,
          internalUseOrEvap = usedFromSplits,
          closingStock = cl,
          physicalStock = phys,
          difference = cl - phys,
          isRaw = true,
          rowType = ErpRowType.STANDARD,
          assignedTankNames = rawCfg.tankSplits.keys.toList(),
          note = ""
        )
      )
    }

    return rows
  }
}

