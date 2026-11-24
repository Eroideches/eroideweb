package com.eroideweb.browser.core.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateUrlTest {

    @Test
    fun `una pagina web diventa un indirizzo di google traduttore`() {
        assertEquals(
            "https://translate.google.com/translate?sl=auto&tl=it&hl=it&u=https%3A%2F%2Fen.wikipedia.org%2Fwiki%2FCoffee%3Fa%3D1",
            TranslateUrl.forPage("https://en.wikipedia.org/wiki/Coffee?a=1", "it"),
        )
    }

    @Test
    fun `pagine non web o gia tradotte non si traducono`() {
        assertNull(TranslateUrl.forPage("about:blank", "it"))
        assertNull(TranslateUrl.forPage("data:text/html,ciao", "it"))
        assertNull(TranslateUrl.forPage("https://example-com.translate.goog/", "it"))
        assertFalse(TranslateUrl.canTranslate("https://translate.google.com/translate?u=x"))
        assertTrue(TranslateUrl.canTranslate("http://sito.it/pagina"))
    }

    @Test
    fun `dal proxy si ricostruisce l indirizzo originale`() {
        assertEquals(
            "https://example.com/a/b?q=1#sez",
            TranslateUrl.originalUrl(
                "https://example-com.translate.goog/a/b?q=1&_x_tr_sl=auto&_x_tr_tl=it&_x_tr_hl=it#sez",
            ),
        )
        // I trattini veri del dominio sono raddoppiati dal proxy.
        assertEquals(
            "https://my-site.co.uk/",
            TranslateUrl.originalUrl("https://my--site-co-uk.translate.goog/?_x_tr_sl=en&_x_tr_tl=it"),
        )
        assertNull(TranslateUrl.originalUrl("https://example.com/"))
    }

    @Test
    fun `i codici lingua di sistema diventano quelli di google`() {
        assertEquals("it", TranslateUrl.languageCode("it", "IT"))
        assertEquals("id", TranslateUrl.languageCode("in"))
        assertEquals("he", TranslateUrl.languageCode("iw"))
        assertEquals("zh-TW", TranslateUrl.languageCode("zh", "TW"))
        assertEquals("zh-CN", TranslateUrl.languageCode("zh", "CN"))
        assertEquals("it", TranslateUrl.languageCode(""))
    }
}
