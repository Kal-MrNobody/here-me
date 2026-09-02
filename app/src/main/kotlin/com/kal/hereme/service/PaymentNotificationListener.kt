package com.kal.hereme.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.kal.hereme.core.DedupeWindow
import com.kal.hereme.core.EntryType
import com.kal.hereme.core.Money
import com.kal.hereme.core.PaymentParser
import com.kal.hereme.data.AppSettings
import com.kal.hereme.hereMe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Where the balance learns that money moved.
 *
 * This reads the confirmation notification the payment app has already posted, rather
 * than watching what gets typed inside it. Watching the typing would mean an
 * AccessibilityService reading another app's input field — the same API a keylogger
 * uses, which payment apps actively detect and which breaks on every redesign. The
 * notification arrives about a second later and keeps working.
 */
class PaymentNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dedupe = DedupeWindow()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerDisconnected() {
        dedupe.reset()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.isOngoing) return

        val packageName = notification.packageName ?: return
        if (packageName == this.packageName) return

        val extras = notification.notification?.extras ?: return
        val title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString()

        scope.launch { handle(packageName, notification.key, title, text, bigText) }
    }

    private suspend fun handle(
        packageName: String,
        notificationKey: String?,
        title: String?,
        text: String?,
        bigText: String?,
    ) {
        val app = applicationContext.hereMe
        val settings: AppSettings = app.settings.settings.first()

        val watched = packageName in settings.watchedPackages
        val financial = PaymentParser.looksFinancial(title, text, bigText)

        // Ignore everything that is neither from a watched app nor looks like money — most
        // of what a phone posts. Anything past this point is worth a line in the debug log.
        if (!watched && !financial) return

        val payment = PaymentParser.parse(packageName, title, text, bigText)

        var applied = false
        val outcome: String = when {
            payment == null ->
                "Seen, but no amount/direction recognised"

            !watched ->
                "Recognised ${direction(payment.direction)} ${Money.format(payment.amount)} — " +
                    "but this app isn't watched yet. Tap “Watch this app”."

            !dedupe.accept(notificationKey, payment, System.currentTimeMillis()) ->
                "Duplicate within 90s — ignored"

            else -> {
                app.repository.record(
                    delta = payment.delta,
                    type = EntryType.AUTO_PAYMENT,
                    counterparty = payment.counterparty,
                    sourcePackage = packageName,
                    rawText = listOfNotNull(title, text, bigText).distinct().joinToString(" · "),
                )
                applied = true
                // The bubble is watching the ledger, so recording the entry is all it takes
                // for the number on screen to start draining.
                "Applied ${direction(payment.direction)} ${Money.format(payment.amount)}"
            }
        }

        app.repository.logNotification(packageName, title, text, bigText, outcome, applied)
    }

    private fun direction(d: com.kal.hereme.core.Direction): String =
        if (d == com.kal.hereme.core.Direction.DEBIT) "−" else "+"
}
