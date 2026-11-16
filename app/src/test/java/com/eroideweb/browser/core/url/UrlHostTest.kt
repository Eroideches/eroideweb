package com.eroideweb.browser.core.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlHostTest {

    @Test
    fun `estrae l host in minuscolo`() {
        assertEquals("www.example.com", urlHost("https://WWW.Example.COM/path?x=1"))
        assertEquals("example.com", urlHost("https://example.com"))
        assertEquals("example.com", urlHost("http://example.com:8080/a"))
    }

    @Test
    fun `gestisce url con caratteri non ascii`() {
        assertEquals(
            "it.wikipedia.org",
            urlHost("https://it.wikipedia.org/wiki/Caffè"),
        )
    }

    @Test
    fun `niente host per schemi senza rete`() {
        assertNull(urlHost("about:blank"))
        assertNull(urlHost("data:text/html,ciao"))
        assertNull(urlHost(null))
        assertNull(urlHost("   "))
    }

    @Test
    fun `toglie credenziali e punto finale`() {
        assertEquals("example.com", urlHost("https://user:pass@example.com./x"))
    }
}
