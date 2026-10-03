package com.jynx.scrollite.tracking

import android.os.SystemClock
import com.jynx.scrollite.data.Platform
import com.jynx.scrollite.data.Section
import com.jynx.scrollite.data.UsageSession

/** A point in time on both clocks: wall time for storage, elapsed time for durations. */
data class Moment(val wallMs: Long, val elapsedMs: Long)

interface Clock {
    fun now(): Moment
}

object AndroidClock : Clock {
    override fun now() = Moment(System.currentTimeMillis(), SystemClock.elapsedRealtime())
}

/**
 * Tracks the one app + section the user is currently in and turns each
 * continuous stretch into a [UsageSession] when it ends. Pure bookkeeping —
 * callers decide when sections change and persist what comes back.
 *
 * Durations come from the elapsed clock, so changing the phone's time
 * mid-session can't stretch or shrink a session; only the start is wall time.
 */
class SessionTracker(private val clock: Clock = AndroidClock) {

    private data class Open(val platform: Platform, val section: Section, val start: Moment)

    private var open: Open? = null

    val currentSection: Section? get() = open?.section

    val currentPlatform: Platform? get() = open?.platform

    /** Wall-clock start of the open session, or null when nothing is tracked. */
    val openSinceWallMs: Long? get() = open?.start?.wallMs

    fun now(): Moment = clock.now()

    /**
     * Starts tracking [platform]/[section] from [at]. If a different one was
     * open, it's closed at [at] and returned; returns null if nothing changed.
     */
    fun switchTo(platform: Platform, section: Section, at: Moment = now()): UsageSession? {
        val current = open
        if (current != null && current.platform == platform && current.section == section) return null
        open = Open(platform, section, at)
        return current?.closeAt(at)
    }

    /** Ends tracking; returns the session that was open, if any. */
    fun stop(at: Moment = now()): UsageSession? {
        val current = open ?: return null
        open = null
        return current.closeAt(at)
    }

    /**
     * Closes the open session at [at] and immediately reopens the same one —
     * used at midnight so each day's session starts fresh.
     */
    fun restart(at: Moment = now()): UsageSession? {
        val current = open ?: return null
        open = current.copy(start = at)
        return current.closeAt(at)
    }

    /** How long the open session has run so far (0 when nothing is open). */
    fun openDurationMs(at: Moment = now()): Long =
        open?.let { (at.elapsedMs - it.start.elapsedMs).coerceAtLeast(0) } ?: 0L

    private fun Open.closeAt(at: Moment) = UsageSession(
        startMs = start.wallMs,
        endMs = start.wallMs + (at.elapsedMs - start.elapsedMs).coerceAtLeast(0),
        platform = platform,
        section = section,
    )
}
