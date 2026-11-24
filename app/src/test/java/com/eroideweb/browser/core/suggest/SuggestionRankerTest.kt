package com.eroideweb.browser.core.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionRankerTest {

    private fun history(url: String, title: String?, visits: Int = 1) =
        SuggestionCandidate(url, title, SuggestionSource.HISTORY, visits)

    @Test
    fun `chi inizia come il testo digitato viene prima`() {
        val ranked = SuggestionRanker.rank(
            "rep",
            listOf(
                history("https://www.ilpost.it/2026/11/reportage", "Un reportage", visits = 9),
                history("https://www.repubblica.it/", "la Repubblica", visits = 1),
            ),
        )

        assertEquals("https://www.repubblica.it/", ranked.first().url)
    }

    @Test
    fun `a parita di corrispondenza preferiti e segnalibri battono la cronologia`() {
        val ranked = SuggestionRanker.rank(
            "wiki",
            listOf(
                history("https://wikipedia.org/a", "A", visits = 3),
                SuggestionCandidate("https://wikipedia.org/b", "B", SuggestionSource.FAVORITE),
            ),
        )

        assertEquals(SuggestionSource.FAVORITE, ranked.first().source)
    }

    @Test
    fun `ogni indirizzo compare una volta sola e si rispetta il limite`() {
        val candidates = listOf(
            history("https://a.it/", "A"),
            SuggestionCandidate("https://a.it/", "A", SuggestionSource.BOOKMARK),
        ) + (1..10).map { history("https://a$it.it/", "A $it") }

        val ranked = SuggestionRanker.rank("a", candidates, limit = 6)

        assertEquals(6, ranked.size)
        assertEquals(1, ranked.count { it.url == "https://a.it/" })
        assertEquals(SuggestionSource.BOOKMARK, ranked.first { it.url == "https://a.it/" }.source)
    }

    @Test
    fun `senza corrispondenze o testo non si propone nulla`() {
        assertTrue(SuggestionRanker.rank("zzz", listOf(history("https://a.it/", "A"))).isEmpty())
        assertTrue(SuggestionRanker.rank("  ", listOf(history("https://a.it/", "A"))).isEmpty())
    }
}
