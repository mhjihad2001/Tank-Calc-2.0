package com.example

import com.example.data.*
import com.example.engine.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VcfVccDualEngineTest {

  @Test
  fun testDensity15And30RoundTripConversion() {
    val rho15 = 750.0
    val rho30 = VcfEngine.convertDensity15To30(rho15, VcfTableType.TABLE_54B)
    assertTrue("Expected rho30 ($rho30) < rho15 ($rho15)", rho30 < rho15)
    assertTrue("Expected realistic rho30 (~732-744 kg/m³), got $rho30", rho30 in 732.0..744.0)

    val recoveredRho15 = VcfEngine.convertDensity30To15(rho30, VcfTableType.TABLE_54B)
    assertEquals("Round-trip rho15 should match within 0.001 kg/m³", rho15, recoveredRho15, 0.001)
  }

  @Test
  fun testDensityNormalizationAcceptsKgLAndKgM3() {
    assertEquals(750.0, VcfEngine.normalizeDensityKgM3(0.750), 1e-6)
    assertEquals(750.0, VcfEngine.normalizeDensityKgM3(750.0), 1e-6)
    assertEquals(0.750, VcfEngine.normalizeDensityKgL(750.0), 1e-6)
    assertEquals(0.750, VcfEngine.normalizeDensityKgL(0.750), 1e-6)
  }

  @Test
  fun testDual30CCalculationWith15CAnd30CDensityProducesMatchingResults() {
    val obsVol = 50000.0
    val obsTemp = 24.5
    val rho15 = 752.0
    val rho30 = VcfEngine.convertDensity15To30(rho15, VcfTableType.TABLE_54B)

    val from15 = VcfEngine.calculateDual30C(
      observedVolume = obsVol,
      observedTemp = obsTemp,
      inputDensity = rho15,
      densityRefTemp = DensityRefTemp.AT_15C,
      tableType = VcfTableType.TABLE_54B,
      round4 = true,
      roundTemp025 = false,
      preferredMethod = TempCorrectionMethod.VCF_ASTM
    )

    val from30 = VcfEngine.calculateDual30C(
      observedVolume = obsVol,
      observedTemp = obsTemp,
      inputDensity = rho30,
      densityRefTemp = DensityRefTemp.AT_30C,
      tableType = VcfTableType.TABLE_54B,
      round4 = true,
      roundTemp025 = false,
      preferredMethod = TempCorrectionMethod.VCC_TABLE
    )

    assertEquals(from15.rho15KgM3, from30.rho15KgM3, 0.01)
    assertEquals(from15.rho30KgM3, from30.rho30KgM3, 0.01)

    assertEquals(from15.volume30Vcf, from30.volume30Vcf, 5.0)
    assertEquals(from15.volume30Vcc, from30.volume30Vcc, 5.0)
    assertTrue("Expected deviation < 0.25%, got ${from15.deviationVcfVsVccPercent}%", from15.deviationVcfVsVccPercent < 0.25)

    assertEquals(from15.volume30Vcf, from15.selectedVolume30, 1e-6)
    assertEquals(from30.volume30Vcc, from30.selectedVolume30, 1e-6)
  }

  @Test
  fun testEditableVccAndAstmTablesAffectOutput() {
    val customVcc = listOf(
      DensityCoefficientRow(0.600, 0.00200),
      DensityCoefficientRow(0.900, 0.00100)
    )
    val baseRes = VcfEngine.calculateDual30C(
      observedVolume = 10000.0,
      observedTemp = 20.0,
      inputDensity = 0.740,
      densityRefTemp = DensityRefTemp.AT_30C,
      vccTable = ProductionReportSetup.defaultVccTable(),
      preferredMethod = TempCorrectionMethod.VCC_TABLE
    )
    val customRes = VcfEngine.calculateDual30C(
      observedVolume = 10000.0,
      observedTemp = 20.0,
      inputDensity = 0.740,
      densityRefTemp = DensityRefTemp.AT_30C,
      vccTable = customVcc,
      preferredMethod = TempCorrectionMethod.VCC_TABLE
    )

    assertEquals(0.00200, customRes.vccCoefficient, 1e-9)
    assertEquals(10200.0, customRes.volume30Vcc, 1e-4)
    assertTrue("Custom VCC table should change calculated 30°C volume", abs(customRes.volume30Vcc - baseRes.volume30Vcc) > 50.0)

    val customBands = VcfEngine.defaultVcfBandParams().map {
      if (it.id == "54B_GASOLINE") it.copy(k0 = 400.0) else it
    }
    val astmDefault = VcfEngine.calculateVcf(740.0, 20.0, round4 = false)
    val astmCustom = VcfEngine.calculateVcf(740.0, 20.0, round4 = false, customBands = customBands)
    assertTrue("Edited ASTM K0 should change alpha15", astmCustom.alpha15 > astmDefault.alpha15)
  }

  @Test
  fun testLossEngine30CComputesDualVcfVsVccComparison() {
    val feed = listOf(
      FeedTankCalcInput(
        tankName = "T-101",
        initialTov = 100000.0,
        finalTov = 50000.0,
        receivingAmount = 0.0,
        density15 = 738.0,
        initialTemp = 22.0,
        finalTemp = 26.0
      )
    )
    val prod = listOf(
      ProductTankCalcInput(
        tankName = "T-201",
        initialTov = 10000.0,
        finalTov = 59000.0,
        deliveryAmount = 0.0,
        density15 = 735.0,
        initialTemp = 25.0,
        finalTemp = 28.0
      )
    )

    val resVcf = LossEngine.computeLoss(
      LossComputationInput(
        mode = CalculationMode.TEMP_30C,
        startTime = "2026-01-01 08:00",
        endTime = "2026-01-02 08:00",
        durationHours = 24.0,
        feedTanks = feed,
        productTanks = prod,
        products = emptyList(),
        densityRefTemp = DensityRefTemp.AT_30C,
        correctionMethod = TempCorrectionMethod.VCF_ASTM
      )
    )

    val resVcc = LossEngine.computeLoss(
      LossComputationInput(
        mode = CalculationMode.TEMP_30C,
        startTime = "2026-01-01 08:00",
        endTime = "2026-01-02 08:00",
        durationHours = 24.0,
        feedTanks = feed,
        productTanks = prod,
        products = emptyList(),
        densityRefTemp = DensityRefTemp.AT_30C,
        correctionMethod = TempCorrectionMethod.VCC_TABLE
      )
    )

    assertNotNull(resVcf.dualComparison)
    assertNotNull(resVcc.dualComparison)
    assertEquals(resVcf.dualComparison!!.vcfLoss, resVcf.loss, 1e-4)
    assertEquals(resVcc.dualComparison!!.vccLoss, resVcc.loss, 1e-4)
    assertEquals(resVcf.dualComparison!!.vcfLoss - resVcf.dualComparison!!.vccLoss, resVcf.dualComparison!!.lossDeviationVcfVsVccL, 1e-4)
  }
}
