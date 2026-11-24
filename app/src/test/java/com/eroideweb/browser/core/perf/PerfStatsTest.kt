package com.eroideweb.browser.core.perf

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerfStatsTest {

    @After
    fun tearDown() {
        PerfStats.reset()
    }

    @Test
    fun `media dei caricamenti sugli ultimi venti`() {
        assertNull(PerfStats.snapshot().averagePageLoadMs)

        (1..25).forEach { PerfStats.recordPageLoad(it * 100L) }
        PerfStats.recordPageLoad(-5)

        val snapshot = PerfStats.snapshot()
        assertEquals(20, snapshot.pageLoads)
        assertEquals(2500L, snapshot.lastPageLoadMs)
        assertEquals((6..25).map { it * 100L }.average().toLong(), snapshot.averagePageLoadMs)
    }

    @Test
    fun `il primo fotogramma si registra una volta sola`() {
        PerfStats.recordFirstFrame(420)
        PerfStats.recordFirstFrame(9000)

        assertEquals(420L, PerfStats.snapshot().firstFrameMs)
    }
}
