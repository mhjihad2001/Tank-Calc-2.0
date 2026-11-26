package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.engine.ExpressionEvaluator
import kotlin.math.abs

/**
 * Primary String-based NumberInput used across DipCalcScreen, TransferScreen, N2CalcScreen, etc.
 * Enhanced with:
 * - Haptic feedback on focus, key tap, and clear.
 * - BODMAS arithmetic expression evaluation (=, +, -, *, /) with live preview badge and safe fallback.
 * - Optional IndustrialKeypadDock bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    suffix: String? = null,
    isDecimal: Boolean = true,
    allowNegative: Boolean = false,
    enabled: Boolean = true,
    errorText: String? = null,
    helperText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val haptics = com.example.engine.rememberHapticController()
    val inAppKbController = LocalInAppKeyboardController.current

    var lastValidText by remember {
        val initEval = ExpressionEvaluator.evaluate(value)
        mutableStateOf(if (value.isBlank() || initEval.isValid) value else "")
    }
    var isFocused by remember { mutableStateOf(false) }
    var showKeypadModal by remember { mutableStateOf(false) }

    val evalResult = remember(value) {
        ExpressionEvaluator.evaluate(value)
    }

    LaunchedEffect(value, evalResult) {
        if (!evalResult.isExpression && (value.isBlank() || evalResult.isValid)) {
            lastValidText = value
        }
    }

    // Keep global in-app keyboard field metadata synced without clobbering user's tapped cursor
    LaunchedEffect(label, suffix, isFocused) {
        if (isFocused) {
            inAppKbController?.activeFieldLabel = label
            inAppKbController?.activeFieldUnit = suffix
        }
    }

    fun commitFormulaIfNeeded() {
        if (value.isBlank()) return
        val res = ExpressionEvaluator.evaluate(value)
        if (res.isExpression) {
            if (res.value != null) {
                val formatted = ExpressionEvaluator.formatResult(res.value, if (isDecimal) 3 else 0)
                lastValidText = formatted
                onValueChange(formatted)
            } else {
                onValueChange(lastValidText)
            }
        } else if (res.error != null) {
            onValueChange(lastValidText)
        }
    }

    val formulaPreview = if (evalResult.isExpression && evalResult.value != null) {
        "= ${ExpressionEvaluator.formatPreview(evalResult.value, if (isDecimal) 3 else 0)}"
    } else null

    val formulaWarning = if (value.isNotBlank() && evalResult.error != null) {
        evalResult.error
    } else null

    val effectiveError = errorText ?: formulaWarning

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { rawInput ->
                val normalized = rawInput.replace(',', '.')
                onValueChange(normalized)
            },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            enabled = enabled,
            suffix = if (suffix != null || formulaPreview != null) {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (formulaPreview != null) {
                            Surface(
                                color = Color(0xFF0D9488).copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, Color(0xFF0D9488)),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = formulaPreview,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F766E),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (suffix != null) {
                            Text(
                                text = suffix,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else null,
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (value.isNotEmpty() && enabled) {
                        IconButton(
                            onClick = {
                                haptics.trigger(com.example.engine.HapticEvent.KEY_DELETE)
                                lastValidText = ""
                                onValueChange("")
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear input",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (enabled) {
                        IconButton(
                            onClick = {
                                haptics.trigger(com.example.engine.HapticEvent.CELL_SELECT)
                                showKeypadModal = true
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dialpad,
                                contentDescription = "Open Industrial Keypad",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            },
            isError = effectiveError != null,
            supportingText = {
                if (effectiveError != null) {
                    Text(effectiveError, color = MaterialTheme.colorScheme.error)
                } else if (helperText != null) {
                    Text(helperText)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    haptics.trigger(com.example.engine.HapticEvent.NAV_STEP)
                    commitFormulaIfNeeded()
                    if (onImeAction != null) {
                        onImeAction()
                    } else {
                        focusManager.moveFocus(FocusDirection.Down)
                    }
                },
                onDone = {
                    haptics.trigger(com.example.engine.HapticEvent.NAV_STEP)
                    commitFormulaIfNeeded()
                    if (onImeAction != null) {
                        onImeAction()
                    } else {
                        focusManager.clearFocus()
                    }
                }
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (state.isFocused && !isFocused) {
                        haptics.trigger(com.example.engine.HapticEvent.CELL_SELECT)
                        inAppKbController?.activeFieldLabel = label
                        inAppKbController?.activeFieldUnit = suffix
                    } else if (!state.isFocused && isFocused) {
                        commitFormulaIfNeeded()
                    }
                    isFocused = state.isFocused
                }
        )
    }

    if (showKeypadModal) {
        ModalBottomSheet(
            onDismissRequest = {
                commitFormulaIfNeeded()
                showKeypadModal = false
            },
            dragHandle = null,
            containerColor = Color.Transparent
        ) {
            IndustrialKeypadDock(
                cellCoordinateLabel = label.take(14),
                columnTitle = label,
                unitBadge = suffix,
                valueText = value,
                isTextMode = false,
                allowNegative = allowNegative,
                maxDecimals = if (isDecimal) 3 else 0,
                validationWarning = errorText,
                livePreviewText = formulaPreview,
                onValueChange = onValueChange,
                onEnterMoveDown = {
                    commitFormulaIfNeeded()
                    showKeypadModal = false
                    if (onImeAction != null) onImeAction()
                    else focusManager.moveFocus(FocusDirection.Down)
                },
                onTabMoveRight = {
                    commitFormulaIfNeeded()
                    showKeypadModal = false
                    focusManager.moveFocus(FocusDirection.Next)
                },
                onClose = {
                    commitFormulaIfNeeded()
                    showKeypadModal = false
                }
            )
        }
    }
}

/**
 * Nullable Double overload of NumberInput with BODMAS expression evaluation and IndustrialKeypadDock.
 */
@Composable
fun NumberInput(
    value: Double?,
    onValueChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    suffix: String? = null,
    error: String? = null,
    step: Double? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null
) {
    var textValue by remember {
        mutableStateOf(
            value?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else ExpressionEvaluator.formatResult(it, 4)
            } ?: ""
        )
    }

    LaunchedEffect(value) {
        val currentEval = ExpressionEvaluator.evaluateOrNull(textValue)
        if (value == null) {
            if (currentEval != null && !ExpressionEvaluator.isFormulaOrExpression(textValue)) {
                textValue = ""
            }
        } else if (currentEval == null || abs(currentEval - value) > 0.0001) {
            textValue = if (value % 1.0 == 0.0) value.toLong().toString() else ExpressionEvaluator.formatResult(value, 4)
        }
    }

    NumberInput(
        value = textValue,
        onValueChange = { newStr ->
            textValue = newStr
            if (newStr.isBlank()) {
                onValueChange(null)
            } else {
                val evaluated = ExpressionEvaluator.evaluateOrNull(newStr)
                if (evaluated != null) {
                    onValueChange(evaluated)
                }
            }
        },
        label = label ?: "Value",
        modifier = modifier,
        placeholder = placeholder,
        suffix = suffix,
        errorText = error,
        imeAction = imeAction,
        onImeAction = onImeAction
    )
}
