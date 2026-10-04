package com.jynx.scrollite.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LimitTimerTest {

    @Test
    fun underLimit_waitsForTheRemainder() {
        assertEquals(
            60_000L,
            LimitTimer.msUntilLimit(todayTotalAtSessionStart = 240_000, openDurationMs = 0, effectiveLimitMs = 300_000),
        )
    }

    @Test
    fun countsTimeAlreadyWatchedThisSession() {
        assertEquals(
            20_000L,
            LimitTimer.msUntilLimit(todayTotalAtSessionStart = 240_000, openDurationMs = 40_000, effectiveLimitMs = 300_000),
        )
    }

    @Test
    fun alreadyOver_checksImmediately() {
        assertEquals(
            0L,
            LimitTimer.msUntilLimit(todayTotalAtSessionStart = 400_000, openDurationMs = 1_000, effectiveLimitMs = 300_000),
        )
    }

    @Test
    fun noLimitLoaded_schedulesNothing() {
        assertNull(LimitTimer.msUntilLimit(todayTotalAtSessionStart = 0, openDurationMs = 0, effectiveLimitMs = Long.MAX_VALUE))
    }
}
