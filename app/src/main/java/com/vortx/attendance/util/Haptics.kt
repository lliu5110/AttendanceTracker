package com.vortx.attendance.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Distinct vibration patterns for the three things a scan can mean, so the result is
 * readable without looking at the screen — which is the point when you're holding a
 * phone at a door and watching the queue instead of the display.
 */
enum class Haptic {
    /** Crisp single tick: checked in. */
    Success,

    /** Two light taps: recognised, but already counted today. */
    Duplicate,

    /** One longer buzz: nobody owns this ID, a decision is needed. */
    Unknown
}

object Haptics {

    fun play(context: Context, kind: Haptic) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        // Every pattern fires at full strength — amplitude carries no meaning here,
        // so the three outcomes are told apart by shape (tick / double-tap / hold)
        // instead of by how hard the buzz is.
        val amp = if (vibrator.hasAmplitudeControl()) 255 else -1

        val effect = when (kind) {
            // One short tick.
            Haptic.Success -> VibrationEffect.createOneShot(20, amp)
            // Two quick taps.
            Haptic.Duplicate -> VibrationEffect.createWaveform(
                longArrayOf(0, 14, 90, 14),
                intArrayOf(0, amp, 0, amp),
                -1
            )
            // One held buzz, longer than either tick.
            Haptic.Unknown -> VibrationEffect.createOneShot(120, amp)
        }

        runCatching { vibrator.vibrate(effect) }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
}
