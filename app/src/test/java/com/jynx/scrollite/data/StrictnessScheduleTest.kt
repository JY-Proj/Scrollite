package com.jynx.scrollite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrictnessScheduleTest {

    private val today = 20_000

    @Test
    fun stricterChange_appliesImmediately() {
        val next = StrictnessSchedule(Strictness.FLEXIBLE).request(Strictness.LOCKED, today)

        assertEquals(Strictness.LOCKED, next.effectiveOn(today))
        assertNull(next.pendingOn(today))
    }

    @Test
    fun looserChange_waitsUntilTomorrow() {
        val next = StrictnessSchedule(Strictness.STRICT).request(Strictness.FLEXIBLE, today)

        assertEquals(Strictness.STRICT, next.effectiveOn(today))
        assertEquals(Strictness.FLEXIBLE, next.pendingOn(today))
        assertEquals(Strictness.FLEXIBLE, next.effectiveOn(today + 1))
        assertNull(next.pendingOn(today + 1))
    }

    @Test
    fun changingBackBeforeTomorrow_cancelsPendingLooserChange() {
        val next = StrictnessSchedule(Strictness.STRICT)
            .request(Strictness.FLEXIBLE, today)
            .request(Strictness.STRICT, today)

        assertEquals(Strictness.STRICT, next.effectiveOn(today + 1))
        assertNull(next.pendingOn(today))
    }

    @Test
    fun pendingChange_becomesTheBaseOnceItsDayArrives() {
        // Flexible was scheduled yesterday and is now active, so going to Strict is stricter
        val scheduled = StrictnessSchedule(Strictness.STRICT).request(Strictness.FLEXIBLE, today - 1)
        val next = scheduled.request(Strictness.STRICT, today)

        assertEquals(Strictness.STRICT, next.effectiveOn(today))
        assertNull(next.pendingOn(today))
    }

    @Test
    fun loosenFromLocked_staysLockedToday() {
        val next = StrictnessSchedule(Strictness.LOCKED).request(Strictness.STRICT, today)

        assertEquals(Strictness.LOCKED, next.effectiveOn(today))
        assertEquals(Strictness.STRICT, next.pendingOn(today))
    }
}
