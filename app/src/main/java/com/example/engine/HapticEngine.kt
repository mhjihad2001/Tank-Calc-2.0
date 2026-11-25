package com.example.engine

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

enum class HapticEvent {
  KEY_TAP,         // Crisp digit / letter key tap (adaptive to typing speed)
  KEY_OPERATOR,    // Distinct tick for decimal '.', '+', '-', '*', '/'
  KEY_DELETE,      // Soft low-amplitude release tick for Backspace / Clear
  CELL_SELECT,     // Subtle micro-tick when selecting a field or table cell
  NAV_STEP,        // Firm mechanical step when pressing NEXT, TAB, or row arrows
  MODE_SWITCH,     // Crisp toggle click when shifting 123 <-> ABC, tabs, or chips
  SUCCESS,         // Rising double-pulse confirmation on '=', Save, or Unlock
  WARNING_ALERT,   // Heavy double-thud warning on outlier, out-of-range, or capacity breach
  BOUNDARY_LIMIT   // Double micro-bump when hitting top/bottom table boundary or empty delete
}

data class HapticConfig(
  val enabled: Boolean = true,
  val intensity: Int = 2 // 1 = Light, 2 = Medium (Smart Default), 3 = Strong (Field Glove)
)

val LocalHapticConfig = compositionLocalOf { HapticConfig() }

object HapticEngine {

  // Adaptive typing cadence & anti-spam state
  @Volatile private var lastEventTimeMs: Long = 0L
  @Volatile private var lastEventType: HapticEvent? = null
  @Volatile private var lastAlertTimeMs: Long = 0L

  private fun getVibrator(context: Context): Vibrator? {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }
    } catch (_: Exception) {
      null
    }
  }

  @Suppress("DEPRECATION")
  fun perform(
    view: View?,
    context: Context?,
    event: HapticEvent,
    config: HapticConfig = HapticConfig()
  ) {
    if (!config.enabled) return

    val now = SystemClock.uptimeMillis()
    val deltaMs = now - lastEventTimeMs

    // 1. Smart Anti-Spam & Duplicate Coalescing:
    // Ignore duplicate identical triggers within 18ms (e.g. focus + tap firing together)
    if (event == lastEventType && deltaMs in 0..18L) {
      return
    }
    // SuppressCELL_SELECT if a KEY_TAP or MODE_SWITCH just fired within 35ms
    if (event == HapticEvent.CELL_SELECT && deltaMs in 0..35L) {
      return
    }
    // Cooldown for WARNING_ALERT so live re-evaluations don't buzz repeatedly on every character
    if (event == HapticEvent.WARNING_ALERT) {
      if (now - lastAlertTimeMs < 450L) return
      lastAlertTimeMs = now
    }

    // Track whether the user is typing rapidly (< 115ms between key taps)
    val isRapidTyping = (event == HapticEvent.KEY_TAP || event == HapticEvent.KEY_DELETE) && deltaMs in 19..115L
    lastEventTimeMs = now
    lastEventType = event

    val intensity = config.intensity.coerceIn(1, 3)
    var handledByRichHardware = false

    // 2. Smart Hardware Primitive / Waveform Engine
    try {
      val ctx = context ?: view?.context
      val vibrator = if (ctx != null) getVibrator(ctx) else null

      if (vibrator != null && vibrator.hasVibrator() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val baseScale = when (intensity) {
          1 -> 0.55f
          2 -> 0.90f
          else -> 1.35f
        }
        // Automatically lighten amplitude by 30% during rapid multi-digit typing so it feels crisp, never muddy
        val effectiveScale = if (isRapidTyping) (baseScale * 0.70f).coerceAtLeast(0.30f) else baseScale

        // Prefer Android 11+ (API 30+) HD Haptic Primitives when supported by the actuator
        val effect: VibrationEffect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          buildPrimitiveOrWaveformEffect(vibrator, event, effectiveScale, isRapidTyping, intensity)
        } else {
          buildOreoWaveformEffect(event, effectiveScale, isRapidTyping, intensity)
        }

        if (effect != null) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = VibrationAttributes.Builder()
              .setUsage(VibrationAttributes.USAGE_TOUCH)
              .build()
            vibrator.vibrate(effect, attrs)
          } else {
            vibrator.vibrate(effect)
          }
          handledByRichHardware = true
        }
      }
    } catch (_: Exception) {
      handledByRichHardware = false
    }

    // 3. View Haptic Fallback ONLY when hardware vibrator did not handle it
    // (Prevents the muddy double-vibration caused by firing both View and Vibrator together)
    if (!handledByRichHardware && view != null) {
      try {
        val constant = when (event) {
          HapticEvent.KEY_TAP -> HapticFeedbackConstants.KEYBOARD_TAP
          HapticEvent.KEY_OPERATOR -> HapticFeedbackConstants.CLOCK_TICK
          HapticEvent.KEY_DELETE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            HapticFeedbackConstants.TEXT_HANDLE_MOVE
          } else {
            HapticFeedbackConstants.KEYBOARD_TAP
          }
          HapticEvent.CELL_SELECT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            HapticFeedbackConstants.TEXT_HANDLE_MOVE
          } else {
            HapticFeedbackConstants.CLOCK_TICK
          }
          HapticEvent.NAV_STEP, HapticEvent.MODE_SWITCH -> HapticFeedbackConstants.VIRTUAL_KEY
          HapticEvent.SUCCESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
          } else {
            HapticFeedbackConstants.VIRTUAL_KEY
          }
          HapticEvent.WARNING_ALERT, HapticEvent.BOUNDARY_LIMIT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
          } else {
            HapticFeedbackConstants.LONG_PRESS
          }
        }
        view.performHapticFeedback(
          constant,
          HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
      } catch (_: Exception) {
        // Safe no-op in headless unit test environments
      }
    }
  }

  @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
  private fun buildPrimitiveOrWaveformEffect(
    vibrator: Vibrator,
    event: HapticEvent,
    scale: Float,
    isRapidTyping: Boolean,
    intensity: Int
  ): VibrationEffect {
    val s = scale.coerceIn(0.2f, 1.0f)
    val canClick = vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
    val canTick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
    } else false
    val canQuickRise = vibrator.areAllPrimitivesSupported(
      VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
      VibrationEffect.Composition.PRIMITIVE_CLICK
    )
    val canThud = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
    } else false

    return when (event) {
      HapticEvent.KEY_TAP -> {
        if (isRapidTyping && canTick && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, s)
            .compose()
        } else if (canClick) {
          VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s)
            .compose()
        } else {
          buildOreoWaveformEffect(event, scale, isRapidTyping, intensity)
        }
      }
      HapticEvent.KEY_OPERATOR -> {
        if (canClick) {
          VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, (s * 1.15f).coerceAtMost(1.0f))
            .compose()
        } else {
          buildOreoWaveformEffect(event, scale, isRapidTyping, intensity)
        }
      }
      HapticEvent.KEY_DELETE, HapticEvent.CELL_SELECT -> {
        if (canTick && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, (s * 0.85f).coerceIn(0.2f, 1.0f))
            .compose()
        } else if (canClick) {
          VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, (s * 0.65f).coerceIn(0.2f, 1.0f))
            .compose()
        } else {
          buildOreoWaveformEffect(event, scale, isRapidTyping, intensity)
        }
      }
      HapticEvent.NAV_STEP, HapticEvent.MODE_SWITCH -> {
        if (canClick) {
          VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, (s * 1.25f).coerceAtMost(1.0f))
            .compose()
        } else {
          buildOreoWaveformEffect(event, scale, isRapidTyping, intensity)
        }
      }
      HapticEvent.SUCCESS -> {
        if (canQuickRise) {
          VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, (s * 0.85f).coerceAtMost(1.0f))
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 35)
            .compose()
        } else {
          buildOreoWaveformEffect(event, scale, isRapidTyping, intensity)
        }
      }
      HapticEvent.WARNING_ALERT -> {
        if (canThud && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 65)
            .compose()
        } else {
          buildOreoWaveformEffect(event, scale, isRapidTyping, intensity)
        }
      }
      HapticEvent.BOUNDARY_LIMIT -> {
        if (canClick) {
          VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, (s * 0.7f).coerceAtMost(1.0f))
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, (s * 0.7f).coerceAtMost(1.0f), 40)
            .compose()
        } else {
          buildOreoWaveformEffect(event, scale, isRapidTyping, intensity)
        }
      }
    }
  }

  @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
  private fun buildOreoWaveformEffect(
    event: HapticEvent,
    scale: Float,
    isRapidTyping: Boolean,
    intensity: Int
  ): VibrationEffect {
    fun amp(base: Int): Int = (base * scale).toInt().coerceIn(1, 255)

    return when (event) {
      HapticEvent.KEY_TAP -> {
        val dur = when {
          isRapidTyping -> 5L
          intensity == 3 -> 10L
          else -> 7L
        }
        VibrationEffect.createOneShot(dur, amp(105))
      }
      HapticEvent.KEY_OPERATOR -> {
        VibrationEffect.createOneShot(if (intensity == 3) 12L else 8L, amp(135))
      }
      HapticEvent.KEY_DELETE -> {
        VibrationEffect.createOneShot(6L, amp(80))
      }
      HapticEvent.CELL_SELECT -> {
        VibrationEffect.createOneShot(5L, amp(65))
      }
      HapticEvent.NAV_STEP, HapticEvent.MODE_SWITCH -> {
        VibrationEffect.createOneShot(if (intensity == 3) 14L else 10L, amp(145))
      }
      HapticEvent.SUCCESS -> {
        VibrationEffect.createWaveform(
          longArrayOf(0L, 8L, 38L, 14L),
          intArrayOf(0, amp(110), 0, amp(210)),
          -1
        )
      }
      HapticEvent.WARNING_ALERT -> {
        VibrationEffect.createWaveform(
          longArrayOf(0L, 22L, 50L, 32L),
          intArrayOf(0, amp(200), 0, amp(255)),
          -1
        )
      }
      HapticEvent.BOUNDARY_LIMIT -> {
        VibrationEffect.createWaveform(
          longArrayOf(0L, 7L, 32L, 7L),
          intArrayOf(0, amp(115), 0, amp(115)),
          -1
        )
      }
    }
  }
}

class HapticFeedbackController(
  private val view: View,
  private val context: Context,
  private val config: HapticConfig
) {
  fun trigger(event: HapticEvent = HapticEvent.KEY_TAP) {
    HapticEngine.perform(view, context, event, config)
  }
}

@Composable
fun rememberHapticController(): HapticFeedbackController {
  val view = LocalView.current
  val context = LocalContext.current
  val config = LocalHapticConfig.current
  return remember(view, context, config) {
    HapticFeedbackController(view, context, config)
  }
}
