package com.example.engine

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ProductionReportExcelUtils {

  fun exportReportExcel(
    context: Context,
    setup: ProductionReportSetup,
    result: ProductionReportCalculationResult,
    settings: AppSettings,
    basis: CalculationBasis
  ): File {
    val basisSuffix = if (basis == CalculationBasis.TEMP_30C) " (30 deg)" else ""
    val filename = "Production_report_${result.reportDate}$basisSuffix.xlsx"
    val file = File(context.cacheDir, filename)

    FileOutputStream(file).use { fos ->
      ZipOutputStream(fos).use { zos ->
        zos.putNextEntry(ZipEntry("[Content_Types].xml"))
        zos.write(getContentTypesXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("_rels/.rels"))
        zos.write(getRelsXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        zos.write(getWorkbookRelsXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("xl/workbook.xml"))
        zos.write(getWorkbookXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("xl/styles.xml"))
        zos.write(getStylesXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        zos.write(getSheet1Xml(setup, result, settings, basis).toByteArray(Charsets.UTF_8))
        zos.closeEntry()
      }
    }
    return file
  }

  fun exportErpExcel(
    context: Context,
    erpRows: List<ErpRowData>,
    result: ProductionReportCalculationResult,
    settings: AppSettings
  ): File {
    val filename = "ERP_Statement_${result.reportDate}.xlsx"
    val file = File(context.cacheDir, filename)

    FileOutputStream(file).use { fos ->
      ZipOutputStream(fos).use { zos ->
        zos.putNextEntry(ZipEntry("[Content_Types].xml"))
        zos.write(getContentTypesXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("_rels/.rels"))
        zos.write(getRelsXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        zos.write(getWorkbookRelsXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("xl/workbook.xml"))
        zos.write(getWorkbookXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("xl/styles.xml"))
        zos.write(getStylesXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        zos.write(getErpSheetXml(erpRows, result, settings).toByteArray(Charsets.UTF_8))
        zos.closeEntry()
      }
    }
    return file
  }

  fun getExcelShareIntent(context: Context, file: File): Intent? {
    return try {
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name.replace(".xlsx", ""))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun getContentTypesXml(): String = """
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
      <Default Extension="xml" ContentType="application/xml"/>
      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.workbook+xml"/>
      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
      <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
    </Types>
  """.trimIndent()

  private fun getRelsXml(): String = """
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
    </Relationships>
  """.trimIndent()

  private fun getWorkbookRelsXml(): String = """
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
    </Relationships>
  """.trimIndent()

  private fun getWorkbookXml(): String = """
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
      <workbookPr fullCalcOnLoad="1"/>
      <sheets>
        <sheet name="Report" sheetId="1" r:id="rId1"/>
      </sheets>
    </workbook>
  """.trimIndent()

  private fun getStylesXml(): String = """
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
      <fonts count="4">
        <font><name val="Calibri"/><sz val="11"/><color rgb="FF000000"/></font>
        <font><name val="Calibri"/><sz val="11"/><b/><color rgb="FF000000"/></font>
        <font><name val="Calibri"/><sz val="20"/><b/><color rgb="FF000000"/></font>
        <font><name val="Calibri"/><sz val="11"/><b/><color rgb="FFFFFFFF"/></font>
      </fonts>
      <fills count="7">
        <fill><patternFill patternType="none"/></fill>
        <fill><patternFill patternType="gray125"/></fill>
        <fill><patternFill patternType="solid"><fgColor rgb="FFF1F5F9"/></patternFill></fill>
        <fill><patternFill patternType="solid"><fgColor rgb="FF0F4C81"/></patternFill></fill>
        <fill><patternFill patternType="solid"><fgColor rgb="FFFFFF00"/></patternFill></fill>
        <fill><patternFill patternType="solid"><fgColor rgb="FF64748B"/></patternFill></fill>
        <fill><patternFill patternType="solid"><fgColor rgb="FFDBEAFE"/></patternFill></fill>
      </fills>
      <borders count="2">
        <border><left/><right/><top/><bottom/></border>
        <border>
          <left style="thin"><color rgb="FFCBD5E1"/></left>
          <right style="thin"><color rgb="FFCBD5E1"/></right>
          <top style="thin"><color rgb="FFCBD5E1"/></top>
          <bottom style="thin"><color rgb="FFCBD5E1"/></bottom>
        </border>
      </borders>
      <cellStyleXfs count="1">
        <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
      </cellStyleXfs>
      <cellXfs count="8">
        <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
        <xf numFmtId="0" fontId="1" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"/>
        <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
        <xf numFmtId="0" fontId="3" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
        <xf numFmtId="3" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/> <!-- #,##0 -->
        <xf numFmtId="9" fontId="1" fillId="4" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"/> <!-- 0.00% yellow -->
        <xf numFmtId="1" fontId="1" fillId="5" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/> <!-- dark grey bold -->
        <xf numFmtId="3" fontId="0" fillId="6" borderId="1" xfId="0" applyNumberFormat="1" applyFill="1" applyBorder="1"/> <!-- light blue -->
      </cellXfs>
    </styleSheet>
  """.trimIndent()

  private fun getSheet1Xml(
    setup: ProductionReportSetup,
    result: ProductionReportCalculationResult,
    settings: AppSettings,
    basis: CalculationBasis
  ): String {
    val companyName = settings.plantName.ifBlank { "PARTEX PETRO LTD." }
    val basisTitle = if (basis == CalculationBasis.TEMP_30C) "Qty (Vol.) @ 30 °C" else "Qty (Vol.) @ Natural"

    return buildString {
      appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
      appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
      appendLine("""  <sheetPr><pageSetUpPr/></sheetPr>""")
      appendLine("""  <sheetFormatPr defaultRowHeight="15"/>""")
      appendLine("""  <cols>""")
      appendLine("""    <col min="1" max="1" width="6.3" customWidth="1"/>""")
      appendLine("""    <col min="2" max="2" width="29.3" customWidth="1"/>""")
      appendLine("""    <col min="3" max="3" width="16.3" customWidth="1"/>""")
      appendLine("""    <col min="4" max="4" width="19.8" customWidth="1"/>""")
      appendLine("""    <col min="5" max="5" width="19.2" customWidth="1"/>""")
      appendLine("""    <col min="6" max="6" width="23.1" customWidth="1"/>""")
      appendLine("""    <col min="7" max="7" width="19.2" customWidth="1"/>""")
      appendLine("""    <col min="8" max="8" width="17.8" customWidth="1"/>""")
      appendLine("""    <col min="9" max="9" width="21.4" customWidth="1"/>""")
      appendLine("""    <col min="10" max="10" width="14.8" customWidth="1"/>""")
      appendLine("""  </cols>""")
      appendLine("""  <sheetData>""")

      // Header rows 1 to 4
      appendLine("""    <row r="1" ht="26.25" customHeight="1">""")
      appendLine("""      <c r="A1" s="2" t="inlineStr"><is><t>$companyName DAILY PRODUCTION REPORT</t></is></c>""")
      appendLine("""    </row>""")

      appendLine("""    <row r="2">""")
      appendLine("""      <c r="B2" s="1" t="inlineStr"><is><t>Duration:</t></is></c>""")
      appendLine("""      <c r="C2" s="0" t="inlineStr"><is><t>${result.previousDate} 10:00 AM To ${result.reportDate} 10:00 AM</t></is></c>""")
      appendLine("""    </row>""")

      appendLine("""    <row r="3">""")
      appendLine("""      <c r="B3" s="1" t="inlineStr"><is><t>Time of Data Collection:</t></is></c>""")
      appendLine("""      <c r="C3" s="0" t="inlineStr"><is><t>10:00 AM (${result.reportDate})</t></is></c>""")
      appendLine("""    </row>""")

      appendLine("""    <row r="4">""")
      appendLine("""      <c r="B4" s="1" t="inlineStr"><is><t>Reporting Date:</t></is></c>""")
      appendLine("""      <c r="C4" s="0" t="inlineStr"><is><t>${result.reportDate}</t></is></c>""")
      appendLine("""    </row>""")

      // Raw material block headers
      appendLine("""    <row r="7" ht="65" customHeight="1">""")
      appendLine("""      <c r="B7" s="3" t="inlineStr"><is><t>RAW MATERIAL</t></is></c>""")
      appendLine("""      <c r="D7" s="3" t="inlineStr"><is><t>Present Stock report (${result.reportDate}) in Ltr.</t></is></c>""")
      appendLine("""      <c r="E7" s="3" t="inlineStr"><is><t>Present Stock report (${result.previousDate}) in Ltr.</t></is></c>""")
      appendLine("""      <c r="F7" s="3" t="inlineStr"><is><t>PROCESSED QTY</t></is></c>""")
      appendLine("""    </row>""")

      appendLine("""    <row r="8">""")
      appendLine("""      <c r="C8" s="1" t="inlineStr"><is><t>TANK ID</t></is></c>""")
      appendLine("""      <c r="D8" s="1" t="inlineStr"><is><t>$basisTitle</t></is></c>""")
      appendLine("""      <c r="E8" s="1" t="inlineStr"><is><t>$basisTitle</t></is></c>""")
      appendLine("""      <c r="F8" s="1" t="inlineStr"><is><t>Qty (Vol.)</t></is></c>""")
      appendLine("""      <c r="G8" s="1" t="inlineStr"><is><t>NGC Received Qty</t></is></c>""")
      appendLine("""      <c r="H8" s="1" t="inlineStr"><is><t>Feed Split</t></is></c>""")
      appendLine("""      <c r="I8" s="1" t="inlineStr"><is><t>Quantity (L)</t></is></c>""")
      appendLine("""      <c r="J8" s="1" t="inlineStr"><is><t>Percentage</t></is></c>""")
      appendLine("""    </row>""")

      // Raw material rows
      var rIdx = 11
      val rawStartRow = rIdx
      result.rawTanks.forEach { rt ->
        appendLine("""    <row r="$rIdx">""")
        appendLine("""      <c r="B$rIdx" s="0" t="inlineStr"><is><t>${rt.productCode}</t></is></c>""")
        appendLine("""      <c r="C$rIdx" s="0" t="inlineStr"><is><t>${rt.tankName}</t></is></c>""")
        appendLine("""      <c r="D$rIdx" s="4"><v>${rt.todayVolume}</v></c>""")
        appendLine("""      <c r="E$rIdx" s="4"><v>${rt.previousVolume}</v></c>""")
        appendLine("""      <c r="F$rIdx" s="4"><f>E$rIdx-D$rIdx+G$rIdx</f><v>${rt.processed}</v></c>""")
        appendLine("""      <c r="G$rIdx" s="4"><v>${rt.received}</v></c>""")
        if (rIdx == 11) {
          appendLine("""      <c r="H$rIdx" s="0" t="inlineStr"><is><t>Condensate (Export)</t></is></c>""")
          appendLine("""      <c r="I$rIdx" s="4"><v>${result.totalFeed * 0.75}</v></c>""")
          appendLine("""      <c r="J$rIdx" s="5"><f>I$rIdx/F$rawStartRow</f><v>0.75</v></c>""")
        } else if (rIdx == 12) {
          appendLine("""      <c r="H$rIdx" s="0" t="inlineStr"><is><t>Naphtha (Local)</t></is></c>""")
          appendLine("""      <c r="I$rIdx" s="4"><v>${result.totalFeed * 0.25}</v></c>""")
          appendLine("""      <c r="J$rIdx" s="5"><f>I$rIdx/F$rawStartRow</f><v>0.25</v></c>""")
        }
        appendLine("""    </row>""")
        rIdx++
      }

      val rawEndRow = rIdx - 1
      val rawTotalRow = rIdx
      appendLine("""    <row r="$rawTotalRow">""")
      appendLine("""      <c r="B$rawTotalRow" s="6" t="inlineStr"><is><t>Total Raw/Semi-Raw Mat.=</t></is></c>""")
      appendLine("""      <c r="C$rawTotalRow" s="6"/>""")
      appendLine("""      <c r="D$rawTotalRow" s="6"><f>SUM(D$rawStartRow:D$rawEndRow)</f><v>${result.rawTanks.sumOf { it.todayVolume }}</v></c>""")
      appendLine("""      <c r="E$rawTotalRow" s="6"><f>SUM(E$rawStartRow:E$rawEndRow)</f><v>${result.rawTanks.sumOf { it.previousVolume }}</v></c>""")
      appendLine("""      <c r="F$rawTotalRow" s="6"><f>SUM(F$rawStartRow:F$rawEndRow)</f><v>${result.totalFeed}</v></c>""")
      appendLine("""      <c r="G$rawTotalRow" s="6"><f>SUM(G$rawStartRow:G$rawEndRow)</f><v>${result.rawTanks.sumOf { it.received }}</v></c>""")
      appendLine("""    </row>""")

      rIdx += 3
      // Product block header
      appendLine("""    <row r="$rIdx" ht="63" customHeight="1">""")
      appendLine("""      <c r="B$rIdx" s="3" t="inlineStr"><is><t>DESCRIPTION OF MATERIAL</t></is></c>""")
      appendLine("""      <c r="C$rIdx" s="3" t="inlineStr"><is><t>TANK ID</t></is></c>""")
      appendLine("""      <c r="D$rIdx" s="3" t="inlineStr"><is><t>Present Stock report (${result.reportDate}) in Ltr.</t></is></c>""")
      appendLine("""      <c r="E$rIdx" s="3" t="inlineStr"><is><t>Present Stock report (${result.previousDate}) in Ltr.</t></is></c>""")
      appendLine("""      <c r="F$rIdx" s="3" t="inlineStr"><is><t>PROCESSED QTY / PRODUCTION</t></is></c>""")
      appendLine("""      <c r="G$rIdx" s="3" t="inlineStr"><is><t>Group stock</t></is></c>""")
      appendLine("""      <c r="H$rIdx" s="3" t="inlineStr"><is><t>Production (Lit)</t></is></c>""")
      appendLine("""    </row>""")
      rIdx++

      val prodStartRow = rIdx
      result.productGroups.forEach { pg ->
        pg.tanks.forEach { pt ->
          appendLine("""    <row r="$rIdx">""")
          appendLine("""      <c r="B$rIdx" s="0" t="inlineStr"><is><t>${pg.productName}</t></is></c>""")
          appendLine("""      <c r="C$rIdx" s="0" t="inlineStr"><is><t>${pt.tankName}</t></is></c>""")
          appendLine("""      <c r="D$rIdx" s="4"><v>${pt.todayVolume}</v></c>""")
          appendLine("""      <c r="E$rIdx" s="4"><v>${pt.previousVolume}</v></c>""")
          appendLine("""      <c r="F$rIdx" s="4"><f>D$rIdx-E$rIdx</f><v>${pt.changeD}</v></c>""")
          appendLine("""      <c r="G$rIdx" s="4"><v>${pg.totalTodayVolume}</v></c>""")
          appendLine("""      <c r="H$rIdx" s="4"><v>${pg.groupProduction}</v></c>""")
          appendLine("""    </row>""")
          rIdx++
        }
      }

      // Blending tanks in product block with D, E, F filled
      result.blendingTanks.forEach { bt ->
        appendLine("""    <row r="$rIdx">""")
        appendLine("""      <c r="B$rIdx" s="0" t="inlineStr"><is><t>${bt.productName}</t></is></c>""")
        appendLine("""      <c r="C$rIdx" s="0" t="inlineStr"><is><t>${bt.tankName}</t></is></c>""")
        appendLine("""      <c r="D$rIdx" s="4"><v>${bt.todayVolume}</v></c>""")
        appendLine("""      <c r="E$rIdx" s="4"><v>${bt.previousVolume}</v></c>""")
        appendLine("""      <c r="F$rIdx" s="4"><v>${bt.production}</v></c>""")
        appendLine("""      <c r="G$rIdx" s="4"><v>${bt.todayVolume}</v></c>""")
        appendLine("""      <c r="H$rIdx" s="4"><v>${bt.production}</v></c>""")
        appendLine("""    </row>""")
        rIdx++
      }

      val prodEndRow = rIdx - 1
      val prodSubTotalRow = rIdx
      appendLine("""    <row r="$prodSubTotalRow">""")
      appendLine("""      <c r="B$prodSubTotalRow" s="6" t="inlineStr"><is><t>Sub Total:</t></is></c>""")
      appendLine("""      <c r="D$prodSubTotalRow" s="6"><f>SUM(D$prodStartRow:D$prodEndRow)</f><v>${result.totalProductToday + result.totalBlendingToday}</v></c>""")
      appendLine("""      <c r="E$prodSubTotalRow" s="6"><f>SUM(E$prodStartRow:E$prodEndRow)</f><v>${result.totalProductPrevious + result.totalBlendingPrevious}</v></c>""")
      appendLine("""      <c r="F$prodSubTotalRow" s="6"><f>SUM(F$prodStartRow:F$prodEndRow)</f><v>${result.productSubTotal}</v></c>""")
      appendLine("""    </row>""")

      rIdx += 3
      val totalProdRow = rIdx
      appendLine("""    <row r="$totalProdRow">""")
      appendLine("""      <c r="B$totalProdRow" s="6" t="inlineStr"><is><t>(B) Total Produced Product</t></is></c>""")
      appendLine("""      <c r="F$totalProdRow" s="6"><v>${result.totalChangeB}</v></c>""")
      appendLine("""    </row>""")

      rIdx++
      val lossRow = rIdx
      appendLine("""    <row r="$lossRow">""")
      appendLine("""      <c r="B$lossRow" s="5" t="inlineStr"><is><t>Total Loss (A-B)</t></is></c>""")
      appendLine("""      <c r="F$lossRow" s="5"><f>F$rawTotalRow-F$totalProdRow</f><v>${result.lossOptionB}</v></c>""")
      appendLine("""    </row>""")

      rIdx++
      val lossPctRow = rIdx
      appendLine("""    <row r="$lossPctRow">""")
      appendLine("""      <c r="B$lossPctRow" s="5" t="inlineStr"><is><t>Loss (%)</t></is></c>""")
      appendLine("""      <c r="F$lossPctRow" s="5"><f>F$lossRow/F$rawTotalRow</f><v>${result.lossPercentOptionB / 100.0}</v></c>""")
      appendLine("""    </row>""")

      rIdx += 2
      // Loss comparison block
      appendLine("""    <row r="$rIdx">""")
      appendLine("""      <c r="B$rIdx" s="3" t="inlineStr"><is><t>Loss comparison</t></is></c>""")
      appendLine("""      <c r="E$rIdx" s="3" t="inlineStr"><is><t>Option A (Total Product)</t></is></c>""")
      appendLine("""      <c r="F$rIdx" s="3" t="inlineStr"><is><t>Option B (All-Tank TOV)</t></is></c>""")
      appendLine("""    </row>""")
      rIdx++
      appendLine("""    <row r="$rIdx">""")
      appendLine("""      <c r="B$rIdx" s="0" t="inlineStr"><is><t>Total Product / Change (L)</t></is></c>""")
      appendLine("""      <c r="E$rIdx" s="4"><v>${result.totalProductA}</v></c>""")
      appendLine("""      <c r="F$rIdx" s="4"><v>${result.totalChangeB}</v></c>""")
      appendLine("""    </row>""")
      rIdx++
      appendLine("""    <row r="$rIdx">""")
      appendLine("""      <c r="B$rIdx" s="0" t="inlineStr"><is><t>Process Loss (L)</t></is></c>""")
      appendLine("""      <c r="E$rIdx" s="4"><v>${result.lossOptionA}</v></c>""")
      appendLine("""      <c r="F$rIdx" s="4"><v>${result.lossOptionB}</v></c>""")
      appendLine("""    </row>""")
      rIdx++
      appendLine("""    <row r="$rIdx">""")
      appendLine("""      <c r="B$rIdx" s="0" t="inlineStr"><is><t>Loss (%)</t></is></c>""")
      appendLine("""      <c r="E$rIdx" s="5"><v>${result.lossPercentOptionA / 100.0}</v></c>""")
      appendLine("""      <c r="F$rIdx" s="5"><v>${result.lossPercentOptionB / 100.0}</v></c>""")
      appendLine("""    </row>""")
      rIdx++
      appendLine("""    <row r="$rIdx">""")
      appendLine("""      <c r="B$rIdx" s="0" t="inlineStr"><is><t>Yield (%)</t></is></c>""")
      appendLine("""      <c r="E$rIdx" s="5"><v>${result.yieldOptionA / 100.0}</v></c>""")
      appendLine("""      <c r="F$rIdx" s="5"><v>${result.yieldOptionB / 100.0}</v></c>""")
      appendLine("""    </row>""")
      rIdx++
      appendLine("""    <row r="$rIdx">""")
      appendLine("""      <c r="B$rIdx" s="1" t="inlineStr"><is><t>Difference (A - B)</t></is></c>""")
      appendLine("""      <c r="E$rIdx" s="4"><v>${result.diffAB}</v></c>""")
      appendLine("""      <c r="F$rIdx" s="0" t="inlineStr"><is><t>${result.diffExplanation}</t></is></c>""")
      appendLine("""    </row>""")

      // Process Waste Table
      rIdx += 3
      appendLine("""    <row r="$rIdx" ht="25" customHeight="1">""")
      appendLine("""      <c r="B$rIdx" s="3" t="inlineStr"><is><t>PROCESS WASTE &amp; LOSS RECONCILIATION</t></is></c>""")
      appendLine("""      <c r="C$rIdx" s="3" t="inlineStr"><is><t>TAG</t></is></c>""")
      appendLine("""      <c r="D$rIdx" s="3" t="inlineStr"><is><t>kg/h</t></is></c>""")
      appendLine("""      <c r="E$rIdx" s="3" t="inlineStr"><is><t>kg/day</t></is></c>""")
      appendLine("""      <c r="F$rIdx" s="3" t="inlineStr"><is><t>Volume (L)</t></is></c>""")
      appendLine("""      <c r="G$rIdx" s="3" t="inlineStr"><is><t>% of Feed</t></is></c>""")
      appendLine("""    </row>""")
      rIdx++

      if (!result.isWasteEntered) {
        appendLine("""    <row r="$rIdx">""")
        appendLine("""      <c r="B$rIdx" s="0" t="inlineStr"><is><t>Not entered</t></is></c>""")
        appendLine("""    </row>""")
        rIdx++
      } else {
        result.wasteItems.forEach { wi ->
          appendLine("""    <row r="$rIdx">""")
          appendLine("""      <c r="B$rIdx" s="0" t="inlineStr"><is><t>${wi.name}</t></is></c>""")
          appendLine("""      <c r="C$rIdx" s="0" t="inlineStr"><is><t>${wi.tag}</t></is></c>""")
          appendLine("""      <c r="D$rIdx" s="4"><v>${wi.kgH}</v></c>""")
          appendLine("""      <c r="E$rIdx" s="4"><v>${wi.kgDay}</v></c>""")
          appendLine("""      <c r="F$rIdx" s="4"><v>${wi.litres24h}</v></c>""")
          appendLine("""      <c r="G$rIdx" s="5"><v>${wi.percentOfFeed / 100.0}</v></c>""")
          appendLine("""    </row>""")
          rIdx++
        }

        appendLine("""    <row r="$rIdx">""")
        appendLine("""      <c r="B$rIdx" s="6" t="inlineStr"><is><t>TOTAL (13 Meters)</t></is></c>""")
        appendLine("""      <c r="C$rIdx" s="6"/>""")
        appendLine("""      <c r="D$rIdx" s="6"><v>${result.totalWasteKgH}</v></c>""")
        appendLine("""      <c r="E$rIdx" s="6"><v>${result.totalWasteKgDay}</v></c>""")
        appendLine("""      <c r="F$rIdx" s="6"><v>${result.totalWasteLitres}</v></c>""")
        appendLine("""      <c r="G$rIdx" s="5"><v>${result.totalMeasuredLossPct / 100.0}</v></c>""")
        appendLine("""    </row>""")
        rIdx++

        appendLine("""    <row r="$rIdx">""")
        appendLine("""      <c r="B$rIdx" s="1" t="inlineStr"><is><t>Unmeasured Process Loss (%)</t></is></c>""")
        appendLine("""      <c r="G$rIdx" s="5"><v>${result.unmeasuredLossPct / 100.0}</v></c>""")
        appendLine("""    </row>""")
        rIdx++

        appendLine("""    <row r="$rIdx">""")
        appendLine("""      <c r="B$rIdx" s="1" t="inlineStr"><is><t>H2 Make-up for KNHT (%)</t></is></c>""")
        appendLine("""      <c r="G$rIdx" s="5"><v>${result.h2MakeUpKnhtPct / 100.0}</v></c>""")
        appendLine("""    </row>""")
        rIdx++

        appendLine("""    <row r="$rIdx">""")
        appendLine("""      <c r="B$rIdx" s="1" t="inlineStr"><is><t>Fuel for Heaters (%)</t></is></c>""")
        appendLine("""      <c r="G$rIdx" s="5"><v>${result.fuelForHeatersPct / 100.0}</v></c>""")
        appendLine("""    </row>""")
        rIdx++

        appendLine("""    <row r="$rIdx">""")
        appendLine("""      <c r="B$rIdx" s="1" t="inlineStr"><is><t>Actual Flaring (%)</t></is></c>""")
        appendLine("""      <c r="G$rIdx" s="5"><v>${result.actualFlaringPct / 100.0}</v></c>""")
        appendLine("""    </row>""")
      }

      appendLine("""  </sheetData>""")
      appendLine("""</worksheet>""")
    }
  }

  private fun getErpSheetXml(
    erpRows: List<ErpRowData>,
    result: ProductionReportCalculationResult,
    settings: AppSettings
  ): String {
    val companyName = settings.plantName.ifBlank { "PARTEX PETRO LTD." }

    return buildString {
      appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
      appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
      appendLine("""  <sheetPr><pageSetUpPr/></sheetPr>""")
      appendLine("""  <sheetFormatPr defaultRowHeight="15"/>""")
      appendLine("""  <cols>""")
      appendLine("""    <col min="1" max="1" width="5.0" customWidth="1"/>""")
      appendLine("""    <col min="2" max="2" width="24.0" customWidth="1"/>""")
      appendLine("""    <col min="3" max="3" width="18.0" customWidth="1"/>""")
      appendLine("""    <col min="4" max="4" width="16.0" customWidth="1"/>""")
      appendLine("""    <col min="5" max="5" width="20.0" customWidth="1"/>""")
      appendLine("""    <col min="6" max="6" width="18.0" customWidth="1"/>""")
      appendLine("""    <col min="7" max="7" width="16.0" customWidth="1"/>""")
      appendLine("""    <col min="8" max="8" width="22.0" customWidth="1"/>""")
      appendLine("""    <col min="9" max="9" width="16.0" customWidth="1"/>""")
      appendLine("""    <col min="10" max="10" width="16.0" customWidth="1"/>""")
      appendLine("""    <col min="11" max="11" width="14.0" customWidth="1"/>""")
      appendLine("""  </cols>""")
      appendLine("""  <sheetData>""")

      // Header rows 1 to 3
      appendLine("""    <row r="1" ht="26.25" customHeight="1">""")
      appendLine("""      <c r="A1" s="2" t="inlineStr"><is><t>$companyName DAILY PRODUCTION &amp; STOCK STATEMENT (ERP)</t></is></c>""")
      appendLine("""    </row>""")
      appendLine("""    <row r="2">""")
      appendLine("""      <c r="B2" s="1" t="inlineStr"><is><t>Duration:</t></is></c>""")
      appendLine("""      <c r="C2" s="0" t="inlineStr"><is><t>${result.previousDate} 10:00 AM To ${result.reportDate} 10:00 AM</t></is></c>""")
      appendLine("""    </row>""")
      appendLine("""    <row r="3">""")
      appendLine("""      <c r="B3" s="1" t="inlineStr"><is><t>Basis:</t></is></c>""")
      appendLine("""      <c r="C3" s="0" t="inlineStr"><is><t>Volumes @ 30 °C (Litres)</t></is></c>""")
      appendLine("""    </row>""")

      var rIdx = 5
      // Finished products header
      appendLine("""    <row r="$rIdx" ht="20" customHeight="1">""")
      appendLine("""      <c r="B$rIdx" s="3" t="inlineStr"><is><t>FINISHED PRODUCTS (@ 30 °C)</t></is></c>""")
      appendLine("""    </row>""")
      rIdx++

      appendLine("""    <row r="$rIdx" ht="30" customHeight="1">""")
      appendLine("""      <c r="B$rIdx" s="3" t="inlineStr"><is><t>Product</t></is></c>""")
      appendLine("""      <c r="C$rIdx" s="3" t="inlineStr"><is><t>Tanks</t></is></c>""")
      appendLine("""      <c r="D$rIdx" s="3" t="inlineStr"><is><t>Opening</t></is></c>""")
      appendLine("""      <c r="E$rIdx" s="3" t="inlineStr"><is><t>Production/Received</t></is></c>""")
      appendLine("""      <c r="F$rIdx" s="3" t="inlineStr"><is><t>Used for blending</t></is></c>""")
      appendLine("""      <c r="G$rIdx" s="3" t="inlineStr"><is><t>Delivered</t></is></c>""")
      appendLine("""      <c r="H$rIdx" s="3" t="inlineStr"><is><t>Internal use/Evap</t></is></c>""")
      appendLine("""      <c r="I$rIdx" s="3" t="inlineStr"><is><t>Closing</t></is></c>""")
      appendLine("""      <c r="J$rIdx" s="3" t="inlineStr"><is><t>Physical Stock</t></is></c>""")
      appendLine("""      <c r="K$rIdx" s="3" t="inlineStr"><is><t>Check</t></is></c>""")
      appendLine("""    </row>""")
      rIdx++

      val finishedRows = erpRows.filter { !it.isRaw }
      val finStart = rIdx
      for (r in finishedRows) {
        val tanks = r.assignedTankNames.joinToString(", ")
        val check = if (kotlin.math.abs(r.difference) <= settings.erpToleranceL) "Balanced" else "Diff: ${r.difference.toInt()}"
        appendLine("""    <row r="$rIdx">""")
        appendLine("""      <c r="B$rIdx" s="0" t="inlineStr"><is><t>${r.itemName}</t></is></c>""")
        appendLine("""      <c r="C$rIdx" s="0" t="inlineStr"><is><t>$tanks</t></is></c>""")
        appendLine("""      <c r="D$rIdx" s="4"><v>${r.openingStock.toLong()}</v></c>""")
        appendLine("""      <c r="E$rIdx" s="4"><v>${r.productionOrReceived.toLong()}</v></c>""")
        appendLine("""      <c r="F$rIdx" s="4"><v>${r.usedForBlending.toLong()}</v></c>""")
        appendLine("""      <c r="G$rIdx" s="4"><v>${r.delivered.toLong()}</v></c>""")
        appendLine("""      <c r="H$rIdx" s="4"><v>${r.internalUseOrEvap.toLong()}</v></c>""")
        appendLine("""      <c r="I$rIdx" s="4"><v>${r.closingStock.toLong()}</v></c>""")
        appendLine("""      <c r="J$rIdx" s="4"><v>${r.physicalStock.toLong()}</v></c>""")
        appendLine("""      <c r="K$rIdx" s="0" t="inlineStr"><is><t>$check</t></is></c>""")
        appendLine("""    </row>""")
        rIdx++
      }
      val finEnd = rIdx - 1
      // Finished Total row
      appendLine("""    <row r="$rIdx">""")
      appendLine("""      <c r="B$rIdx" s="6" t="inlineStr"><is><t>Total Finished Products</t></is></c>""")
      appendLine("""      <c r="C$rIdx" s="6"/>""")
      appendLine("""      <c r="D$rIdx" s="6"><f>SUM(D$finStart:D$finEnd)</f><v>${finishedRows.sumOf { it.openingStock }.toLong()}</v></c>""")
      appendLine("""      <c r="E$rIdx" s="6"><f>SUM(E$finStart:E$finEnd)</f><v>${finishedRows.sumOf { it.productionOrReceived }.toLong()}</v></c>""")
      appendLine("""      <c r="F$rIdx" s="6"><f>SUM(F$finStart:F$finEnd)</f><v>${finishedRows.sumOf { it.usedForBlending }.toLong()}</v></c>""")
      appendLine("""      <c r="G$rIdx" s="6"><f>SUM(G$finStart:G$finEnd)</f><v>${finishedRows.sumOf { it.delivered }.toLong()}</v></c>""")
      appendLine("""      <c r="H$rIdx" s="6"><f>SUM(H$finStart:H$finEnd)</f><v>${finishedRows.sumOf { it.internalUseOrEvap }.toLong()}</v></c>""")
      appendLine("""      <c r="I$rIdx" s="6"><f>SUM(I$finStart:I$finEnd)</f><v>${finishedRows.sumOf { it.closingStock }.toLong()}</v></c>""")
      appendLine("""      <c r="J$rIdx" s="6"><f>SUM(J$finStart:J$finEnd)</f><v>${finishedRows.sumOf { it.physicalStock }.toLong()}</v></c>""")
      appendLine("""      <c r="K$rIdx" s="6"/>""")
      appendLine("""    </row>""")
      rIdx += 2

      // Raw Materials block
      appendLine("""    <row r="$rIdx" ht="20" customHeight="1">""")
      appendLine("""      <c r="B$rIdx" s="3" t="inlineStr"><is><t>RAW MATERIALS (@ 30 °C)</t></is></c>""")
      appendLine("""    </row>""")
      rIdx++

      appendLine("""    <row r="$rIdx" ht="30" customHeight="1">""")
      appendLine("""      <c r="B$rIdx" s="3" t="inlineStr"><is><t>Raw Material</t></is></c>""")
      appendLine("""      <c r="C$rIdx" s="3" t="inlineStr"><is><t>Tanks</t></is></c>""")
      appendLine("""      <c r="D$rIdx" s="3" t="inlineStr"><is><t>Opening</t></is></c>""")
      appendLine("""      <c r="E$rIdx" s="3" t="inlineStr"><is><t>Received</t></is></c>""")
      appendLine("""      <c r="F$rIdx" s="3" t="inlineStr"><is><t>Used for blending</t></is></c>""")
      appendLine("""      <c r="G$rIdx" s="3" t="inlineStr"><is><t>Delivered</t></is></c>""")
      appendLine("""      <c r="H$rIdx" s="3" t="inlineStr"><is><t>Used as Feed</t></is></c>""")
      appendLine("""      <c r="I$rIdx" s="3" t="inlineStr"><is><t>Closing</t></is></c>""")
      appendLine("""      <c r="J$rIdx" s="3" t="inlineStr"><is><t>Physical Stock</t></is></c>""")
      appendLine("""      <c r="K$rIdx" s="3" t="inlineStr"><is><t>Check</t></is></c>""")
      appendLine("""    </row>""")
      rIdx++

      val rawRows = erpRows.filter { it.isRaw }
      val rawStart = rIdx
      for (r in rawRows) {
        val tanks = r.assignedTankNames.joinToString(", ")
        val check = if (kotlin.math.abs(r.difference) <= settings.erpToleranceL) "Balanced" else "Diff: ${r.difference.toInt()}"
        appendLine("""    <row r="$rIdx">""")
        appendLine("""      <c r="B$rIdx" s="0" t="inlineStr"><is><t>${r.itemName}</t></is></c>""")
        appendLine("""      <c r="C$rIdx" s="0" t="inlineStr"><is><t>$tanks</t></is></c>""")
        appendLine("""      <c r="D$rIdx" s="4"><v>${r.openingStock.toLong()}</v></c>""")
        appendLine("""      <c r="E$rIdx" s="4"><v>${r.productionOrReceived.toLong()}</v></c>""")
        appendLine("""      <c r="F$rIdx" s="4"><v>0</v></c>""")
        appendLine("""      <c r="G$rIdx" s="4"><v>0</v></c>""")
        appendLine("""      <c r="H$rIdx" s="4"><v>${r.internalUseOrEvap.toLong()}</v></c>""")
        appendLine("""      <c r="I$rIdx" s="4"><v>${r.closingStock.toLong()}</v></c>""")
        appendLine("""      <c r="J$rIdx" s="4"><v>${r.physicalStock.toLong()}</v></c>""")
        appendLine("""      <c r="K$rIdx" s="0" t="inlineStr"><is><t>$check</t></is></c>""")
        appendLine("""    </row>""")
        rIdx++
      }
      val rawEnd = rIdx - 1
      appendLine("""    <row r="$rIdx">""")
      appendLine("""      <c r="B$rIdx" s="6" t="inlineStr"><is><t>Total Raw Materials</t></is></c>""")
      appendLine("""      <c r="C$rIdx" s="6"/>""")
      appendLine("""      <c r="D$rIdx" s="6"><f>SUM(D$rawStart:D$rawEnd)</f><v>${rawRows.sumOf { it.openingStock }.toLong()}</v></c>""")
      appendLine("""      <c r="E$rIdx" s="6"><f>SUM(E$rawStart:E$rawEnd)</f><v>${rawRows.sumOf { it.productionOrReceived }.toLong()}</v></c>""")
      appendLine("""      <c r="F$rIdx" s="6"><v>0</v></c>""")
      appendLine("""      <c r="G$rIdx" s="6"><v>0</v></c>""")
      appendLine("""      <c r="H$rIdx" s="6"><f>SUM(H$rawStart:H$rawEnd)</f><v>${rawRows.sumOf { it.internalUseOrEvap }.toLong()}</v></c>""")
      appendLine("""      <c r="I$rIdx" s="6"><f>SUM(I$rawStart:I$rawEnd)</f><v>${rawRows.sumOf { it.closingStock }.toLong()}</v></c>""")
      appendLine("""      <c r="J$rIdx" s="6"><f>SUM(J$rawStart:J$rawEnd)</f><v>${rawRows.sumOf { it.physicalStock }.toLong()}</v></c>""")
      appendLine("""      <c r="K$rIdx" s="6"/>""")
      appendLine("""    </row>""")
      rIdx += 2

      // Plant efficiency block
      appendLine("""    <row r="$rIdx">""")
      appendLine("""      <c r="B$rIdx" s="3" t="inlineStr"><is><t>PLANT EFFICIENCY &amp; LOADS</t></is></c>""")
      appendLine("""      <c r="D$rIdx" s="0" t="inlineStr"><is><t>CFU: ${String.format(Locale.US, "%.1f%%", result.plantEfficiency["CFU"] ?: 0.0)}</t></is></c>""")
      appendLine("""      <c r="F$rIdx" s="0" t="inlineStr"><is><t>KNHT: ${String.format(Locale.US, "%.1f%%", result.plantEfficiency["KNHT"] ?: 0.0)}</t></is></c>""")
      appendLine("""      <c r="H$rIdx" s="0" t="inlineStr"><is><t>CRU: ${String.format(Locale.US, "%.1f%%", result.plantEfficiency["CRU"] ?: 0.0)}</t></is></c>""")
      appendLine("""    </row>""")
      rIdx += 2

      // Fuel Gas and Loss Breakdown (2 columns: Option A and Option B)
      appendLine("""    <row r="$rIdx">""")
      appendLine("""      <c r="B$rIdx" s="3" t="inlineStr"><is><t>FUEL GAS &amp; LOSS BREAKDOWN</t></is></c>""")
      appendLine("""      <c r="D$rIdx" s="3" t="inlineStr"><is><t>Option A (Total Product)</t></is></c>""")
      appendLine("""      <c r="F$rIdx" s="3" t="inlineStr"><is><t>Option B (All-Tank TOV)</t></is></c>""")
      appendLine("""    </row>""")
      rIdx++
      appendLine("""    <row r="$rIdx">""")
      appendLine("""      <c r="B$rIdx" s="0" t="inlineStr"><is><t>Total Gas Production (Loss %)</t></is></c>""")
      appendLine("""      <c r="D$rIdx" s="5"><v>${result.lossPercentOptionA / 100.0}</v></c>""")
      appendLine("""      <c r="F$rIdx" s="5"><v>${result.lossPercentOptionB / 100.0}</v></c>""")
      appendLine("""    </row>""")
      rIdx++
      appendLine("""    <row r="$rIdx">""")
      appendLine("""      <c r="B$rIdx" s="0" t="inlineStr"><is><t>Actual Flaring (%)</t></is></c>""")
      appendLine("""      <c r="D$rIdx" s="5"><v>${result.actualFlaringPctOptionA / 100.0}</v></c>""")
      appendLine("""      <c r="F$rIdx" s="5"><v>${result.actualFlaringPctOptionB / 100.0}</v></c>""")
      appendLine("""    </row>""")
      rIdx++
      appendLine("""    <row r="$rIdx">""")
      appendLine("""      <c r="B$rIdx" s="0" t="inlineStr"><is><t>Fuel for Heaters (%)</t></is></c>""")
      appendLine("""      <c r="D$rIdx" s="5"><v>${result.fuelForHeatersPct / 100.0}</v></c>""")
      appendLine("""      <c r="F$rIdx" s="5"><v>${result.fuelForHeatersPct / 100.0}</v></c>""")
      appendLine("""    </row>""")
      rIdx++
      appendLine("""    <row r="$rIdx">""")
      appendLine("""      <c r="B$rIdx" s="0" t="inlineStr"><is><t>H2 Make-up for KNHT (%)</t></is></c>""")
      appendLine("""      <c r="D$rIdx" s="5"><v>${result.h2MakeUpKnhtPct / 100.0}</v></c>""")
      appendLine("""      <c r="F$rIdx" s="5"><v>${result.h2MakeUpKnhtPct / 100.0}</v></c>""")
      appendLine("""    </row>""")

      appendLine("""  </sheetData>""")
      appendLine("""</worksheet>""")
    }
  }
}
