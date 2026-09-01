package com.kal.hereme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `groups digits the Indian way`() {
        assertEquals("112", Money.groupIndian("112"))
        assertEquals("4,888", Money.groupIndian("4888"))
        assertEquals("1,00,000", Money.groupIndian("100000"))
        assertEquals("1,23,45,678", Money.groupIndian("12345678"))
    }

    @Test
    fun `hides paise when the amount is whole rupees`() {
        assertEquals("₹4,888", Money.format(Paise(488_800)))
        assertEquals("₹112.50", Money.format(Paise(11_250)))
        assertEquals("₹0", Money.format(Paise.ZERO))
    }

    @Test
    fun `formats an overdrawn balance as negative`() {
        assertEquals("-₹112", Money.format(Paise(-11_200)))
    }

    @Test
    fun `parses amounts with grouping and decimals`() {
        assertEquals(Paise(11_200), Money.parseAmount("112"))
        assertEquals(Paise(11_250), Money.parseAmount("112.50"))
        assertEquals(Paise(11_250), Money.parseAmount("112.5"))
        assertEquals(Paise(1_12_000_50), Money.parseAmount("1,12,000.50"))
    }

    @Test
    fun `rejects junk and implausible amounts`() {
        assertNull(Money.parseAmount(""))
        assertNull(Money.parseAmount("abc"))
        assertNull(Money.parseAmount("112.345"))
        assertNull(Money.parseAmount("1000000000"))
    }

    @Test
    fun `arithmetic stays in whole paise`() {
        assertEquals(Paise(38_800), Paise(50_000) - Paise(11_200))
        assertEquals(Paise(61_200), Paise(50_000) + Paise(11_200))
        assertEquals(Paise(11_200), Paise(-11_200).absolute)
    }
}
