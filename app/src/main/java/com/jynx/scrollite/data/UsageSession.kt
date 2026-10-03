package com.jynx.scrollite.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One continuous stretch of time in a single app + section, e.g. 08:20–08:41
 * in Instagram Reels. The raw record every usage metric is derived from —
 * daily totals, per-app or global limits, time-of-day patterns — so it
 * stores facts only (no limit or policy decisions).
 *
 * Times are wall-clock epoch millis. A session may span midnight; queries
 * clip it to the day being asked about.
 */
@Entity(
    tableName = "usage_sessions",
    indices = [Index("startMs"), Index("endMs")],
)
data class UsageSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long,
    val platform: Platform,
    val section: Section,
) {
    val durationMs: Long get() = endMs - startMs
}
