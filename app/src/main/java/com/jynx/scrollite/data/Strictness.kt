package com.jynx.scrollite.data

/** How much extra time the user allows themselves once the daily limit is hit. */
enum class Strictness(val maxExtensionsPerDay: Int) {
    LOCKED(0),
    STRICT(1),
    FLEXIBLE(3);

    companion object {
        val DEFAULT = STRICT

        fun fromNameOrNull(name: String?): Strictness? = entries.firstOrNull { it.name == name }
    }
}

/**
 * The active strictness plus a looser one scheduled for a later day. Kept
 * free of Android types so the scheduling rule is unit-testable.
 *
 * Stricter (or equal) changes apply immediately; looser ones apply from the
 * next day. Otherwise, after using today's extension, a user could switch to
 * Flexible and keep going — the same impulse the limit exists to interrupt.
 */
data class StrictnessSchedule(
    val active: Strictness,
    val pending: Strictness? = null,
    val pendingFromEpochDay: Int? = null,
) {
    fun effectiveOn(epochDay: Int): Strictness =
        if (pending != null && pendingFromEpochDay != null && epochDay >= pendingFromEpochDay) pending
        else active

    /** The looser strictness still waiting to take effect on [epochDay], if any. */
    fun pendingOn(epochDay: Int): Strictness? =
        if (pending != null && pendingFromEpochDay != null && epochDay < pendingFromEpochDay) pending
        else null

    fun request(requested: Strictness, today: Int): StrictnessSchedule {
        val current = effectiveOn(today)
        return if (requested.maxExtensionsPerDay <= current.maxExtensionsPerDay) {
            StrictnessSchedule(active = requested)
        } else {
            StrictnessSchedule(active = current, pending = requested, pendingFromEpochDay = today + 1)
        }
    }
}
