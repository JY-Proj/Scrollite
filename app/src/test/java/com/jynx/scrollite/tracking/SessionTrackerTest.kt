package com.jynx.scrollite.tracking

import com.jynx.scrollite.data.Platform
import com.jynx.scrollite.data.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTrackerTest {

    private class FakeClock(var wallMs: Long = 1_000_000L, var elapsedMs: Long = 50_000L) : Clock {
        override fun now() = Moment(wallMs, elapsedMs)
        fun advance(ms: Long) { wallMs += ms; elapsedMs += ms }
    }

    private val clock = FakeClock()
    private val tracker = SessionTracker(clock)

    @Test
    fun switchingSection_closesThePreviousOne() {
        tracker.switchTo(Platform.INSTAGRAM, Section.OTHER)
        clock.advance(60_000)

        val closed = tracker.switchTo(Platform.INSTAGRAM, Section.SHORT_VIDEO)!!

        assertEquals(Section.OTHER, closed.section)
        assertEquals(1_000_000L, closed.startMs)
        assertEquals(60_000L, closed.durationMs)
        assertEquals(Section.SHORT_VIDEO, tracker.currentSection)
    }

    @Test
    fun sameSectionAgain_isNoOp() {
        tracker.switchTo(Platform.INSTAGRAM, Section.SHORT_VIDEO)
        clock.advance(5_000)

        assertNull(tracker.switchTo(Platform.INSTAGRAM, Section.SHORT_VIDEO))
        assertEquals(5_000L, tracker.openDurationMs())
    }

    @Test
    fun switchDatedFromFirstReading_notFromWhenItWasConfirmed() {
        tracker.switchTo(Platform.INSTAGRAM, Section.SHORT_VIDEO)
        clock.advance(10_000)
        val firstSeen = tracker.now()
        clock.advance(500) // debounce delay

        val closed = tracker.switchTo(Platform.INSTAGRAM, Section.OTHER, at = firstSeen)!!

        assertEquals(10_000L, closed.durationMs)
        assertEquals(500L, tracker.openDurationMs())
    }

    @Test
    fun durationComesFromElapsedClock_soWallClockChangesDontDistortIt() {
        tracker.switchTo(Platform.INSTAGRAM, Section.SHORT_VIDEO)
        clock.elapsedMs += 30_000
        clock.wallMs += 3_600_000 // user moved the phone's clock forward an hour

        val closed = tracker.stop()!!

        assertEquals(30_000L, closed.durationMs)
    }

    @Test
    fun restart_closesAndReopensSameSection() {
        tracker.switchTo(Platform.INSTAGRAM, Section.SHORT_VIDEO)
        clock.advance(20_000)

        val closed = tracker.restart()!!

        assertEquals(20_000L, closed.durationMs)
        assertEquals(Section.SHORT_VIDEO, tracker.currentSection)
        assertEquals(0L, tracker.openDurationMs())
    }

    @Test
    fun stopWithNothingOpen_returnsNull() {
        assertNull(tracker.stop())
        assertNull(tracker.restart())
        assertEquals(0L, tracker.openDurationMs())
    }
}
