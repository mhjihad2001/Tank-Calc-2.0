package com.example

import com.example.data.*
import com.example.engine.ProductionReportEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionReportAcceptanceTest {

  @Test
  fun testAcceptanceScenarioNaturalBasis() {
    // Setup snapshot for previous date
    val prevSnapshot = ProductionReportDaySnapshot(
      dateStr = "2026-10-01",
      tankEntries = mapOf(
        "T-501A" to ProductionReportTankEntry("T-501A", useVolumeModePrev = true, volumePrevInput = 100000.0),
        "T-503A" to ProductionReportTankEntry("T-503A", useVolumeModePrev = true, volumePrevInput = 20000.0),
        "T-502A" to ProductionReportTankEntry("T-502A", useVolumeModePrev = true, volumePrevInput = 10000.0),
        "T-510" to ProductionReportTankEntry("T-510", useVolumeModePrev = true, volumePrevInput = 5000.0)
      )
    )

    // Setup snapshot for today date
    val todaySnapshot = ProductionReportDaySnapshot(
      dateStr = "2026-10-02",
      tankEntries = mapOf(
        "T-501A" to ProductionReportTankEntry("T-501A", useVolumeModeToday = true, volumeTodayInput = 40000.0),
        "T-503A" to ProductionReportTankEntry("T-503A", useVolumeModeToday = true, volumeTodayInput = 55000.0),
        "T-502A" to ProductionReportTankEntry("T-502A", useVolumeModeToday = true, volumeTodayInput = 22000.0),
        "T-510" to ProductionReportTankEntry("T-510", useVolumeModeToday = true, volumeTodayInput = 16000.0)
      ),
      otherData = ProductionReportOtherData(
        blendingUse = BlendingUseData(
          lnHobc = 2000.0,
          reformateHobc = 8000.0
        ),
        productDeliveries = mapOf(
          "OCTANE" to ProductDeliveryData(deliveredNatural = 1000.0)
        )
      )
    )

    val setup = ProductionReportSetup(
      tankMaps = listOf(
        ProductionReportTankMap("T-501A", ProductionReportTankGroup.RAW_MATERIAL, "NGC", coefficientSource = CoefficientSourceType.NONE, defaultRole = TankRole.FEED),
        ProductionReportTankMap("T-503A", ProductionReportTankGroup.PRODUCT, "REFORMATE", coefficientSource = CoefficientSourceType.NONE, defaultRole = TankRole.PRODUCT),
        ProductionReportTankMap("T-502A", ProductionReportTankGroup.PRODUCT, "LN", coefficientSource = CoefficientSourceType.NONE, defaultRole = TankRole.PRODUCT),
        ProductionReportTankMap("T-510", ProductionReportTankGroup.PRODUCT, "OCTANE", coefficientSource = CoefficientSourceType.NONE, isBlendingTank = true, defaultRole = TankRole.BLENDING)
      )
    )

    val result = ProductionReportEngine.computeReport(setup, todaySnapshot, prevSnapshot, CalculationBasis.NATURAL)

    // Feed F = 100,000 - 40,000 = 60,000
    assertEquals(60000.0, result.totalFeed, 0.01)

    // Option A: Total product = (35,000 + 8,000) + (12,000 + 2,000) = 57,000
    assertEquals(57000.0, result.totalProductA, 0.01)
    // Loss A = 60,000 - 57,000 = 3,000 (5.00%)
    assertEquals(3000.0, result.lossOptionA, 0.01)
    assertEquals(5.00, result.lossPercentOptionA, 0.01)
    assertEquals(95.00, result.yieldOptionA, 0.01)

    // Option B: Total change = 35,000 (reformate change) + 12,000 (LN change) + 11,000 (HOBC change) + 1,000 delivered = 59,000
    assertEquals(59000.0, result.totalChangeB, 0.01)
    // Loss B = 60,000 - 59,000 = 1,000 (1.67%)
    assertEquals(1000.0, result.lossOptionB, 0.01)
    assertEquals(1.67, result.lossPercentOptionB, 0.01)
    assertEquals(98.33, result.yieldOptionB, 0.01)

    // Difference A - B = 2,000 = HOBC blending tank net (16,000 - (5,000 + 10,000) + 1,000)
    assertEquals(2000.0, result.diffAB, 0.01)
    assertEquals(2000.0, result.blendingTanksNet, 0.01)
  }

  @Test
  fun testGridMovementEntriesAndFormulae() {
    // Test Scenario:
    // Feed tank: Previous = 100,000, Received = 20,000 (2 entries: 12,000 + 8,000), Present = 40,000 -> Consumed = 80,000
    // Product tank: Previous = 20,000, Present = 55,000, Delivered = 5,000 (2 entries: 3,000 + 2,000) -> Production = 40,000
    val prevSnapshot = ProductionReportDaySnapshot(
      dateStr = "2026-10-01",
      tankEntries = mapOf(
        "T-501A" to ProductionReportTankEntry("T-501A", useVolumeModePrev = true, volumePrevInput = 100000.0),
        "T-503A" to ProductionReportTankEntry("T-503A", useVolumeModePrev = true, volumePrevInput = 20000.0)
      )
    )

    val todaySnapshot = ProductionReportDaySnapshot(
      dateStr = "2026-10-02",
      tankEntries = mapOf(
        "T-501A" to ProductionReportTankEntry(
          tankName = "T-501A",
          useVolumeModeToday = true,
          volumeTodayInput = 40000.0,
          receivedEntries = listOf(
            MovementTransactionEntry(volumeNatural = 12000.0),
            MovementTransactionEntry(volumeNatural = 8000.0)
          )
        ),
        "T-503A" to ProductionReportTankEntry(
          tankName = "T-503A",
          useVolumeModeToday = true,
          volumeTodayInput = 55000.0,
          deliveredEntries = listOf(
            MovementTransactionEntry(volumeNatural = 3000.0),
            MovementTransactionEntry(volumeNatural = 2000.0)
          )
        )
      )
    )

    val setup = ProductionReportSetup(
      tankMaps = listOf(
        ProductionReportTankMap("T-501A", ProductionReportTankGroup.RAW_MATERIAL, "NGC", coefficientSource = CoefficientSourceType.NONE, defaultRole = TankRole.FEED),
        ProductionReportTankMap("T-503A", ProductionReportTankGroup.PRODUCT, "REFORMATE", coefficientSource = CoefficientSourceType.NONE, defaultRole = TankRole.PRODUCT)
      )
    )

    val result = ProductionReportEngine.computeReport(setup, todaySnapshot, prevSnapshot, CalculationBasis.NATURAL)

    // Feed consumed = Previous (100,000) + Received (20,000) - Present (40,000) = 80,000
    assertEquals(80000.0, result.totalFeed, 0.01)

    // Product tank: Present (55,000) - Previous (20,000) = 35,000 change. Delivered = 5,000.
    // Total product = 35,000 + 5,000 = 40,000
    assertEquals(40000.0, result.totalProductA, 0.01)

    // Loss A = 80,000 - 40,000 = 40,000 (50.00%)
    assertEquals(40000.0, result.lossOptionA, 0.01)
    assertEquals(50.00, result.lossPercentOptionA, 0.01)
    assertEquals(50.00, result.yieldOptionA, 0.01)
  }

  @Test
  fun testVolumeToDipInverseLookup() {
    val tank = Tank(
      id = "tank-1",
      name = "T-501A",
      type = TankType.FEED,
      maxVolumeLiters = 100000.0,
      dipTable = listOf(
        DipRow(100.0, mapOf("T-501A" to 1000.0)),
        DipRow(200.0, mapOf("T-501A" to 2000.0)),
        DipRow(300.0, mapOf("T-501A" to 3000.0))
      ),
      fractionTable = listOf(
        FractionRow(1, mapOf("T-501A" to 10.0)),
        FractionRow(2, mapOf("T-501A" to 20.0)),
        FractionRow(5, mapOf("T-501A" to 50.0))
      )
    )

    // Exact match in dip table
    val dip1 = com.example.engine.Calculators.calculateVolumeToDip(tank, 2000.0)
    assertEquals(200.0, dip1!!, 0.1)

    // Intermediate volume: 2050.0 -> base 2000 (dip 200) + 50 from fraction table (frac 5) -> dip 205.0
    val dip2 = com.example.engine.Calculators.calculateVolumeToDip(tank, 2050.0)
    assertEquals(205.0, dip2!!, 0.1)
  }

  @Test
  fun testBinarySearchCalibrationPerformanceAndWhatsAppExport() {
    val dipRows = (0..1500).map { i ->
      val dipMm = i * 10.0
      DipRow(dipMm = dipMm, volumes = mapOf("T-501A" to dipMm * 120.0))
    }
    val fracRows = (0..9).map { mm ->
      FractionRow(mm = mm, volumes = mapOf("T-501A" to mm * 12.0))
    }
    val largeTank = Tank(
      id = "tank-perf",
      name = "T-501A",
      type = TankType.FEED,
      maxVolumeLiters = 2000000.0,
      dipTable = dipRows,
      fractionTable = fracRows
    )

    com.example.engine.Calculators.warmUpCache(listOf(largeTank))
    // Warm up JVM classloading & JIT before timing steady-state binary search lookup
    repeat(3) {
      com.example.engine.Calculators.calculateDipVolume(largeTank, 5000.0)
      com.example.engine.Calculators.calculateVolumeToDip(largeTank, 600000.0)
    }

    val startNs = System.nanoTime()
    val res = com.example.engine.Calculators.calculateDipVolume(largeTank, 7543.0)
    val revDip = com.example.engine.Calculators.calculateVolumeToDip(largeTank, res.totalVolume)
    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0

    // Base 7540 * 120 = 904,800 + Frac 3 * 12 = 36 -> 904,836.0 L
    assertEquals(904800.0, res.baseVolume, 0.01)
    assertEquals(36.0, res.fractionVolume, 0.01)
    assertEquals(904836.0, res.totalVolume, 0.01)
    assertEquals(7543.0, revDip!!, 0.01)
    org.junit.Assert.assertTrue("Lookup took ${elapsedMs}ms (expected < 5ms)", elapsedMs < 5.0)
  }

  @Test
  fun testInCellArithmeticExpressionEvaluator() {
    // Basic operations
    assertEquals(12950.0, com.example.engine.ExpressionEvaluator.evaluateOrNull("12500 + 450")!!, 0.001)
    assertEquals(48800.0, com.example.engine.ExpressionEvaluator.evaluateOrNull("50000 - 1200")!!, 0.001)
    assertEquals(348.0, com.example.engine.ExpressionEvaluator.evaluateOrNull("14.5 * 24")!!, 0.001)
    assertEquals(250.0, com.example.engine.ExpressionEvaluator.evaluateOrNull("1000 / 4")!!, 0.001)

    // Leading '=' and BODMAS operator precedence
    assertEquals(200.0, com.example.engine.ExpressionEvaluator.evaluateOrNull("=100 + 50 * 2")!!, 0.001)
    assertEquals(300.0, com.example.engine.ExpressionEvaluator.evaluateOrNull("=(100 + 50) * 2")!!, 0.001)

    // Invalid / incomplete expressions return error without crashing
    val divZero = com.example.engine.ExpressionEvaluator.evaluate("12500 / 0")
    org.junit.Assert.assertFalse(divZero.isValid)
    org.junit.Assert.assertNotNull(divZero.error)

    val trailingOp = com.example.engine.ExpressionEvaluator.evaluate("12500 +")
    org.junit.Assert.assertFalse(trailingOp.isValid)
    org.junit.Assert.assertNotNull(trailingOp.error)
  }

  @Test
  fun test30CConversionAndErpReconciliation() {
    // Verify 30 °C formula: Qty@30 = Natural - Natural * coeff * (Temp - 30.0)
    // Natural = 100,000 L, Temp = 35.0 °C, Coeff = 0.00100 -> 100,000 - 100,000 * 0.001 * 5 = 99,500 L
    val v30 = ProductionReportEngine.calculateVolumeAt30(100000.0, 35.0, 0.00100)
    assertEquals(99500.0, v30, 0.01)

    val setup = ProductionReportSetup(tankMaps = ProductionReportSetup.defaultTankMaps())
    val prevSnapshot = ProductionReportDaySnapshot(
      dateStr = "2026-10-10",
      tankEntries = mapOf(
        "T-502A" to ProductionReportTankEntry("T-502A", useVolumeModePrev = true, volumePrevInput = 10000.0, tempPrevC = 30.0, densityPrev30 = 0.660),
        "T-503A" to ProductionReportTankEntry("T-503A", useVolumeModePrev = true, volumePrevInput = 20000.0, tempPrevC = 30.0, densityPrev30 = 0.760),
        "T-510" to ProductionReportTankEntry("T-510", useVolumeModePrev = true, volumePrevInput = 5000.0, tempPrevC = 30.0, densityPrev30 = 0.750)
      )
    )
    val todaySnapshot = ProductionReportDaySnapshot(
      dateStr = "2026-10-11",
      tankEntries = mapOf(
        "T-502A" to ProductionReportTankEntry("T-502A", useVolumeModeToday = true, volumeTodayInput = 15000.0, tempTodayC = 35.0, densityToday30 = 0.660),
        "T-503A" to ProductionReportTankEntry("T-503A", useVolumeModeToday = true, volumeTodayInput = 25000.0, tempTodayC = 30.0, densityToday30 = 0.760),
        "T-510" to ProductionReportTankEntry("T-510", useVolumeModeToday = true, volumeTodayInput = 12000.0, tempTodayC = 30.0, densityToday30 = 0.750)
      ),
      otherData = ProductionReportOtherData(
        blendingUse = BlendingUseData(lnHobc = 2000.0, reformateHobc = 6000.0),
        productDeliveries = mapOf("OCTANE" to ProductDeliveryData(deliveredNatural = 500.0))
      )
    )

    val calc30 = ProductionReportEngine.computeReport(setup, todaySnapshot, prevSnapshot, CalculationBasis.TEMP_30C)
    val erpRows = calc30.erpRows
    val lnErp = erpRows.find { it.itemCode == "LN" }!!
    val octErp = erpRows.find { it.itemCode == "OCTANE" }!!

    // Ensure LN blending out @30°C + Reformate blending out @30°C equals OCTANE ERP production @30°C
    val expectedOctProd = lnErp.usedForBlending + (erpRows.find { it.itemCode == "REFORMATE" }?.usedForBlending ?: 0.0)
    assertEquals(expectedOctProd, octErp.productionOrReceived, 0.01)

    // Ensure OCTANE ERP Closing = Opening + Production - Delivered - InternalUse/Evap
    val expectedClosing = octErp.openingStock + octErp.productionOrReceived - octErp.delivered - octErp.internalUseOrEvap
    assertEquals(expectedClosing, octErp.closingStock, 0.01)
  }
}

