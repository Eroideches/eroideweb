package com.eroideweb.browser.core.history

import com.eroideweb.browser.core.data.HistoryDao
import com.eroideweb.browser.core.data.HistoryEntry
import com.eroideweb.browser.core.data.HistorySuggestion
import com.eroideweb.browser.core.data.escapeLike
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Cronologia locale. Le schede private usano [NoOpHistoryRepository]. */
interface HistoryRepository {
    fun observe(query: String): Flow<List<HistoryEntry>>

    suspend fun record(url: String, title: String?)

    suspend fun delete(id: Long)

    suspend fun deleteAll()

    /** Pagine visitate che contengono [query], le più frequenti prima. */
    suspend fun suggest(query: String, limit: Int): List<HistorySuggestion>

    /** Cancella le visite da [since] (millisecondi) in poi. */
    suspend fun deleteSince(since: Long)
}

class RoomHistoryRepository(
    private val dao: HistoryDao,
    private val now: () -> Long = System::currentTimeMillis,
) : HistoryRepository {

    override fun observe(query: String): Flow<List<HistoryEntry>> = dao.observe(query.trim())

    /** Inserimenti dall'ultimo taglio: il primo dopo l'avvio taglia subito. */
    private var insertsSinceTrim = TRIM_EVERY

    override suspend fun record(url: String, title: String?) {
        dao.insert(
            HistoryEntry(
                url = url,
                title = title?.takeIf { it.isNotBlank() },
                visitedAt = now(),
            ),
        )
        // Limite prudente: la cronologia resta veloce anche dopo mesi d'uso.
        // Il taglio (una DELETE con sottoquery) si fa ogni tanto, non a ogni pagina.
        if (++insertsSinceTrim >= TRIM_EVERY) {
            insertsSinceTrim = 0
            dao.trimTo(MAX_ENTRIES)
        }
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun deleteAll() = dao.deleteAll()

    override suspend fun suggest(query: String, limit: Int): List<HistorySuggestion> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return dao.suggest(escapeLike(trimmed), limit)
    }

    override suspend fun deleteSince(since: Long) = dao.deleteSince(since)

    private companion object {
        const val MAX_ENTRIES = 5000
        const val TRIM_EVERY = 50
    }
}

/** Modalità privata: niente viene scritto, niente viene mostrato. */
object NoOpHistoryRepository : HistoryRepository {
    override fun observe(query: String): Flow<List<HistoryEntry>> = flowOf(emptyList())

    override suspend fun record(url: String, title: String?) = Unit

    override suspend fun delete(id: Long) = Unit

    override suspend fun deleteAll() = Unit

    override suspend fun suggest(query: String, limit: Int): List<HistorySuggestion> = emptyList()

    override suspend fun deleteSince(since: Long) = Unit
}
