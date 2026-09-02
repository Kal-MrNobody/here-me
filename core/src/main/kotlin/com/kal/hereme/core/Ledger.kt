package com.kal.hereme.core

import kotlin.math.abs
import kotlin.math.roundToLong

enum class EntryType {
    /** Set when the user first tells the app what they have. */
    OPENING,

    /** Read off a payment app or bank notification. */
    AUTO_PAYMENT,

    /** Typed by the user in the bubble or the history screen. */
    MANUAL,

    /** Cash the app can never see by itself. */
    CASH,

    /** The correction applied when the user confirms their real balance. */
    RECONCILE,
}

/**
 * Balance arithmetic, kept pure so the storage layer only has to persist what these
 * functions return.
 */
object Ledger {

    fun applyAll(opening: Paise, deltas: List<Paise>): Paise =
        deltas.fold(opening) { running, delta -> running + delta }

    /**
     * The correction needed to move [current] to the balance the user says is real.
     * Recorded as its own entry so the drift stays visible instead of being silently
     * papered over.
     */
    fun reconciliationDelta(current: Paise, actual: Paise): Paise = actual - current

    /** How far the tracked number had wandered from reality, unsigned. */
    fun driftMagnitude(current: Paise, actual: Paise): Paise = (actual - current).absolute
}

object Staleness {

    const val DEFAULT_THRESHOLD_DAYS = 3

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    /**
     * A balance nobody has confirmed in days is a guess. The bubble says so rather than
     * continuing to present it as fact — a tracker that is quietly wrong teaches you to
     * ignore it, which is the opposite of what this app is for.
     */
    fun isStale(
        lastReconciledAt: Long,
        now: Long,
        thresholdDays: Int = DEFAULT_THRESHOLD_DAYS,
    ): Boolean {
        if (lastReconciledAt <= 0L) return true
        return now - lastReconciledAt >= thresholdDays * MILLIS_PER_DAY
    }

    fun daysSince(lastReconciledAt: Long, now: Long): Long {
        if (lastReconciledAt <= 0L) return Long.MAX_VALUE
        return (now - lastReconciledAt) / MILLIS_PER_DAY
    }
}

/**
 * The feel of the thing.
 *
 * A payment does not snap the number to its new value — it drains, and how long the
 * drain takes depends on how big a bite it took out of what you had. ₹20 out of ₹5,000
 * is a blink. ₹2,000 out of ₹5,000 takes an uncomfortably long moment, which is exactly
 * the discomfort of watching notes leave a wallet.
 */
object Drain {

    const val MIN_MILLIS = 300L
    const val MAX_MILLIS = 2_000L

    /** One haptic tick per this many milliseconds of drain. */
    private const val MILLIS_PER_TICK = 90L

    fun durationMillis(
        amount: Paise,
        balanceBefore: Paise,
        minMillis: Long = MIN_MILLIS,
        maxMillis: Long = MAX_MILLIS,
    ): Long {
        val bite = biteFraction(amount, balanceBefore)
        return minMillis + ((maxMillis - minMillis) * bite).roundToLong()
    }

    /**
     * What proportion of the balance this payment consumed, clamped to 0..1. Spending
     * against an empty or overdrawn balance counts as taking everything.
     */
    fun biteFraction(amount: Paise, balanceBefore: Paise): Double {
        if (balanceBefore.raw <= 0L) return 1.0
        val fraction = abs(amount.raw).toDouble() / balanceBefore.raw.toDouble()
        return fraction.coerceIn(0.0, 1.0)
    }

    fun tickCount(durationMillis: Long): Int =
        (durationMillis / MILLIS_PER_TICK).toInt().coerceIn(1, 24)
}
