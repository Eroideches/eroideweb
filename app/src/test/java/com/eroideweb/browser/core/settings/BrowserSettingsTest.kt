package com.eroideweb.browser.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSettingsTest {

    @Test
    fun `il tema di sistema segue il telefono`() {
        assertTrue(AppTheme.SYSTEM.isDark(systemDark = true))
        assertFalse(AppTheme.SYSTEM.isDark(systemDark = false))
        assertTrue(AppTheme.DARK.isDark(systemDark = false))
        assertFalse(AppTheme.LIGHT.isDark(systemDark = true))
    }

    @Test
    fun `valori sconosciuti ricadono sui default`() {
        assertEquals(AppTheme.SYSTEM, AppTheme.fromId("VIOLA"))
        assertEquals(ReaderTheme.AUTO, ReaderTheme.fromId(null))
        assertEquals(ReaderTheme.SEPIA, ReaderTheme.fromId("SEPIA"))
    }

    @Test
    fun `la dimensione del testo resta tra 50 e 200`() {
        assertEquals(50, clampTextZoom(10))
        assertEquals(200, clampTextZoom(500))
        assertEquals(130, clampTextZoom(130))
    }
}
