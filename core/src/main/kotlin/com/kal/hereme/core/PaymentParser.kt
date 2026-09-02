package com.kal.hereme.core

enum class Direction { DEBIT, CREDIT }

data class ParsedPayment(
    val amount: Paise,
    val direction: Direction,
    val counterparty: String?,
    val sourcePackage: String,
    val confidence: Float,
) {
    /** Signed change to apply to the balance. */
    val delta: Paise
        get() = if (direction == Direction.DEBIT) -amount else amount
}

object PaymentApps {
    const val GPAY = "com.google.android.apps.nbu.paisa.user"
    const val PHONEPE = "com.phonepe.app"
    const val PAYTM = "net.one97.paytm"
    const val WHATSAPP = "com.whatsapp"
    const val BHIM = "in.org.npci.upiapp"
    const val CRED = "com.dreamplug.androidapp"
    const val AMAZON = "in.amazon.mShop.android.shopping"

    val KNOWN: Set<String> = setOf(GPAY, PHONEPE, PAYTM, WHATSAPP, BHIM, CRED, AMAZON)

    /** Bank alerts arrive through whichever app shows SMS, so those are watched too. */
    val DEFAULT_WATCHED: Set<String> = KNOWN + setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
    )
}

/**
 * Turns a payment app's notification into a balance change.
 *
 * Everything here is pure text in, data out — no Android types — so the whole thing is
 * unit tested against a corpus of real notification strings. This is where the app is
 * most likely to be wrong, and being wrong in the DEBIT/CREDIT direction means adding
 * money to the balance when it should have been taken away.
 */
object PaymentParser {

    const val MIN_CONFIDENCE = 0.6f

    /**
     * A loose "this notification is about money" test, used only to decide what to surface
     * on the debug screen. Deliberately broad: it should catch a bank or payment message
     * from an app we don't yet watch (so it can be discovered), while ignoring ordinary
     * chatter. Being a false positive here costs nothing — it only means a line in a log.
     */
    private val FINANCIAL_HINT = Regex(
        """(₹|\bRs\.?|\bINR\b|\bdebited\b|\bcredited\b|\bdebit\b|\bcredit\b|\bpaid\b|""" +
            """\bsent\b|\breceived\b|\bpayment\b|\bUPI\b|\btransferred\b|\bwithdrawn\b)""",
        RegexOption.IGNORE_CASE,
    )

    fun looksFinancial(title: String?, text: String?, bigText: String?): Boolean {
        val blob = listOfNotNull(title, text, bigText).joinToString(" ")
        if (blob.isBlank()) return false
        return FINANCIAL_HINT.containsMatchIn(blob)
    }

    /** Never a completed payment, whatever else the text says. */
    private val HARD_REJECT = listOf(
        """\bfailed\b""",
        """\bdeclined\b""",
        """\bcancelled\b""",
        """\bcanceled\b""",
        """\bunsuccessful\b""",
        """\bpending\b""",
        """\brequest(s|ed|ing)\b""",
        """\bis requesting\b""",
        """\breminder\b""",
        """\bexpir(e|es|ed|ing)\b""",
        """\bwill be debited\b""",
        """\bdue\b""",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    /** Marketing noise — rejected only when there is no real transaction phrase alongside it. */
    private val SOFT_REJECT = listOf(
        """\bcashback\b""",
        """\boffers?\b""",
        """\bscratch card\b""",
        """\bwon\b""",
        """\brewards?\b""",
        """\bup ?to\b""",
        """\bget \S+ ?₹""",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val STRONG_DEBIT = listOf(
        """you paid""",
        """paid to""",
        """debited from""",
        """debited by""",
        """is debited""",
        """you sent""",
        """sent to""",
        """money sent""",
        """withdrawn""",
        """spent at""",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val STRONG_CREDIT = listOf(
        """received from""",
        """you received""",
        """credited to""",
        """credited by""",
        """is credited""",
        """money received""",
        """refund""",
        """deposited""",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val WEAK_DEBIT = listOf(
        """\bpaid\b""",
        """\bsent\b""",
        """\bdebited\b""",
        """\bdebit\b""",
        """\bdeducted\b""",
        """\bpayment of\b""",
        """\bpayment successful\b""",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val WEAK_CREDIT = listOf(
        """\breceived\b""",
        """\bcredited\b""",
        """\bcredit\b""",
        """\badded to\b""",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    /** "₹112.50", "Rs.112", "INR 1,12,000" — a currency marker is required. */
    private val AMOUNT_PREFIXED = Regex(
        """(?:₹|\bRs\.?|\bINR)\s*(\d[\d,]*(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    /** "112.00 INR", "500 rupees" */
    private val AMOUNT_SUFFIXED = Regex(
        """(\d[\d,]*(?:\.\d{1,2})?)\s*(?:INR|rupees)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val COUNTERPARTY = Regex(
        """\b(?:to|from)\s+((?:[A-Za-z0-9@][\w.&'-]*)(?:\s+[A-Za-z0-9@][\w.&'-]*){0,3})""",
    )

    private val COUNTERPARTY_STOPWORDS = setOf(
        "on", "using", "via", "for", "at", "is", "was", "successfully", "successful",
        "your", "the", "a", "an", "and", "in", "with", "ref", "upi", "account", "acct",
    )

    fun parse(
        sourcePackage: String,
        title: String?,
        text: String?,
        bigText: String? = null,
    ): ParsedPayment? {
        val blob = listOfNotNull(title, text, bigText)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(separator = ". ")

        if (blob.isEmpty()) return null
        if (HARD_REJECT.any { it.containsMatchIn(blob) }) return null

        val strongDebitAt = earliestMatch(blob, STRONG_DEBIT)
        val strongCreditAt = earliestMatch(blob, STRONG_CREDIT)
        val hasStrongPhrase = strongDebitAt != null || strongCreditAt != null

        if (!hasStrongPhrase && SOFT_REJECT.any { it.containsMatchIn(blob) }) return null

        val direction = resolveDirection(blob, strongDebitAt, strongCreditAt) ?: return null

        val amountMatch = AMOUNT_PREFIXED.find(blob) ?: AMOUNT_SUFFIXED.find(blob) ?: return null
        val amount = Money.parseAmount(amountMatch.groupValues[1]) ?: return null
        if (amount.isZero) return null

        val confidence = when {
            hasStrongPhrase && sourcePackage in PaymentApps.KNOWN -> 1.0f
            hasStrongPhrase -> 0.8f
            else -> 0.6f
        }
        if (confidence < MIN_CONFIDENCE) return null

        return ParsedPayment(
            amount = amount,
            direction = direction,
            counterparty = extractCounterparty(blob, from = amountMatch.range.last + 1),
            sourcePackage = sourcePackage,
            confidence = confidence,
        )
    }

    private fun resolveDirection(blob: String, strongDebitAt: Int?, strongCreditAt: Int?): Direction? {
        if (strongDebitAt != null || strongCreditAt != null) {
            return pickEarlier(strongDebitAt, strongCreditAt)
        }
        return pickEarlier(earliestMatch(blob, WEAK_DEBIT), earliestMatch(blob, WEAK_CREDIT))
    }

    private fun pickEarlier(debitAt: Int?, creditAt: Int?): Direction? = when {
        debitAt == null && creditAt == null -> null
        creditAt == null -> Direction.DEBIT
        debitAt == null -> Direction.CREDIT
        debitAt <= creditAt -> Direction.DEBIT
        else -> Direction.CREDIT
    }

    private fun earliestMatch(blob: String, patterns: List<Regex>): Int? =
        patterns.mapNotNull { it.find(blob)?.range?.first }.minOrNull()

    private fun extractCounterparty(blob: String, from: Int): String? {
        if (from >= blob.length) return null

        val raw = COUNTERPARTY.find(blob, startIndex = from)?.groupValues?.get(1) ?: return null
        val words = raw.split(' ').filter { it.isNotBlank() }

        // Trailing filler ("Rahul on 01-Sep", "Shop using UPI") is not part of the name.
        val kept = words.takeWhile { it.lowercase().trimEnd('.', ',') !in COUNTERPARTY_STOPWORDS }
        if (kept.isEmpty()) return null

        val name = kept.joinToString(" ").trimEnd('.', ',', ';', '!')
        return name.ifBlank { null }
    }
}
