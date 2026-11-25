package com.example.engine

import com.example.data.VcfTableType
import kotlin.math.exp

data class VcfResult(
  val vcf: Double,
  val alpha15: Double,
  val dT: Double,
  val bandName: String,
  val warning: String?
)

object VcfEngine {

  fun calculateVcf(
    rho15: Double, // Density at 15 °C (kg/m3)
    observedTemp: Double, // Temperature in °C
    tableType: VcfTableType = VcfTableType.TABLE_54B,
    round4: Boolean = true,
    roundTemp025: Boolean = false
  ): VcfResult {
    val t = if (roundTemp025) {
      Math.round(observedTemp * 4.0) / 4.0
    } else {
      observedTemp
    }

    val dT = t - 15.0
    var alpha15 = 0.0
    var bandName = ""
    var warning: String? = null

    when (tableType) {
      VcfTableType.TABLE_54A -> {
        // Crude Oil: K0 = 613.9723, K1 = 0, K2 = 0
        bandName = "Table 54A (Crude Oil)"
        if (rho15 < 653.0 || rho15 > 1074.0) {
          warning = "Density ${rho15} is outside Table 54A range (653 - 1074 kg/m³)."
        }
        val k0 = 613.9723
        alpha15 = k0 / (rho15 * rho15)
      }
      VcfTableType.TABLE_54B -> {
        if (rho15 >= 653.0 && rho15 <= 770.5) {
          bandName = "Table 54B - Gasoline Range (653.0 - 770.5)"
          val k0 = 346.4228
          val k1 = 0.4388
          alpha15 = k0 / (rho15 * rho15) + k1 / rho15
        } else if (rho15 > 770.5 && rho15 < 787.5) {
          bandName = "Table 54B - Transition Range (770.5 - 787.5)"
          alpha15 = -0.00336312 + 2680.3206 / (rho15 * rho15)
        } else if (rho15 >= 787.5 && rho15 <= 838.5) {
          bandName = "Table 54B - Jet / Kerosene (787.5 - 838.5)"
          val k0 = 594.5418
          alpha15 = k0 / (rho15 * rho15)
        } else if (rho15 > 838.5 && rho15 <= 1163.5) {
          bandName = "Table 54B - Fuel Oil (838.5 - 1163.5)"
          val k0 = 186.9696
          val k1 = 0.4862
          alpha15 = k0 / (rho15 * rho15) + k1 / rho15
        } else {
          bandName = "Table 54B - Generalized Products (Out of Standard Range)"
          warning = "Density ${rho15} is outside standard Table 54B range (653.0 - 1163.5 kg/m³)."
          val k0 = 346.4228
          val k1 = 0.4388
          alpha15 = k0 / (rho15 * rho15) + k1 / rho15
        }
      }
      VcfTableType.TABLE_54D -> {
        bandName = "Table 54D (Lubricating Oils)"
        if (rho15 < 800.0 || rho15 > 1163.0) {
          warning = "Density ${rho15} is outside Table 54D range."
        }
        val k0 = 0.0 // lubricating oil default standard
        alpha15 = 0.00045 // standard lube approx expansion coefficient
      }
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
}
