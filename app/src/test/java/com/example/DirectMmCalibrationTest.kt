package com.example

import com.example.data.*
import com.example.engine.Calculators
import org.junit.Assert.*
import org.junit.Test

class DirectMmCalibrationTest {

  private fun buildLpgTank(
    name: String = "512A",
    mode: DipCalibrationMode = DipCalibrationMode.AUTO,
    withStaleFraction: Boolean = false
  ): Tank {
    // Direct 1mm calibrated chart (e.g. LPG bullet 512A: 0 mm .. 20 mm)
    val dipRows = (0..20).map { mm ->
      DipRow(dipMm = mm.toDouble(), volumes = mapOf(name to (mm * 125.5)))
    }
    val fracRows = if (withStaleFraction) {
      (0..9).map { mm ->
        FractionRow(mm = mm, volumes = mapOf(name to (mm * 999.0)))
      }
    } else {
      emptyList()
    }
    return Tank(
      id = "lpg-$name",
      name = name,
      type = TankType.PRODUCT,
      shape = TankShape.HORIZONTAL,
      maxVolumeLiters = 50000.0,
      dipTable = dipRows,
      fractionTable = fracRows,
      dipCalibrationMode = mode
    )
  }

  @Test
  fun autoDetectsDirect1mmTankAndSkipsFraction() {
    val tank = buildLpgTank("512A", DipCalibrationMode.AUTO, withStaleFraction = false)
    assertTrue(Calculators.isDirectPerMmTank(tank))

    val res = Calculators.calculateDipVolume(tank, 13.0)
    assertFalse(res.invalidInput)
    assertNull(res.warning)
    assertTrue(res.usedDirectMmLookup)
    assertEquals(13, res.baseDip)
    assertEquals(0, res.fracMm)
    assertEquals(0.0, res.fractionVolume, 1e-6)
    assertEquals(13 * 125.5, res.totalVolume, 1e-6)
  }

  @Test
  fun explicitDirect1mmModeIgnoresEvenStaleFractionTable() {
    val tank = buildLpgTank("512B", DipCalibrationMode.DIRECT_1MM, withStaleFraction = true)
    assertTrue(Calculators.isDirectPerMmTank(tank))

    val res = Calculators.calculateDipVolume(tank, 17.0)
    assertFalse(res.invalidInput)
    assertNull(res.warning)
    assertTrue(res.usedDirectMmLookup)
    assertEquals(0.0, res.fractionVolume, 1e-6)
    assertEquals(17 * 125.5, res.totalVolume, 1e-6)
  }

  @Test
  fun volumeToDipUsesExactDirectMmWithoutAddingFraction() {
    val tank = buildLpgTank("512C", DipCalibrationMode.DIRECT_1MM, withStaleFraction = true)
    val dip = Calculators.calculateVolumeToDip(tank, 14 * 125.5)
    assertNotNull(dip)
    assertEquals(14.0, dip!!, 1e-6)
  }

  @Test
  fun decimalInterpolationOnDirect1mmTankKeepsZeroFraction() {
    val tank = buildLpgTank("512D", DipCalibrationMode.AUTO)
    val res = Calculators.calculateDipVolume(tank, 12.5, interpolateDecimal = true)
    assertFalse(res.invalidInput)
    assertTrue(res.usedDirectMmLookup)
    assertEquals(0.0, res.fractionVolume, 1e-6)
    assertEquals(12.5 * 125.5, res.totalVolume, 1e-6)
  }

  @Test
  fun standard10mmPlusFractionTankRemainsUnchanged() {
    val dipRows = listOf(
      DipRow(0.0, mapOf("T-101" to 0.0)),
      DipRow(10.0, mapOf("T-101" to 1000.0)),
      DipRow(20.0, mapOf("T-101" to 2000.0))
    )
    val fracRows = (0..9).map { mm ->
      FractionRow(mm, mapOf("T-101" to mm * 100.0))
    }
    val standardTank = Tank(
      id = "std-101",
      name = "T-101",
      type = TankType.PRODUCT,
      dipTable = dipRows,
      fractionTable = fracRows,
      dipCalibrationMode = DipCalibrationMode.AUTO
    )
    assertFalse(Calculators.isDirectPerMmTank(standardTank))

    val res = Calculators.calculateDipVolume(standardTank, 14.0)
    assertFalse(res.invalidInput)
    assertNull(res.warning)
    assertFalse(res.usedDirectMmLookup)
    assertEquals(10, res.baseDip)
    assertEquals(4, res.fracMm)
    assertEquals(1000.0, res.baseVolume, 1e-6)
    assertEquals(400.0, res.fractionVolume, 1e-6)
    assertEquals(1400.0, res.totalVolume, 1e-6)
  }
}
