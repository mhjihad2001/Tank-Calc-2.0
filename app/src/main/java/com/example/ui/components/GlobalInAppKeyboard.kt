package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.awaitCancellation

/**
 * Represents an active text input session intercepted from ANY OutlinedTextField / BasicTextField
 * across the entire application (screens, dialogs, bottom sheets).
 */
class InAppImeSession(
  initialValue: TextFieldValue,
  val imeOptions: ImeOptions,
  val onEditCommand: (List<EditCommand>) -> Unit,
  val onImeAction: ((ImeAction) -> Unit)?
) {
  var currentFieldValue by mutableStateOf(initialValue)

  val isInitialTextMode: Boolean = when (imeOptions.keyboardType) {
    KeyboardType.Number,
    KeyboardType.Decimal,
    KeyboardType.Phone,
    KeyboardType.NumberPassword -> false
    else -> true
  }

  fun updateFullText(newText: String, newCursorPos: Int = newText.length) {
    val safeCursor = newCursorPos.coerceIn(0, newText.length)
    currentFieldValue = TextFieldValue(
      text = newText,
      selection = TextRange(safeCursor)
    )
    // Replace entire text buffer atomically via SetComposingTextCommand + FinishComposingTextCommand
    onEditCommand(
      listOf(
        androidx.compose.ui.text.input.DeleteAllCommand(),
        androidx.compose.ui.text.input.CommitTextCommand(newText, 1),
        androidx.compose.ui.text.input.SetSelectionCommand(safeCursor, safeCursor)
      )
    )
  }

  fun performNextOrEnter(focusManager: androidx.compose.ui.focus.FocusManager, onClose: () -> Unit) {
    val action = imeOptions.imeAction
    if (onImeAction != null && action != ImeAction.Default && action != ImeAction.None) {
      onImeAction.invoke(action)
      if (action == ImeAction.Done || action == ImeAction.Go || action == ImeAction.Search || action == ImeAction.Send) {
        focusManager.clearFocus()
        onClose()
      } else if (action == ImeAction.Next) {
        val moved = focusManager.moveFocus(FocusDirection.Down) || focusManager.moveFocus(FocusDirection.Next)
        if (!moved) {
          focusManager.clearFocus()
          onClose()
        }
      }
    } else {
      val moved = focusManager.moveFocus(FocusDirection.Down) || focusManager.moveFocus(FocusDirection.Next)
      if (!moved) {
        focusManager.clearFocus()
        onClose()
      }
    }
  }

  fun performTabNext(focusManager: androidx.compose.ui.focus.FocusManager) {
    val moved = focusManager.moveFocus(FocusDirection.Next) || focusManager.moveFocus(FocusDirection.Down)
    if (!moved) {
      focusManager.clearFocus()
    }
  }

  fun performShiftTabPrevious(focusManager: androidx.compose.ui.focus.FocusManager) {
    focusManager.moveFocus(FocusDirection.Previous)
  }

  fun performMoveUp(focusManager: androidx.compose.ui.focus.FocusManager) {
    focusManager.moveFocus(FocusDirection.Up)
  }
}

class GlobalInAppKeyboardController {
  var activeSession by mutableStateOf<InAppImeSession?>(null)
  var activeFieldLabel by mutableStateOf("Input Field")
  var activeFieldUnit by mutableStateOf<String?>(null)

  fun dismiss() {
    activeSession = null
  }
}

val LocalInAppKeyboardController = compositionLocalOf<GlobalInAppKeyboardController?> { null }

/**
 * Wraps the entire application so that any standard Compose text field (OutlinedTextField,
 * TextField, BasicTextField, NumberInput) automatically uses the app's own built-in
 * Industrial Dual-Mode (123 + ABC) Keyboard instead of the Android system keyboard.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlobalInAppKeyboardHost(
  enabled: Boolean = true,
  content: @Composable () -> Unit
) {
  val controller = remember { GlobalInAppKeyboardController() }
  val focusManager = LocalFocusManager.current

  CompositionLocalProvider(LocalInAppKeyboardController provides controller) {
    if (!enabled) {
      content()
    } else {
      InterceptPlatformTextInput(
        interceptor = { request, _ ->
          // Legacy View TextInputServiceRequest provides state, imeOptions, onEditCommand, onImeActionPerformed
          val reflectionSession = tryExtractLegacyTextInputSession(request)
          if (reflectionSession != null) {
            controller.activeSession = reflectionSession
            try {
              awaitCancellation()
            } finally {
              if (controller.activeSession === reflectionSession) {
                controller.activeSession = null
              }
            }
          } else {
            awaitCancellation()
          }
        }
      ) {
        Box(modifier = Modifier.fillMaxSize()) {
          content()

          val session = controller.activeSession
          BackHandler(enabled = session != null) {
            focusManager.clearFocus()
            controller.dismiss()
          }

          AnimatedVisibility(
            visible = session != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .windowInsetsPadding(WindowInsets.navigationBars)
          ) {
            if (session != null) {
              val modeBadge = if (session.isInitialTextMode) "TEXT / NUM" else "NUM / TEXT"
              IndustrialKeypadDock(
                cellCoordinateLabel = modeBadge,
                columnTitle = controller.activeFieldLabel,
                unitBadge = controller.activeFieldUnit,
                valueText = session.currentFieldValue.text,
                isTextMode = session.isInitialTextMode,
                allowNegative = true,
                maxDecimals = 4,
                cursorSelection = session.currentFieldValue.selection,
                onValueChange = { newText ->
                  session.updateFullText(newText, newText.length)
                },
                onValueAndCursorChange = { newText, newCursor ->
                  session.updateFullText(newText, newCursor)
                },
                onEnterMoveDown = {
                  session.performNextOrEnter(focusManager) {
                    controller.dismiss()
                  }
                },
                onMoveUp = {
                  session.performMoveUp(focusManager)
                },
                onTabMoveRight = {
                  session.performTabNext(focusManager)
                },
                onShiftTabMoveLeft = {
                  session.performShiftTabPrevious(focusManager)
                },
                onClose = {
                  focusManager.clearFocus()
                  controller.dismiss()
                }
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Extracts TextFieldValue, ImeOptions, onEditCommand, and onImeActionPerformed from Compose's
 * PlatformTextInputMethodRequest across both legacy TextField and BasicTextField2 request types.
 */
@Suppress("UNCHECKED_CAST")
private fun tryExtractLegacyTextInputSession(request: Any): InAppImeSession? {
  return try {
    val cls = request.javaClass
    val stateField = cls.declaredFields.firstOrNull { it.name == "state" }?.apply { isAccessible = true }
    val imeOptionsField = cls.declaredFields.firstOrNull { it.name == "imeOptions" }?.apply { isAccessible = true }
    val onEditCommandField = cls.declaredFields.firstOrNull { it.name == "onEditCommand" }?.apply { isAccessible = true }
    val onImeActionField = cls.declaredFields.firstOrNull { it.name == "onImeActionPerformed" }?.apply { isAccessible = true }

    val initialVal = stateField?.get(request) as? TextFieldValue ?: TextFieldValue("")
    val imeOpts = imeOptionsField?.get(request) as? ImeOptions ?: ImeOptions.Default
    val onEdit = onEditCommandField?.get(request) as? ((List<EditCommand>) -> Unit) ?: return null
    val onAction = onImeActionField?.get(request) as? ((ImeAction) -> Unit)

    val session = InAppImeSession(
      initialValue = initialVal,
      imeOptions = imeOpts,
      onEditCommand = onEdit,
      onImeAction = onAction
    )
    session
  } catch (_: Exception) {
    null
  }
}
