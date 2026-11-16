package com.eroideweb.browser.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertNull(SessionCodec.decode("v2\nactive=x"))
        assertNull(SessionCodec.decode(""))
        assertNull(SessionCodec.decode("v1\nactive=x\n"))
    }

    @Test
    fun `righe corrotte vengono ignorate`() {
        val text = "v1\nactive=a\ntab=a\thttps%3A%2F%2Fa.b\tA\ntab=rotto\n"

        val decoded = SessionCodec.decode(text)

        assertEquals(1, decoded?.tabs?.size)
        assertEquals("a", decoded?.activeTabId)
    }
}
