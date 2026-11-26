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
import androidx.compose.ui.text.input.BackspaceCommand
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteAllCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.lang.reflect.Field

/**
 * Represents an active text input session intercepted from ANY OutlinedTextField / BasicTextField
 * across the entire application (screens, dialogs, bottom sheets).
 *
 * Continuously syncs live cursor & selection from Compose's platform input request so tapping
 * anywhere in a text field inserts/deletes at the exact tapped character position.
 */
class InAppImeSession(
  initialValue: TextFieldValue,
  val imeOptions: ImeOptions,
  val onEditCommand: (List<EditCommand>) -> Unit,
  val onImeAction: ((ImeAction) -> Unit)?,
  private val requestRef: Any? = null,
  private val stateFieldRef: Field? = null
) {
  var currentFieldValue by mutableStateOf(initialValue)

  val isInitialTextMode: Boolean = when (imeOptions.keyboardType) {
    KeyboardType.Number,
    KeyboardType.Decimal,
    KeyboardType.Phone,
    KeyboardType.NumberPassword -> false
    else -> true
  }

  /**
   * Reads the live TextFieldValue (text + user-tapped cursor/selection) from Compose's
   * LegacyTextInputMethodRequest. Keeps the keypad formula bar and edits 100% synced with taps.
   */
  fun syncFromPlatform(): TextFieldValue {
    val live = try {
      stateFieldRef?.get(requestRef) as? TextFieldValue
    } catch (_: Exception) {
      null
    }
    if (live != null && live != currentFieldValue) {
      currentFieldValue = live
    }
    return live ?: currentFieldValue
  }

  /**
   * Inserts text natively at the live tapped cursor (or replaces current selection) via
   * Compose's CommitTextCommand without clobbering the user's cursor position.
   */
  fun commitTextAtCursor(text: String) {
    val live = syncFromPlatform()
    onEditCommand(listOf(CommitTextCommand(text, 1)))
    // Optimistically mirror the edit immediately, then re-sync if platform updated synchronously
    val start = live.selection.min.coerceIn(0, live.text.length)
    val end = live.selection.max.coerceIn(0, live.text.length)
    val updatedText = live.text.substring(0, start) + text + live.text.substring(end)
    val nextCursor = (start + text.length).coerceIn(0, updatedText.length)
    currentFieldValue = TextFieldValue(text = updatedText, selection = TextRange(nextCursor))
    syncFromPlatform()
  }

  /**
   * Deletes the character immediately before the live tapped cursor (or deletes selected text)
   * using Compose's native BackspaceCommand.
   */
  fun backspaceAtCursor(): Boolean {
    val live = syncFromPlatform()
    if (live.text.isEmpty()) return false
    if (live.selection.collapsed && live.selection.min <= 0) return false

    onEditCommand(listOf(BackspaceCommand()))

    val start = live.selection.min.coerceIn(0, live.text.length)
    val end = live.selection.max.coerceIn(0, live.text.length)
    val updatedText: String
    val nextCursor: Int
    if (start != end) {
      updatedText = live.text.substring(0, start) + live.text.substring(end)
      nextCursor = start
    } else {
      updatedText = live.text.substring(0, start - 1) + live.text.substring(start)
      nextCursor = start - 1
    }
    currentFieldValue = TextFieldValue(
      text = updatedText,
      selection = TextRange(nextCursor.coerceIn(0, updatedText.length))
    )
    syncFromPlatform()
    return true
  }

  /**
   * Moves the text cursor left (-1) or right (+1) by [delta] characters.
   * Returns true if cursor moved, false if already at the start/end boundary.
   */
  fun moveCursorBy(delta: Int): Boolean {
    val live = syncFromPlatform()
    val len = live.text.length
    val currentSelection = live.selection
    val targetPos = if (!currentSelection.collapsed) {
      if (delta < 0) currentSelection.min else currentSelection.max
    } else {
      (currentSelection.min + delta).coerceIn(0, len)
    }
    if (currentSelection.collapsed && targetPos == currentSelection.min) {
      return false
    }
    onEditCommand(listOf(SetSelectionCommand(targetPos, targetPos)))
    currentFieldValue = live.copy(selection = TextRange(targetPos))
    syncFromPlatform()
    return true
  }

  /**
   * Explicitly places the cursor at [pos] (used when advancing fields via NEXT/TAB).
   */
  fun setCursorPosition(pos: Int) {
    val live = syncFromPlatform()
    val safe = pos.coerceIn(0, live.text.length)
    onEditCommand(listOf(SetSelectionCommand(safe, safe)))
    currentFieldValue = live.copy(selection = TextRange(safe))
    syncFromPlatform()
  }

  /**
   * Selects all text in the active field so the next key press replaces it cleanly.
   */
  fun selectAll() {
    val live = syncFromPlatform()
    if (live.text.isEmpty()) return
    onEditCommand(listOf(SetSelectionCommand(0, live.text.length)))
    currentFieldValue = live.copy(selection = TextRange(0, live.text.length))
    syncFromPlatform()
  }

  /**
   * Replaces the entire text buffer atomically (used for CLR, '=' formula eval, and '±' toggle).
   */
  fun updateFullText(newText: String, newCursorPos: Int = newText.length) {
    val safeCursor = newCursorPos.coerceIn(0, newText.length)
    currentFieldValue = TextFieldValue(
      text = newText,
      selection = TextRange(safeCursor)
    )
    onEditCommand(
      listOf(
        DeleteAllCommand(),
        CommitTextCommand(newText, 1),
        SetSelectionCommand(safeCursor, safeCursor)
      )
    )
    syncFromPlatform()
  }

  fun performNextOrEnter(
    focusManager: androidx.compose.ui.focus.FocusManager,
    controller: GlobalInAppKeyboardController,
    onClose: () -> Unit
  ) {
    val action = imeOptions.imeAction
    if (onImeAction != null && action != ImeAction.Default && action != ImeAction.None) {
      onImeAction.invoke(action)
      if (action == ImeAction.Done || action == ImeAction.Go || action == ImeAction.Search || action == ImeAction.Send) {
        focusManager.clearFocus()
        onClose()
      } else if (action == ImeAction.Next) {
        controller.movedByKeyboardNav = true
        val moved = focusManager.moveFocus(FocusDirection.Down) || focusManager.moveFocus(FocusDirection.Next)
        if (!moved) {
          controller.movedByKeyboardNav = false
          focusManager.clearFocus()
          onClose()
        }
      }
    } else {
      controller.movedByKeyboardNav = true
      val moved = focusManager.moveFocus(FocusDirection.Down) || focusManager.moveFocus(FocusDirection.Next)
      if (!moved) {
        controller.movedByKeyboardNav = false
        focusManager.clearFocus()
        onClose()
      }
    }
  }

  fun performTabNext(
    focusManager: androidx.compose.ui.focus.FocusManager,
    controller: GlobalInAppKeyboardController
  ) {
    controller.movedByKeyboardNav = true
    val moved = focusManager.moveFocus(FocusDirection.Next) || focusManager.moveFocus(FocusDirection.Down)
    if (!moved) {
      controller.movedByKeyboardNav = false
      focusManager.clearFocus()
    }
  }

  fun performShiftTabPrevious(
    focusManager: androidx.compose.ui.focus.FocusManager,
    controller: GlobalInAppKeyboardController
  ) {
    controller.movedByKeyboardNav = true
    if (!focusManager.moveFocus(FocusDirection.Previous)) {
      controller.movedByKeyboardNav = false
    }
  }

  fun performMoveUp(
    focusManager: androidx.compose.ui.focus.FocusManager,
    controller: GlobalInAppKeyboardController
  ) {
    controller.movedByKeyboardNav = true
    if (!focusManager.moveFocus(FocusDirection.Up)) {
      controller.movedByKeyboardNav = false
    }
  }
}

class GlobalInAppKeyboardController {
  var activeSession by mutableStateOf<InAppImeSession?>(null)
  var activeFieldLabel by mutableStateOf("Input Field")
  var activeFieldUnit by mutableStateOf<String?>(null)
  var movedByKeyboardNav: Boolean = false

  fun dismiss() {
    activeSession = null
    movedByKeyboardNav = false
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

          // Continuously mirror live tap cursor / selection changes from Compose's TextField into the keypad
          LaunchedEffect(session) {
            if (session != null) {
              // If focus arrived via NEXT/TAB/UP keyboard navigation, place cursor at end of text
              // (If user tapped the field directly, NEVER overwrite their exact tapped cursor!)
              if (controller.movedByKeyboardNav) {
                controller.movedByKeyboardNav = false
                delay(35L)
                val live = session.syncFromPlatform()
                if (live.text.isNotEmpty() && live.selection == TextRange.Zero) {
                  session.setCursorPosition(live.text.length)
                }
              }
              while (true) {
                session.syncFromPlatform()
                delay(50L)
              }
            }
          }

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
                onInsertText = { str ->
                  session.commitTextAtCursor(str)
                },
                onBackspace = {
                  session.backspaceAtCursor()
                },
                onMoveCursor = { delta ->
                  session.moveCursorBy(delta)
                },
                onSelectAll = {
                  session.selectAll()
                },
                onEnterMoveDown = {
                  session.performNextOrEnter(focusManager, controller) {
                    controller.dismiss()
                  }
                },
                onMoveUp = {
                  session.performMoveUp(focusManager, controller)
                },
                onTabMoveRight = {
                  session.performTabNext(focusManager, controller)
                },
                onShiftTabMoveLeft = {
                  session.performShiftTabPrevious(focusManager, controller)
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
 * Retains the request and stateField references for live cursor & selection syncing.
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

    InAppImeSession(
      initialValue = initialVal,
      imeOptions = imeOpts,
      onEditCommand = onEdit,
      onImeAction = onAction,
      requestRef = request,
      stateFieldRef = stateField
    )
  } catch (_: Exception) {
    null
  }
}
