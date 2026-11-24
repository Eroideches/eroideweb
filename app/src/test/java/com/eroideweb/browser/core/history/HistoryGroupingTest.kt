package com.eroideweb.browser.core.history

import com.eroideweb.browser.core.data.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HistoryGroupingTest {

    private val zone = ZoneId.of("Europe/Rome")

    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `oggi ieri e i giorni precedenti in ordine`() {
        val now = at(2026, 11, 24, 10)
        val entries = listOf(
            HistoryEntry(1, "https://a.it", "A", at(2026, 11, 24, 9)),
            HistoryEntry(2, "https://b.it", "B", at(2026, 11, 24, 0)),
            HistoryEntry(3, "https://c.it", "C", at(2026, 11, 23, 22)),
            HistoryEntry(4, "https://d.it", "D", at(2026, 11, 20, 8)),
        )

        val days = HistoryGrouping.groupByDay(entries, now, zone)

        assertEquals(listOf(DayKind.TODAY, DayKind.YESTERDAY, DayKind.OTHER), days.map { it.kind })
        assertEquals(listOf(1L, 2L), days[0].entries.map { it.id })
        assertEquals(20, days[2].date.dayOfMonth)
    }

    @Test
    fun `nessuna voce nessun gruppo`() {
        assertTrue(HistoryGrouping.groupByDay(emptyList(), 0, zone).isEmpty())
    }
}
