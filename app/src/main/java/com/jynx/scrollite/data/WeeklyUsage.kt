package com.jynx.scrollite.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Time spent on one day in one app + section, after clipping sessions to that day. */
data class UsageSlice(
    val date: LocalDate,
    val platform: Platform,
    val section: Section,
    val ms: Long,
)

/**
 * One Monday–Sunday week of usage as generic slices. Views group them however
 * they need (by app, by section, filtered to one app) instead of the data
 * shape deciding which breakdowns exist.
 */
class WeeklyUsage(val weekStart: LocalDate, val slices: List<UsageSlice>) {

    val dates: List<LocalDate> = (0L until 7L).map { weekStart.plusDays(it) }

    fun totalMs(filter: (UsageSlice) -> Boolean = { true }): Long =
        slices.filter(filter).sumOf { it.ms }

    /** Seven per-day totals (Monday first) of the slices matching [filter]. */
    fun dailyTotals(filter: (UsageSlice) -> Boolean = { true }): List<Long> {
        val byDate = slices.filter(filter).groupBy { it.date }
        return dates.map { date -> byDate[date].orEmpty().sumOf { it.ms } }
    }

    /** Seven per-day totals for each distinct [key] among the slices matching [filter]. */
    fun <K> dailyTotalsBy(key: (UsageSlice) -> K, filter: (UsageSlice) -> Boolean = { true }): Map<K, List<Long>> =
        slices.filter(filter).groupBy(key).mapValues { (_, group) ->
            val byDate = group.groupBy { it.date }
            dates.map { date -> byDate[date].orEmpty().sumOf { it.ms } }
        }

    companion object {
        /** Weeks start on Monday (ISO 8601). */
        fun weekStartOf(date: LocalDate): LocalDate =
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /**
         * Buckets [sessions] into the seven days from [weekStart]. A session
         * crossing midnight (or the week's edges) is clipped, so each day only
         * gets the part that happened on it.
         */
        fun from(sessions: List<UsageSession>, weekStart: LocalDate, zone: ZoneId): WeeklyUsage {
            val totals = linkedMapOf<Triple<LocalDate, Platform, Section>, Long>()
            for (offset in 0L until 7L) {
                val date = weekStart.plusDays(offset)
                val day = DayBounds.of(date, zone)
                for (session in sessions) {
                    val overlap = minOf(session.endMs, day.endMs) - maxOf(session.startMs, day.startMs)
                    if (overlap <= 0) continue
                    val key = Triple(date, session.platform, session.section)
                    totals[key] = (totals[key] ?: 0L) + overlap
                }
            }
            return WeeklyUsage(weekStart, totals.map { (key, ms) -> UsageSlice(key.first, key.second, key.third, ms) })
        }
    }
}

/**
 * Headline numbers for a week: the daily average (over the days that have
 * happened so far, like Screen Time) and the change from the previous week's
 * average. [changePercent] is null when last week had nothing to compare to.
 */
data class WeekSummary(val dailyAverageMs: Long, val changePercent: Int?) {
    companion object {
        fun of(
            week: WeeklyUsage,
            previousWeek: WeeklyUsage,
            today: LocalDate,
            filter: (UsageSlice) -> Boolean = { true },
        ): WeekSummary {
            val daysSoFar = week.dates.count { !it.isAfter(today) }
            val average = if (daysSoFar == 0) 0L else week.totalMs(filter) / daysSoFar
            val previousAverage = previousWeek.totalMs(filter) / 7
            val change = if (previousAverage == 0L) null
            else ((average - previousAverage) * 100.0 / previousAverage).roundToInt()
            return WeekSummary(average, change)
        }
    }
}
