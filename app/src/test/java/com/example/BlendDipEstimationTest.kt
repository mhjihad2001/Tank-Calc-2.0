package com.example

import com.example.data.*
import com.example.engine.Calculators
import org.junit.Assert.*
import org.junit.Test

class BlendDipEstimationTest {

  private fun createStandardTank(name: String = "T-201"): Tank {
    // 10mm step tank: 100 L per 1mm (1000 L per 10mm)
    val dipRows = (0..100).map { step ->
      val mm = (step * 10).toDouble()
      DipRow(dipMm = mm, volumes = mapOf(name to mm * 100.0))
    }
    val fracRows = (0..9).map { mm ->
      FractionRow(mm = mm, volumes = mapOf(name to mm * 100.0))
    }
    return Tank(
      name = name,
      type = TankType.BLENDING,
      maxVolumeLiters = 100900.0,
      dipTable = dipRows,
      fractionTable = fracRows,
      dipCalibrationMode = DipCalibrationMode.BASE_10MM_PLUS_FRACTION
    )
  }

  private fun createDirectMmTank(name: String = "512A"): Tank {
    // Direct 1mm calibrated LPG tank: 50 L per 1mm
    val dipRows = (0..500).map { mm ->
      DipRow(dipMm = mm.toDouble(), volumes = mapOf(name to mm * 50.0))
    }
    return Tank(
      name = name,
      type = TankType.PRODUCT,
      maxVolumeLiters = 25000.0,
      dipTable = dipRows,
      fractionTable = emptyList(),
      dipCalibrationMode = DipCalibrationMode.AUTO
    )
  }

  @Test
  fun estimateTargetDip_standardTank_calculatesExactDipAndDelta() {
    val tank = createStandardTank()
    // Initial dip = 200 mm -> 20,000 L; transfer = +15,400 L -> target = 35,400 L -> 354 mm
    val res = Calculators.estimateTargetDip(
      tank = tank,
      initialDipMm = 200.0,
      transferVolumeL = 15400.0
    )

    assertEquals(20000.0, res.initialVolumeL, 0.01)
    assertEquals(35400.0, res.targetVolumeL, 0.01)
    assertNotNull(res.estimatedDipMm)
    assertEquals(354.0, res.estimatedDipMm!!, 0.01)
    assertEquals(154.0, res.dipDeltaMm!!, 0.01)
    assertFalse(res.exceedsCapacity)
    assertNull(res.warning)
  }

  @Test
  fun estimateTargetDip_direct1mmTank_calculatesWithoutFraction() {
    val tank = createDirectMmTank("512A")
    // Initial dip = 100 mm -> 5,000 L; transfer = +3,650 L -> target = 8,650 L -> 173 mm
    val res = Calculators.estimateTargetDip(
      tank = tank,
      initialDipMm = 100.0,
      transferVolumeL = 3650.0
    )

    assertEquals(5000.0, res.initialVolumeL, 0.01)
    assertEquals(8650.0, res.targetVolumeL, 0.01)
    assertEquals(173.0, res.estimatedDipMm!!, 0.01)
    assertEquals(73.0, res.dipDeltaMm!!, 0.01)
    assertFalse(res.exceedsCapacity)
    assertNull(res.warning)
  }

  @Test
  fun estimateTargetDip_exceedsCapacity_flagsWarning() {
    val tank = createStandardTank()
    // Initial dip = 900 mm -> 90,000 L; transfer = +25,000 L -> target = 115,000 L (> 100,900 L)
    val res = Calculators.estimateTargetDip(
      tank = tank,
      initialDipMm = 900.0,
      transferVolumeL = 25000.0
    )

    assertTrue(res.exceedsCapacity)
    assertNotNull(res.warning)
    assertTrue(res.warning!!.contains("capacity", ignoreCase = true))
  }

  @Test
  fun estimateTargetDip_invalidInitialDip_returnsErrorWarning() {
    val tank = createStandardTank()
    val res = Calculators.estimateTargetDip(
      tank = tank,
      initialDipMm = 5000.0,
      transferVolumeL = 5000.0
    )

    assertNull(res.estimatedDipMm)
    assertNotNull(res.warning)
  }
}
