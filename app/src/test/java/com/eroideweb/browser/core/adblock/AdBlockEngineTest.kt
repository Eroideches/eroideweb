package com.eroideweb.browser.core.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockEngineTest {

    private val engine = AdBlockEngine(
        BlockRules(
            blocked = setOf("ads.example.com", "example.net"),
            allowed = setOf("ads.example.net"),
        ),
    )

    @Test
    fun `blocca l host esatto e i suoi sottodomini`() {
        assertTrue(engine.isBlocked("https://ads.example.com/banner.js", "https://sito.it"))
        assertTrue(engine.isBlocked("https://x.y.ads.example.com/pixel", "https://sito.it"))
        assertTrue(engine.isBlocked("https://example.net/tracker", "https://sito.it"))
    }

    @Test
    fun `non blocca host diversi`() {
        assertFalse(engine.isBlocked("https://example.com/pagina", "https://sito.it"))
        assertFalse(engine.isBlocked("https://notexample.net/x", "https://sito.it"))
    }

    @Test
    fun `le eccezioni hanno la precedenza`() {
        // esempio.net è bloccato, ma ads.example.net è esplicitamente consentito
        assertTrue(engine.isBlocked("https://example.net/x", "https://sito.it"))
        assertFalse(engine.isBlocked("https://ads.example.net/x", "https://sito.it"))
    }

    @Test
    fun `la prima parte non viene mai bloccata`() {
        assertFalse(engine.isBlocked("https://example.net/x", "https://example.net/"))
        assertTrue(engine.isBlocked("https://example.net/x", "https://altro.it/"))
    }

    @Test
    fun `url senza host non causano blocchi`() {
        assertFalse(engine.isBlocked("about:blank", "https://sito.it"))
        assertFalse(engine.isBlocked("data:text/html,ciao", "https://sito.it"))
        assertFalse(engine.isBlocked("", "https://sito.it"))
    }
}
