package com.krishinirnay.data

import com.krishinirnay.core.data.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test fun fromStored_knownValues() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromStored("DARK"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStored("LIGHT"))
    }

    @Test fun fromStored_nullOrCorrupt_fallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("purple"))
    }

    @Test fun isDark_followsSystemOnlyForSystemMode() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
    }
}
