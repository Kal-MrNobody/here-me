package com.kal.hereme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTest {

    @Test
    fun `applies a run of debits and credits in order`() {
        val opening = Paise.ofRupees(5_000)
        val result = Ledger.applyAll(
            opening,
            listOf(Paise(-11_200), Paise(-25_000), Paise(50_000)),
        )
        assertEquals(Paise(500_000 - 11_200 - 25_000 + 50_000), result)
    }

    @Test
    fun `balance is allowed to go negative`() {
        val result = Ledger.applyAll(Paise.ofRupees(100), listOf(Paise.ofRupees(-150)))
        assertTrue(result.isNegative)
        assertEquals(Paise.ofRupees(-50), result)
    }

    @Test
    fun `reconciliation delta moves the tracked balance onto the real one`() {
        val tracked = Paise.ofRupees(4_888)
        val actual = Paise.ofRupees(4_500)

        val delta = Ledger.reconciliationDelta(tracked, actual)

        assertEquals(Paise.ofRupees(-388), delta)
        assertEquals(actual, tracked + delta)
        assertEquals(Paise.ofRupees(388), Ledger.driftMagnitude(tracked, actual))
    }

    @Test
    fun `a never-confirmed balance is stale immediately`() {
        assertTrue(Staleness.isStale(lastReconciledAt = 0L, now = 1_000L))
    }

    @Test
    fun `staleness turns on at the threshold`() {
        val now = 10L * DAY
        assertFalse(Staleness.isStale(lastReconciledAt = now - 2 * DAY, now = now))
        assertTrue(Staleness.isStale(lastReconciledAt = now - 3 * DAY, now = now))
        assertEquals(3L, Staleness.daysSince(lastReconciledAt = now - 3 * DAY, now = now))
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
