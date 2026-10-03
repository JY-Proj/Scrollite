package com.jynx.scrollite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class WeeklyUsageTest {

    private val zone = ZoneId.of("America/New_York")
    private val monday = LocalDate.of(2026, 9, 28)
    private val min = 60_000L

    private fun session(
        date: LocalDate,
        startMin: Long,
        endMin: Long,
        section: Section,
        platform: Platform = Platform.INSTAGRAM,
    ) = UsageSession(
        startMs = DayBounds.of(date, zone).startMs + startMin * min,
        endMs = DayBounds.of(date, zone).startMs + endMin * min,
        platform = platform,
        section = section,
    )

    @Test
    fun weekStartsOnMonday() {
        assertEquals(monday, WeeklyUsage.weekStartOf(LocalDate.of(2026, 10, 2))) // Friday
        assertEquals(monday, WeeklyUsage.weekStartOf(monday))
        assertEquals(monday, WeeklyUsage.weekStartOf(LocalDate.of(2026, 10, 4))) // Sunday
    }

    @Test
    fun alwaysSevenDays_zeroWhereNothingRecorded() {
        val week = WeeklyUsage.from(emptyList(), monday, zone)

        assertEquals(7, week.dates.size)
        assertEquals(DayOfWeek.MONDAY, week.dates.first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, week.dates.last().dayOfWeek)
        assertEquals(List(7) { 0L }, week.dailyTotals())
    }

    @Test
    fun groupsByAnyKey() {
        val wednesday = monday.plusDays(2)
        val week = WeeklyUsage.from(
            listOf(
                session(wednesday, 60, 90, Section.SHORT_VIDEO),
                session(wednesday, 90, 100, Section.OTHER),
                session(wednesday, 100, 120, Section.SHORT_VIDEO, Platform.TIKTOK),
            ),
            monday, zone,
        )

        val byApp = week.dailyTotalsBy({ it.platform })
        assertEquals(40 * min, byApp.getValue(Platform.INSTAGRAM)[2])
        assertEquals(20 * min, byApp.getValue(Platform.TIKTOK)[2])
        assertEquals(50 * min, week.totalMs { it.section.isShortForm })
    }

    @Test
    fun sessionAcrossMidnight_countsOnBothDays() {
        // Tuesday 23:50 → Wednesday 00:10
        val tuesday = monday.plusDays(1)
        val week = WeeklyUsage.from(listOf(session(tuesday, 23 * 60 + 50, 24 * 60 + 10, Section.SHORT_VIDEO)), monday, zone)

        val daily = week.dailyTotals()
        assertEquals(10 * min, daily[1])
        assertEquals(10 * min, daily[2])
    }

    @Test
    fun sessionStartingLastWeek_onlyCountsTheMondayPart() {
        // Sunday 23:30 (previous week) → Monday 00:20
        val week = WeeklyUsage.from(listOf(session(monday.minusDays(1), 23 * 60 + 30, 24 * 60 + 20, Section.OTHER)), monday, zone)

        assertEquals(20 * min, week.dailyTotals()[0])
        assertEquals(20 * min, week.totalMs())
    }

    @Test
    fun summary_averagesOverDaysSoFar_andComparesToLastWeek() {
        // This week: 60 min on Monday, today is Tuesday → average 30 min/day
        val thisWeek = WeeklyUsage.from(listOf(session(monday, 60, 120, Section.SHORT_VIDEO)), monday, zone)
        // Last week: 280 min total → 40 min/day
        val lastMonday = monday.minusWeeks(1)
        val lastWeek = WeeklyUsage.from(listOf(session(lastMonday, 0, 280, Section.SHORT_VIDEO)), lastMonday, zone)

        val summary = WeekSummary.of(thisWeek, lastWeek, today = monday.plusDays(1))

        assertEquals(30 * min, summary.dailyAverageMs)
        assertEquals(-25, summary.changePercent)
    }

    @Test
    fun summary_noComparisonWithoutLastWeek() {
        val thisWeek = WeeklyUsage.from(listOf(session(monday, 0, 10, Section.OTHER)), monday, zone)
        val empty = WeeklyUsage.from(emptyList(), monday.minusWeeks(1), zone)

        assertNull(WeekSummary.of(thisWeek, empty, today = monday).changePercent)
    }

    @Test
    fun summary_pastWeekAveragesOverSevenDays() {
        val week = WeeklyUsage.from(listOf(session(monday, 0, 70, Section.OTHER)), monday, zone)
        val empty = WeeklyUsage.from(emptyList(), monday.minusWeeks(1), zone)

        assertEquals(10 * min, WeekSummary.of(week, empty, today = monday.plusWeeks(2)).dailyAverageMs)
    }
}
