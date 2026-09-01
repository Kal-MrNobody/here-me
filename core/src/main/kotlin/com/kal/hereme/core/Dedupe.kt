package com.kal.hereme.core

/**
 * Payment apps post the same payment more than once — an initial notification, then an
 * update, then sometimes a group summary carrying the same text. Without this the
 * balance would drop two or three times for one payment.
 *
 * The trade-off: two genuinely separate payments of the same amount, from the same app,
 * inside the window collapse into one. That is recoverable from the history screen; a
 * balance that triple-counts every payment is not.
 */
class DedupeWindow(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val maxEntries: Int = 128,
) {

    private val seenNotificationKeys = LinkedHashMap<String, Long>()
    private val seenPayments = LinkedHashMap<String, Long>()

    @Synchronized
    fun accept(notificationKey: String?, payment: ParsedPayment, now: Long): Boolean {
        prune(now)

        if (notificationKey != null && seenNotificationKeys.containsKey(notificationKey)) {
            return false
        }

        val paymentKey = paymentKey(payment)
        if (seenPayments.containsKey(paymentKey)) return false

        if (notificationKey != null) seenNotificationKeys[notificationKey] = now
        seenPayments[paymentKey] = now
        return true
    }

    @Synchronized
    fun reset() {
        seenNotificationKeys.clear()
        seenPayments.clear()
    }

    private fun paymentKey(payment: ParsedPayment): String =
        "${payment.sourcePackage}|${payment.amount.raw}|${payment.direction}"

    private fun prune(now: Long) {
        seenNotificationKeys.entries.removeAll { now - it.value >= windowMillis }
        seenPayments.entries.removeAll { now - it.value >= windowMillis }

        trimToSize(seenNotificationKeys)
        trimToSize(seenPayments)
    }

    private fun trimToSize(map: LinkedHashMap<String, Long>) {
        while (map.size > maxEntries) {
            val oldest = map.keys.firstOrNull() ?: return
            map.remove(oldest)
        }
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 90_000L
    }
}
