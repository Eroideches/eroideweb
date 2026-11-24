package com.eroideweb.browser.core.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataUrlTest {

    @Test
    fun `base64 e testo semplice`() {
        val png = DataUrl.parse("data:image/png;base64,iVBORw0KGgo=")!!
        assertEquals("image/png", png.mimeType)
        assertArrayEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), png.bytes)

        val csv = DataUrl.parse("data:text/csv;charset=utf-8,nome%2Ceta%0AAnna%2C30")!!
        assertEquals("text/csv", csv.mimeType)
        assertEquals("nome,eta\nAnna,30", String(csv.bytes))

        assertEquals("text/plain", DataUrl.parse("data:,ciao+mondo")!!.mimeType)
        assertEquals("ciao+mondo", String(DataUrl.parse("data:,ciao+mondo")!!.bytes))
    }

    @Test
    fun `url non validi o troppo grandi si rifiutano`() {
        assertNull(DataUrl.parse("https://example.com"))
        assertNull(DataUrl.parse("data:senza-virgola"))
        assertNull(DataUrl.parse("data:image/png;base64,@@@"))
        assertNull(DataUrl.parse("data:text/plain;base64," + "A".repeat(DataUrl.MAX_BYTES * 2)))
    }
}
