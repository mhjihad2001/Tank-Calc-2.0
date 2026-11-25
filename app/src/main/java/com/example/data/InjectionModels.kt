package com.example.data

import java.util.UUID

data class DosingTank(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val diameterMm: Double,
  val heightMm: Double,
  val overrideLitresPerMm: Double? = null, // if null, auto-calculated
  val linkedChemicalName: String,
  val isDefault: Boolean = true
) {
  val calculatedLitresPerMm: Double
    get() = if (overrideLitresPerMm != null && overrideLitresPerMm > 0.0) {
      overrideLitresPerMm
    } else {
      val radius = diameterMm / 2.0
      val pi = Math.PI
      (pi * radius * radius / 1_000_000.0) * 1000.0
    }

  val capacityLiters: Double
    get() = calculatedLitresPerMm * heightMm

  val mmPerLitre: Double
    get() = if (calculatedLitresPerMm > 0) 1.0 / calculatedLitresPerMm else 0.0
}

data class Chemical(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val densityKgL: Double, // kg/L (also 1000 * density = kg/m3)
  val activeFractionPercent: Double, // e.g. 100.0, 5.0, 1.0, 10.0
  val elementFactor: Double = 1.0, // e.g. 0.885 for Cl
  val dosingBasisStreamName: String = "CRU feed (kg/hr)",
  val targetWtPpm: Double = 5.0,
  val minPpm: Double = 4.0,
  val maxPpm: Double = 6.0,
  val productHeaderType: String = "General",
  val isDefault: Boolean = true,
  val activeBatchId: String? = null,
  val activeBatchDate: String? = null
) {
  val densityKgM3: Double
    get() = densityKgL * 1000.0

  val activeFraction: Double
    get() = activeFractionPercent / 100.0
}

data class DailyInjectionRecord(
  val id: String = UUID.randomUUID().toString(),
  val date: String,
  val tankName: String,
  val chemicalName: String,
  val dosingMmDay: Double,
  val dosingLHr: Double = 0.0,
  val basisStreamKgHr: Double,
  val snapshotTank: DosingTank,
  val snapshotChemical: Chemical,
  val timestamp: String,
  val dosingPointName: String = "",
  val wtPpm: Double = 0.0,
  val status: String = ""
) {
  val effectiveLHr: Double
    get() = if (dosingLHr > 0.0) dosingLHr else (dosingMmDay * snapshotTank.calculatedLitresPerMm / 24.0)

  val effectiveMmDay: Double
    get() = if (dosingMmDay > 0.0) dosingMmDay else (if (snapshotTank.calculatedLitresPerMm > 0) dosingLHr * 24.0 / snapshotTank.calculatedLitresPerMm else 0.0)
}

data class SolutionPreset(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val neatChemicalName: String,
  val neatDensityKgL: Double,
  val solventName: String,
  val strengthPercent: Double,
  val basisString: String,
  val defaultTankName: String,
  val drumSizeKg: Double = 200.0,
  val isDefault: Boolean = true
)

data class SolutionBatchRecord(
  val id: String = UUID.randomUUID().toString(),
  val dateTime: String,
  val presetName: String,
  val plannedStrengthPercent: Double,
  val actualStrengthPercent: Double,
  val neatVolumeL: Double,
  val solventVolumeL: Double,
  val totalVolumeL: Double,
  val tankName: String,
  val initialLevelMm: Double,
  val finalLevelMm: Double,
  val actualFinalLevelMm: Double,
  val remarks: String = "",
  val chemicalName: String = ""
)

data class WashWaterRecord(
  val date: String,
  val actualLitrePerDay: Double,
  val weeklyPlanLitre: Double = 650.0
)
