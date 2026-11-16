package com.eroideweb.browser.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchEngineTest {

    @Test
    fun `google codifica la query`() {
        assertEquals(
            "https://www.google.com/search?q=caff%C3%A8+con+brioche",
            SearchEngine.GOOGLE.searchUrl("caffè con brioche"),
        )
    }

    @Test
    fun `ogni motore ha un template con segnaposto`() {
        SearchEngine.entries.forEach { engine ->
            val url = engine.searchUrl("test")
            assertEquals("template mancante per ${engine.id}", true, url.contains("test"))
        }
    }

    @Test
    fun `id sconosciuto torna google`() {
        assertEquals(SearchEngine.GOOGLE, SearchEngine.fromId(null))
        assertEquals(SearchEngine.GOOGLE, SearchEngine.fromId("boh"))
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.fromId("duckduckgo"))
    }
}
