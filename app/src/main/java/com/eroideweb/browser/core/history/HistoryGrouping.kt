package com.eroideweb.browser.core.history

import com.eroideweb.browser.core.data.HistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Come chiamare un giorno della cronologia. */
enum class DayKind {
    TODAY,
    YESTERDAY,
    OTHER,
}

data class HistoryDay(
    val date: LocalDate,
    val kind: DayKind,
    val entries: List<HistoryEntry>,
)

/** Raggruppa la cronologia per giorno (Oggi, Ieri, poi le date). Logica pura. */
object HistoryGrouping {

    fun groupByDay(entries: List<HistoryEntry>, now: Long, zone: ZoneId): List<HistoryDay> {
        if (entries.isEmpty()) return emptyList()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val yesterday = today.minusDays(1)
        // Le voci arrivano già dalla più recente: l'ordine dei gruppi segue.
        return entries
            .groupBy { Instant.ofEpochMilli(it.visitedAt).atZone(zone).toLocalDate() }
            .map { (date, dayEntries) ->
                HistoryDay(
                    date = date,
                    kind = when (date) {
                        today -> DayKind.TODAY
                        yesterday -> DayKind.YESTERDAY
                        else -> DayKind.OTHER
                    },
                    entries = dayEntries,
                )
            }
            .sortedByDescending { it.date }
    }
}
