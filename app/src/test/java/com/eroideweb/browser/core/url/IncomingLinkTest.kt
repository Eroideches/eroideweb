package com.eroideweb.browser.core.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingLinkTest {

    @Test
    fun `un link web si apre cosi com e`() {
        assertEquals(
            IncomingLink.Url("https://example.com/a?b=1"),
            IncomingLink.from(IncomingLink.ACTION_VIEW, "https://example.com/a?b=1"),
        )
    }

    @Test
    fun `schemi non web o link vuoti vengono ignorati`() {
        assertNull(IncomingLink.from(IncomingLink.ACTION_VIEW, "file:///sdcard/x.html"))
        assertNull(IncomingLink.from(IncomingLink.ACTION_VIEW, "intent://scan/#Intent;end"))
        assertNull(IncomingLink.from(IncomingLink.ACTION_VIEW, null))
        assertNull(IncomingLink.from("android.intent.action.EDIT", "https://example.com"))
    }

    @Test
    fun `da una condivisione si prende il link`() {
        assertEquals(
            IncomingLink.Url("https://example.com/articolo"),
            IncomingLink.from(
                IncomingLink.ACTION_SEND,
                data = null,
                sharedText = "Guarda questo: https://example.com/articolo.",
            ),
        )
        assertEquals(
            IncomingLink.Text("ricetta carbonara"),
            IncomingLink.from(IncomingLink.ACTION_SEND, data = null, sharedText = "  ricetta carbonara "),
        )
    }

    @Test
    fun `ricerche e testo selezionato diventano testo da cercare`() {
        assertEquals(
            IncomingLink.Text("meteo roma"),
            IncomingLink.from(IncomingLink.ACTION_WEB_SEARCH, data = null, searchQuery = "meteo roma"),
        )
        assertEquals(
            IncomingLink.Text("fotosintesi"),
            IncomingLink.from(IncomingLink.ACTION_PROCESS_TEXT, data = null, processText = "fotosintesi"),
        )
        assertNull(IncomingLink.from(IncomingLink.ACTION_PROCESS_TEXT, data = null, processText = "   "))
    }

    @Test
    fun `un testo enorme viene accorciato`() {
        val link = IncomingLink.from(IncomingLink.ACTION_SEND, data = null, sharedText = "a".repeat(5000))

        assertEquals(2000, (link as IncomingLink.Text).text.length)
    }
}
