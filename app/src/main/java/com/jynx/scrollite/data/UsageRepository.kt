package com.jynx.scrollite.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/** Start (inclusive) and end (exclusive) of a local calendar day, in epoch millis. */
data class DayBounds(val startMs: Long, val endMs: Long) {
    companion object {
        /** Uses the zone's real midnight, so DST days come out 23h or 25h long. */
        fun of(date: LocalDate, zone: ZoneId): DayBounds = DayBounds(
            startMs = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }
}

/**
 * Reads and writes usage sessions. "Today" is the device's local calendar
 * day, so a trip across timezones can make one day slightly short or long.
 */
class UsageRepository(
    private val dao: UsageSessionDao,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val shortFormNames = Section.shortForm.map { it.name }

    suspend fun record(session: UsageSession) {
        if (session.durationMs > 0) dao.insert(session)
    }

    /** Short-form time (what the daily limit counts) on [date]. */
    suspend fun shortFormTotalOn(date: LocalDate = today()): Long {
        val day = DayBounds.of(date, zone())
        return dao.totalBetween(day.startMs, day.endMs, shortFormNames)
    }

    /**
     * Live short-form total for the day this is called on. Doesn't roll over
     * at midnight on its own — re-collect after a day change.
     */
    fun observeShortFormTotalToday(): Flow<Long> {
        val day = DayBounds.of(today(), zone())
        return dao.observeTotalBetween(day.startMs, day.endMs, shortFormNames)
    }

    /** Live Monday–Sunday usage for the week starting [weekStart]; updates as sessions are saved. */
    fun observeWeek(weekStart: LocalDate): Flow<WeeklyUsage> {
        val zone = zone()
        val from = DayBounds.of(weekStart, zone).startMs
        val to = DayBounds.of(weekStart.plusDays(6), zone).endMs
        return dao.observeSessionsBetween(from, to).map { WeeklyUsage.from(it, weekStart, zone) }
    }

    /** Apps with any recorded usage, in their fixed [Platform] order. */
    fun observeTrackedPlatforms(): Flow<List<Platform>> =
        dao.observeTrackedPlatforms().map { platforms -> platforms.sortedBy { it.ordinal } }

    fun today(): LocalDate = LocalDate.now(zone())
}
