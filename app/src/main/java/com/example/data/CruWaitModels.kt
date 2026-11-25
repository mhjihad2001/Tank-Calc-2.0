package com.example.data

import java.util.UUID

enum class PressureUnit(val label: String) {
  KG_CM2_G("kg/cm² g"),
  BAR_G("bar g"),
  PSIG("psig");

  val symbol: String get() = label
}

data class CruLookupTables(
  // Fig 16, WHSV (hr-1), x from 0.8 step 0.1 (25 values)
  val fig16WhsvX: List<Double> = (0..24).map { 0.8 + it * 0.1 },
  val fig16WhsvY: List<Double> = listOf(
    -15.6, -14.0, -12.5, -11.0, -9.6, -8.2, -6.8, -5.6, -4.4, -3.2,
    -2.1, -1.0, 0.0, 1.0, 2.0, 2.9, 3.8, 4.6, 5.4, 6.2,
    6.9, 7.5, 8.1, 8.7, 9.3
  ),

  // Fig 17, Pressure (kg/cm2 g), x from 10 step 1 (26 values)
  val fig17PressureX: List<Double> = (0..25).map { 10.0 + it * 1.0 },
  val fig17PressureY: List<Double> = listOf(
    -7.1, -6.2, -5.4, -4.6, -3.8, -3.1, -2.4, -1.7, -1.1, -0.5,
    0.0, 0.5, 1.0, 1.5, 1.9, 2.4, 2.8, 3.3, 3.7, 4.2,
    4.6, 5.1, 5.6, 6.1, 6.7, 7.2
  ),

  // Fig 18, RON clear, x from 90 step 0.5 (25 values)
  val fig18RonX: List<Double> = (0..24).map { 90.0 + it * 0.5 },
  val fig18RonY: List<Double> = listOf(
    -16.5, -15.3, -14.3, -13.4, -12.4, -11.5, -10.5, -9.4, -8.4, -7.4,
    -6.3, -5.2, -4.2, -3.1, -2.0, -1.0, 0.2, 1.4, 2.7, 4.1,
    5.6, 7.1, 8.8, 10.5, 12.2
  ),

  // Fig 19, A+0.85N (vol %), x from 24 step 1 (27 values)
  val fig19AnX: List<Double> = (0..26).map { 24.0 + it * 1.0 },
  val fig19AnY: List<Double> = listOf(
    9.5, 8.7, 7.9, 7.1, 6.3, 5.5, 4.7, 3.9, 3.1, 2.3,
    1.5, 0.7, -0.1, -0.8, -1.6, -2.5, -3.3, -4.1, -4.9, -5.7,
    -6.5, -7.3, -8.1, -8.9, -9.7, -10.5, -11.3
  ),

  // Fig 20, MABP (C), x from 100 step 2 (26 values)
  val fig20MabpX: List<Double> = (0..25).map { 100.0 + it * 2.0 },
  val fig20MabpY: List<Double> = listOf(
    4.6, 4.4, 4.2, 3.9, 3.6, 3.3, 2.9, 2.6, 2.2, 1.8,
    1.3, 0.9, 0.4, -0.1, -0.7, -1.3, -1.9, -2.5, -3.1, -3.8,
    -4.4, -5.1, -5.8, -6.6, -7.4, -8.3
  ),

  // Fig 22, SOR WAIT (C) versus RON, x from 92 step 0.5 (21 values)
  val fig22RonX: List<Double> = (0..20).map { 92.0 + it * 0.5 },
  val fig22An25: List<Double> = listOf(
    494.6, 495.5, 496.5, 497.5, 498.5, 499.6, 500.7, 501.8, 502.9, 504.0,
    505.1, 506.2, 507.4, 508.6, 509.8, 511.1, 512.4, 513.8, 515.3, 516.9, 518.6
  ),
  val fig22An35: List<Double> = listOf(
    489.5, 490.4, 491.4, 492.4, 493.4, 494.4, 495.4, 496.5, 497.6, 498.7,
    499.8, 500.9, 502.1, 503.4, 504.7, 506.0, 507.4, 508.8, 510.3, 511.8, 513.4
  ),
  val fig22An45: List<Double> = listOf(
    483.3, 484.2, 485.2, 486.2, 487.2, 488.3, 489.3, 490.4, 491.6, 492.7,
    493.8, 495.0, 496.2, 497.4, 498.7, 500.0, 501.4, 502.8, 504.2, 505.7, 507.3
  ),
  val fig22An55: List<Double> = listOf(
    477.7, 478.4, 479.2, 480.1, 481.0, 482.0, 483.0, 484.0, 485.1, 486.2,
    487.3, 488.5, 489.7, 490.9, 492.2, 493.5, 494.9, 496.4, 498.0, 499.7, 501.5
  ),

  // Reference conditions of Fig 22 (editable)
  val refWhsv: Double = 2.0,
  val refPressureBarG: Double = 13.5, // 13.5 bar g -> converted to kg/cm2g internally
  val refMabp: Double = 125.6
) {
  val refMabpC: Double get() = refMabp
  val fig16WhsvValues: List<Double> get() = fig16WhsvY
  val fig17PressureValues: List<Double> get() = fig17PressureY
  val fig18RonValues: List<Double> get() = fig18RonY
  val fig19NaphthenesAromaticsValues: List<Double> get() = fig19AnY
  val fig20MabpValues: List<Double> get() = fig20MabpY
  val fig22SorWait25: List<Double> get() = fig22An25
  val fig22SorWait35: List<Double> get() = fig22An35
  val fig22SorWait45: List<Double> get() = fig22An45
  val fig22SorWait55: List<Double> get() = fig22An55
}

data class CruReactorBed(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val catalystWeightTonnes: Double,
  val inletTempC: Double = 490.0,
  val outletTempC: Double = 430.0
) {
  val catalystWeightTon: Double get() = catalystWeightTonnes
  val bedNumber: Int get() = 1
}

data class CruActivityRecord(
  val id: String = UUID.randomUUID().toString(),
  val dateTime: String,
  val runDay: String = "",
  val ronClear: Double = 0.0,
  val anIndex: Double = 0.0,
  val pressureVal: Double = 0.0,
  val pressureUnit: PressureUnit = PressureUnit.BAR_G,
  val whsv: Double = 0.0,
  val mabp: Double = 0.0,
  val actualWait: Double = 0.0,
  val sorWaitBase: Double = 0.0,
  val pressCorr: Double = 0.0,
  val whsvCorr: Double = 0.0,
  val mabpCorr: Double = 0.0,
  val correctedSorWait: Double = 0.0,
  val activityLoss: Double = 0.0,
  val remarks: String = ""
) {
  val ron: Double get() = ronClear
  val naphthenesAromatics: Double get() = anIndex
  val pressure: Double get() = pressureVal
  val deltaPressure: Double get() = pressCorr
  val deltaWhsv: Double get() = whsvCorr
  val deltaMabp: Double get() = mabpCorr
}
