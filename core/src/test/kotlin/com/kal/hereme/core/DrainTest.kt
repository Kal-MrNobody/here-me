package com.kal.hereme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrainTest {

    @Test
    fun `a small bite drains almost instantly`() {
        val duration = Drain.durationMillis(
            amount = Paise.ofRupees(20),
            balanceBefore = Paise.ofRupees(5_000),
        )
        assertTrue("expected a near-minimum drain, got $duration", duration < 400L)
    }

    @Test
    fun `a big bite takes noticeably longer than a small one`() {
        val small = Drain.durationMillis(Paise.ofRupees(20), Paise.ofRupees(5_000))
        val big = Drain.durationMillis(Paise.ofRupees(2_000), Paise.ofRupees(5_000))

        assertTrue("big spends must feel longer: $small vs $big", big > small * 2)
    }

    @Test
    fun `spending everything runs the full animation`() {
        assertEquals(
            Drain.MAX_MILLIS,
            Drain.durationMillis(Paise.ofRupees(5_000), Paise.ofRupees(5_000)),
        )
    }

    @Test
    fun `spending against an empty balance counts as taking everything`() {
        assertEquals(1.0, Drain.biteFraction(Paise.ofRupees(50), Paise.ZERO), 0.0)
        assertEquals(1.0, Drain.biteFraction(Paise.ofRupees(50), Paise.ofRupees(-10)), 0.0)
    }

    @Test
    fun `overspending is clamped rather than running away`() {
        val duration = Drain.durationMillis(Paise.ofRupees(9_000), Paise.ofRupees(5_000))
        assertEquals(Drain.MAX_MILLIS, duration)
    }

    @Test
    fun `tick count stays in a sane range`() {
        assertTrue(Drain.tickCount(Drain.MIN_MILLIS) >= 1)
        assertTrue(Drain.tickCount(Drain.MAX_MILLIS) <= 24)
    }
}
