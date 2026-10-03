package com.jynx.scrollite.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationFormatTest {

    private val min = 60_000L

    @Test
    fun compactFormat() {
        assertEquals("0m", formatDuration(0))
        assertEquals("<1m", formatDuration(30_000))
        assertEquals("47m", formatDuration(47 * min))
        assertEquals("2h", formatDuration(120 * min))
        assertEquals("1h 5m", formatDuration(65 * min))
    }

    @Test
    fun spokenFormat() {
        assertEquals("less than a minute", spokenDuration(10_000))
        assertEquals("1 hour 5 minutes", spokenDuration(65 * min))
        assertEquals("2 hours", spokenDuration(120 * min))
    }

    @Test
    fun niceAxis_coversTheMaxWithAFewGridlines() {
        listOf(0L, 3 * min, 30 * min, 47 * min, 95 * min, 5 * 60 * min).forEach { max ->
            val (step, top) = niceAxis(max)
            assertTrue("top $top must cover $max", top >= max)
            assertTrue("too many gridlines for $max", top / step <= 4)
            assertEquals(0L, top % step)
        }
    }
}
