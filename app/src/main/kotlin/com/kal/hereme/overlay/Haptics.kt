package com.kal.hereme.overlay

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The tactile half of the drain. Counting notes out of a wallet has a rhythm to it, and
 * a silent number sliding down a screen does not.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }.getOrNull()

    fun tick(strong: Boolean = false) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return

        runCatching {
            device.vibrate(
                VibrationEffect.createOneShot(
                    if (strong) 18L else 9L,
                    if (strong) 90 else 45,
                ),
            )
        }
    }
}
