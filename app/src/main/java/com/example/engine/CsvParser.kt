package com.example.engine

import com.example.data.DipRow
import com.example.data.FractionRow
import com.example.data.N2Row

enum class CsvTableType { DIP, FRACTION, N2, UNKNOWN }

data class CsvParseResult(
  val detectedType: CsvTableType,
  val tankNames: List<String>,
  val dipRows: List<DipRow> = emptyList(),
  val fractionRows: List<FractionRow> = emptyList(),
  val n2Rows: List<N2Row> = emptyList(),
  val rowCount: Int = 0,
  val errors: List<String> = emptyList(),
  val warnings: List<String> = emptyList()
)

object CsvParser {

  /**
   * Auto-detect table type from filename or header.
   */
  fun detectTypeFromName(filename: String): CsvTableType {
    val lower = filename.lowercase()
    return when {
      lower.contains("dip") -> CsvTableType.DIP
      lower.contains("fraction") || lower.contains("frac") -> CsvTableType.FRACTION
      lower.contains("n2") || lower.contains("nitrogen") -> CsvTableType.N2
      else -> CsvTableType.UNKNOWN
    }
  }

  /**
   * Auto-detect delimiter: comma, semicolon, tab.
   */
  fun detectDelimiter(firstLine: String): Char {
    val commas = firstLine.count { it == ',' }
    val semicolons = firstLine.count { it == ';' }
    val tabs = firstLine.count { it == '\t' }
    return when {
      semicolons > commas && semicolons > tabs -> ';'
      tabs > commas && tabs > semicolons -> '\t'
      else -> ','
    }
  }

  /**
   * Parse a single line into columns with support for quoted strings and decimal commas.
   */
  fun parseCsvLine(line: String, delimiter: Char): List<String> {
    val tokens = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false

    var i = 0
    while (i < line.length) {
      val c = line[i]
      if (c == '"') {
        if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
          sb.append('"')
          i++
        } else {
          inQuotes = !inQuotes
        }
      } else if (c == delimiter && !inQuotes) {
        tokens.add(sb.toString().trim())
        sb.clear()
      } else {
        sb.append(c)
      }
      i++
    }
    tokens.add(sb.toString().trim())
    return tokens
  }

  /**
   * Parse numeric string safely, supporting European decimal comma ("12,5" -> 12.5)
   * while ignoring thousand separator commas if already in decimal format.
   */
  fun parseNumber(raw: String): Double? {
    var s = raw.trim().replace("\"", "").replace("'", "")
    if (s.isEmpty()) return null
    // If it has both comma and dot, e.g. "1,234.56" -> remove comma
    if (s.contains(",") && s.contains(".")) {
      s = s.replace(",", "")
    } else if (s.contains(",") && !s.contains(".")) {
      // European decimal comma e.g. "123,45"
      s = s.replace(",", ".")
    }
    return s.toDoubleOrNull()
  }

  /**
   * Parse raw CSV content into appropriate rows based on specified or detected type.
   */
  fun parseCsvContent(
    content: String,
    forcedType: CsvTableType = CsvTableType.UNKNOWN,
    filename: String = ""
  ): CsvParseResult {
    // Strip UTF-8 BOM if present
    val clean = if (content.startsWith("\uFEFF")) content.substring(1) else content
    val lines = clean.lines().map { it.trim() }.filter { it.isNotEmpty() }

    if (lines.isEmpty()) {
      return CsvParseResult(
        detectedType = forcedType,
        tankNames = emptyList(),
        errors = listOf("File is empty.")
      )
    }

    val delimiter = detectDelimiter(lines[0])
    val headerCols = parseCsvLine(lines[0], delimiter)
    if (headerCols.size < 2) {
      return CsvParseResult(
        detectedType = forcedType,
        tankNames = emptyList(),
        errors = listOf("Header row must contain at least 2 columns (index column + at least one tank).")
      )
    }

    val firstHeader = headerCols[0].lowercase()
    val tableType = if (forcedType != CsvTableType.UNKNOWN) {
      forcedType
    } else {
      val nameType = detectTypeFromName(filename)
      if (nameType != CsvTableType.UNKNOWN) nameType
      else when {
        firstHeader.contains("dip") -> CsvTableType.DIP
        firstHeader.contains("fraction") || firstHeader.contains("frac") || firstHeader == "mm" -> CsvTableType.FRACTION
        firstHeader.contains("n2") || firstHeader.contains("height") || firstHeader.contains("water") -> CsvTableType.N2
        else -> CsvTableType.UNKNOWN
      }
    }

    val tankNames = headerCols.drop(1).map { it.trim() }.filter { it.isNotEmpty() }
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    when (tableType) {
      CsvTableType.DIP -> {
        val dipRows = mutableListOf<DipRow>()
        var lastDip = -1.0
        for (lineIdx in 1 until lines.size) {
          val cols = parseCsvLine(lines[lineIdx], delimiter)
          if (cols.isEmpty() || cols.all { it.isEmpty() }) continue
          val dipMm = parseNumber(cols[0])
          if (dipMm == null) {
            errors.add("Row ${lineIdx + 1}: Invalid dip value '${cols[0]}'.")
            continue
          }
          if (dipMm <= lastDip) {
            warnings.add("Row ${lineIdx + 1}: Dip ${dipMm}mm is not strictly ascending (previous was ${lastDip}mm).")
          }
          lastDip = dipMm

          val vols = mutableMapOf<String, Double>()
          for (tankIdx in tankNames.indices) {
            val rawVal = cols.getOrNull(tankIdx + 1) ?: ""
            val vol = parseNumber(rawVal) ?: 0.0
            vols[tankNames[tankIdx]] = vol
          }
          dipRows.add(DipRow(dipMm = dipMm, volumes = vols))
        }
        return CsvParseResult(
          detectedType = CsvTableType.DIP,
          tankNames = tankNames,
          dipRows = dipRows,
          rowCount = dipRows.size,
          errors = errors,
          warnings = warnings
        )
      }

      CsvTableType.FRACTION -> {
        val fracRows = mutableListOf<FractionRow>()
        val mmFound = mutableSetOf<Int>()
        for (lineIdx in 1 until lines.size) {
          val cols = parseCsvLine(lines[lineIdx], delimiter)
          if (cols.isEmpty() || cols.all { it.isEmpty() }) continue
          val mm = parseNumber(cols[0])?.toInt()
          if (mm == null || mm < 0 || mm > 9) {
            errors.add("Row ${lineIdx + 1}: Invalid fraction mm value '${cols[0]}' (expected 0..9).")
            continue
          }
          mmFound.add(mm)
          val vols = mutableMapOf<String, Double>()
          for (tankIdx in tankNames.indices) {
            val rawVal = cols.getOrNull(tankIdx + 1) ?: ""
            val vol = parseNumber(rawVal) ?: 0.0
            vols[tankNames[tankIdx]] = vol
          }
          fracRows.add(FractionRow(mm = mm, volumes = vols))
        }

        val missingMm = (0..9).filter { !mmFound.contains(it) }
        if (missingMm.isNotEmpty()) {
          warnings.add("Fraction table is missing rows for mm: ${missingMm.joinToString(", ")}.")
        }

        return CsvParseResult(
          detectedType = CsvTableType.FRACTION,
          tankNames = tankNames,
          fractionRows = fracRows.sortedBy { it.mm },
          rowCount = fracRows.size,
          errors = errors,
          warnings = warnings
        )
      }

      CsvTableType.N2 -> {
        val n2Rows = mutableListOf<N2Row>()
        var lastHeight = -1.0
        for (lineIdx in 1 until lines.size) {
          val cols = parseCsvLine(lines[lineIdx], delimiter)
          if (cols.isEmpty() || cols.all { it.isEmpty() }) continue
          val h = parseNumber(cols[0])
          if (h == null) {
            errors.add("Row ${lineIdx + 1}: Invalid water column height '${cols[0]}'.")
            continue
          }
          if (h <= lastHeight) {
            warnings.add("Row ${lineIdx + 1}: Height ${h}mm is not strictly ascending.")
          }
          lastHeight = h

          val vols = mutableMapOf<String, Double>()
          for (tankIdx in tankNames.indices) {
            val rawVal = cols.getOrNull(tankIdx + 1) ?: ""
            val vol = parseNumber(rawVal) ?: 0.0
            vols[tankNames[tankIdx]] = vol
          }
          n2Rows.add(N2Row(heightMm = h, volumes = vols))
        }
        return CsvParseResult(
          detectedType = CsvTableType.N2,
          tankNames = tankNames,
          n2Rows = n2Rows,
          rowCount = n2Rows.size,
          errors = errors,
          warnings = warnings
        )
      }

      CsvTableType.UNKNOWN -> {
        return CsvParseResult(
          detectedType = CsvTableType.UNKNOWN,
          tankNames = tankNames,
          rowCount = lines.size - 1,
          warnings = listOf("Table type could not be determined automatically. Please select whether this is Dip, Fraction, or N2 table.")
        )
      }
    }
  }
}
