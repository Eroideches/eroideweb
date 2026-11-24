package com.eroideweb.browser.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCodecTest {

    private val snapshot = SessionSnapshot(
        tabs = listOf(
            SessionTab(id = "1", url = "https://example.com", title = "Example"),
            SessionTab(id = "2", url = null, title = null),
            SessionTab(
                id = "3",
                url = "https://it.wikipedia.org/wiki/Caff%C3%A8?x=1&y=2",
                title = "Caffè — Wikipedia",
            ),
        ),
        activeTabId = "3",
    )

    @Test
    fun `encode e decode fanno il giro completo`() {
        val decoded = SessionCodec.decode(SessionCodec.encode(snapshot))

        assertEquals(snapshot, decoded)
    }

    @Test
    fun `campi vuoti restano null`() {
        val decoded = SessionCodec.decode(
            SessionCodec.encode(
                SessionSnapshot(tabs = listOf(SessionTab("a", null, null)), activeTabId = "a"),
            ),
        )

        assertNull(decoded?.tabs?.single()?.url)
        assertNull(decoded?.tabs?.single()?.title)
    }

    @Test
    fun `tab e newline nei titoli non rompono il formato`() {
        val weird = SessionSnapshot(
            tabs = listOf(SessionTab("id", "https://a.b/c", "Titolo\tcon\ttab\ne newline")),
            activeTabId = "id",
        )

        val decoded = SessionCodec.decode(SessionCodec.encode(weird))

        assertEquals("Titolo\tcon\ttab\ne newline", decoded?.tabs?.single()?.title)
    }

    @Test
    fun `una scheda attiva inesistente ricade sulla prima`() {
        val text = SessionCodec.encode(
            SessionSnapshot(
                tabs = listOf(SessionTab("a", "https://a.b", null), SessionTab("b", "https://c.d", null)),
                activeTabId = "sconosciuta",
            ),
        )

        assertEquals("a", SessionCodec.decode(text)?.activeTabId)
    }

    @Test
    fun `un formato sconosciuto non produce sessione`() {
        assertNull(SessionCodec.decode("v9\nactive=x\ntab=a\thttps%3A%2F%2Fa.b\tA"))
        assertNull(SessionCodec.decode(""))
        assertNull(SessionCodec.decode("v1\nactive=x\n"))
        assertNull(SessionCodec.decode("v2\nactive=x"))
    }

    @Test
    fun `la versione desktop fa il giro completo`() {
        val desktop = SessionSnapshot(
            tabs = listOf(
                SessionTab("a", "https://a.b", "A", desktopMode = true),
                SessionTab("b", "https://c.d", "C", desktopMode = false),
            ),
            activeTabId = "a",
        )

        val text = SessionCodec.encode(desktop)

        assertTrue(text.startsWith("v2\n"))
        assertEquals(desktop, SessionCodec.decode(text))
    }

    @Test
    fun `una sessione salvata dalla versione precedente si legge ancora`() {
        val v1 = "v1\nactive=b\ntab=a\thttps%3A%2F%2Fa.b\tA\ntab=b\thttps%3A%2F%2Fc.d\tC\n"

        val decoded = SessionCodec.decode(v1)

        assertEquals(listOf("a", "b"), decoded?.tabs?.map { it.id })
        assertEquals("b", decoded?.activeTabId)
        assertFalse(decoded!!.tabs.any { it.desktopMode })
    }

    @Test
    fun `righe corrotte vengono ignorate`() {
        val text = "v2\nactive=a\ntab=a\thttps%3A%2F%2Fa.b\tA\t0\ntab=rotto\n"

        val decoded = SessionCodec.decode(text)

        assertEquals(1, decoded?.tabs?.size)
        assertEquals("a", decoded?.activeTabId)
    }
}
