package com.kal.hereme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The corpus that matters. Getting the direction wrong here adds money to the balance
 * when it should have taken it away, so every real notification shape gets a case.
 */
class PaymentParserTest {

    // --- payments that must be caught -------------------------------------------------

    @Test
    fun `gpay payment`() {
        val result = PaymentParser.parse(
            sourcePackage = PaymentApps.GPAY,
            title = "Google Pay",
            text = "You paid ₹112.00 to Rahul Sharma",
        )

        assertNotNull(result)
        assertEquals(Paise(11_200), result!!.amount)
        assertEquals(Direction.DEBIT, result.direction)
        assertEquals("Rahul Sharma", result.counterparty)
        assertEquals(Paise(-11_200), result.delta)
        assertEquals(1.0f, result.confidence, 0.001f)
    }

    @Test
    fun `gpay money received`() {
        val result = PaymentParser.parse(
            sourcePackage = PaymentApps.GPAY,
            title = "Google Pay",
            text = "You received ₹500 from Amit",
        )

        assertEquals(Direction.CREDIT, result!!.direction)
        assertEquals(Paise(50_000), result.amount)
        assertEquals(Paise(50_000), result.delta)
        assertEquals("Amit", result.counterparty)
    }

    @Test
    fun `phonepe payment`() {
        val result = PaymentParser.parse(
            sourcePackage = PaymentApps.PHONEPE,
            title = "PhonePe",
            text = "₹112 paid to SHREE STORES",
        )

        assertEquals(Direction.DEBIT, result!!.direction)
        assertEquals(Paise(11_200), result.amount)
        assertEquals("SHREE STORES", result.counterparty)
    }

    @Test
    fun `paytm payment without a strong phrase still parses`() {
        val result = PaymentParser.parse(
            sourcePackage = PaymentApps.PAYTM,
            title = "Paytm",
            text = "Paid ₹250 to Coffee House",
        )

        assertEquals(Direction.DEBIT, result!!.direction)
        assertEquals(Paise(25_000), result.amount)
    }

    @Test
    fun `whatsapp pay`() {
        val result = PaymentParser.parse(
            sourcePackage = PaymentApps.WHATSAPP,
            title = "Priya",
            text = "You sent ₹75 to Priya",
        )

        assertEquals(Direction.DEBIT, result!!.direction)
        assertEquals(Paise(7_500), result.amount)
    }

    @Test
    fun `bank debit sms takes the transaction amount not the closing balance`() {
        val result = PaymentParser.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "HDFCBK",
            text = "Your A/c XX1234 is debited by Rs.112.00 on 01-Sep-25. Avl Bal Rs.4888.00",
        )

        assertEquals(Direction.DEBIT, result!!.direction)
        assertEquals(Paise(11_200), result.amount)
        assertEquals(0.8f, result.confidence, 0.001f)
    }

    @Test
    fun `bank credit sms with grouped amount`() {
        val result = PaymentParser.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "SBIINB",
            text = "Your A/c XX1234 is credited by Rs.50,000.00 - SALARY SEP",
        )

        assertEquals(Direction.CREDIT, result!!.direction)
        assertEquals(Paise(50_00_000), result.amount)
    }

    @Test
    fun `reads the amount out of bigText when text is truncated`() {
        val result = PaymentParser.parse(
            sourcePackage = PaymentApps.GPAY,
            title = "Google Pay",
            text = "Payment successful",
            bigText = "You paid ₹1,250.75 to Big Bazaar",
        )

        assertEquals(Direction.DEBIT, result!!.direction)
        assertEquals(Paise(1_25_075), result.amount)
    }

    // --- direction traps ---------------------------------------------------------------

    @Test
    fun `a refund mentioning a payment is still a credit`() {
        val result = PaymentParser.parse(
            sourcePackage = PaymentApps.PHONEPE,
            title = "PhonePe",
            text = "₹500 credited to your account as a refund for the ₹500 you paid",
        )

        assertEquals(Direction.CREDIT, result!!.direction)
        assertEquals(Paise(50_000), result.amount)
    }

    @Test
    fun `a payment with a cashback tagline is still a debit`() {
        val result = PaymentParser.parse(
            sourcePackage = PaymentApps.PAYTM,
            title = "Paytm",
            text = "You paid ₹112 to Store. ₹5 cashback credited.",
        )

        assertEquals(Direction.DEBIT, result!!.direction)
        assertEquals(Paise(11_200), result.amount)
    }

    // --- things that must never move the balance ---------------------------------------

    @Test
    fun `failed payment`() {
        assertNull(
            PaymentParser.parse(PaymentApps.GPAY, "Google Pay", "Payment of ₹500 failed"),
        )
    }

    @Test
    fun `incoming money request`() {
        assertNull(
            PaymentParser.parse(PaymentApps.GPAY, "Google Pay", "Rahul is requesting ₹500"),
        )
    }

    @Test
    fun `bill reminder`() {
        assertNull(
            PaymentParser.parse(PaymentApps.PHONEPE, "PhonePe", "Reminder: your bill of ₹1,200 is due"),
        )
    }

    @Test
    fun `marketing offer`() {
        assertNull(
            PaymentParser.parse(PaymentApps.PAYTM, "Paytm", "Get up to ₹50 cashback on your next payment"),
        )
    }

    @Test
    fun `scratch card`() {
        assertNull(
            PaymentParser.parse(PaymentApps.GPAY, "Google Pay", "You won a scratch card worth ₹25"),
        )
    }

    @Test
    fun `autopay warning is not a completed payment`() {
        assertNull(
            PaymentParser.parse(PaymentApps.PHONEPE, "PhonePe", "₹499 will be debited tomorrow for Netflix"),
        )
    }

    @Test
    fun `a bare reference number is not an amount`() {
        assertNull(
            PaymentParser.parse(PaymentApps.GPAY, "Google Pay", "Transaction 4888123 processed"),
        )
    }

    @Test
    fun `a chat message with no direction is ignored`() {
        assertNull(
            PaymentParser.parse(PaymentApps.WHATSAPP, "Priya", "can you send ₹200 later"),
        )
    }

    @Test
    fun `implausibly large amounts are rejected`() {
        assertNull(
            PaymentParser.parse(PaymentApps.GPAY, "Google Pay", "You paid ₹1,00,00,00,000 to Someone"),
        )
    }

    @Test
    fun `zero rupees is ignored`() {
        assertNull(PaymentParser.parse(PaymentApps.GPAY, "Google Pay", "You paid ₹0 to Someone"))
    }

    @Test
    fun `empty notification is ignored`() {
        assertNull(PaymentParser.parse(PaymentApps.GPAY, null, null, null))
    }

    // --- counterparty ------------------------------------------------------------------

    @Test
    fun `counterparty stops before trailing metadata`() {
        val result = PaymentParser.parse(
            sourcePackage = PaymentApps.GPAY,
            title = "Google Pay",
            text = "You paid ₹112 to Rahul Sharma on 01 Sep using UPI",
        )

        assertEquals("Rahul Sharma", result!!.counterparty)
    }

    @Test
    fun `counterparty is null when the text only says your account`() {
        val result = PaymentParser.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "HDFCBK",
            text = "Rs.112.00 is debited from your account XX1234",
        )

        assertEquals(Direction.DEBIT, result!!.direction)
        assertNull(result.counterparty)
    }

    // --- looksFinancial (debug-screen gate) --------------------------------------------

    @Test
    fun `looksFinancial catches bank and payment wording from any app`() {
        assertNotNull(PaymentParser.parse(PaymentApps.GPAY, "Google Pay", "You paid ₹112 to Rahul"))
        assert(PaymentParser.looksFinancial("HDFCBK", "Sent Rs.100.00 From HDFC Bank A/C", null))
        assert(PaymentParser.looksFinancial("Bank of Baroda", "Account credited with INR 100.00 by UPI.", null))
        assert(PaymentParser.looksFinancial(null, "₹500 received", null))
    }

    @Test
    fun `looksFinancial ignores ordinary chatter and blanks`() {
        assert(!PaymentParser.looksFinancial("Mom", "are you coming home for dinner", null))
        assert(!PaymentParser.looksFinancial(null, null, null))
        assert(!PaymentParser.looksFinancial("", "  ", null))
    }
}
