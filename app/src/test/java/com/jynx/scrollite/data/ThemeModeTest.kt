package com.jynx.scrollite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun system_followsPhone() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemIsDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemIsDark = false))
    }

    @Test
    fun explicitChoice_overridesPhone() {
        assertFalse(ThemeMode.LIGHT.isDark(systemIsDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemIsDark = false))
    }

    @Test
    fun storedName_roundTrips_unknownIsNull() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromNameOrNull(it.name)) }
        assertNull(ThemeMode.fromNameOrNull("SEPIA"))
        assertNull(ThemeMode.fromNameOrNull(null))
    }
}
