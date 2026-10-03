package com.jynx.scrollite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyLimitOptionsTest {

    @Test
    fun onStep_movesOneStep() {
        assertEquals(35, DailyLimitOptions.stepUp(30))
        assertEquals(25, DailyLimitOptions.stepDown(30))
    }

    @Test
    fun offStep_snapsToNearestStepInThatDirection() {
        assertEquals(10, DailyLimitOptions.stepUp(6))
        assertEquals(5, DailyLimitOptions.stepDown(6))
    }

    @Test
    fun staysWithinBounds() {
        assertEquals(DailyLimitOptions.MIN_MINUTES, DailyLimitOptions.stepDown(DailyLimitOptions.MIN_MINUTES))
        assertEquals(DailyLimitOptions.MAX_MINUTES, DailyLimitOptions.stepUp(DailyLimitOptions.MAX_MINUTES))
    }

    @Test
    fun onlyAHigherLimitCountsAsRaise() {
        assertTrue(DailyLimitOptions.isRaise(currentMinutes = 30, newMinutes = 35))
        assertFalse(DailyLimitOptions.isRaise(currentMinutes = 30, newMinutes = 30))
        assertFalse(DailyLimitOptions.isRaise(currentMinutes = 30, newMinutes = 15))
    }
}
