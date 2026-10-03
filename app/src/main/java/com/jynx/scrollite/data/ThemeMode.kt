package com.jynx.scrollite.data

/** The user's light/dark choice for Scrollite, independent of the phone's setting unless [SYSTEM]. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        val DEFAULT = SYSTEM

        fun fromNameOrNull(name: String?): ThemeMode? = entries.firstOrNull { it.name == name }
    }
}
