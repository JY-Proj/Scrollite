package com.jynx.scrollite.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayBoundsTest {

    private val hourMs = 3_600_000L

    @Test
    fun normalDay_is24Hours() {
        val day = DayBounds.of(LocalDate.of(2026, 10, 2), ZoneId.of("America/New_York"))
        assertEquals(24 * hourMs, day.endMs - day.startMs)
    }

    @Test
    fun springForwardDay_is23Hours() {
        val day = DayBounds.of(LocalDate.of(2026, 3, 8), ZoneId.of("America/New_York"))
        assertEquals(23 * hourMs, day.endMs - day.startMs)
    }

    @Test
    fun fallBackDay_is25Hours() {
        val day = DayBounds.of(LocalDate.of(2026, 11, 1), ZoneId.of("America/New_York"))
        assertEquals(25 * hourMs, day.endMs - day.startMs)
    }

    @Test
    fun consecutiveDays_touchWithoutGapOrOverlap() {
        val zone = ZoneId.of("America/New_York")
        val today = DayBounds.of(LocalDate.of(2026, 10, 2), zone)
        val tomorrow = DayBounds.of(LocalDate.of(2026, 10, 3), zone)
        assertEquals(today.endMs, tomorrow.startMs)
    }
}
