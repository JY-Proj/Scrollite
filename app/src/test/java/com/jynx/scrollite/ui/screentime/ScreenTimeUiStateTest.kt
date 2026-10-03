package com.jynx.scrollite.ui.screentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimeUiStateTest {

    private val minute = 60_000L

    @Test
    fun underLimit_countsMinutesLeft_noHeadsUp() {
        val state = ScreenTimeUiState.of(totalMillis = 10 * minute, limitMinutes = 30)

        assertEquals(20L, state.minutesLeft)
        assertFalse(state.limitReached)
        assertFalse(state.showHeadsUp)
    }

    @Test
    fun lastFifthOfLimit_showsHeadsUp() {
        assertTrue(ScreenTimeUiState.of(totalMillis = 24 * minute, limitMinutes = 30).showHeadsUp)
        assertFalse(ScreenTimeUiState.of(totalMillis = 23 * minute, limitMinutes = 30).showHeadsUp)
    }

    @Test
    fun atLimit_reached_noHeadsUp_zeroLeft() {
        val state = ScreenTimeUiState.of(totalMillis = 31 * minute, limitMinutes = 30)

        assertTrue(state.limitReached)
        assertFalse(state.showHeadsUp)
        assertEquals(0L, state.minutesLeft)
    }
}
