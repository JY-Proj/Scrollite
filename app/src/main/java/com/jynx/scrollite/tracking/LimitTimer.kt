package com.jynx.scrollite.tracking

/**
 * When the open short-form session will cross the daily limit. The service
 * schedules a check for that moment, so the overlay doesn't depend on the
 * tracked app sending an event right then (a single video playing can be
 * silent for seconds, and the first readings of a session land before its
 * limit state has loaded).
 */
object LimitTimer {

    /**
     * Milliseconds until [todayTotalAtSessionStart] + [openDurationMs] reaches
     * [effectiveLimitMs] (0 if already there), or null when no limit is loaded.
     */
    fun msUntilLimit(todayTotalAtSessionStart: Long, openDurationMs: Long, effectiveLimitMs: Long): Long? {
        if (effectiveLimitMs == Long.MAX_VALUE) return null
        return (effectiveLimitMs - todayTotalAtSessionStart - openDurationMs).coerceAtLeast(0)
    }
}
