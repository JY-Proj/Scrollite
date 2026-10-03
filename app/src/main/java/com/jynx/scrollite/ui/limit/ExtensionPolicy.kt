package com.jynx.scrollite.ui.limit

/**
 * Rules for the "Need more time?" escape hatch on the limit overlay. How many
 * extensions a day are allowed comes from the user's Strictness setting.
 */
object ExtensionPolicy {
    /** Pause before the extra-time options unlock — enough to get past the impulse. */
    const val WAIT_SECONDS = 10

    const val SHORT_OPTION_MINUTES = 5
    const val LONG_OPTION_MINUTES = 15

    fun remaining(extensionsUsedToday: Int, maxExtensionsPerDay: Int): Int =
        (maxExtensionsPerDay - extensionsUsedToday).coerceAtLeast(0)
}
