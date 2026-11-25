package com.example.engine

import java.util.Locale
import kotlin.math.abs

/**
 * Deterministic BODMAS arithmetic expression evaluator for spreadsheet cells and numeric inputs.
 * Supports leading '=', '+', '-', '*', '/' (as well as '×', '÷'), parentheses '(', ')',
 * unary +/-, and comma/decimal number formatting.
 */
object ExpressionEvaluator {

    data class EvalResult(
        val value: Double?,
        val isExpression: Boolean,
        val error: String? = null
    ) {
        val isValid: Boolean get() = value != null && error == null
    }

    /**
     * Returns true if the input string starts with '=' or contains binary arithmetic operators
     * (+, -, *, /, ×, ÷) beyond a leading sign.
     */
    fun isFormulaOrExpression(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("=")) return true
        // Strip leading sign to check for binary operators or parentheses
        val body = if (trimmed.startsWith("+") || trimmed.startsWith("-")) {
            trimmed.substring(1)
        } else {
            trimmed
        }
        return body.any { it == '+' || it == '-' || it == '*' || it == '/' || it == '×' || it == '÷' || it == '(' || it == ')' }
    }

    /**
     * Evaluates a numeric string or arithmetic expression following standard BODMAS precedence.
     */
    fun evaluate(raw: String): EvalResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return EvalResult(value = null, isExpression = false, error = null)
        }

        val isExpr = isFormulaOrExpression(trimmed)
        val normalized = normalizeInput(trimmed)
        if (normalized.isEmpty()) {
            return EvalResult(
                value = null,
                isExpression = isExpr,
                error = if (isExpr) "Incomplete expression" else null
            )
        }

        if (!isExpr) {
            val simple = normalized.toDoubleOrNull()
            return if (simple != null && simple.isFinite()) {
                EvalResult(value = simple, isExpression = false, error = null)
            } else {
                EvalResult(value = null, isExpression = false, error = "Invalid number")
            }
        }

        return try {
            val parser = Parser(normalized)
            val result = parser.parseExpression()
            if (parser.pos < normalized.length) {
                EvalResult(value = null, isExpression = true, error = "Unexpected character '${normalized[parser.pos]}'")
            } else if (!result.isFinite()) {
                EvalResult(value = null, isExpression = true, error = "Division by zero or overflow")
            } else {
                EvalResult(value = result, isExpression = true, error = null)
            }
        } catch (e: EvalException) {
            EvalResult(value = null, isExpression = true, error = e.message ?: "Invalid expression")
        } catch (_: Exception) {
            EvalResult(value = null, isExpression = true, error = "Invalid expression")
        }
    }

    /**
     * Convenience helper that returns the evaluated Double or null if invalid.
     */
    fun evaluateOrNull(raw: String): Double? = evaluate(raw).value

    /**
     * Formats an evaluated numeric value to the target maximum decimal places,
     * stripping unnecessary trailing zeroes while preserving clean numeric representation.
     */
    fun formatResult(value: Double, maxDecimals: Int = 2): String {
        if (!value.isFinite()) return ""
        val cleanVal = if (abs(value) < 1e-9) 0.0 else value
        val formatted = String.format(Locale.US, "%.${maxDecimals.coerceIn(0, 6)}f", cleanVal)
        return if (formatted.contains('.')) {
            formatted.trimEnd('0').trimEnd('.')
        } else {
            formatted
        }
    }

    /**
     * Formats a preview string with thousands separators for the keypad live preview badge.
     */
    fun formatPreview(value: Double, maxDecimals: Int = 2): String {
        if (!value.isFinite()) return "ERR"
        val cleanVal = if (abs(value) < 1e-9) 0.0 else value
        val decimals = maxDecimals.coerceIn(0, 4)
        return if (abs(cleanVal - cleanVal.toLong()) < 1e-6) {
            String.format(Locale.US, "%,d", cleanVal.toLong())
        } else {
            String.format(Locale.US, "%,.${decimals}f", cleanVal).trimEnd('0').trimEnd('.')
        }
    }

    private fun normalizeInput(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("=")) {
            s = s.substring(1).trim()
        }
        // Replace visual multiply/divide symbols and remove grouping commas if decimal point is present,
        // or convert comma to decimal dot if used as decimal separator
        s = s.replace('×', '*').replace('÷', '/')
        s = if (s.contains('.')) {
            s.replace(",", "")
        } else {
            // If a comma is followed by != 3 digits or multiple operators exist, treat comma as '.'
            s.replace(',', '.')
        }
        return s.replace(" ", "")
    }

    private class EvalException(message: String) : Exception(message)

    private class Parser(val input: String) {
        var pos: Int = 0

        fun parseExpression(): Double {
            var x = parseTerm()
            while (pos < input.length) {
                when (input[pos]) {
                    '+' -> {
                        pos++
                        x += parseTerm()
                    }
                    '-' -> {
                        pos++
                        x -= parseTerm()
                    }
                    else -> return x
                }
            }
            return x
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (pos < input.length) {
                when (input[pos]) {
                    '*' -> {
                        pos++
                        x *= parseFactor()
                    }
                    '/' -> {
                        pos++
                        val divisor = parseFactor()
                        if (abs(divisor) < 1e-12) {
                            throw EvalException("Division by zero")
                        }
                        x /= divisor
                    }
                    else -> return x
                }
            }
            return x
        }

        private fun parseFactor(): Double {
            if (pos >= input.length) {
                throw EvalException("Incomplete expression")
            }
            if (input[pos] == '+') {
                pos++
                return parseFactor()
            }
            if (input[pos] == '-') {
                pos++
                return -parseFactor()
            }

            val startPos = pos
            val x: Double
            if (input[pos] == '(') {
                pos++
                x = parseExpression()
                if (pos >= input.length || input[pos] != ')') {
                    throw EvalException("Missing closing ')'")
                }
                pos++
            } else if (input[pos].isDigit() || input[pos] == '.') {
                var dotCount = 0
                while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
                    if (input[pos] == '.') dotCount++
                    if (dotCount > 1) throw EvalException("Multiple decimal points")
                    pos++
                }
                val numStr = input.substring(startPos, pos)
                x = numStr.toDoubleOrNull() ?: throw EvalException("Invalid number '$numStr'")
            } else {
                throw EvalException("Unexpected '${input[pos]}'")
            }
            return x
        }
    }
}
