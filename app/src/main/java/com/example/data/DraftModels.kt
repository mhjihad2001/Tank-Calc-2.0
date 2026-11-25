package com.example.data

import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class DraftRecord(
  val id: String = UUID.randomUUID().toString(),
  val moduleKey: String, // e.g. "dip_calc", "n2_calc", "transfer_calc", "loss_calc", "prod_report", "injection", "solution", "vcf", "cru_wait"
  val moduleTitle: String,
  val contextKey: String = "", // e.g. report date pair "2026-10-26_2026-10-27" or tank ID
  val payloadJson: String,
  val lastModified: Long = System.currentTimeMillis()
)
