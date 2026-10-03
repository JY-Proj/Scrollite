package com.jynx.scrollite.ui.screentime

/**
 * How the "are you sure?" for loosening a setting sounds. Never a block,
 * just a little sadder each time it happens on the same day.
 */
enum class LooseningTone {
    /** First loosening today: plain question. */
    FIRST,

    /** Second: notes that it's happening again. */
    AGAIN,

    /** Third and later: openly a bit sad about it. */
    SAD;

    companion object {
        /** [looseningsToday] counts the confirmed loosenings before this one. */
        fun forCount(looseningsToday: Int): LooseningTone = when {
            looseningsToday <= 0 -> FIRST
            looseningsToday == 1 -> AGAIN
            else -> SAD
        }
    }
}
