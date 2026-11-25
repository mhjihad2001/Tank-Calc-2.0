package com.example.engine

import com.example.data.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

data class BlendingLineInput(
  val id: String = UUID.randomUUID().toString(),
  val amount: Double = 0.0,
  val destinationTankName: String? = null,
  val time: String? = null
) {
  fun toBlendingLine() = BlendingLine(
    id = id,
    amount = amount,
    destinationTankName = destinationTankName,
    time = time
  )
}

data class FeedTankCalcInput(
  val tankName: String,
  val initialTov: Double,
  val finalTov: Double,
  val receivingAmount: Double = 0.0,
  val density15: Double? = null,
  val initialTemp: Double? = null,
  val finalTemp: Double? = null,
  val isInitialSynced: Boolean = false,
  val isFinalSynced: Boolean = false
)

data class ProductTankCalcInput(
  val tankName: String,
  val initialTov: Double,
  val finalTov: Double,
  val blendingLines: List<BlendingLineInput> = emptyList(),
  val deliveryAmount: Double = 0.0,
  val density15: Double? = null,
  val initialTemp: Double? = null,
  val finalTemp: Double? = null,
  val productId: String? = null,
  val isInitialSynced: Boolean = false,
  val isFinalSynced: Boolean = false
)

data class BlendingTankCalcInput(
  val tankName: String,
  val initialTov: Double,
  val finalTov: Double,
  val deliveryAmount: Double = 0.0,
  val density15: Double? = null,
  val initialTemp: Double? = null,
  val finalTemp: Double? = null,
  val blendedProductId: String? = null,
  val isInitialSynced: Boolean = false,
  val isFinalSynced: Boolean = false
)

data class LossComputationInput(
  val mode: CalculationMode,
  val startTime: String,
  val endTime: String,
  val durationHours: Double,
  val feedTanks: List<FeedTankCalcInput>,
  val productTanks: List<ProductTankCalcInput>,
  val blendingTanks: List<BlendingTankCalcInput> = emptyList(),
  val products: List<Product>,
  val vcfTableType: VcfTableType = VcfTableType.TABLE_54B,
  val roundVcfTo4Decimals: Boolean = true,
  val roundTempTo025: Boolean = false,
  val lossThresholdPercent: Double = 1.5
)

data class LossComputationResult(
  val mode: CalculationMode,
  val startTime: String,
  val endTime: String,
  val durationHours: Double,
  val feedTanks: List<LossTankEntry>,
  val productTanks: List<LossTankEntry>,
  val blendingTanks: List<LossTankEntry> = emptyList(),
  val totalFeed: Double,
  val totalProduct: Double,
  val loss: Double,
  val lossPercent: Double,
  val yieldPercent: Double,
  val feedRatePerHour: Double,
  val productRatePerHour: Double,
  val lossRatePerHour: Double,
  val flag: String,
  val productYields: List<ProductYieldEntry>,
  val feedTankRatios: List<FeedTankRatioEntry> = emptyList(),
  val totalProductPercent: Double?,
  val lossPercentOnFeed: Double?,
  val productTankLinksSnapshot: Map<String, String>,
  val warnings: List<String>
) {
  fun toLossReport(id: String = UUID.randomUUID().toString()): LossReport {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    return LossReport(
      id = id,
      mode = mode,
      startTime = startTime,
      endTime = endTime,
      durationHours = durationHours,
      feedTanks = feedTanks,
      productTanks = productTanks,
      blendingTanks = blendingTanks,
      totalFeed = totalFeed,
      totalProduct = totalProduct,
      loss = loss,
      lossPercent = lossPercent,
      yieldPercent = yieldPercent,
      feedRatePerHour = feedRatePerHour,
      productRatePerHour = productRatePerHour,
      lossRatePerHour = lossRatePerHour,
      flag = flag,
      timestamp = timestamp,
      productYields = productYields,
      feedTankRatios = feedTankRatios,
      totalProductPercent = totalProductPercent,
      lossPercentOnFeed = lossPercentOnFeed,
      productTankLinksSnapshot = productTankLinksSnapshot,
      warnings = warnings
    )
  }
}

object LossEngine {

  /**
   * Pure calculation engine function for refinery loss calculation.
   * Fully decoupled, deterministic, and unit-testable.
   */
  fun computeLoss(input: LossComputationInput): LossComputationResult {
    val warnings = mutableListOf<String>()

    // 1. Process Feed Tanks
    val calculatedFeedTanks = input.feedTanks.map { feed ->
      val initTov = feed.initialTov
      val finalTov = feed.finalTov
      val rec = feed.receivingAmount

      if (input.mode == CalculationMode.TEMP_30C) {
        val density = feed.density15 ?: 750.0
        val tInit = feed.initialTemp ?: 25.0
        val tFinal = feed.finalTemp ?: 25.0

        val vcfInit = VcfEngine.calculateVcf(density, tInit, input.vcfTableType, input.roundVcfTo4Decimals, input.roundTempTo025)
        val vcfFinal = VcfEngine.calculateVcf(density, tFinal, input.vcfTableType, input.roundVcfTo4Decimals, input.roundTempTo025)
        val vcf30 = VcfEngine.calculateVcf(density, 30.0, input.vcfTableType, input.roundVcfTo4Decimals, input.roundTempTo025)

        val initTov30 = if (vcf30.vcf > 0) initTov * vcfInit.vcf / vcf30.vcf else initTov
        val finalTov30 = if (vcf30.vcf > 0) finalTov * vcfFinal.vcf / vcf30.vcf else finalTov
        val netQty = initTov30 - finalTov30 + rec

        LossTankEntry(
          tankName = feed.tankName,
          initialTov = initTov,
          finalTov = finalTov,
          receivingAmount = rec,
          density15 = feed.density15,
          initialTemp = feed.initialTemp,
          finalTemp = feed.finalTemp,
          initialVcf = vcfInit.vcf,
          finalVcf = vcfFinal.vcf,
          initialTov30 = initTov30,
          finalTov30 = finalTov30,
          netQuantity = netQty,
          isInitialSynced = feed.isInitialSynced,
          isFinalSynced = feed.isFinalSynced
        )
      } else {
        val netQty = initTov - finalTov + rec
        LossTankEntry(
          tankName = feed.tankName,
          initialTov = initTov,
          finalTov = finalTov,
          receivingAmount = rec,
          density15 = feed.density15,
          initialTemp = feed.initialTemp,
          finalTemp = feed.finalTemp,
          netQuantity = netQty,
          isInitialSynced = feed.isInitialSynced,
          isFinalSynced = feed.isFinalSynced
        )
      }
    }

    // 2. Validate Blending Lines & Build Blended-In Mapping
    val runningProdTankNames = input.productTanks.map { it.tankName }.toSet()
    val runningBlendingTankNames = input.blendingTanks.map { it.tankName }.toSet()
    val allRunningDestinations = runningProdTankNames + runningBlendingTankNames

    val blendedInMap = mutableMapOf<String, Double>() // destination -> total blended in
    val blendedInSourcesMap = mutableMapOf<String, MutableList<Pair<String, Double>>>() // destination -> [(source, amount)]
    val blendGraph = mutableMapOf<String, MutableSet<String>>() // source -> destinations

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val periodStart = try { dateFormat.parse(input.startTime.trim()) } catch (_: Exception) { null }
    val periodEnd = try { dateFormat.parse(input.endTime.trim()) } catch (_: Exception) { null }

    for (pTank in input.productTanks) {
      for (line in pTank.blendingLines) {
        val amount = line.amount
        if (amount < 0) {
          warnings.add("Negative blending amount (${amount}) on ${pTank.tankName} was clamped to 0.")
        }
        val validAmount = amount.coerceAtLeast(0.0)
        val dest = line.destinationTankName?.trim()

        if (!dest.isNullOrBlank()) {
          if (dest.equals(pTank.tankName, ignoreCase = true)) {
            warnings.add("Blending destination on ${pTank.tankName} cannot be itself.")
          } else {
            // Track graph for circular blending detection
            blendGraph.getOrPut(pTank.tankName) { mutableSetOf() }.add(dest)

            if (allRunningDestinations.contains(dest)) {
              blendedInMap[dest] = (blendedInMap[dest] ?: 0.0) + validAmount
              blendedInSourcesMap.getOrPut(dest) { mutableListOf() }.add(Pair(pTank.tankName, validAmount))
            } else {
              warnings.add("Destination tank '$dest' for blend from ${pTank.tankName} is not a running tank in this period.")
            }
          }
        }

        // Validate time against period
        if (!line.time.isNullOrBlank() && periodStart != null && periodEnd != null) {
          val tStr = line.time.trim()
          val lineDate = try {
            if (tStr.length <= 5 && tStr.contains(":")) {
              val cal = Calendar.getInstance().apply { time = periodStart }
              val parts = tStr.split(":")
              cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
              cal.set(Calendar.MINUTE, parts[1].toInt())
              cal.time
            } else {
              dateFormat.parse(tStr)
            }
          } catch (_: Exception) { null }

          if (lineDate != null && (lineDate.before(periodStart) || lineDate.after(periodEnd))) {
            warnings.add("Blending line at ${line.time} on ${pTank.tankName} falls outside the period range (${input.startTime} to ${input.endTime}).")
          }
        }
      }
    }

    // Check for circular blending (A -> B and B -> A or longer cycles)
    val visited = mutableSetOf<String>()
    val stack = mutableSetOf<String>()
    fun detectCycle(curr: String, path: List<String>): Boolean {
      visited.add(curr)
      stack.add(curr)
      val neighbors = blendGraph[curr] ?: emptySet()
      for (nbr in neighbors) {
        if (!visited.contains(nbr)) {
          if (detectCycle(nbr, path + nbr)) return true
        } else if (stack.contains(nbr)) {
          warnings.add("Circular blending detected: ${path.joinToString(" -> ")} -> $nbr")
          return true
        }
      }
      stack.remove(curr)
      return false
    }

    for (node in blendGraph.keys) {
      if (!visited.contains(node)) {
        detectCycle(node, listOf(node))
      }
    }

    // Product lookup helper
    val productMap = input.products.associateBy { it.id }

    fun resolveProductIdForTank(tankName: String, directPid: String?): String? {
      if (!directPid.isNullOrBlank() && productMap.containsKey(directPid)) return directPid
      return input.products.find { it.tankIds.contains(tankName) }?.id
    }

    // 3. Process Product Tanks
    val calculatedProductTanks = input.productTanks.map { prod ->
      val initTov = prod.initialTov
      val finalTov = prod.finalTov
      val blendLines = prod.blendingLines.map { it.toBlendingLine() }
      val totalBlendingOut = blendLines.sumOf { it.amount.coerceAtLeast(0.0) }
      val del = prod.deliveryAmount

      val blendedInAmt = blendedInMap[prod.tankName] ?: 0.0
      val blendedInSources = blendedInSourcesMap[prod.tankName]?.map { (src, amt) ->
        "${String.format(Locale.US, "%,.0f", amt)} from $src"
      } ?: emptyList()

      val effectivePid = resolveProductIdForTank(prod.tankName, prod.productId)
      val prodObj = effectivePid?.let { productMap[it] }

      if (input.mode == CalculationMode.TEMP_30C) {
        val density = prod.density15 ?: prodObj?.defaultDensity15 ?: 750.0
        val tInit = prod.initialTemp ?: 25.0
        val tFinal = prod.finalTemp ?: 25.0

        val vcfInit = VcfEngine.calculateVcf(density, tInit, input.vcfTableType, input.roundVcfTo4Decimals, input.roundTempTo025)
        val vcfFinal = VcfEngine.calculateVcf(density, tFinal, input.vcfTableType, input.roundVcfTo4Decimals, input.roundTempTo025)
        val vcf30 = VcfEngine.calculateVcf(density, 30.0, input.vcfTableType, input.roundVcfTo4Decimals, input.roundTempTo025)

        val initTov30 = if (vcf30.vcf > 0) initTov * vcfInit.vcf / vcf30.vcf else initTov
        val finalTov30 = if (vcf30.vcf > 0) finalTov * vcfFinal.vcf / vcf30.vcf else finalTov

        val effectiveInitTov30 = initTov30 + blendedInAmt
        val effectiveInitTov = initTov + blendedInAmt

        val netQty = finalTov30 - effectiveInitTov30 + totalBlendingOut + del

        val note = if (blendedInAmt > 0) {
          val srcListStr = blendedInSourcesMap[prod.tankName]?.joinToString(", ") { it.first } ?: ""
          "Initial TOV ${String.format(Locale.US, "%,.0f", initTov30)} + blended in ${String.format(Locale.US, "%,.0f", blendedInAmt)} (from $srcListStr) = effective initial ${String.format(Locale.US, "%,.0f", effectiveInitTov30)}"
        } else null

        LossTankEntry(
          tankName = prod.tankName,
          initialTov = initTov,
          finalTov = finalTov,
          receivingAmount = 0.0,
          blendingAmount = totalBlendingOut,
          deliveryAmount = del,
          density15 = density,
          initialTemp = tInit,
          finalTemp = tFinal,
          initialVcf = vcfInit.vcf,
          finalVcf = vcfFinal.vcf,
          initialTov30 = initTov30,
          finalTov30 = finalTov30,
          netQuantity = netQty,
          isInitialSynced = prod.isInitialSynced,
          isFinalSynced = prod.isFinalSynced,
          blendingLines = blendLines,
          effectiveInitialTov = effectiveInitTov,
          effectiveInitialTov30 = effectiveInitTov30,
          blendedInAmount = blendedInAmt,
          blendedInSources = blendedInSources,
          effectiveInitialNote = note,
          productId = prodObj?.id,
          productName = prodObj?.name,
          productShortCode = prodObj?.shortCode,
          productColorHex = prodObj?.colorHex
        )
      } else {
        val effectiveInitTov = initTov + blendedInAmt
        val netQty = finalTov - effectiveInitTov + totalBlendingOut + del

        val note = if (blendedInAmt > 0) {
          val srcListStr = blendedInSourcesMap[prod.tankName]?.joinToString(", ") { it.first } ?: ""
          "Initial TOV ${String.format(Locale.US, "%,.0f", initTov)} + blended in ${String.format(Locale.US, "%,.0f", blendedInAmt)} (from $srcListStr) = effective initial ${String.format(Locale.US, "%,.0f", effectiveInitTov)}"
        } else null

        LossTankEntry(
          tankName = prod.tankName,
          initialTov = initTov,
          finalTov = finalTov,
          receivingAmount = 0.0,
          blendingAmount = totalBlendingOut,
          deliveryAmount = del,
          density15 = prod.density15 ?: prodObj?.defaultDensity15,
          initialTemp = prod.initialTemp,
          finalTemp = prod.finalTemp,
          netQuantity = netQty,
          isInitialSynced = prod.isInitialSynced,
          isFinalSynced = prod.isFinalSynced,
          blendingLines = blendLines,
          effectiveInitialTov = effectiveInitTov,
          blendedInAmount = blendedInAmt,
          blendedInSources = blendedInSources,
          effectiveInitialNote = note,
          productId = prodObj?.id,
          productName = prodObj?.name,
          productShortCode = prodObj?.shortCode,
          productColorHex = prodObj?.colorHex
        )
      }
    }

    // 3b. Process Blending Tanks (Net = Final - effectiveInitial + Delivery)
    val calculatedBlendingTanks = input.blendingTanks.map { bTank ->
      val initTov = bTank.initialTov
      val finalTov = bTank.finalTov
      val del = bTank.deliveryAmount

      val blendedInAmt = blendedInMap[bTank.tankName] ?: 0.0
      val blendedInSources = blendedInSourcesMap[bTank.tankName]?.map { (src, amt) ->
        "${String.format(Locale.US, "%,.0f", amt)} from $src"
      } ?: emptyList()

      val prodObj = bTank.blendedProductId?.let { productMap[it] }

      if (input.mode == CalculationMode.TEMP_30C) {
        val density = bTank.density15 ?: prodObj?.defaultDensity15 ?: 750.0
        val tInit = bTank.initialTemp ?: 25.0
        val tFinal = bTank.finalTemp ?: 25.0

        val vcfInit = VcfEngine.calculateVcf(density, tInit, input.vcfTableType, input.roundVcfTo4Decimals, input.roundTempTo025)
        val vcfFinal = VcfEngine.calculateVcf(density, tFinal, input.vcfTableType, input.roundVcfTo4Decimals, input.roundTempTo025)
        val vcf30 = VcfEngine.calculateVcf(density, 30.0, input.vcfTableType, input.roundVcfTo4Decimals, input.roundTempTo025)

        val initTov30 = if (vcf30.vcf > 0) initTov * vcfInit.vcf / vcf30.vcf else initTov
        val finalTov30 = if (vcf30.vcf > 0) finalTov * vcfFinal.vcf / vcf30.vcf else finalTov

        val effectiveInitTov30 = initTov30 + blendedInAmt
        val effectiveInitTov = initTov + blendedInAmt

        val netQty = finalTov30 - effectiveInitTov30 + del

        val note = if (blendedInAmt > 0) {
          val srcListStr = blendedInSourcesMap[bTank.tankName]?.joinToString(", ") { it.first } ?: ""
          "Initial TOV ${String.format(Locale.US, "%,.0f", initTov30)} + blended in ${String.format(Locale.US, "%,.0f", blendedInAmt)} (from $srcListStr) = effective initial ${String.format(Locale.US, "%,.0f", effectiveInitTov30)}"
        } else null

        LossTankEntry(
          tankName = bTank.tankName,
          initialTov = initTov,
          finalTov = finalTov,
          receivingAmount = 0.0,
          blendingAmount = 0.0,
          deliveryAmount = del,
          density15 = density,
          initialTemp = tInit,
          finalTemp = tFinal,
          initialVcf = vcfInit.vcf,
          finalVcf = vcfFinal.vcf,
          initialTov30 = initTov30,
          finalTov30 = finalTov30,
          netQuantity = netQty,
          isInitialSynced = bTank.isInitialSynced,
          isFinalSynced = bTank.isFinalSynced,
          effectiveInitialTov = effectiveInitTov,
          effectiveInitialTov30 = effectiveInitTov30,
          blendedInAmount = blendedInAmt,
          blendedInSources = blendedInSources,
          effectiveInitialNote = note,
          productId = prodObj?.id,
          productName = prodObj?.name,
          productShortCode = prodObj?.shortCode,
          productColorHex = prodObj?.colorHex
        )
      } else {
        val effectiveInitTov = initTov + blendedInAmt
        val netQty = finalTov - effectiveInitTov + del

        val note = if (blendedInAmt > 0) {
          val srcListStr = blendedInSourcesMap[bTank.tankName]?.joinToString(", ") { it.first } ?: ""
          "Initial TOV ${String.format(Locale.US, "%,.0f", initTov)} + blended in ${String.format(Locale.US, "%,.0f", blendedInAmt)} (from $srcListStr) = effective initial ${String.format(Locale.US, "%,.0f", effectiveInitTov)}"
        } else null

        LossTankEntry(
          tankName = bTank.tankName,
          initialTov = initTov,
          finalTov = finalTov,
          receivingAmount = 0.0,
          blendingAmount = 0.0,
          deliveryAmount = del,
          density15 = bTank.density15 ?: prodObj?.defaultDensity15,
          initialTemp = bTank.initialTemp,
          finalTemp = bTank.finalTemp,
          netQuantity = netQty,
          isInitialSynced = bTank.isInitialSynced,
          isFinalSynced = bTank.isFinalSynced,
          effectiveInitialTov = effectiveInitTov,
          blendedInAmount = blendedInAmt,
          blendedInSources = blendedInSources,
          effectiveInitialNote = note,
          productId = prodObj?.id,
          productName = prodObj?.name,
          productShortCode = prodObj?.shortCode,
          productColorHex = prodObj?.colorHex
        )
      }
    }

    // 4. Totals, Yields and Ratios
    val totalFeed = calculatedFeedTanks.sumOf { it.netQuantity }
    val totalProductTanksMade = calculatedProductTanks.sumOf { it.netQuantity }
    val totalBlendingTanksNet = calculatedBlendingTanks.sumOf { it.netQuantity }
    val totalProduct = totalProductTanksMade + totalBlendingTanksNet
    val loss = totalFeed - totalProduct

    val lossPercent = if (totalFeed > 0) (loss / totalFeed) * 100.0 else 0.0
    val yieldPercent = if (totalFeed > 0) (totalProduct / totalFeed) * 100.0 else 0.0

    val totalProductPercent = if (totalFeed > 0) (totalProduct / totalFeed) * 100.0 else null
    val lossPercentOnFeed = if (totalFeed > 0) (loss / totalFeed) * 100.0 else null

    val durationHours = input.durationHours
    val feedRatePerHour = if (durationHours > 0) totalFeed / durationHours else 0.0
    val productRatePerHour = if (durationHours > 0) totalProduct / durationHours else 0.0
    val lossRatePerHour = if (durationHours > 0) loss / durationHours else 0.0

    val flag = when {
      totalFeed <= 0 -> "NO FEED"
      lossPercent <= input.lossThresholdPercent -> "GREEN (Normal)"
      lossPercent <= input.lossThresholdPercent * 1.5 -> "AMBER (Elevated)"
      else -> "RED (High Loss)"
    }

    // 4b. Feed Tank Ratios
    val feedTankRatios = calculatedFeedTanks.map { ft ->
      val pct = if (totalFeed > 0) (ft.netQuantity / totalFeed) * 100.0 else null
      FeedTankRatioEntry(
        tankName = ft.tankName,
        feedConsumed = ft.netQuantity,
        percentOfTotalFeed = pct
      )
    }

    // 5. Product Grouping & Yield Analysis (CRITICAL: EXCLUDES BLENDING TANKS)
    val productTankLinksSnapshot = mutableMapOf<String, String>()

    // Record snapshot of blending tanks
    for (b in calculatedBlendingTanks) {
      productTankLinksSnapshot[b.tankName] = b.productName?.let { "Blending: $it" } ?: "Blending Tank"
    }

    // Group only calculated product tanks by resolved Product ID
    val groupedByProduct = calculatedProductTanks.groupBy {
      resolveProductIdForTank(it.tankName, it.productId) ?: "UNASSIGNED"
    }

    val productYieldEntries = mutableListOf<ProductYieldEntry>()

    // For every product in input.products (live products master)
    for (prod in input.products) {
      val tanksInProd = groupedByProduct[prod.id] ?: emptyList()
      // "A product is shown when at least one of its linked tanks is selected as a running tank (show 0.0 % if the amounts are still empty)."
      if (tanksInProd.isNotEmpty()) {
        val prodMade = tanksInProd.sumOf { it.netQuantity }
        val pctOfFeed = if (totalFeed > 0) (prodMade / totalFeed) * 100.0 else null
        val pctOfTotProd = if (totalProduct > 0) (prodMade / totalProduct) * 100.0 else 0.0

        val tankBreakdown = mutableMapOf<String, Double>()
        for (t in tanksInProd) {
          productTankLinksSnapshot[t.tankName] = prod.name
          val share = if (prodMade > 0) (t.netQuantity / prodMade) * 100.0 else 0.0
          tankBreakdown[t.tankName] = share
        }

        productYieldEntries.add(
          ProductYieldEntry(
            productId = prod.id,
            productName = prod.name,
            shortCode = prod.shortCode,
            colorHex = prod.colorHex,
            category = prod.category,
            tankNames = tanksInProd.map { it.tankName },
            productMade = prodMade,
            percentOfFeed = pctOfFeed,
            percentOfTotalProduct = pctOfTotProd,
            tankBreakdown = tankBreakdown
          )
        )
      }
    }

    // Unassigned product tanks
    val unassignedTanks = groupedByProduct["UNASSIGNED"] ?: emptyList()
    if (unassignedTanks.isNotEmpty()) {
      val unassignedMade = unassignedTanks.sumOf { it.netQuantity }
      val pctOfFeed = if (totalFeed > 0) (unassignedMade / totalFeed) * 100.0 else null
      val pctOfTotProd = if (totalProduct > 0) (unassignedMade / totalProduct) * 100.0 else 0.0

      val tankBreakdown = mutableMapOf<String, Double>()
      for (t in unassignedTanks) {
        productTankLinksSnapshot[t.tankName] = "Unassigned"
        val share = if (unassignedMade > 0) (t.netQuantity / unassignedMade) * 100.0 else 0.0
        tankBreakdown[t.tankName] = share
      }

      productYieldEntries.add(
        ProductYieldEntry(
          productId = null,
          productName = "Unassigned",
          shortCode = "UNASGN",
          colorHex = "#757575",
          category = ProductCategory.PRODUCT,
          tankNames = unassignedTanks.map { it.tankName },
          productMade = unassignedMade,
          percentOfFeed = pctOfFeed,
          percentOfTotalProduct = pctOfTotProd,
          tankBreakdown = tankBreakdown
        )
      )
    }

    // Sort by quantity descending as required:
    val sortedProductYields = productYieldEntries.sortedByDescending { it.productMade }

    return LossComputationResult(
      mode = input.mode,
      startTime = input.startTime,
      endTime = input.endTime,
      durationHours = durationHours,
      feedTanks = calculatedFeedTanks,
      productTanks = calculatedProductTanks,
      blendingTanks = calculatedBlendingTanks,
      totalFeed = totalFeed,
      totalProduct = totalProduct,
      loss = loss,
      lossPercent = lossPercent,
      yieldPercent = yieldPercent,
      feedRatePerHour = feedRatePerHour,
      productRatePerHour = productRatePerHour,
      lossRatePerHour = lossRatePerHour,
      flag = flag,
      productYields = sortedProductYields,
      feedTankRatios = feedTankRatios,
      totalProductPercent = totalProductPercent,
      lossPercentOnFeed = lossPercentOnFeed,
      productTankLinksSnapshot = productTankLinksSnapshot,
      warnings = warnings
    )
  }
}
