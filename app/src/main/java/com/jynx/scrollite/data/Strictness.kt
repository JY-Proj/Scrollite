package com.jynx.scrollite.data

/** How much extra time the user allows themselves once the daily limit is hit. */
enum class Strictness(val maxExtensionsPerDay: Int) {
    LOCKED(0),
    STRICT(1),
    FLEXIBLE(3);

    /** Allows more extensions than [other]; switching to it asks for confirmation. */
    fun isLooserThan(other: Strictness): Boolean = maxExtensionsPerDay > other.maxExtensionsPerDay

    companion object {
        val DEFAULT = STRICT

        fun fromNameOrNull(name: String?): Strictness? = entries.firstOrNull { it.name == name }
    }
}
