package com.eroideweb.browser.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LikeEscapeTest {

    @Test
    fun `percentuale trattino basso e barra diventano caratteri normali`() {
        assertEquals("100\\%", escapeLike("100%"))
        assertEquals("nome\\_file", escapeLike("nome_file"))
        assertEquals("a\\\\b", escapeLike("a\\b"))
        assertEquals("caffè", escapeLike("caffè"))
    }
}
