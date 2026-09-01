package com.kal.hereme.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.kal.hereme.core.DedupeWindow
import com.kal.hereme.core.EntryType
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

        if (packageName !in settings.watchedPackages) return

        val payment = PaymentParser.parse(packageName, title, text, bigText) ?: return
        if (payment.confidence < PaymentParser.MIN_CONFIDENCE) return
        if (!dedupe.accept(notificationKey, payment, System.currentTimeMillis())) return

        app.repository.record(
            delta = payment.delta,
            type = EntryType.AUTO_PAYMENT,
            counterparty = payment.counterparty,
            sourcePackage = packageName,
            rawText = listOfNotNull(title, text, bigText).distinct().joinToString(" · "),
        )
        // The bubble is watching the ledger, so recording the entry is all it takes for
        // the number on screen to start draining.
    }
}
