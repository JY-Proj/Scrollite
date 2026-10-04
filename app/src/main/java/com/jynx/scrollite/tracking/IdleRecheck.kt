package com.jynx.scrollite.tracking

/**
 * Detection only runs when the tracked app sends events. If a screen change
 * is followed by silence (e.g. the last reading caught Reels mid-transition
 * to Home, then nothing moves), timing would stay on the stale section. This
 * re-checks after [intervalMs] of quiet while a session is open; any normal
 * detection pushes it back, so during active use it never fires.
 *
 * Scheduling is passed in (a Handler in the service) to keep this testable.
 */
class IdleRecheck(
    private val intervalMs: Long,
    private val postDelayed: (Runnable, Long) -> Unit,
    private val removeCallbacks: (Runnable) -> Unit,
    onRecheck: () -> Unit,
) {
    private val recheck = Runnable { onRecheck() }

    /** Call after every detection; schedules the next re-check only while a session is open. */
    fun afterDetection(sessionOpen: Boolean) {
        removeCallbacks(recheck)
        if (sessionOpen) postDelayed(recheck, intervalMs)
    }

    /** Call whenever tracking stops, so nothing runs once the session is over. */
    fun stop() {
        removeCallbacks(recheck)
    }
}
