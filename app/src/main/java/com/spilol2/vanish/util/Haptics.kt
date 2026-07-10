package com.spilol2.vanish.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * A real haptics engine, not just Compose's generic LongPress feedback.
 * minSdk is 29, so predefined VibrationEffect constants (TICK/CLICK/HEAVY_CLICK/
 * DOUBLE_CLICK, all API 29) are always available — no fallback branching needed
 * for those. Custom waveforms are used for patterns Android doesn't predefine
 * (the vanish "poof", the warning buzz, the download-complete pulse).
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            mgr.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private fun fire(effect: VibrationEffect) {
        try { vibrator.vibrate(effect) } catch (_: Exception) { /* no vibrator hardware */ }
    }

    private fun predefined(id: Int) = fire(VibrationEffect.createPredefined(id))

    /** Smallest possible bump — tool switches, radio selects, toggles. */
    fun tick() = predefined(VibrationEffect.EFFECT_TICK)

    /** Standard button press. */
    fun click() = predefined(VibrationEffect.EFFECT_CLICK)

    /** Undo/redo — a touch softer than a full click. */
    fun softTick() = predefined(VibrationEffect.EFFECT_TICK)

    /** A closed lasso loop, a slider hitting its 50% detent — a magnetic "snap". */
    fun snap() = predefined(VibrationEffect.EFFECT_HEAVY_CLICK)

    /**
     * The vanish moment: object gone, background rebuilt. A quick rising
     * two-pulse — distinct from a flat click, feels like a small "pop".
     */
    fun vanish() = fire(
        VibrationEffect.createWaveform(
            longArrayOf(0, 12, 40, 22),
            intArrayOf(0, 110, 0, 255),
            -1,
        )
    )

    /** Something worked: save complete, model download finished. */
    fun success() = predefined(VibrationEffect.EFFECT_DOUBLE_CLICK)

    /** Something didn't work: empty mask, download failed. Short-short, firmer. */
    fun warning() = fire(
        VibrationEffect.createWaveform(
            longArrayOf(0, 30, 60, 30),
            intArrayOf(0, 200, 0, 200),
            -1,
        )
    )

    /**
     * A single very light pulse meant to be called often in a row (once per
     * ~N px of drag) so a brush stroke feels textured under the finger,
     * like drawing on a slightly grainy surface.
     */
    fun texture() = fire(
        VibrationEffect.createOneShot(6, 60)
    )

    /**
     * One pulse in a rubber-band zoom limit, strength scaling with [intensity]
     * in [0,1] — call repeatedly as the user pulls further past the limit so
     * it ramps from barely-there to a firm push-back.
     */
    fun overscroll(intensity: Float) {
        val i = intensity.coerceIn(0f, 1f)
        val amplitude = (35 + i * 220).toInt().coerceIn(1, 255)
        val duration = (9 + i * 16).toLong().coerceAtLeast(1)
        fire(VibrationEffect.createOneShot(duration, amplitude))
    }

    /**
     * The stretched gesture snapping back to its resting bound — a sharp
     * punch that decays away fast, like letting go of a pulled spring.
     */
    fun springRelease() = fire(
        VibrationEffect.createWaveform(
            longArrayOf(0, 14, 16, 14, 18, 12),
            intArrayOf(0, 255, 0, 130, 0, 55),
            -1,
        )
    )
}
