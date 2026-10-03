package com.jynx.scrollite.ui.dashboard

/** Compact duration for labels: "0m", "<1m", "47m", "2h", "1h 5m". */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0m"
    val totalMinutes = ms / 60_000
    if (totalMinutes == 0L) return "<1m"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "${minutes}m"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}

/** Spoken form for TalkBack: "1 hour 5 minutes", "less than a minute". */
fun spokenDuration(ms: Long): String {
    if (ms <= 0) return "none"
    val totalMinutes = ms / 60_000
    if (totalMinutes == 0L) return "less than a minute"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val parts = buildList {
        if (hours > 0) add(if (hours == 1L) "1 hour" else "$hours hours")
        if (minutes > 0) add(if (minutes == 1L) "1 minute" else "$minutes minutes")
    }
    return parts.joinToString(" ")
}

/**
 * Picks a gridline step that gives ~2–4 lines for a chart whose tallest value
 * is [maxMs], and the axis top (a whole number of steps at or above it).
 */
fun niceAxis(maxMs: Long): Pair<Long, Long> {
    val minute = 60_000L
    val steps = listOf(5, 10, 15, 30, 60, 120, 180, 240, 360).map { it * minute }
    val step = steps.firstOrNull { maxMs <= it * 4 } ?: steps.last()
    val top = maxOf(step, ((maxMs + step - 1) / step) * step)
    return step to top
}
