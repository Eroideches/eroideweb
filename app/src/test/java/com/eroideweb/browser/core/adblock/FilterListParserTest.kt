package com.eroideweb.browser.core.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterListParserTest {

    @Test
    fun `estrae gli host delle regole di rete`() {
        val rules = FilterListParser.parse(
            listOf(
                "||ads.example.com^",
                "||tracker.example.net^\$third-party",
                "||Analytics.Example.org^",
            ).asSequence(),
        )

        assertEquals(
            setOf("ads.example.com", "tracker.example.net", "analytics.example.org"),
            rules.blocked,
        )
    }

    @Test
    fun `le eccezioni finiscono tra gli host consentiti`() {
        val rules = FilterListParser.parse(
            listOf("||ads.example.com^", "@@||good.example.com^").asSequence(),
        )

        assertTrue("ads.example.com" in rules.blocked)
        assertTrue("good.example.com" in rules.allowed)
        assertFalse("good.example.com" in rules.blocked)
    }

    @Test
    fun `ignora cosmetiche, commenti e regole non basate su host`() {
        val rules = FilterListParser.parse(
            listOf(
                "! commento",
                "[Adblock Plus 2.0]",
                "example.com##.banner",
                "/banner/*/img^",
                "##.sponsor",
                "&ad_type=",
            ).asSequence(),
        )

        assertTrue(rules.blocked.isEmpty())
        assertTrue(rules.allowed.isEmpty())
    }

    @Test
    fun `salta le regole con dominio specifico e quelle document`() {
        val rules = FilterListParser.parse(
            listOf(
                "||cdn.example.com^\$domain=sito.it",
                "||whole.example.com^\$document",
                "||banner.example.com^",
            ).asSequence(),
        )

        assertEquals(setOf("banner.example.com"), rules.blocked)
    }

    @Test
    fun `scarta host senza punto o malformati`() {
        val rules = FilterListParser.parse(
            listOf(
                "||localhost^",
                "||example..com^",
                "||.example.com^",
                "||ok.example.com^",
            ).asSequence(),
        )

        assertEquals(setOf("ok.example.com"), rules.blocked)
    }
}
