package com.kal.hereme.core

import kotlin.math.abs

/**
 * Money is always whole paise, never a floating point rupee value. A balance that
 * drifts by rounding error is worse than useless here: the whole point of the app is
 * that the number on screen is trustworthy enough to react to.
 */
@JvmInline
value class Paise(val raw: Long) : Comparable<Paise> {

    operator fun plus(other: Paise): Paise = Paise(raw + other.raw)

    operator fun minus(other: Paise): Paise = Paise(raw - other.raw)

    operator fun unaryMinus(): Paise = Paise(-raw)

    override fun compareTo(other: Paise): Int = raw.compareTo(other.raw)

    val isZero: Boolean get() = raw == 0L

    val isNegative: Boolean get() = raw < 0L

    val absolute: Paise get() = Paise(abs(raw))

    override fun toString(): String = Money.format(this)

    companion object {
        val ZERO = Paise(0L)

        fun ofRupees(rupees: Long): Paise = Paise(rupees * 100L)
    }
}

object Money {

    /** Anything above this is a reference number or a parse accident, not a UPI payment. */
    private const val MAX_SANE_RUPEES = 100_000_000L

    private val NUMERIC = Regex("""^(\d+)(?:\.(\d{1,2}))?$""")

    /**
     * Parses the numeric part of an amount as it appears in a notification: digits with
     * optional Indian comma grouping and at most two decimal places. Returns null for
     * anything else, including implausibly large values.
     */
    fun parseAmount(raw: String): Paise? {
        val cleaned = raw.replace(",", "").replace(" ", "").trim()
        val match = NUMERIC.matchEntire(cleaned) ?: return null

        val rupees = match.groupValues[1].toLongOrNull() ?: return null
        if (rupees > MAX_SANE_RUPEES) return null

        val fraction = match.groupValues[2]
        val paise = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 10L
            else -> fraction.toLong()
        }
        return Paise(rupees * 100L + paise)
    }

    /** Formats with Indian digit grouping: 1,00,000 rather than 100,000. */
    fun format(amount: Paise, showPaise: Boolean = amount.raw % 100L != 0L): String {
        val negative = amount.raw < 0L
        val absolute = abs(amount.raw)
        val rupees = absolute / 100L
        val paise = absolute % 100L

        val body = buildString {
            append(groupIndian(rupees.toString()))
            if (showPaise) {
                append('.')
                append(paise.toString().padStart(2, '0'))
            }
        }
        return if (negative) "-₹$body" else "₹$body"
    }

    /** Last three digits, then pairs: 1,23,45,678. */
    internal fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits

        val last3 = digits.takeLast(3)
        var rest = digits.dropLast(3)
        val groups = ArrayDeque<String>()
        while (rest.length > 2) {
            groups.addFirst(rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) groups.addFirst(rest)

        return groups.joinToString(separator = ",") + "," + last3
    }
}
