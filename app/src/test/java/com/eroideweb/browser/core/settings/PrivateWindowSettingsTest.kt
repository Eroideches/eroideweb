package com.eroideweb.browser.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateWindowSettingsTest {

    @Test
    fun `le impostazioni arrivano intatte alla finestra privata`() {
        val settings = BrowserSettings(
            toolbarPosition = ToolbarPosition.TOP,
            searchEngine = SearchEngine.DUCKDUCKGO,
            adBlockEnabled = false,
            adBlockDisabledSites = setOf("a.it", "b.it"),
            translateNoticeAccepted = true,
            appTheme = AppTheme.DARK,
            darkenWebPages = true,
            textZoom = 120,
            readerTextZoom = 130,
            readerTheme = ReaderTheme.SEPIA,
            readerSerif = false,
            allowThirdPartyCookies = true,
        )
        val extras = PrivateWindowSettings.toExtras(settings)

        val decoded = PrivateWindowSettings.fromExtras { extras[it] }

        // La finestra privata non ripristina mai le schede.
        assertEquals(settings.copy(restoreTabs = false), decoded)
        assertTrue(PrivateWindowSettings.hasExtras { extras[it] })
    }

    @Test
    fun `senza extra o con valori rovinati si usano i default`() {
        val empty = PrivateWindowSettings.fromExtras { null }
        assertEquals(BrowserSettings(restoreTabs = false), empty)
        assertFalse(PrivateWindowSettings.hasExtras { null })

        // L'elenco dei siti accetta qualsiasi testo; tutti gli altri valori rovinati ricadono sui default.
        val extras = PrivateWindowSettings.toExtras(BrowserSettings())
            .mapValues { (key, value) -> if (key.endsWith("adblockSites")) value else "rovinato" }
        assertEquals(BrowserSettings(restoreTabs = false), PrivateWindowSettings.fromExtras { extras[it] })
    }
}
