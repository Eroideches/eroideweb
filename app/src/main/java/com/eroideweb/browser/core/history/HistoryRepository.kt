package com.eroideweb.browser.core.history

import com.eroideweb.browser.core.data.HistoryDao
import com.eroideweb.browser.core.data.HistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Cronologia locale. Le schede private usano [NoOpHistoryRepository]. */
interface HistoryRepository {
    fun observe(query: String): Flow<List<HistoryEntry>>

    suspend fun record(url: String, title: String?)

    suspend fun delete(id: Long)

    suspend fun deleteAll()
}

class RoomHistoryRepository(
    private val dao: HistoryDao,
    private val now: () -> Long = System::currentTimeMillis,
) : HistoryRepository {

    override fun observe(query: String): Flow<List<HistoryEntry>> = dao.observe(query.trim())

    override suspend fun record(url: String, title: String?) {
        dao.insert(
            HistoryEntry(
                url = url,
                title = title?.takeIf { it.isNotBlank() },
                visitedAt = now(),
            ),
        )
        // Limite prudente: la cronologia resta veloce anche dopo mesi d'uso.
        dao.trimTo(MAX_ENTRIES)
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun deleteAll() = dao.deleteAll()

    private companion object {
        const val MAX_ENTRIES = 5000
    }
}

/** Modalità privata: niente viene scritto, niente viene mostrato. */
object NoOpHistoryRepository : HistoryRepository {
    override fun observe(query: String): Flow<List<HistoryEntry>> = flowOf(emptyList())

    override suspend fun record(url: String, title: String?) = Unit

    override suspend fun delete(id: Long) = Unit

    override suspend fun deleteAll() = Unit
}
