package com.kal.hereme.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.kal.hereme.hereMe
import com.kal.hereme.overlay.OverlayService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A bubble that does not come back after a restart is a bubble you stop relying on. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val app = context.hereMe
        val pendingResult = goAsync()

        app.applicationScope.launch {
            try {
                val settings = app.settings.settings.first()
                if (settings.bubbleEnabled && Settings.canDrawOverlays(context)) {
                    OverlayService.start(context)
                }
                CheckInScheduler.schedule(context, settings.checkInHour)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
