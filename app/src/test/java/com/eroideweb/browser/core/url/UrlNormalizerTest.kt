package com.eroideweb.browser.core.url

import com.eroideweb.browser.core.settings.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlNormalizerTest {

    private fun load(input: String, engine: SearchEngine = SearchEngine.GOOGLE): String? =
        UrlNormalizer.toLoadableUrl(input, engine)

    @Test
    fun `input vuoto non produce url`() {
        assertNull(load(""))
        assertNull(load("   "))
    }

    @Test
    fun `dominio semplice diventa https`() {
        assertEquals("https://example.com", load("example.com"))
        assertEquals("https://sub.example.co.uk/path?a=1", load("sub.example.co.uk/path?a=1"))
        assertEquals("https://Example.COM", load("  Example.COM  "))
    }

    @Test
    fun `schema esplicito rispettato`() {
        assertEquals("http://example.com", load("http://example.com"))
        assertEquals("https://example.com/a", load("https://example.com/a"))
        assertEquals("about:blank", load("about:blank"))
    }

    @Test
    fun `localhost e ip usano http`() {
        assertEquals("http://localhost:8080", load("localhost:8080"))
        assertEquals("http://192.168.1.1", load("192.168.1.1"))
        assertEquals("http://127.0.0.1:8080/admin", load("127.0.0.1:8080/admin"))
    }

    @Test
    fun `testo libero diventa ricerca`() {
        assertEquals(
            "https://www.google.com/search?q=caff%C3%A8+con+brioche",
            load("caffè con brioche"),
        )
        assertEquals("https://www.google.com/search?q=kotlin+coroutines", load("kotlin coroutines"))
    }

    @Test
    fun `usa il motore scelto`() {
        assertEquals(
            "https://duckduckgo.com/?q=caff%C3%A8",
            load("caffè", SearchEngine.DUCKDUCKGO),
        )
    }

    @Test
    fun `schemi non supportati diventano ricerca`() {
        assertEquals(
            "https://www.google.com/search?q=ftp%3A%2F%2Fexample.com%2Ffile",
            load("ftp://example.com/file"),
        )
        assertEquals(
            "https://www.google.com/search?q=mailto%3Ainfo%40example.com",
            load("mailto:info@example.com"),
        )
    }

    @Test
    fun `parole con punto ma senza tld valido restano ricerche`() {
        assertEquals("https://www.google.com/search?q=1.2", load("1.2"))
        assertEquals("https://www.google.com/search?q=example", load("example"))
    }

    @Test
    fun `dominio con tld corto valido viene navigato`() {
        assertEquals("https://what.is", load("what.is"))
        assertEquals("https://a.b.it", load("a.b.it"))
    }
}
