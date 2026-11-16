package com.eroideweb.browser.feature.browser

import com.eroideweb.browser.core.data.BookmarkEntry
import com.eroideweb.browser.core.bookmark.BookmarkRepository
import com.eroideweb.browser.core.data.HistoryEntry
import com.eroideweb.browser.core.history.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Cronologia in memoria: registra le visite per poterle verificare nei test. */
class FakeHistoryRepository : HistoryRepository {

    val recorded = mutableListOf<Pair<String, String?>>()

    override fun observe(query: String): Flow<List<HistoryEntry>> = flowOf(emptyList())

    override suspend fun record(url: String, title: String?) {
        recorded += url to title
    }

    override suspend fun delete(id: Long) = Unit

    override suspend fun deleteAll() = recorded.clear()
}

/** Segnalibri in memoria, con flow osservabile come quello vero. */
class FakeBookmarkRepository : BookmarkRepository {

    private val state = MutableStateFlow<List<BookmarkEntry>>(emptyList())
    private var nextId = 1L

    override fun observe(query: String): Flow<List<BookmarkEntry>> =
        state.map { list ->
            if (query.isBlank()) {
                list
            } else {
                list.filter { it.url.contains(query, ignoreCase = true) }
            }
        }

    override fun observeAll(): Flow<List<BookmarkEntry>> = state

    override suspend fun add(url: String, title: String?) {
        state.update { current ->
            if (current.any { it.url == url }) {
                current
            } else {
                current + BookmarkEntry(id = nextId++, url = url, title = title, createdAt = 0)
            }
        }
    }

    override suspend fun removeByUrl(url: String) {
        state.update { list -> list.filterNot { it.url == url } }
    }

    override suspend fun delete(id: Long) {
        state.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun deleteAll() {
        state.update { emptyList() }
    }
}
