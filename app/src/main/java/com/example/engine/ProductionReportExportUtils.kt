package com.example.engine

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object ProductionReportExportUtils {

  fun formatReportText(result: ProductionReportCalculationResult, setup: ProductionReportSetup): String {
    val basisLabel = if (result.basis == CalculationBasis.TEMP_30C) "30 °C Basis" else "Natural Basis"
    fun fmt(v: Double, negBrackets: Boolean = false): String {
      return if (negBrackets && v < 0) {
        String.format(Locale.US, "(%,.2f)", kotlin.math.abs(v))
      } else {
        String.format(Locale.US, "%,.2f", v)
      }
    }
    return buildString {
      appendLine("================================================================================")
      appendLine("                              PARTEX PETRO LTD.")
      appendLine("         CONDENSATE FRACTIONATION PLANT - DAILY PRODUCTION REPORT ($basisLabel)")
      appendLine("================================================================================")
      appendLine("Reporting Date/Time: ${result.reportDate} 10:00 AM")
      appendLine("Duration: ${result.durationText}")
      appendLine("--------------------------------------------------------------------------------")
      appendLine("SUMMARY (BASE OPTION B):")
      appendLine("Total Feed Consumed:         ${String.format(Locale.US, "%,.2f", result.totalFeed)} L")
      appendLine("(B) Total Produced Product:  ${String.format(Locale.US, "%,.2f", result.totalChangeB)} L")
      appendLine("Total Loss (A-B):            ${String.format(Locale.US, "%,.2f", result.lossOptionB)} L (${String.format(Locale.US, "%.2f", result.lossPercentOptionB)}%)")
      appendLine("LOSS COMPARISON:")
      appendLine("  Option A (Total Product):")
      appendLine("    Total Product: ${String.format(Locale.US, "%,.2f", result.totalProductA)} L | Loss: ${String.format(Locale.US, "%,.2f", result.lossOptionA)} L (${String.format(Locale.US, "%.2f%%", result.lossPercentOptionA)}) | Yield: ${String.format(Locale.US, "%.2f%%", result.yieldOptionA)}")
      appendLine("  Option B (All-Tank TOV Change):")
      appendLine("    Total Change:  ${String.format(Locale.US, "%,.2f", result.totalChangeB)} L | Loss: ${String.format(Locale.US, "%,.2f", result.lossOptionB)} L (${String.format(Locale.US, "%.2f%%", result.lossPercentOptionB)}) | Yield: ${String.format(Locale.US, "%.2f%%", result.yieldOptionB)}")
      appendLine("  Difference (A - B): ${String.format(Locale.US, "%+,.2f", result.diffAB)} L (${result.diffExplanation})")
      appendLine("--------------------------------------------------------------------------------")
      appendLine("RAW MATERIALS TABLE:")
      appendLine(String.format(Locale.US, "%-10s %-12s %14s %14s %14s %14s", "Tank", "Product", "Present (L)", "Previous (L)", "Received (L)", "Processed (L)"))
      result.rawTanks.forEach { rt ->
        appendLine(String.format(Locale.US, "%-10s %-12s %14s %14s %14s %14s", rt.tankName, rt.productCode, fmt(rt.todayVolume), fmt(rt.previousVolume), fmt(rt.received), fmt(rt.processed)))
      }
      appendLine(String.format(Locale.US, "%-10s %-12s %14s %14s %14s %14s", "TOTAL RAW", "", fmt(result.totalRawToday), fmt(result.totalRawPrevious), fmt(result.totalRawReceived), fmt(result.totalFeed)))
      appendLine("--------------------------------------------------------------------------------")
      appendLine("PRODUCTS TABLE (PER TANK & GROUP PRODUCTION):")
      appendLine(String.format(Locale.US, "%-12s %-10s %14s %14s %14s %14s", "Product", "Tank", "Present (L)", "Previous (L)", "Change (L)", "Group Prod (L)"))
      result.productGroups.forEach { pg ->
        pg.tanks.forEach { pt ->
          val adjTag = if (pt.hasAdjustment) " [adj]" else ""
          appendLine(String.format(Locale.US, "%-12s %-10s %14s %14s %14s %14s", pg.productName, pt.tankName + adjTag, fmt(pt.todayVolume), fmt(pt.previousVolume), fmt(pt.changeD, true), "-"))
        }
        appendLine(String.format(Locale.US, "%-12s %-10s %14s %14s %14s %14s (%5.2f%%)", "  Total " + pg.productName, "", fmt(pg.totalTodayVolume), fmt(pg.totalPreviousVolume), fmt(pg.totalChangeD, true), fmt(pg.groupProduction), pg.percentOfFeed))
      }
      appendLine(String.format(Locale.US, "%-12s %-10s %14s %14s %14s %14s", "TOTAL PROD", "", fmt(result.totalProductToday), fmt(result.totalProductPrevious), fmt(result.productSubTotal, true), fmt(result.totalProduced)))
      appendLine("--------------------------------------------------------------------------------")
      if (result.blendingTanks.isNotEmpty()) {
        appendLine("BLENDING TANKS:")
        appendLine(String.format(Locale.US, "%-10s %-16s %14s %14s %14s %14s", "Tank", "Product", "Present (L)", "Previous (L)", "Delivered (L)", "Production (L)"))
        result.blendingTanks.forEach { bt ->
          appendLine(String.format(Locale.US, "%-10s %-16s %14s %14s %14s %14s", bt.tankName, bt.productName, fmt(bt.todayVolume), fmt(bt.previousVolume), fmt(bt.delivered), fmt(bt.production)))
        }
        appendLine(String.format(Locale.US, "%-10s %-16s %14s %14s %14s %14s", "TOTAL BLEND", "", fmt(result.totalBlendingToday), fmt(result.totalBlendingPrevious), fmt(result.totalBlendingDelivered), fmt(result.totalBlendingProduction)))
        appendLine("--------------------------------------------------------------------------------")
      }
      appendLine("PROCESS WASTE & LOSS RECONCILIATION:")
      if (!result.isWasteEntered) {
        appendLine("Process Waste: Not entered")
      } else {
        appendLine(String.format(Locale.US, "%-22s %-12s %10s %14s %10s", "Meter", "Tag", "kg/h", "Litres (24h)", "% of Feed"))
        result.wasteItems.forEach { wi ->
          appendLine(String.format(Locale.US, "%-22s %-12s %10.2f %14.2f %9.2f%%", wi.name, wi.tag, wi.kgH, wi.litres24h, wi.percentOfFeed))
        }
        appendLine("Total Measured Loss: ${String.format(Locale.US, "%.2f%%", result.totalMeasuredLossPct)}")
        appendLine("Unmeasured Loss:     ${String.format(Locale.US, "%.2f%%", result.unmeasuredLossPct)}")
        appendLine("Fuel for Heaters:    ${String.format(Locale.US, "%.2f%%", result.fuelForHeatersPct)}")
        appendLine("H2 Make-up for KNHT: ${String.format(Locale.US, "%.2f%%", result.h2MakeUpKnhtPct)}")
        appendLine("Actual Flaring:      ${String.format(Locale.US, "%.2f%%", result.actualFlaringPct)}")
      }
      appendLine("--------------------------------------------------------------------------------")
      appendLine("DRY-OUT FORECAST:")
      appendLine("Raw Material Stock (T-501): ${String.format(Locale.US, "%,.0f", result.dryOutStockL)} L")
      appendLine("Consumable (Stock - 840k):  ${String.format(Locale.US, "%,.0f", result.dryOutConsumableL)} L")
      appendLine("Days Remaining:             ${String.format(Locale.US, "%.2f", result.dryOutDays)} days")
      appendLine("Expected Dry-Out Date:      ${result.dryOutDateStr}")
      appendLine("================================================================================")
      appendLine("Generated by Tank Calc | Developed by Meherab Hossain Jihad")
    }
  }

  /**
   * Quick WhatsApp / Telegram Summary for Daily Production Report shift handover.
   */
  fun formatReportWhatsApp(
    result: ProductionReportCalculationResult,
    setup: ProductionReportSetup,
    settings: AppSettings? = null
  ): String {
    val companyName = settings?.companyName?.ifBlank { "PARTEX PETRO LTD." } ?: "PARTEX PETRO LTD."
    val plantName = settings?.plantName?.ifBlank { "CONDENSATE FRACTIONATION PLANT" } ?: "CONDENSATE FRACTIONATION PLANT"
    val basisLabel = if (result.basis == CalculationBasis.TEMP_30C) "30 °C Basis" else "Natural Basis"
    fun fmt0(v: Double): String = String.format(Locale.US, "%,.0f", v)

    return buildString {
      appendLine("*$companyName*")
      appendLine("*$plantName*")
      appendLine("📋 *DAILY PRODUCTION HANDOVER ($basisLabel)*")
      appendLine("🗓 *Period:* `${result.previousDate} 10:00` → `${result.reportDate} 10:00`")
      appendLine()
      appendLine("```")
      appendLine("KPI SUMMARY (LITRES)")
      appendLine("------------------------------")
      appendLine(String.format(Locale.US, "Feed Used  : %14s L", fmt0(result.totalFeed)))
      appendLine(String.format(Locale.US, "Produced   : %14s L", fmt0(result.totalProduced)))
      appendLine(String.format(Locale.US, "Loss (B)   : %14s L", fmt0(result.lossOptionB)))
      appendLine(String.format(Locale.US, "Loss %%     : %13.2f %%", result.lossPercentOptionB))
      appendLine(String.format(Locale.US, "Yield %%    : %13.2f %%", result.yieldOptionB))
      appendLine("------------------------------")
      appendLine("PRODUCT GROUP BREAKDOWN")
      result.productGroups.forEach { pg ->
        appendLine(
          String.format(
            Locale.US,
            "%-10s : %10s L (%5.1f%%)",
            pg.productName.take(10),
            fmt0(pg.groupProduction),
            pg.percentOfFeed
          )
        )
      }
      if (result.blendingTanks.isNotEmpty()) {
        appendLine("------------------------------")
        appendLine("BLENDING TANKS")
        result.blendingTanks.forEach { bt ->
          appendLine(
            String.format(
              Locale.US,
              "%-6s %-4s: %10s L",
              bt.tankName.take(6),
              bt.productName.take(4),
              fmt0(bt.production)
            )
          )
        }
      }
      appendLine("------------------------------")
      appendLine(String.format(Locale.US, "T-501 Stock: %14s L", fmt0(result.dryOutStockL)))
      appendLine(String.format(Locale.US, "Dry-Out    : %7.1f d (%s)", result.dryOutDays, result.dryOutDateStr))
      appendLine("```")
      appendLine("_Generated by Tank Calc | Developed by Meherab Hossain Jihad_")
    }
  }

  /**
   * Quick WhatsApp / Telegram Summary for ERP Production & Stock Statement.
   */
  fun formatErpWhatsApp(
    erpRows: List<ErpRowData>,
    result: ProductionReportCalculationResult,
    settings: AppSettings? = null
  ): String {
    val companyName = settings?.companyName?.ifBlank { "PARTEX PETRO LTD." } ?: "PARTEX PETRO LTD."
    val plantName = settings?.plantName?.ifBlank { "CONDENSATE FRACTIONATION PLANT" } ?: "CONDENSATE FRACTIONATION PLANT"
    fun fmt0(v: Double): String = String.format(Locale.US, "%,.0f", v)

    return buildString {
      appendLine("*$companyName*")
      appendLine("*$plantName*")
      appendLine("📊 *ERP STOCK & PRODUCTION STATEMENT (@ 30 °C)*")
      appendLine("🗓 *Date:* `${result.reportDate}`")
      appendLine()
      appendLine("```")
      appendLine(String.format(Locale.US, "%-9s %9s %9s %9s", "ITEM", "PROD/REC", "DELIV", "CLOSING"))
      appendLine("---------------------------------------")
      erpRows.forEach { r ->
        appendLine(
          String.format(
            Locale.US,
            "%-9s %9s %9s %9s",
            r.itemName.take(9),
            fmt0(r.productionOrReceived),
            fmt0(r.delivered),
            fmt0(r.closingStock)
          )
        )
      }
      appendLine("---------------------------------------")
      appendLine(String.format(Locale.US, "Feed: %s L | Loss: %.2f%%", fmt0(result.totalFeed), result.lossPercent))
      appendLine("```")
      appendLine("_Generated by Tank Calc | Developed by Meherab Hossain Jihad_")
    }
  }

  fun formatReportCsv(result: ProductionReportCalculationResult): String {
    return buildString {
      appendLine("PARTEX PETRO LTD. - CONDENSATE FRACTIONATION PLANT - PRODUCTION REPORT")
      appendLine("Basis,${result.basis}")
      appendLine("Report Date,${result.reportDate}")
      appendLine("Duration,${result.durationText}")
      appendLine()
      appendLine("SUMMARY")
      appendLine("Total Feed (L),${result.totalFeed}")
      appendLine("Total Produced (L),${result.totalProduced}")
      appendLine("Total Loss (L),${result.totalLoss}")
      appendLine("Loss Percent %,${result.lossPercent}")
      appendLine("Option A Loss (L),${result.lossOptionA}")
      appendLine("Option A Loss %,${result.lossPercentOptionA}")
      appendLine("Option A Yield %,${result.productYieldOptionA}")
      appendLine("Option B Loss (L),${result.lossOptionB}")
      appendLine("Option B Loss %,${result.lossPercentOptionB}")
      appendLine("Option B Recovery %,${result.productYieldOptionB}")
      appendLine("Difference A-B (L),${result.diffLossMethods}")
      appendLine()
      appendLine("RAW MATERIALS")
      appendLine("Tank,Product,Present (L),Previous (L),Received (L),Processed (L)")
      result.rawTanks.forEach {
        appendLine("${it.tankName},${it.productCode},${it.todayVolume},${it.previousVolume},${it.received},${it.processed}")
      }
      appendLine("Total Raw,,${result.totalRawToday},${result.totalRawPrevious},${result.totalRawReceived},${result.totalFeed}")
      appendLine()
      appendLine("PRODUCTS (PER TANK & GROUP PRODUCTION)")
      appendLine("Product,Tank,Present (L),Previous (L),Change (L),Group Production (L),Percent of Feed %")
      result.productGroups.forEach { pg ->
        pg.tanks.forEach { pt ->
          appendLine("${pg.productName},${pt.tankName},${pt.todayVolume},${pt.previousVolume},${pt.changeD},,")
        }
        appendLine("Total ${pg.productName},,${pg.totalTodayVolume},${pg.totalPreviousVolume},${pg.totalChangeD},${pg.groupProduction},${pg.percentOfFeed}")
      }
      appendLine("Total Products,,${result.totalProductToday},${result.totalProductPrevious},${result.productSubTotal},${result.totalProduced},")
      appendLine()
      if (result.blendingTanks.isNotEmpty()) {
        appendLine("BLENDING TANKS")
        appendLine("Tank,Product,Present (L),Previous (L),Delivered (L),Production (L)")
        result.blendingTanks.forEach {
          appendLine("${it.tankName},${it.productName},${it.todayVolume},${it.previousVolume},${it.delivered},${it.production}")
        }
        appendLine("Total Blending,,${result.totalBlendingToday},${result.totalBlendingPrevious},${result.totalBlendingDelivered},${result.totalBlendingProduction}")
        appendLine()
      }
      appendLine("PROCESS WASTE")
      if (!result.isWasteEntered) {
        appendLine("Status,Not entered")
      } else {
        appendLine("Meter,Tag,Category,kg/h,Litres 24h,Percent of Feed %")
        result.wasteItems.forEach {
          appendLine("\"${it.name}\",${it.tag},${it.category},${it.kgH},${it.litres24h},${it.percentOfFeed}")
        }
        appendLine("Total Measured Loss %,${result.totalMeasuredLossPct}")
        appendLine("Unmeasured Loss %,${result.unmeasuredLossPct}")
        appendLine("Fuel for Heaters %,${result.fuelForHeatersPct}")
        appendLine("H2 Make-up for KNHT %,${result.h2MakeUpKnhtPct}")
        appendLine("Actual Flaring %,${result.actualFlaringPct}")
      }
      appendLine()
      appendLine("DRY OUT FORECAST")
      appendLine("Raw Material Stock (L),${result.dryOutStockL}")
      appendLine("Consumable (L),${result.dryOutConsumableL}")
      appendLine("Dry-out Days,${result.dryOutDays}")
      appendLine("Dry-out Date,${result.dryOutDateStr}")
      appendLine()
      appendLine("Footer,Generated by Tank Calc | Developed by Meherab Hossain Jihad")
    }
  }

  fun formatErpCsv(erpRows: List<ErpRowData>, dateStr: String): String {
    return buildString {
      appendLine("PARTEX PETRO LTD. - ERP PRODUCTION BALANCE REPORT (30 DEG C BASIS)")
      appendLine("Date,$dateStr")
      appendLine()
      appendLine("Product,Tanks,Opening,Production/Received,Used for blending,Delivered,Internal use/Evaporation,Closing,Physical Stock,Check")
      erpRows.forEach { r ->
        val tanksStr = r.assignedTankNames.joinToString(";")
        appendLine("\"${r.itemName}\",\"$tanksStr\",${r.openingStock},${r.productionOrReceived},${r.usedForBlending},${r.delivered},${r.internalUseOrEvap},${r.closingStock},${r.physicalStock},${r.difference}")
      }
      appendLine()
      appendLine("Footer,Generated by Tank Calc | Developed by Meherab Hossain Jihad")
    }
  }

  fun exportCsvFile(context: Context, filename: String, content: String): Intent? {
    return try {
      val file = File(context.cacheDir, filename)
      file.writeText(content, Charsets.UTF_8)
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, filename.replace(".csv", ""))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    } catch (_: Exception) {
      null
    }
  }

  fun createErpPdfFile(
    context: Context,
    erpRows: List<ErpRowData>,
    result: ProductionReportCalculationResult,
    settings: AppSettings
  ): File {
    val doc = android.graphics.pdf.PdfDocument()
    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(842, 595, 1).create() // A4 landscape
    val page = doc.startPage(pageInfo)
    val canvas = page.canvas

    val deepBlue = android.graphics.Color.parseColor("#0B2A5B")
    val tealAccent = android.graphics.Color.parseColor("#14B8A6")
    val darkSlate = android.graphics.Color.parseColor("#1E293B")
    val textDark = android.graphics.Color.parseColor("#0F172A")
    val lightBg = android.graphics.Color.parseColor("#F8FAFC")
    val altRowBg = android.graphics.Color.parseColor("#F1F5F9")
    val borderCol = android.graphics.Color.parseColor("#CBD5E1")
    val redCol = android.graphics.Color.parseColor("#DC2626")
    val greenCol = android.graphics.Color.parseColor("#16A34A")
    val amberCol = android.graphics.Color.parseColor("#D97706")

    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    // 1. Executive Header Band (PARTEX PETRO LTD. + Refinery Unit)
    p.color = deepBlue
    canvas.drawRect(0f, 0f, 842f, 44f, p)
    p.color = tealAccent
    canvas.drawRect(0f, 41f, 842f, 45f, p)

    val companyName = settings.companyName.ifBlank { "PARTEX PETRO LTD." }
    val plantTitle = settings.plantName.ifBlank { "CONDENSATE FRACTIONATION PLANT" }

    p.color = android.graphics.Color.WHITE
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    p.textSize = 12.5f
    canvas.drawText("$companyName — $plantTitle", 30f, 18f, p)

    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    p.color = android.graphics.Color.parseColor("#99F6E4")
    p.textSize = 8.5f
    canvas.drawText("DAILY PRODUCTION & STOCK STATEMENT (ERP RECONCILIATION @ 30 °C, LITRES)", 30f, 33f, p)

    val periodStr = "Period: ${result.previousDate} 10:00 to ${result.reportDate} 10:00"
    p.color = android.graphics.Color.WHITE
    p.typeface = android.graphics.Typeface.DEFAULT
    p.textSize = 8.5f
    val periodWidth = p.measureText(periodStr)
    canvas.drawText(periodStr, 812f - periodWidth, 26f, p)

    // 2. KPI row (y = 50 to 86)
    val kpiY = 50f
    val kpiHeight = 36f
    val kpiWidth = 188f
    val kpis = listOf(
      Pair("Total Feed Consumed", String.format(Locale.US, "%,.0f L", result.totalFeed)),
      Pair("Total Produced Product", String.format(Locale.US, "%,.0f L", result.totalProduced)),
      Pair("Process Loss / (Gain)", String.format(Locale.US, "%,.0f L (%.2f%%)", result.totalLoss, result.lossPercent)),
      Pair("Plant Efficiency (CFU/KNHT/CRU)", "${result.plantEfficiency["CFU"]?.toInt() ?: 0}% / ${result.plantEfficiency["KNHT"]?.toInt() ?: 0}% / ${result.plantEfficiency["CRU"]?.toInt() ?: 0}%")
    )
    for (i in kpis.indices) {
      val kx = 30f + i * (kpiWidth + 10f)
      p.color = lightBg
      canvas.drawRoundRect(kx, kpiY, kx + kpiWidth, kpiY + kpiHeight, 5f, 5f, p)
      p.color = if (i % 2 == 0) deepBlue else tealAccent
      canvas.drawRoundRect(kx, kpiY, kx + kpiWidth, kpiY + 3f, 2f, 2f, p)
      p.color = borderCol
      p.style = android.graphics.Paint.Style.STROKE
      p.strokeWidth = 0.8f
      canvas.drawRoundRect(kx, kpiY, kx + kpiWidth, kpiY + kpiHeight, 5f, 5f, p)
      p.style = android.graphics.Paint.Style.FILL

      p.color = textDark
      p.textSize = 7.2f
      p.typeface = android.graphics.Typeface.DEFAULT
      canvas.drawText(kpis[i].first, kx + 8f, kpiY + 14f, p)

      p.textSize = 9.5f
      p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
      canvas.drawText(kpis[i].second, kx + 8f, kpiY + 28f, p)
    }

    // 3. Finished Products Table
    var curY = 96f
    p.color = deepBlue
    p.textSize = 9f
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText("FINISHED PRODUCTS (@ 30 °C)", 30f, curY + 9f, p)
    curY += 14f

    // Columns: Total 782 pt (30 to 812)
    val colDefs = listOf(
      Pair("Product", 80f),
      Pair("Tanks", 62f),
      Pair("Opening", 75f),
      Pair("Prod / Rec", 80f),
      Pair("Blending", 75f),
      Pair("Delivered", 75f),
      Pair("Internal", 75f),
      Pair("Closing", 75f),
      Pair("Physical", 75f),
      Pair("Check", 110f)
    )

    fun drawTableHeader(y: Float) {
      p.color = darkSlate
      canvas.drawRect(30f, y, 812f, y + 15f, p)
      p.color = android.graphics.Color.WHITE
      p.textSize = 6.8f
      p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
      var hx = 30f
      for (c in colDefs) {
        val tw = p.measureText(c.first)
        val tx = if (c.first == "Product" || c.first == "Tanks") hx + 4f else if (c.first == "Check") hx + (c.second - tw) / 2f else hx + c.second - tw - 4f
        canvas.drawText(c.first, tx, y + 10.5f, p)
        hx += c.second
      }
    }

    fun formatNum(num: Double): String = if (num == 0.0) "-" else String.format(Locale.US, "%,.0f", num)

    fun drawRow(r: ErpRowData, y: Float, isAlt: Boolean) {
      p.color = if (isAlt) altRowBg else android.graphics.Color.WHITE
      canvas.drawRect(30f, y, 812f, y + 13f, p)

      p.textSize = 6.5f
      p.typeface = android.graphics.Typeface.DEFAULT

      var rx = 30f
      val tanksDisplay = r.assignedTankNames.joinToString(",")
      val vals = listOf(
        Pair(r.itemName, false),
        Pair(tanksDisplay, false),
        Pair(formatNum(r.openingStock), r.openingStock < 0),
        Pair(formatNum(r.productionOrReceived), r.productionOrReceived < 0),
        Pair(formatNum(r.usedForBlending), r.usedForBlending < 0),
        Pair(formatNum(r.delivered), r.delivered < 0),
        Pair(formatNum(r.internalUseOrEvap), r.internalUseOrEvap < 0),
        Pair(formatNum(r.closingStock), r.closingStock < 0),
        Pair(formatNum(r.physicalStock), r.physicalStock < 0)
      )

      for (i in vals.indices) {
        val col = colDefs[i]
        val (txt, isNeg) = vals[i]
        p.color = if (isNeg) redCol else textDark
        val tw = p.measureText(txt)
        val tx = if (i < 2) rx + 4f else rx + col.second - tw - 4f
        canvas.drawText(txt, tx, y + 9.5f, p)
        rx += col.second
      }

      // Check column
      val tol = settings.erpToleranceL
      val isBalanced = kotlin.math.abs(r.difference) <= tol
      p.color = if (isBalanced) greenCol else amberCol
      val checkText = if (isBalanced) "Balanced" else String.format(Locale.US, "Diff: %,.0f", r.difference)
      val tw = p.measureText(checkText)
      canvas.drawText(checkText, rx + (colDefs.last().second - tw) / 2f, y + 9.5f, p)
    }

    drawTableHeader(curY)
    curY += 15f

    val finishedRows = erpRows.filter { !it.isRaw }

    var isAlt = false
    for (r in finishedRows) {
      drawRow(r, curY, isAlt)
      curY += 13f
      isAlt = !isAlt
    }

    // Finished products total row
    p.color = borderCol
    canvas.drawRect(30f, curY, 812f, curY + 13f, p)
    p.color = textDark
    p.textSize = 6.8f
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText("Total Finished Products", 34f, curY + 9.5f, p)

    val finTotals = listOf(
      finishedRows.sumOf { it.openingStock },
      finishedRows.sumOf { it.productionOrReceived },
      finishedRows.sumOf { it.usedForBlending },
      finishedRows.sumOf { it.delivered },
      finishedRows.sumOf { it.internalUseOrEvap },
      finishedRows.sumOf { it.closingStock },
      finishedRows.sumOf { it.physicalStock }
    )
    var tx = 30f + colDefs[0].second + colDefs[1].second
    for (i in finTotals.indices) {
      val col = colDefs[i + 2]
      val txt = formatNum(finTotals[i])
      val tw = p.measureText(txt)
      canvas.drawText(txt, tx + col.second - tw - 4f, curY + 9.5f, p)
      tx += col.second
    }
    curY += 18f

    // 4. Raw Materials Table
    p.color = deepBlue
    p.textSize = 9f
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText("RAW MATERIALS (@ 30 °C)", 30f, curY + 9f, p)
    curY += 14f

    drawTableHeader(curY)
    curY += 15f

    val rawRows = erpRows.filter { it.isRaw }

    isAlt = false
    for (r in rawRows) {
      drawRow(r, curY, isAlt)
      curY += 13f
      isAlt = !isAlt
    }

    // Raw materials total row
    p.color = borderCol
    canvas.drawRect(30f, curY, 812f, curY + 13f, p)
    p.color = textDark
    p.textSize = 6.8f
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText("Total Raw Materials", 34f, curY + 9.5f, p)

    val rawTotals = listOf(
      rawRows.sumOf { it.openingStock },
      rawRows.sumOf { it.productionOrReceived },
      rawRows.sumOf { it.usedForBlending },
      rawRows.sumOf { it.delivered },
      rawRows.sumOf { it.internalUseOrEvap },
      rawRows.sumOf { it.closingStock },
      rawRows.sumOf { it.physicalStock }
    )
    tx = 30f + colDefs[0].second + colDefs[1].second
    for (i in rawTotals.indices) {
      val col = colDefs[i + 2]
      val txt = formatNum(rawTotals[i])
      val tw = p.measureText(txt)
      canvas.drawText(txt, tx + col.second - tw - 4f, curY + 9.5f, p)
      tx += col.second
    }
    curY += 18f

    // 5. Plant Efficiency & Fuel Gas Breakdown Strip (y = curY to curY + 40)
    val stripWidth = 385f
    // Left: Efficiency
    p.color = lightBg
    canvas.drawRoundRect(30f, curY, 30f + stripWidth, curY + 40f, 4f, 4f, p)
    p.color = borderCol
    p.style = android.graphics.Paint.Style.STROKE
    canvas.drawRoundRect(30f, curY, 30f + stripWidth, curY + 40f, 4f, 4f, p)
    p.style = android.graphics.Paint.Style.FILL

    p.color = textDark
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    p.textSize = 7.5f
    canvas.drawText("PLANT EFFICIENCY & LOADS", 38f, curY + 12f, p)
    p.typeface = android.graphics.Typeface.DEFAULT
    p.textSize = 7f
    val cfuPct = result.plantEfficiency["CFU"] ?: 0.0
    val knhtPct = result.plantEfficiency["KNHT"] ?: 0.0
    val cruPct = result.plantEfficiency["CRU"] ?: 0.0
    canvas.drawText(String.format(Locale.US, "CFU: %.1f%% | KNHT: %.1f%% | CRU: %.1f%%", cfuPct, knhtPct, cruPct), 38f, curY + 28f, p)

    // Right: Fuel Gas & Loss Breakdown (Two Columns for Option A & Option B)
    val rxBox = 427f
    p.color = lightBg
    canvas.drawRoundRect(rxBox, curY, rxBox + stripWidth, curY + 40f, 4f, 4f, p)
    p.color = borderCol
    p.style = android.graphics.Paint.Style.STROKE
    canvas.drawRoundRect(rxBox, curY, rxBox + stripWidth, curY + 40f, 4f, 4f, p)
    p.style = android.graphics.Paint.Style.FILL

    p.color = textDark
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    p.textSize = 7.5f
    canvas.drawText("FUEL GAS & LOSS BREAKDOWN (2 COLUMNS)", rxBox + 8f, curY + 11f, p)
    p.typeface = android.graphics.Typeface.DEFAULT
    p.textSize = 6.5f
    canvas.drawText(
      String.format(
        Locale.US,
        "Option A: Gas (Loss) %.2f%% | Flaring: %.2f%%",
        result.lossPercentOptionA, result.actualFlaringPctOptionA
      ),
      rxBox + 8f,
      curY + 22f,
      p
    )
    canvas.drawText(
      String.format(
        Locale.US,
        "Option B: Gas (Loss) %.2f%% | Flaring: %.2f%% | Heaters: %.2f%%",
        result.lossPercentOptionB, result.actualFlaringPctOptionB, result.fuelForHeatersPct
      ),
      rxBox + 8f,
      curY + 33f,
      p
    )

    // 6. Executive Signature Boxes (y = 528 to 568)
    val sigY = 528f
    val sigWidth = 240f
    val sigs = listOf(
      Pair("PREPARED BY", settings.signaturePreparedBy.ifBlank { "Shift Operator" }),
      Pair("CHECKED BY", settings.signatureCheckedBy.ifBlank { "Shift In-Charge" }),
      Pair("APPROVED BY", settings.signatureApprovedBy.ifBlank { "Plant Manager" })
    )

    for (i in sigs.indices) {
      val sx = 30f + i * (sigWidth + 31f)
      p.color = lightBg
      canvas.drawRoundRect(sx, sigY, sx + sigWidth, sigY + 38f, 4f, 4f, p)
      p.color = borderCol
      p.style = android.graphics.Paint.Style.STROKE
      p.strokeWidth = 0.8f
      canvas.drawRoundRect(sx, sigY, sx + sigWidth, sigY + 38f, 4f, 4f, p)
      canvas.drawLine(sx + 8f, sigY + 22f, sx + sigWidth - 8f, sigY + 22f, p)
      p.style = android.graphics.Paint.Style.FILL

      p.color = deepBlue
      p.textSize = 6.8f
      p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
      canvas.drawText(sigs[i].first, sx + 8f, sigY + 33f, p)
      p.color = textDark
      p.typeface = android.graphics.Typeface.DEFAULT
      canvas.drawText(sigs[i].second, sx + 80f, sigY + 33f, p)
    }

    // 7. Footer
    p.color = android.graphics.Color.parseColor("#64748B")
    p.textSize = 7f
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText("Generated by Tank Calc | Developed by Meherab Hossain Jihad", 30f, 584f, p)
    val pageTxt = "$companyName • Page 1 of 1"
    val ptw = p.measureText(pageTxt)
    canvas.drawText(pageTxt, 812f - ptw, 584f, p)

    doc.finishPage(page)

    val filename = "ERP_Production_Statement_${result.reportDate}.pdf"
    val file = File(context.cacheDir, filename)
    java.io.FileOutputStream(file).use { out ->
      doc.writeTo(out)
    }
    doc.close()
    return file
  }

  fun createReportPdfFile(
    context: Context,
    result: ProductionReportCalculationResult,
    setup: ProductionReportSetup,
    settings: AppSettings
  ): File {
    val doc = android.graphics.pdf.PdfDocument()
    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(842, 595, 1).create() // A4 landscape
    val page = doc.startPage(pageInfo)
    val canvas = page.canvas

    val basisLabel = if (result.basis == CalculationBasis.TEMP_30C) "30 °C Basis" else "Natural Basis"
    val deepBlue = android.graphics.Color.parseColor("#0B2A5B")
    val tealAccent = android.graphics.Color.parseColor("#14B8A6")
    val darkSlate = android.graphics.Color.parseColor("#1E293B")
    val textDark = android.graphics.Color.parseColor("#0F172A")
    val lightBg = android.graphics.Color.parseColor("#F8FAFC")
    val altRowBg = android.graphics.Color.parseColor("#F1F5F9")
    val borderCol = android.graphics.Color.parseColor("#CBD5E1")

    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    // Executive Header Band (PARTEX PETRO LTD. + Refinery Unit)
    p.color = deepBlue
    canvas.drawRect(0f, 0f, 842f, 44f, p)
    p.color = tealAccent
    canvas.drawRect(0f, 41f, 842f, 45f, p)

    val companyName = settings.companyName.ifBlank { "PARTEX PETRO LTD." }
    val plantTitle = settings.plantName.ifBlank { "CONDENSATE FRACTIONATION PLANT" }

    p.color = android.graphics.Color.WHITE
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    p.textSize = 12.5f
    canvas.drawText("$companyName — $plantTitle", 30f, 18f, p)

    p.color = android.graphics.Color.parseColor("#99F6E4")
    p.textSize = 8.5f
    canvas.drawText("DAILY PRODUCTION REPORT ($basisLabel)", 30f, 33f, p)

    val periodStr = "Period: ${result.previousDate} 10:00 to ${result.reportDate} 10:00"
    p.color = android.graphics.Color.WHITE
    p.typeface = android.graphics.Typeface.DEFAULT
    p.textSize = 8.5f
    val periodWidth = p.measureText(periodStr)
    canvas.drawText(periodStr, 812f - periodWidth, 26f, p)

    fun formatNum(num: Double, negBrackets: Boolean = false): String {
      return if (num == 0.0) {
        "-"
      } else if (negBrackets && num < 0) {
        String.format(Locale.US, "(%,.1f)", kotlin.math.abs(num))
      } else {
        String.format(Locale.US, "%,.1f", num)
      }
    }

    val redCol = android.graphics.Color.parseColor("#DC2626")

    // KPI row
    val kpiY = 48f
    val kpiHeight = 28f
    val kpiWidth = 188f
    val kpis = listOf(
      Pair("Total Feed Consumed", String.format(Locale.US, "%,.0f L", result.totalFeed)),
      Pair("Total Produced Product", String.format(Locale.US, "%,.0f L", result.totalProduced)),
      Pair("Process Loss / (Gain)", String.format(Locale.US, "%,.0f L (%.2f%%)", result.totalLoss, result.lossPercent)),
      Pair("Dry-Out Forecast", String.format(Locale.US, "%.1f Days (${result.dryOutDateStr})", result.dryOutDays))
    )
    for (i in kpis.indices) {
      val kx = 30f + i * (kpiWidth + 10f)
      p.color = lightBg
      canvas.drawRoundRect(kx, kpiY, kx + kpiWidth, kpiY + kpiHeight, 4f, 4f, p)
      p.color = if (i % 2 == 0) deepBlue else tealAccent
      canvas.drawRoundRect(kx, kpiY, kx + kpiWidth, kpiY + 2.5f, 2f, 2f, p)
      p.color = borderCol
      p.style = android.graphics.Paint.Style.STROKE
      p.strokeWidth = 0.8f
      canvas.drawRoundRect(kx, kpiY, kx + kpiWidth, kpiY + kpiHeight, 4f, 4f, p)
      p.style = android.graphics.Paint.Style.FILL

      p.color = textDark
      p.textSize = 6.5f
      p.typeface = android.graphics.Typeface.DEFAULT
      canvas.drawText(kpis[i].first, kx + 6f, kpiY + 11f, p)

      p.textSize = 8.5f
      p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
      canvas.drawText(kpis[i].second, kx + 6f, kpiY + 22f, p)
    }

    // 1. Raw Materials Table
    var curY = 82f
    p.color = deepBlue
    p.textSize = 8f
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText("RAW MATERIALS TABLE (PER TANK STOCK & PROCESSED QTY)", 30f, curY + 7f, p)
    curY += 10f

    val rawColDefs = listOf(
      Pair("Tank", 85f),
      Pair("Product", 95f),
      Pair("Present (L)", 150f),
      Pair("Previous (L)", 150f),
      Pair("Received (L)", 150f),
      Pair("Processed qty (Raw mat. used)", 152f)
    )

    p.color = darkSlate
    canvas.drawRect(30f, curY, 812f, curY + 12f, p)
    p.color = android.graphics.Color.WHITE
    p.textSize = 6.5f
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    var hx = 30f
    for (c in rawColDefs) {
      val tw = p.measureText(c.first)
      val tx = if (c.first == "Tank" || c.first == "Product") hx + 4f else hx + c.second - tw - 4f
      canvas.drawText(c.first, tx, curY + 8.5f, p)
      hx += c.second
    }
    curY += 12f

    var isAlt = false
    for (rt in result.rawTanks) {
      p.color = if (isAlt) altRowBg else android.graphics.Color.WHITE
      canvas.drawRect(30f, curY, 812f, curY + 10f, p)
      p.color = textDark
      p.textSize = 6.2f
      p.typeface = android.graphics.Typeface.DEFAULT

      var rx = 30f
      val adjLabel = if (rt.hasAdjustment) " [adj]" else ""
      val vals = listOf(
        rt.tankName + adjLabel,
        rt.productCode,
        formatNum(rt.todayVolume),
        formatNum(rt.previousVolume),
        formatNum(rt.received),
        formatNum(rt.processed)
      )
      for (i in vals.indices) {
        val col = rawColDefs[i]
        val tw = p.measureText(vals[i])
        val tx = if (i < 2) rx + 4f else rx + col.second - tw - 4f
        canvas.drawText(vals[i], tx, curY + 7.5f, p)
        rx += col.second
      }
      curY += 10f
      isAlt = !isAlt
    }

    // Raw Subtotal row
    p.color = borderCol
    canvas.drawRect(30f, curY, 812f, curY + 10f, p)
    p.color = textDark
    p.textSize = 6.2f
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText("Total Raw Materials", 34f, curY + 7.5f, p)
    val rawTotals = listOf(
      formatNum(result.totalRawToday),
      formatNum(result.totalRawPrevious),
      formatNum(result.totalRawReceived),
      formatNum(result.totalFeed)
    )
    var rtx = 30f + rawColDefs[0].second + rawColDefs[1].second
    for (i in rawTotals.indices) {
      val col = rawColDefs[i + 2]
      val tw = p.measureText(rawTotals[i])
      canvas.drawText(rawTotals[i], rtx + col.second - tw - 4f, curY + 7.5f, p)
      rtx += col.second
    }
    curY += 14f

    // 2. Products Table
    p.color = deepBlue
    p.textSize = 8f
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText("PRODUCT PRODUCTION TABLE (PER TANK STOCK & GROUP PRODUCTION)", 30f, curY + 7f, p)
    curY += 10f

    val prodColDefs = listOf(
      Pair("Product", 110f),
      Pair("Tank", 80f),
      Pair("Present (L)", 140f),
      Pair("Previous (L)", 140f),
      Pair("Change/Production (L)", 145f),
      Pair("Group Production (L)", 167f)
    )

    p.color = darkSlate
    canvas.drawRect(30f, curY, 812f, curY + 12f, p)
    p.color = android.graphics.Color.WHITE
    p.textSize = 6.5f
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    hx = 30f
    for (c in prodColDefs) {
      val tw = p.measureText(c.first)
      val tx = if (c.first == "Product" || c.first == "Tank") hx + 4f else hx + c.second - tw - 4f
      canvas.drawText(c.first, tx, curY + 8.5f, p)
      hx += c.second
    }
    curY += 12f

    isAlt = false
    for (pg in result.productGroups) {
      // Tank rows
      for (pt in pg.tanks) {
        p.color = if (isAlt) altRowBg else android.graphics.Color.WHITE
        canvas.drawRect(30f, curY, 812f, curY + 9.5f, p)
        p.textSize = 6.2f
        p.typeface = android.graphics.Typeface.DEFAULT

        val adjLabel = if (pt.hasAdjustment) " [adj]" else ""
        val isNeg = pt.changeD < 0

        var rx = 30f
        val vals = listOf(
          Pair(pg.productName, false),
          Pair(pt.tankName + adjLabel, false),
          Pair(formatNum(pt.todayVolume), false),
          Pair(formatNum(pt.previousVolume), false),
          Pair(formatNum(pt.changeD, true), isNeg),
          Pair("-", false)
        )
        for (i in vals.indices) {
          val col = prodColDefs[i]
          val (txt, neg) = vals[i]
          p.color = if (neg) redCol else textDark
          val tw = p.measureText(txt)
          val tx = if (i < 2) rx + 4f else rx + col.second - tw - 4f
          canvas.drawText(txt, tx, curY + 7.2f, p)
          rx += col.second
        }
        curY += 9.5f
        isAlt = !isAlt
      }

      // Group total row
      p.color = android.graphics.Color.parseColor("#E2E8F0")
      canvas.drawRect(30f, curY, 812f, curY + 9.5f, p)
      p.textSize = 6.2f
      p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)

      val groupProdTxt = String.format(Locale.US, "%s (%.2f%%)", formatNum(pg.groupProduction), pg.percentOfFeed)
      val isGroupNeg = pg.totalChangeD < 0
      val gVals = listOf(
        Pair("  Total " + pg.productName, false),
        Pair("", false),
        Pair(formatNum(pg.totalTodayVolume), false),
        Pair(formatNum(pg.totalPreviousVolume), false),
        Pair(formatNum(pg.totalChangeD, true), isGroupNeg),
        Pair(groupProdTxt, false)
      )
      var grx = 30f
      for (i in gVals.indices) {
        val col = prodColDefs[i]
        val (txt, neg) = gVals[i]
        p.color = if (neg) redCol else textDark
        val tw = p.measureText(txt)
        val tx = if (i < 2) grx + 4f else grx + col.second - tw - 4f
        canvas.drawText(txt, tx, curY + 7.2f, p)
        grx += col.second
      }
      curY += 9.5f
    }

    // Product Subtotal row
    p.color = borderCol
    canvas.drawRect(30f, curY, 812f, curY + 10f, p)
    p.color = textDark
    p.textSize = 6.2f
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText("Total Finished Products", 34f, curY + 7.5f, p)
    val prodTotals = listOf(
      formatNum(result.totalProductToday),
      formatNum(result.totalProductPrevious),
      formatNum(result.productSubTotal, true),
      formatNum(result.totalProduced)
    )
    var ptx = 30f + prodColDefs[0].second + prodColDefs[1].second
    for (i in prodTotals.indices) {
      val col = prodColDefs[i + 2]
      val txt = prodTotals[i]
      p.color = if (i == 2 && result.productSubTotal < 0) redCol else textDark
      val tw = p.measureText(txt)
      canvas.drawText(txt, ptx + col.second - tw - 4f, curY + 7.5f, p)
      ptx += col.second
    }
    curY += 13f

    // 3. Blending Tanks Block (if present)
    if (result.blendingTanks.isNotEmpty()) {
      p.color = deepBlue
      p.textSize = 8f
      p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
      canvas.drawText("BLENDING TANKS (NOT INCLUDED IN PRODUCT %)", 30f, curY + 7f, p)
      curY += 9f

      val blendColDefs = listOf(
        Pair("Tank", 90f),
        Pair("Product", 130f),
        Pair("Present (L)", 140f),
        Pair("Previous (L)", 140f),
        Pair("Delivered (L)", 140f),
        Pair("Production (L)", 142f)
      )

      p.color = darkSlate
      canvas.drawRect(30f, curY, 812f, curY + 11f, p)
      p.color = android.graphics.Color.WHITE
      p.textSize = 6.3f
      p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
      hx = 30f
      for (c in blendColDefs) {
        val tw = p.measureText(c.first)
        val tx = if (c.first == "Tank" || c.first == "Product") hx + 4f else hx + c.second - tw - 4f
        canvas.drawText(c.first, tx, curY + 8f, p)
        hx += c.second
      }
      curY += 11f

      isAlt = false
      for (bt in result.blendingTanks) {
        p.color = if (isAlt) altRowBg else android.graphics.Color.WHITE
        canvas.drawRect(30f, curY, 812f, curY + 9f, p)
        p.color = textDark
        p.textSize = 6.2f
        p.typeface = android.graphics.Typeface.DEFAULT

        var rx = 30f
        val adjLabel = if (bt.hasAdjustment) " [adj]" else ""
        val vals = listOf(
          bt.tankName + adjLabel,
          bt.productName,
          formatNum(bt.todayVolume),
          formatNum(bt.previousVolume),
          formatNum(bt.delivered),
          formatNum(bt.production)
        )
        for (i in vals.indices) {
          val col = blendColDefs[i]
          val tw = p.measureText(vals[i])
          val tx = if (i < 2) rx + 4f else rx + col.second - tw - 4f
          canvas.drawText(vals[i], tx, curY + 6.8f, p)
          rx += col.second
        }
        curY += 9f
        isAlt = !isAlt
      }

      // Blending subtotal
      p.color = borderCol
      canvas.drawRect(30f, curY, 812f, curY + 9f, p)
      p.color = textDark
      p.textSize = 6.2f
      p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
      canvas.drawText("Total Blending Tanks", 34f, curY + 6.8f, p)
      val blendTotals = listOf(
        formatNum(result.totalBlendingToday),
        formatNum(result.totalBlendingPrevious),
        formatNum(result.totalBlendingDelivered),
        formatNum(result.totalBlendingProduction)
      )
      var btx = 30f + blendColDefs[0].second + blendColDefs[1].second
      for (i in blendTotals.indices) {
        val col = blendColDefs[i + 2]
        val tw = p.measureText(blendTotals[i])
        canvas.drawText(blendTotals[i], btx + col.second - tw - 4f, curY + 6.8f, p)
        btx += col.second
      }
      curY += 12f
    }

    // 4. Process Waste & Loss Reconciliation Strip
    val stripWidth = 385f
    p.color = lightBg
    canvas.drawRoundRect(30f, curY, 30f + stripWidth, curY + 30f, 4f, 4f, p)
    p.color = borderCol
    p.style = android.graphics.Paint.Style.STROKE
    canvas.drawRoundRect(30f, curY, 30f + stripWidth, curY + 30f, 4f, 4f, p)
    p.style = android.graphics.Paint.Style.FILL

    p.color = textDark
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    p.textSize = 7f
    canvas.drawText("PRODUCTION BALANCE SUMMARY", 38f, curY + 10f, p)
    p.typeface = android.graphics.Typeface.DEFAULT
    p.textSize = 6.5f
    val balStr = String.format(
      Locale.US,
      "Feed: %,.0f L | Produced: %,.0f L | Loss: %,.0f L (%.2f%%)",
      result.totalFeed, result.totalProduced, result.totalLoss, result.lossPercent
    )
    canvas.drawText(balStr, 38f, curY + 22f, p)

    val rxBox = 427f
    p.color = lightBg
    canvas.drawRoundRect(rxBox, curY, rxBox + stripWidth, curY + 30f, 4f, 4f, p)
    p.color = borderCol
    p.style = android.graphics.Paint.Style.STROKE
    canvas.drawRoundRect(rxBox, curY, rxBox + stripWidth, curY + 30f, 4f, 4f, p)
    p.style = android.graphics.Paint.Style.FILL

    p.color = textDark
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    p.textSize = 7f
    canvas.drawText("PROCESS WASTE & FLARING (13 METERS)", rxBox + 8f, curY + 10f, p)
    p.typeface = android.graphics.Typeface.DEFAULT
    p.textSize = 6.5f
    if (!result.isWasteEntered) {
      canvas.drawText("Process Waste: Not entered", rxBox + 8f, curY + 22f, p)
    } else {
      val wasteStr = String.format(
        Locale.US,
        "Flaring: %.2f%% | Heaters: %.2f%% | H2: %.2f%% | Measured: %.2f%% | Unmeasured: %.2f%%",
        result.actualFlaringPct, result.fuelForHeatersPct, result.h2MakeUpKnhtPct, result.totalMeasuredLossPct, result.unmeasuredLossPct
      )
      canvas.drawText(wasteStr, rxBox + 8f, curY + 22f, p)
    }

    // Executive Signature Boxes
    val sigY = 528f
    val sigWidth = 240f
    val sigs = listOf(
      Pair("PREPARED BY", settings.signaturePreparedBy.ifBlank { "Shift Operator" }),
      Pair("CHECKED BY", settings.signatureCheckedBy.ifBlank { "Shift In-Charge" }),
      Pair("APPROVED BY", settings.signatureApprovedBy.ifBlank { "Plant Manager" })
    )

    for (i in sigs.indices) {
      val sx = 30f + i * (sigWidth + 31f)
      p.color = lightBg
      canvas.drawRoundRect(sx, sigY, sx + sigWidth, sigY + 38f, 4f, 4f, p)
      p.color = borderCol
      p.style = android.graphics.Paint.Style.STROKE
      p.strokeWidth = 0.8f
      canvas.drawRoundRect(sx, sigY, sx + sigWidth, sigY + 38f, 4f, 4f, p)
      canvas.drawLine(sx + 8f, sigY + 22f, sx + sigWidth - 8f, sigY + 22f, p)
      p.style = android.graphics.Paint.Style.FILL

      p.color = deepBlue
      p.textSize = 6.8f
      p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
      canvas.drawText(sigs[i].first, sx + 8f, sigY + 33f, p)
      p.color = textDark
      p.typeface = android.graphics.Typeface.DEFAULT
      canvas.drawText(sigs[i].second, sx + 80f, sigY + 33f, p)
    }

    // Footer
    p.color = android.graphics.Color.parseColor("#64748B")
    p.textSize = 7f
    p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText("Generated by Tank Calc | Developed by Meherab Hossain Jihad", 30f, 584f, p)
    val pageTxt = "$companyName • Page 1 of 1"
    val ptw = p.measureText(pageTxt)
    canvas.drawText(pageTxt, 812f - ptw, 584f, p)

    doc.finishPage(page)

    val filename = "Production_Report_${result.basis}_${result.reportDate}.pdf"
    val file = File(context.cacheDir, filename)
    java.io.FileOutputStream(file).use { out ->
      doc.writeTo(out)
    }
    doc.close()
    return file
  }

  fun getPdfShareIntent(context: Context, file: File, subject: String): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return Intent(Intent.ACTION_SEND).apply {
      type = "application/pdf"
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, subject)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }

  fun getPdfViewIntent(context: Context, file: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, "application/pdf")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }
}
