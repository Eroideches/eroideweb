package com.eroideweb.browser.feature.browser

import com.eroideweb.browser.core.data.BookmarkEntry
import com.eroideweb.browser.core.bookmark.BookmarkRepository
import com.eroideweb.browser.core.data.FavoriteEntry
import com.eroideweb.browser.core.data.HistoryEntry
import com.eroideweb.browser.core.data.HistorySuggestion
import com.eroideweb.browser.core.favorites.FavoriteRepository
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

    override suspend fun suggest(query: String, limit: Int): List<HistorySuggestion> =
        recorded
            .filter { (url, title) -> url.contains(query, true) || title?.contains(query, true) == true }
            .groupBy { it.first }
            .map { (url, visits) -> HistorySuggestion(url, visits.last().second, 0, visits.size) }
            .take(limit)

    /** Ultimo istante passato a [deleteSince], per le verifiche. */
    var deletedSince: Long? = null

    override suspend fun deleteSince(since: Long) {
        deletedSince = since
    }
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

    override suspend fun suggest(query: String, limit: Int): List<BookmarkEntry> =
        state.value.filter { it.url.contains(query, true) || it.title?.contains(query, true) == true }.take(limit)
}

/** Preferiti in memoria, con lo stesso ordinamento per posizione del database. */
class FakeFavoriteRepository : FavoriteRepository {

    private val state = MutableStateFlow<List<FavoriteEntry>>(emptyList())
    private var nextId = 1L

    val current: List<FavoriteEntry> get() = state.value

    override fun observeAll(): Flow<List<FavoriteEntry>> = state

    override suspend fun add(url: String, title: String?) {
        state.update { list ->
            if (list.any { it.url == url }) list else list + FavoriteEntry(nextId++, url, title, list.size, 0)
        }
    }

    override suspend fun update(id: Long, url: String, title: String?): Boolean {
        if (state.value.any { it.url == url && it.id != id }) return false
        state.update { list -> list.map { if (it.id == id) it.copy(url = url, title = title) else it } }
        return true
    }

    override suspend fun delete(id: Long) {
        state.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun removeByUrl(url: String) {
        state.update { list -> list.filterNot { it.url == url } }
    }

    override suspend fun move(id: Long, delta: Int) {
        state.update { list ->
            val from = list.indexOfFirst { it.id == id }
            if (from < 0) return@update list
            val to = (from + delta).coerceIn(0, list.lastIndex)
            list.toMutableList().apply { add(to, removeAt(from)) }
                .mapIndexed { index, entry -> entry.copy(position = index) }
        }
    }

    override suspend fun deleteAll() {
        state.update { emptyList() }
    }
}
