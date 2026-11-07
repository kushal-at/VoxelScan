package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

enum class TactilePulse {
    TAP_LIGHT,
    GUIDEPOINT_LOCK,
    RECORD_START_STOP,
    BATCH_STEP_COMPLETE,
    EXPORT_SUCCESS
}

object HapticFeedbackController {

    fun perform(
        context: Context,
        hapticFeedback: HapticFeedback?,
        pulse: TactilePulse = TactilePulse.TAP_LIGHT
    ) {
        try {
            when (pulse) {
                TactilePulse.TAP_LIGHT -> {
                    hapticFeedback?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vibratePattern(context, durationMs = 12L, amplitude = 90)
                }
                TactilePulse.GUIDEPOINT_LOCK -> {
                    hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
                    vibratePattern(context, durationMs = 28L, amplitude = 180)
                }
                TactilePulse.RECORD_START_STOP -> {
                    hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
                    vibrateWaveform(context, longArrayOf(0, 22, 40, 35), intArrayOf(0, 160, 0, 220))
                }
                TactilePulse.BATCH_STEP_COMPLETE -> {
                    hapticFeedback?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vibratePattern(context, durationMs = 18L, amplitude = 130)
                }
                TactilePulse.EXPORT_SUCCESS -> {
                    hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
                    vibrateWaveform(context, longArrayOf(0, 20, 35, 25, 35, 45), intArrayOf(0, 140, 0, 190, 0, 255))
                }
            }
        } catch (_: Exception) {
            // Ignore on devices without vibrator hardware
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun vibratePattern(context: Context, durationMs: Long, amplitude: Int) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun vibrateWaveform(context: Context, timings: LongArray, amplitudes: IntArray) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40L)
        }
    }
}
