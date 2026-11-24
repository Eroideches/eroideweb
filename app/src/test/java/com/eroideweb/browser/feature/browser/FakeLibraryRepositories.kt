package com.eroideweb.browser.feature.browser

import com.eroideweb.browser.core.data.BookmarkEntry
import com.eroideweb.browser.core.bookmark.BookmarkRepository
import com.eroideweb.browser.core.data.FavoriteEntry
import com.eroideweb.browser.core.data.HistoryEntry
import com.eroideweb.browser.core.data.HistorySuggestion
import com.eroideweb.browser.core.favorites.FavoriteRepository
import com.eroideweb.browser.core.password.PasswordRepository
import com.eroideweb.browser.core.password.SaveDecision
import com.eroideweb.browser.core.password.SavePolicy
import com.eroideweb.browser.core.password.SavedLogin
import com.eroideweb.browser.core.password.StoredLogin
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

/** Password in memoria, con la stessa regola di salvataggio del repository vero. */
class FakePasswordRepository : PasswordRepository {

    private data class Row(val login: SavedLogin, val password: String)

    private val rows = MutableStateFlow<List<Row>>(emptyList())
    private var nextId = 1L
    val usedIds = mutableListOf<Long>()

    fun passwordOf(origin: String, username: String): String? =
        rows.value.firstOrNull { it.login.origin == origin && it.login.username == username }?.password

    override fun observeAll(): Flow<List<SavedLogin>> = rows.map { list -> list.map { it.login } }

    override suspend fun loginsFor(origin: String): List<SavedLogin> =
        rows.value.filter { it.login.origin == origin }.map { it.login }

    override suspend fun password(id: Long): String? = rows.value.firstOrNull { it.login.id == id }?.password

    override suspend fun decide(origin: String, username: String, password: String, neverForSite: Boolean): SaveDecision =
        SavePolicy.decide(
            rows.value.filter { it.login.origin == origin }.map { StoredLogin(it.login.id, it.login.username, it.password) },
            username,
            password,
            neverForSite,
        )

    override suspend fun save(origin: String, username: String, password: String) {
        val existing = rows.value.firstOrNull { it.login.origin == origin && it.login.username == username }
        rows.update { list ->
            if (existing != null) {
                list.map { if (it == existing) it.copy(password = password) else it }
            } else {
                list + Row(SavedLogin(nextId++, origin, username, 0, null), password)
            }
        }
    }

    override suspend fun update(id: Long, username: String, password: String): Boolean {
        val row = rows.value.firstOrNull { it.login.id == id } ?: return false
        if (rows.value.any { it.login.id != id && it.login.origin == row.login.origin && it.login.username == username }) {
            return false
        }
        rows.update { list ->
            list.map { if (it.login.id == id) Row(it.login.copy(username = username), password) else it }
        }
        return true
    }

    override suspend fun markUsed(id: Long) {
        usedIds += id
    }

    override suspend fun delete(id: Long) {
        rows.update { list -> list.filterNot { it.login.id == id } }
    }

    override suspend fun deleteAll() {
        rows.update { emptyList() }
    }
}
