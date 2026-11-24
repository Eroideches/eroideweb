package com.eroideweb.browser.core.reader

import com.eroideweb.browser.core.settings.ReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderDocumentTest {

    private val article = ReaderArticle(
        url = "https://example.com/articolo",
        title = "Caffè <script>alert(1)</script> & \"amici\"",
        byline = "Mario Rossi",
        siteName = "Esempio",
        contentHtml = "<p>Testo dell'articolo.</p>",
        language = "it",
        direction = null,
    )

    @Test
    fun `titolo e autore sono sempre testo`() {
        val html = ReaderDocument.build(article, ReaderTheme.LIGHT, serif = true)

        assertFalse(html.contains("<script>alert(1)</script>"))
        assertTrue(html.contains("Caffè &lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;amici&quot;"))
        assertTrue(html.contains("<p class=\"meta\">Mario Rossi · Esempio</p>"))
        assertTrue(html.contains("<p>Testo dell'articolo.</p>"))
    }

    @Test
    fun `la csp vieta script frame e oggetti`() {
        val html = ReaderDocument.build(article, ReaderTheme.LIGHT, serif = true)

        assertTrue(html.contains("Content-Security-Policy"))
        assertTrue(ReaderDocument.CONTENT_SECURITY_POLICY.contains("script-src 'none'"))
        assertTrue(ReaderDocument.CONTENT_SECURITY_POLICY.contains("frame-src 'none'"))
        assertTrue(ReaderDocument.CONTENT_SECURITY_POLICY.contains("object-src 'none'"))
    }

    @Test
    fun `tema e carattere cambiano i colori e il font`() {
        val sepia = ReaderDocument.build(article, ReaderTheme.SEPIA, serif = false)
        val dark = ReaderDocument.build(article, ReaderTheme.DARK, serif = true)

        assertTrue(sepia.contains(ReaderPalette.of(ReaderTheme.SEPIA).background))
        assertTrue(sepia.contains("sans-serif"))
        assertTrue(dark.contains(ReaderPalette.of(ReaderTheme.DARK).background))
        assertTrue(dark.contains("Georgia"))
    }

    @Test
    fun `lingua e direzione accettano solo valori validi`() {
        val rtl = ReaderDocument.build(article.copy(direction = "RTL", language = "ar"), ReaderTheme.LIGHT, true)
        val injected = ReaderDocument.build(
            article.copy(language = "it\" onload=\"x"),
            ReaderTheme.LIGHT,
            true,
        )

        assertTrue(rtl.contains("<html lang=\"ar\" dir=\"rtl\">"))
        assertFalse(injected.contains("onload"))
        assertTrue(injected.contains("<html dir=\"ltr\">"))
    }

    @Test
    fun `automatico segue il tema dell app`() {
        assertEquals(ReaderTheme.DARK, ReaderStyle(ReaderTheme.AUTO).resolvedTheme(appDark = true))
        assertEquals(ReaderTheme.LIGHT, ReaderStyle(ReaderTheme.AUTO).resolvedTheme(appDark = false))
        assertEquals(ReaderTheme.SEPIA, ReaderStyle(ReaderTheme.SEPIA).resolvedTheme(appDark = true))
    }
}
