package com.eroideweb.browser.core.browsingdata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearDataRequestTest {

    private val now = 10L * 24 * 60 * 60 * 1000

    @Test
    fun `gli intervalli partono dal momento giusto`() {
        assertEquals(now - 60 * 60 * 1000, ClearRange.LAST_HOUR.since(now))
        assertEquals(now - 24L * 60 * 60 * 1000, ClearRange.LAST_DAY.since(now))
        assertEquals(now - 7L * 24 * 60 * 60 * 1000, ClearRange.LAST_WEEK.since(now))
        assertEquals(0L, ClearRange.LAST_FOUR_WEEKS.since(now))
        assertEquals(0L, ClearRange.ALL.since(now))
    }

    @Test
    fun `una richiesta senza caselle e vuota`() {
        assertFalse(ClearDataRequest().isEmpty)
        assertTrue(ClearDataRequest(history = false, cookiesAndSiteData = false, cache = false).isEmpty)
        assertFalse(ClearDataRequest(history = false, cookiesAndSiteData = false, cache = false, openTabs = true).isEmpty)
    }
}
