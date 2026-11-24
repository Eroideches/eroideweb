package com.eroideweb.browser.core.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentsTest {

    private val webViewUa =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A.240805.005; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/143.0.7499.40 Mobile Safari/537.36"

    @Test
    fun `lo ua mobile somiglia a chrome senza tracce della webview`() {
        val ua = UserAgents.mobile(webViewUa)

        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/143.0.0.0 Mobile Safari/537.36",
            ua,
        )
        assertFalse(ua.contains("; wv"))
        assertFalse(ua.contains("Version/4.0"))
        assertFalse(ua.contains("Pixel"))
    }

    @Test
    fun `lo ua desktop non dice mobile ne android`() {
        val ua = UserAgents.desktop(webViewUa)

        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/143.0.0.0 Safari/537.36",
            ua,
        )
        assertFalse(ua.contains("Mobile"))
        assertFalse(ua.contains("Android"))
    }

    @Test
    fun `senza versione chrome il mobile resta quello di default`() {
        val odd = "Mozilla/5.0 (Linux; Android 9) qualcosa di strano"

        assertEquals(odd, UserAgents.mobile(odd))
        assertTrue(UserAgents.desktop(odd).contains("Chrome/"))
        assertNull(UserAgents.chromeMajor(odd))
    }
}
