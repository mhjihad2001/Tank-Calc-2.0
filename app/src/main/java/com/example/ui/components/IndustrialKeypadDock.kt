package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ExpressionEvaluator
import com.example.engine.HapticEvent
import com.example.engine.rememberHapticController

/**
 * Shared Industrial Bottom-Docked Dual-Mode (123 Numeric + ABC Text/Symbols) Keypad & Formula Bar.
 * Supports instant shifting between Number (123) and Text (ABC / #+=) modes across the entire app.
 */
@Composable
fun IndustrialKeypadDock(
    cellCoordinateLabel: String,
    columnTitle: String,
    unitBadge: String? = null,
    valueText: String,
    isTextMode: Boolean = false,
    allowNegative: Boolean = false,
    maxDecimals: Int = 3,
    validationWarning: String? = null,
    outlierWarning: String? = null,
    livePreviewText: String? = null,
    cursorSelection: TextRange? = null,
    onValueChange: (String) -> Unit,
    onValueAndCursorChange: ((String, Int) -> Unit)? = null,
    onInsertText: ((String) -> Unit)? = null,
    onBackspace: (() -> Boolean)? = null,
    onMoveCursor: ((Int) -> Boolean)? = null,
    onSelectAll: (() -> Unit)? = null,
    onEnterMoveDown: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onTabMoveRight: () -> Unit,
    onShiftTabMoveLeft: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticController()

    // User can shift between Numeric (false) and Text/QWERTY (true) at any time
    var activeTextLayout by remember(cellCoordinateLabel, columnTitle, isTextMode) {
        mutableStateOf(isTextMode)
    }
    var isUpperCase by remember { mutableStateOf(true) }
    var isSymbolLayer by remember { mutableStateOf(false) }

    // Track the initial valid value when switching into a cell so invalid expressions can fall back safely
    var lastValidCommittedText by remember(cellCoordinateLabel, columnTitle) {
        val initEval = ExpressionEvaluator.evaluate(valueText)
        mutableStateOf(
            if (valueText.isBlank() || initEval.isValid) valueText else ""
        )
    }

    // Trigger subtle haptic on cell selection change
    LaunchedEffect(cellCoordinateLabel, columnTitle) {
        haptics.trigger(HapticEvent.CELL_SELECT)
    }

    // Trigger distinct alert haptic only when a new validation or outlier warning transitions from null to non-null
    var previousWarningMessage by remember(cellCoordinateLabel, columnTitle) {
        mutableStateOf<String?>(validationWarning ?: outlierWarning)
    }
    LaunchedEffect(validationWarning, outlierWarning) {
        val currentMsg = validationWarning ?: outlierWarning
        if (currentMsg != null && currentMsg != previousWarningMessage) {
            haptics.trigger(HapticEvent.WARNING_ALERT)
        }
        previousWarningMessage = currentMsg
    }

    val evalResult = remember(valueText, isTextMode, activeTextLayout) {
        if (isTextMode || activeTextLayout) {
            ExpressionEvaluator.EvalResult(value = null, isExpression = false, error = null)
        } else {
            ExpressionEvaluator.evaluate(valueText)
        }
    }

    // Update lastValidCommittedText whenever a plain valid number is present
    LaunchedEffect(valueText, evalResult, isTextMode) {
        if (!isTextMode && !evalResult.isExpression && (valueText.isEmpty() || evalResult.isValid)) {
            lastValidCommittedText = valueText
        }
    }

    // Fallback cursor position for standalone spreadsheet/modal callers without platform TextField selection
    var localCursorPos by remember(cellCoordinateLabel, columnTitle) {
        mutableIntStateOf(valueText.length)
    }
    LaunchedEffect(valueText) {
        if (localCursorPos > valueText.length) {
            localCursorPos = valueText.length
        }
    }

    val effectiveSelection = remember(cursorSelection, localCursorPos, valueText) {
        if (cursorSelection != null) {
            TextRange(
                cursorSelection.min.coerceIn(0, valueText.length),
                cursorSelection.max.coerceIn(0, valueText.length)
            )
        } else {
            val safe = localCursorPos.coerceIn(0, valueText.length)
            TextRange(safe, safe)
        }
    }

    fun updateText(newText: String, newCursor: Int = newText.length) {
        val safeCursor = newCursor.coerceIn(0, newText.length)
        localCursorPos = safeCursor
        if (onValueAndCursorChange != null) {
            onValueAndCursorChange(newText, safeCursor)
        } else {
            onValueChange(newText)
        }
    }

    fun insertAtCursor(insertStr: String) {
        if (onInsertText != null) {
            onInsertText(insertStr)
        } else if (onValueAndCursorChange != null) {
            val start = effectiveSelection.min.coerceIn(0, valueText.length)
            val end = effectiveSelection.max.coerceIn(0, valueText.length)
            val updated = valueText.substring(0, start) + insertStr + valueText.substring(end)
            val nextCursor = start + insertStr.length
            localCursorPos = nextCursor
            onValueAndCursorChange(updated, nextCursor)
        } else {
            val pos = localCursorPos.coerceIn(0, valueText.length)
            val updated = valueText.substring(0, pos) + insertStr + valueText.substring(pos)
            localCursorPos = pos + insertStr.length
            onValueChange(updated)
        }
    }

    fun deleteAtCursor(): Boolean {
        if (valueText.isEmpty()) return false
        if (onBackspace != null) {
            return onBackspace()
        }
        val start = effectiveSelection.min.coerceIn(0, valueText.length)
        val end = effectiveSelection.max.coerceIn(0, valueText.length)
        if (start != end) {
            val updated = valueText.substring(0, start) + valueText.substring(end)
            updateText(updated, start)
            return true
        } else if (start > 0) {
            val updated = valueText.substring(0, start - 1) + valueText.substring(start)
            val cleaned = if (!isTextMode && !activeTextLayout && updated == "-") "" else updated
            updateText(cleaned, (start - 1).coerceAtMost(cleaned.length))
            return true
        }
        return false
    }

    fun nudgeCursor(delta: Int) {
        if (onMoveCursor != null) {
            val moved = onMoveCursor(delta)
            haptics.trigger(if (moved) HapticEvent.KEY_TAP else HapticEvent.BOUNDARY_LIMIT)
        } else {
            val next = (localCursorPos + delta).coerceIn(0, valueText.length)
            if (next != localCursorPos) {
                localCursorPos = next
                haptics.trigger(HapticEvent.KEY_TAP)
            } else {
                haptics.trigger(HapticEvent.BOUNDARY_LIMIT)
            }
        }
    }

    // Commits the expression to its formatted numeric value before leaving the cell
    fun commitFormulaIfNeeded() {
        if (isTextMode || activeTextLayout || valueText.isBlank()) return
        val check = ExpressionEvaluator.evaluate(valueText)
        if (check.isExpression) {
            if (check.value != null) {
                val formatted = ExpressionEvaluator.formatResult(check.value, maxDecimals)
                lastValidCommittedText = formatted
                updateText(formatted)
            } else {
                updateText(lastValidCommittedText)
            }
        } else if (check.error != null) {
            updateText(lastValidCommittedText)
        }
    }

    val formulaPreviewBadge = remember(evalResult, isTextMode, activeTextLayout) {
        if (!isTextMode && !activeTextLayout && evalResult.isExpression && evalResult.value != null) {
            "= ${ExpressionEvaluator.formatPreview(evalResult.value, maxDecimals)}"
        } else null
    }

    val formulaErrorWarning = remember(evalResult, valueText, isTextMode, activeTextLayout) {
        if (!isTextMode && !activeTextLayout && valueText.isNotBlank() && evalResult.error != null) {
            "Formula: ${evalResult.error} (retaining ${lastValidCommittedText.ifBlank { "0" }})"
        } else null
    }

    val effectiveWarning = validationWarning ?: formulaErrorWarning

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("industrial_keypad_dock"),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 12.dp,
        shadowElevation = 18.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Validation or Outlier Warning Banner
            AnimatedVisibility(
                visible = effectiveWarning != null || outlierWarning != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val isError = validationWarning != null
                val bannerBg = if (isError) Color(0xFFFFEBEE) else Color(0xFFFEF3C7)
                val bannerFg = if (isError) Color(0xFFB71C1C) else Color(0xFF92400E)
                val bannerBorder = if (isError) Color(0xFFEF5350) else Color(0xFFF59E0B)
                val message = effectiveWarning ?: outlierWarning ?: ""

                Surface(
                    color = bannerBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, bannerBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = bannerFg,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = bannerFg,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 2. Top Formula / Cell Context Bar + 123 <-> ABC Shift Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Active Cell Coordinate Pill
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = cellCoordinateLabel.ifBlank { "INPUT" }.take(14),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }

                // Column / Field Title
                Text(
                    text = columnTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Live Formula Evaluation Badge (e.g. "= 12,950")
                if (formulaPreviewBadge != null) {
                    Surface(
                        color = Color(0xFF0D9488).copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color(0xFF0D9488)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = formulaPreviewBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F766E),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                // Live Calculation Preview Chip
                if (!livePreviewText.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = livePreviewText,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                // 123 <-> ABC Shift Toggle Pill
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            haptics.trigger(HapticEvent.MODE_SWITCH)
                            activeTextLayout = !activeTextLayout
                            isSymbolLayer = false
                        }
                        .testTag("keypad_mode_shift_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            color = if (!activeTextLayout) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "123",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (!activeTextLayout) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Surface(
                            color = if (activeTextLayout) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "ABC",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (activeTextLayout) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // Close / Dismiss Keypad Button
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            haptics.trigger(HapticEvent.NAV_STEP)
                            commitFormulaIfNeeded()
                            onClose()
                        }
                        .testTag("keypad_close_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close keypad",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Value / Formula Display Box (with live caret & selection) + Cursor & Row Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val selectionBg = MaterialTheme.colorScheme.primaryContainer
                val selectionFg = MaterialTheme.colorScheme.onPrimaryContainer

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.5.dp,
                            when {
                                effectiveWarning != null -> MaterialTheme.colorScheme.error
                                formulaPreviewBadge != null -> Color(0xFF0D9488)
                                else -> primaryColor
                            },
                            RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (valueText.isNotEmpty()) {
                                haptics.trigger(HapticEvent.CELL_SELECT)
                                if (onSelectAll != null) {
                                    onSelectAll()
                                } else {
                                    localCursorPos = valueText.length
                                }
                            }
                        }
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            activeTextLayout -> "Aa"
                            evalResult.isExpression -> "fx"
                            else -> "="
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (evalResult.isExpression) Color(0xFF0D9488) else primaryColor,
                        modifier = Modifier.padding(end = 6.dp)
                    )

                    val placeholderText = if (activeTextLayout) "Type text..." else "0"
                    if (valueText.isEmpty()) {
                        Text(
                            text = placeholderText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = if (activeTextLayout) FontFamily.Default else FontFamily.Monospace,
                                fontWeight = if (activeTextLayout) FontWeight.SemiBold else FontWeight.ExtraBold,
                                fontSize = if (activeTextLayout) 15.sp else 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        val annotatedDisplay = remember(valueText, effectiveSelection, primaryColor, selectionBg, selectionFg) {
                            val selMin = effectiveSelection.min.coerceIn(0, valueText.length)
                            val selMax = effectiveSelection.max.coerceIn(0, valueText.length)
                            buildAnnotatedString {
                                append(valueText.substring(0, selMin))
                                if (selMin == selMax) {
                                    withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Black)) {
                                        append("|")
                                    }
                                } else {
                                    withStyle(SpanStyle(background = selectionBg, color = selectionFg)) {
                                        append(valueText.substring(selMin, selMax))
                                    }
                                }
                                append(valueText.substring(selMax))
                            }
                        }
                        Text(
                            text = annotatedDisplay,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = if (activeTextLayout) FontFamily.Default else FontFamily.Monospace,
                                fontWeight = if (activeTextLayout) FontWeight.SemiBold else FontWeight.ExtraBold,
                                fontSize = if (activeTextLayout) 15.sp else 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!unitBadge.isNullOrBlank()) {
                        Text(
                            text = unitBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                // Cursor Left (◀)
                KeypadIconButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Move cursor left",
                    modifier = Modifier.testTag("keypad_cursor_left"),
                    onClick = { nudgeCursor(-1) }
                )

                // Cursor Right (▶)
                KeypadIconButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Move cursor right",
                    modifier = Modifier.testTag("keypad_cursor_right"),
                    onClick = { nudgeCursor(+1) }
                )

                // Up Row
                if (onMoveUp != null) {
                    KeypadIconButton(
                        icon = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous row",
                        onClick = {
                            haptics.trigger(HapticEvent.NAV_STEP)
                            commitFormulaIfNeeded()
                            onMoveUp()
                        }
                    )
                }

                // Down Row
                KeypadIconButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Next row",
                    onClick = {
                        haptics.trigger(HapticEvent.NAV_STEP)
                        commitFormulaIfNeeded()
                        onEnterMoveDown()
                    }
                )
            }

            // 4A. NUMERIC (123) MODE
            if (!activeTextLayout) {
                val opDelimiters = charArrayOf('+', '-', '*', '/', '×', '÷', '(', ')', '=')

                val appendNumericChar: (String) -> Unit = { ch ->
                    val selMin = effectiveSelection.min.coerceIn(0, valueText.length)
                    val selMax = effectiveSelection.max.coerceIn(0, valueText.length)
                    when (ch) {
                        "." -> {
                            // Inspect only the numeric token surrounding the cursor (excluding selected text being replaced)
                            val beforeCursor = valueText.substring(0, selMin)
                            val afterCursor = valueText.substring(selMax)
                            val leftToken = beforeCursor.substringAfterLastAny(opDelimiters)
                            val rightToken = afterCursor.substringBeforeFirstAny(opDelimiters)
                            val activeToken = leftToken + rightToken
                            if (!activeToken.contains(".")) {
                                haptics.trigger(HapticEvent.KEY_OPERATOR)
                                val ins = if (leftToken.isEmpty()) "0." else "."
                                insertAtCursor(ins)
                            } else {
                                haptics.trigger(HapticEvent.BOUNDARY_LIMIT)
                            }
                        }
                        "+", "-", "*", "/" -> {
                            if (valueText.isEmpty()) {
                                if (ch == "-" && (allowNegative || isTextMode)) {
                                    haptics.trigger(HapticEvent.KEY_OPERATOR)
                                    insertAtCursor("-")
                                } else {
                                    haptics.trigger(HapticEvent.BOUNDARY_LIMIT)
                                }
                            } else {
                                haptics.trigger(HapticEvent.KEY_OPERATOR)
                                val charBefore = if (selMin > 0 && selMin == selMax) valueText[selMin - 1] else null
                                if (charBefore != null && (charBefore == '+' || charBefore == '-' || charBefore == '*' || charBefore == '/' || charBefore == '×' || charBefore == '÷')) {
                                    // Replace consecutive operator immediately before cursor
                                    if (deleteAtCursor()) {
                                        insertAtCursor(ch)
                                    } else {
                                        insertAtCursor(ch)
                                    }
                                } else {
                                    insertAtCursor(ch)
                                }
                            }
                        }
                        "=" -> {
                            if (valueText.isEmpty()) {
                                haptics.trigger(HapticEvent.KEY_OPERATOR)
                                insertAtCursor("=")
                            } else {
                                val check = ExpressionEvaluator.evaluate(valueText)
                                if (check.value != null) {
                                    haptics.trigger(HapticEvent.SUCCESS)
                                    val formatted = ExpressionEvaluator.formatResult(check.value, maxDecimals)
                                    lastValidCommittedText = formatted
                                    updateText(formatted, formatted.length)
                                } else {
                                    haptics.trigger(HapticEvent.WARNING_ALERT)
                                }
                            }
                        }
                        "±" -> {
                            if ((allowNegative || isTextMode) && !ExpressionEvaluator.isFormulaOrExpression(valueText)) {
                                haptics.trigger(HapticEvent.KEY_OPERATOR)
                                val next = if (valueText.startsWith("-")) {
                                    valueText.removePrefix("-")
                                } else {
                                    "-$valueText"
                                }
                                updateText(next, next.length)
                            } else {
                                haptics.trigger(HapticEvent.BOUNDARY_LIMIT)
                            }
                        }
                        else -> {
                            haptics.trigger(HapticEvent.KEY_TAP)
                            if (valueText == "0" && !isTextMode && selMin == 1 && selMax == 1) {
                                updateText(ch, ch.length)
                            } else {
                                insertAtCursor(ch)
                            }
                        }
                    }
                }

                val handleBackspace: () -> Unit = {
                    val deleted = deleteAtCursor()
                    haptics.trigger(if (deleted) HapticEvent.KEY_DELETE else HapticEvent.BOUNDARY_LIMIT)
                }

                val handleClear: () -> Unit = {
                    if (valueText.isEmpty()) {
                        haptics.trigger(HapticEvent.BOUNDARY_LIMIT)
                    } else {
                        haptics.trigger(HapticEvent.KEY_DELETE)
                        lastValidCommittedText = ""
                        updateText("")
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Quick Formula Operator Strip (+, -, ×, ÷, =, ABC)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        OperatorKeyButton(label = "+", tag = "key_add", modifier = Modifier.weight(1f)) { appendNumericChar("+") }
                        OperatorKeyButton(label = "−", tag = "key_sub", modifier = Modifier.weight(1f)) { appendNumericChar("-") }
                        OperatorKeyButton(label = "×", tag = "key_mul", modifier = Modifier.weight(1f)) { appendNumericChar("*") }
                        OperatorKeyButton(label = "÷", tag = "key_div", modifier = Modifier.weight(1f)) { appendNumericChar("/") }
                        OperatorKeyButton(
                            label = "=",
                            tag = "key_eval",
                            isHighlight = evalResult.isExpression && evalResult.isValid,
                            modifier = Modifier.weight(1.1f)
                        ) { appendNumericChar("=") }
                        OperatorKeyButton(
                            label = "ABC",
                            tag = "key_shift_abc",
                            isHighlight = false,
                            modifier = Modifier.weight(1.1f)
                        ) {
                            haptics.trigger(HapticEvent.MODE_SWITCH)
                            activeTextLayout = true
                        }
                    }

                    // Row 1: 7 | 8 | 9 | BKSP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DigitKeyButton("7", Modifier.weight(1f)) { appendNumericChar("7") }
                        DigitKeyButton("8", Modifier.weight(1f)) { appendNumericChar("8") }
                        DigitKeyButton("9", Modifier.weight(1f)) { appendNumericChar("9") }
                        ActionKeyButton(
                            label = "⌫",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1.15f),
                            onClick = handleBackspace
                        )
                    }

                    // Row 2: 4 | 5 | 6 | CLR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DigitKeyButton("4", Modifier.weight(1f)) { appendNumericChar("4") }
                        DigitKeyButton("5", Modifier.weight(1f)) { appendNumericChar("5") }
                        DigitKeyButton("6", Modifier.weight(1f)) { appendNumericChar("6") }
                        ActionKeyButton(
                            label = "CLR",
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1.15f),
                            onClick = handleClear
                        )
                    }

                    // Row 3: 1 | 2 | 3 | TAB ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DigitKeyButton("1", Modifier.weight(1f)) { appendNumericChar("1") }
                        DigitKeyButton("2", Modifier.weight(1f)) { appendNumericChar("2") }
                        DigitKeyButton("3", Modifier.weight(1f)) { appendNumericChar("3") }
                        ActionKeyButton(
                            label = "TAB →",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1.15f),
                            onClick = {
                                haptics.trigger(HapticEvent.NAV_STEP)
                                commitFormulaIfNeeded()
                                onTabMoveRight()
                            }
                        )
                    }

                    // Row 4: 0 / ± | . | ENTER / NEXT ↓
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (allowNegative) {
                            DigitKeyButton("±", Modifier.weight(0.6f)) { appendNumericChar("±") }
                            DigitKeyButton("0", Modifier.weight(1.4f)) { appendNumericChar("0") }
                        } else {
                            DigitKeyButton("0", Modifier.weight(1f)) { appendNumericChar("0") }
                            DigitKeyButton("00", Modifier.weight(1f)) {
                                haptics.trigger(HapticEvent.KEY_TAP)
                                if (valueText.isEmpty() || valueText == "0") {
                                    updateText("0")
                                } else {
                                    insertAtCursor("00")
                                }
                            }
                        }
                        DigitKeyButton(".", Modifier.weight(1f)) { appendNumericChar(".") }
                        EnterNextButton(
                            modifier = Modifier.weight(1.15f),
                            onClick = {
                                haptics.trigger(HapticEvent.NAV_STEP)
                                commitFormulaIfNeeded()
                                onEnterMoveDown()
                            }
                        )
                    }
                }
            } else {
                // 4B. ALPHANUMERIC / QWERTY (ABC & SYMBOLS) MODE
                val numberRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
                val row1 = if (isSymbolLayer) {
                    listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/")
                } else {
                    listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
                }
                val row2 = if (isSymbolLayer) {
                    listOf("*", "\"", "'", ":", ";", "!", "?", "_", "=", ".")
                } else {
                    listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
                }
                val row3 = if (isSymbolLayer) {
                    listOf(",", "/", "-", ":", "(", ")", "#")
                } else {
                    listOf("Z", "X", "C", "V", "B", "N", "M")
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Quick Top Number Strip (1..0) so tank tags like T-101 or 512A don't even require mode switching
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        numberRow.forEach { num ->
                            QwertyKeyButton(
                                label = num,
                                isCompact = true,
                                modifier = Modifier.weight(1f)
                            ) {
                                haptics.trigger(HapticEvent.KEY_TAP)
                                insertAtCursor(num)
                            }
                        }
                    }

                    // QWERTY Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row1.forEach { rawKey ->
                            val k = if (!isSymbolLayer && !isUpperCase) rawKey.lowercase() else rawKey
                            QwertyKeyButton(
                                label = k,
                                modifier = Modifier.weight(1f)
                            ) {
                                haptics.trigger(HapticEvent.KEY_TAP)
                                insertAtCursor(k)
                            }
                        }
                    }

                    // QWERTY Row 2
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isSymbolLayer) 0.dp else 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row2.forEach { rawKey ->
                            val k = if (!isSymbolLayer && !isUpperCase) rawKey.lowercase() else rawKey
                            QwertyKeyButton(
                                label = k,
                                modifier = Modifier.weight(1f)
                            ) {
                                haptics.trigger(HapticEvent.KEY_TAP)
                                insertAtCursor(k)
                            }
                        }
                    }

                    // QWERTY Row 3: Shift / #+= | Z..M | Backspace
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ActionKeyButton(
                            label = when {
                                isSymbolLayer -> "ABC"
                                isUpperCase -> "⇧"
                                else -> "↑"
                            },
                            containerColor = if (isUpperCase && !isSymbolLayer) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            contentColor = if (isUpperCase && !isSymbolLayer) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            heightDp = 38,
                            modifier = Modifier.weight(1.35f),
                            onClick = {
                                haptics.trigger(HapticEvent.MODE_SWITCH)
                                if (isSymbolLayer) {
                                    isSymbolLayer = false
                                } else {
                                    isUpperCase = !isUpperCase
                                }
                            }
                        )

                        row3.forEach { rawKey ->
                            val k = if (!isSymbolLayer && !isUpperCase) rawKey.lowercase() else rawKey
                            QwertyKeyButton(
                                label = k,
                                modifier = Modifier.weight(1f)
                            ) {
                                haptics.trigger(HapticEvent.KEY_TAP)
                                insertAtCursor(k)
                            }
                        }

                        ActionKeyButton(
                            label = "⌫",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            heightDp = 38,
                            modifier = Modifier.weight(1.35f),
                            onClick = {
                                haptics.trigger(HapticEvent.KEY_DELETE)
                                deleteAtCursor()
                            }
                        )
                    }

                    // QWERTY Row 4: 123 | #+= | - | SPACE | . | CLR | NEXT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ActionKeyButton(
                            label = "123",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            heightDp = 40,
                            modifier = Modifier.weight(1.1f),
                            onClick = {
                                haptics.trigger(HapticEvent.MODE_SWITCH)
                                activeTextLayout = false
                            }
                        )
                        ActionKeyButton(
                            label = if (isSymbolLayer) "ABC" else "#+=",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            heightDp = 40,
                            modifier = Modifier.weight(1.0f),
                            onClick = {
                                haptics.trigger(HapticEvent.MODE_SWITCH)
                                isSymbolLayer = !isSymbolLayer
                            }
                        )
                        QwertyKeyButton(
                            label = "-",
                            heightDp = 40,
                            modifier = Modifier.weight(0.75f)
                        ) {
                            haptics.trigger(HapticEvent.KEY_TAP)
                            insertAtCursor("-")
                        }
                        QwertyKeyButton(
                            label = "SPACE",
                            heightDp = 40,
                            modifier = Modifier.weight(2.3f)
                        ) {
                            haptics.trigger(HapticEvent.KEY_TAP)
                            insertAtCursor(" ")
                        }
                        QwertyKeyButton(
                            label = ".",
                            heightDp = 40,
                            modifier = Modifier.weight(0.75f)
                        ) {
                            haptics.trigger(HapticEvent.KEY_TAP)
                            insertAtCursor(".")
                        }
                        ActionKeyButton(
                            label = "CLR",
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            heightDp = 40,
                            modifier = Modifier.weight(0.95f),
                            onClick = {
                                haptics.trigger(HapticEvent.KEY_DELETE)
                                updateText("")
                            }
                        )
                        EnterNextButton(
                            heightDp = 40,
                            modifier = Modifier.weight(1.3f),
                            onClick = {
                                haptics.trigger(HapticEvent.NAV_STEP)
                                onEnterMoveDown()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnterNextButton(
    heightDp: Int = 42,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .height(heightDp.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("keypad_enter_next_button")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NEXT",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                contentDescription = "Enter Next",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun QwertyKeyButton(
    label: String,
    isCompact: Boolean = false,
    heightDp: Int = if (isCompact) 31 else 38,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (isCompact) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = if (isCompact) 1.dp else 2.dp,
        modifier = modifier
            .height(heightDp.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .testTag("keypad_qwerty_$label")
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (isCompact) FontFamily.Monospace else FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (label == "SPACE") 12.sp else if (isCompact) 13.sp else 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OperatorKeyButton(
    label: String,
    tag: String,
    isHighlight: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = if (isHighlight) Color(0xFF0D9488) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
    val fgColor = if (isHighlight) Color.White else MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isHighlight) Color(0xFF0F766E) else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (label == "ABC") 12.sp else 14.sp
                ),
                color = fgColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DigitKeyButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp,
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("keypad_digit_$label")
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ActionKeyButton(
    label: String,
    containerColor: Color,
    contentColor: Color,
    heightDp: Int = 42,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .height(heightDp.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("keypad_action_$label")
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun KeypadIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

private fun String.substringAfterLastAny(delimiters: CharArray): String {
    val idx = lastIndexOfAny(delimiters)
    return if (idx < 0) this else substring(idx + 1)
}

private fun String.substringBeforeFirstAny(delimiters: CharArray): String {
    val idx = indexOfAny(delimiters)
    return if (idx < 0) this else substring(0, idx)
}
