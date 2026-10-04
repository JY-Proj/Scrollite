package com.jynx.scrollite.tracking

import com.jynx.scrollite.tracking.Throttle.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class ThrottleTest {

    private val throttle = Throttle(intervalMs = 300)

    @Test
    fun firstRequest_runsNow() {
        assertEquals(Decision.RunNow, throttle.request(nowMs = 1_000))
    }

    @Test
    fun requestInsideWindow_becomesOneTrailingRunAtWindowEnd() {
        throttle.onRan(nowMs = 1_000)

        assertEquals(Decision.RunLater(delayMs = 200), throttle.request(nowMs = 1_100))
        // Further requests in the same window are covered by that one run
        assertEquals(Decision.AlreadyScheduled, throttle.request(nowMs = 1_150))
        assertEquals(Decision.AlreadyScheduled, throttle.request(nowMs = 1_290))
    }

    @Test
    fun lastRequestIsNeverDropped_trailingRunHappensEvenWithNoLaterRequests() {
        throttle.onRan(nowMs = 1_000)
        // The final event of a burst (e.g. leaving Reels) lands inside the window...
        val decision = throttle.request(nowMs = 1_250)

        // ...and still gets a run scheduled, rather than being skipped
        assertEquals(Decision.RunLater(delayMs = 50), decision)
    }

    @Test
    fun afterTrailingRun_nextWindowStartsFromIt() {
        throttle.onRan(nowMs = 1_000)
        throttle.request(nowMs = 1_100)
        throttle.onRan(nowMs = 1_300) // the trailing run fires

        assertEquals(Decision.RunLater(delayMs = 250), throttle.request(nowMs = 1_350))
    }

    @Test
    fun requestAfterWindow_runsNow() {
        throttle.onRan(nowMs = 1_000)

        assertEquals(Decision.RunNow, throttle.request(nowMs = 1_300))
    }

    @Test
    fun cancelledTrailingRun_canBeScheduledAgain() {
        throttle.onRan(nowMs = 1_000)
        throttle.request(nowMs = 1_100)
        throttle.cancelTrailing()

        assertEquals(Decision.RunLater(delayMs = 100), throttle.request(nowMs = 1_200))
    }
}
