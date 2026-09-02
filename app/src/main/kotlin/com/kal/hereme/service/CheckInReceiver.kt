package com.kal.hereme.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kal.hereme.hereMe
import kotlinx.coroutines.launch

/** Handles the "Yes, that's right" action on the daily check-in notification. */
class CheckInReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CONFIRM) return

        val app = context.hereMe
        val pendingResult = goAsync()

        app.applicationScope.launch {
            try {
                app.repository.markConfirmed()
                Notifications.cancelCheckIn(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CONFIRM = "com.kal.hereme.action.CONFIRM_BALANCE"
    }
}
