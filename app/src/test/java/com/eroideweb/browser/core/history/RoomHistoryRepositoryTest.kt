package com.eroideweb.browser.core.history

import com.eroideweb.browser.core.data.HistoryDao
import com.eroideweb.browser.core.data.HistoryEntry
import com.eroideweb.browser.core.data.HistorySuggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomHistoryRepositoryTest {

    private class CountingDao : HistoryDao {
        var inserts = 0
        var trims = 0
        var lastQuery: String? = null

        override fun observe(query: String): Flow<List<HistoryEntry>> = flowOf(emptyList())
        override suspend fun insert(entry: HistoryEntry): Long = (++inserts).toLong()
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun deleteAll() = Unit
        override suspend fun trimTo(keep: Int) {
            trims++
        }
        override suspend fun count(): Int = inserts
        override suspend fun suggest(query: String, limit: Int): List<HistorySuggestion> {
            lastQuery = query
            return emptyList()
        }
        override suspend fun deleteSince(since: Long) = Unit
    }

    @Test
    fun `il taglio della cronologia non avviene a ogni pagina`() = runTest {
        val dao = CountingDao()
        val repository = RoomHistoryRepository(dao) { 0 }

        repeat(120) { repository.record("https://a.it/$it", null) }

        assertEquals(120, dao.inserts)
        // Una volta subito dopo l'avvio, poi ogni 50 inserimenti.
        assertEquals(3, dao.trims)
    }

    @Test
    fun `i suggerimenti proteggono i caratteri jolly`() = runTest {
        val dao = CountingDao()
        val repository = RoomHistoryRepository(dao) { 0 }

        repository.suggest(" 100%_ ", 5)
        assertEquals("100\\%\\_", dao.lastQuery)
        assertTrue(repository.suggest("   ", 5).isEmpty())
    }
}
