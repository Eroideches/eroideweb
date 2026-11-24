package com.eroideweb.browser.core.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.StringWriter

class HostCacheTest {

    private val rules = BlockRules(blocked = setOf("ads.com", "tracker.net"), allowed = setOf("ok.ads.com"))

    private fun written(key: String): String = StringWriter().also { HostCache.write(it, key, rules) }.toString()

    @Test
    fun `la cache restituisce gli stessi host`() {
        val read = HostCache.read(written("v1").reader().buffered(), "v1")!!

        assertEquals(rules.blocked, read.blocked)
        assertEquals(rules.allowed, read.allowed)
    }

    @Test
    fun `una cache di un altra versione o troncata si scarta`() {
        assertNull(HostCache.read(written("vecchia").reader().buffered(), "nuova"))
        val truncated = written("v1").substringBefore("@@")
        assertNull(HostCache.read(truncated.reader().buffered(), "v1"))
        assertNull(HostCache.read("".reader().buffered(), "v1"))
    }
}
