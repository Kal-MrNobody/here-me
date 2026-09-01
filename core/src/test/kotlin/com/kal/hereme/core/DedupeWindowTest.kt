package com.kal.hereme.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupeWindowTest {

    private val window = DedupeWindow(windowMillis = 90_000L)

    private fun payment(
        rupees: Long,
        direction: Direction = Direction.DEBIT,
        pkg: String = PaymentApps.GPAY,
    ) = ParsedPayment(
        amount = Paise.ofRupees(rupees),
        direction = direction,
        counterparty = "Store",
        sourcePackage = pkg,
        confidence = 1.0f,
    )

    @Test
    fun `the same payment re-posted inside the window is dropped`() {
        assertTrue(window.accept("key-1", payment(112), now = 0L))
        assertFalse(window.accept("key-1", payment(112), now = 5_000L))
    }

    @Test
    fun `a repost under a different notification key is still dropped`() {
        assertTrue(window.accept("key-1", payment(112), now = 0L))
        assertFalse(window.accept("key-2", payment(112), now = 5_000L))
    }

    @Test
    fun `the same amount after the window is a new payment`() {
        assertTrue(window.accept("key-1", payment(112), now = 0L))
        assertTrue(window.accept("key-2", payment(112), now = 95_000L))
    }

    @Test
    fun `different amounts both go through`() {
        assertTrue(window.accept("key-1", payment(112), now = 0L))
        assertTrue(window.accept("key-2", payment(250), now = 1_000L))
    }

    @Test
    fun `a debit and a credit of the same amount are different events`() {
        assertTrue(window.accept("key-1", payment(500, Direction.DEBIT), now = 0L))
        assertTrue(window.accept("key-2", payment(500, Direction.CREDIT), now = 1_000L))
    }

    @Test
    fun `the same amount from two apps is not collapsed`() {
        assertTrue(window.accept("key-1", payment(112, pkg = PaymentApps.GPAY), now = 0L))
        assertTrue(window.accept("key-2", payment(112, pkg = PaymentApps.PHONEPE), now = 1_000L))
    }
}
