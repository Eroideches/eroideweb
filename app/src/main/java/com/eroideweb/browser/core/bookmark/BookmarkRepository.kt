package com.eroideweb.browser.core.bookmark

import com.eroideweb.browser.core.data.BookmarkDao
import com.eroideweb.browser.core.data.BookmarkEntry
import com.eroideweb.browser.core.data.escapeLike
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Segnalibri locali. In modalità privata non sono disponibili. */
interface BookmarkRepository {
    fun observe(query: String): Flow<List<BookmarkEntry>>

    fun observeAll(): Flow<List<BookmarkEntry>>

    suspend fun add(url: String, title: String?)

    suspend fun removeByUrl(url: String)

    suspend fun delete(id: Long)

    suspend fun deleteAll()

    /** Segnalibri che contengono [query] nel titolo o nell'indirizzo. */
    suspend fun suggest(query: String, limit: Int): List<BookmarkEntry>
}

class RoomBookmarkRepository(
    private val dao: BookmarkDao,
    private val now: () -> Long = System::currentTimeMillis,
) : BookmarkRepository {

    override fun observe(query: String): Flow<List<BookmarkEntry>> = dao.observe(query.trim())

    override fun observeAll(): Flow<List<BookmarkEntry>> = dao.observeAll()

    override suspend fun add(url: String, title: String?) {
        dao.insert(
            BookmarkEntry(
                url = url,
                title = title?.takeIf { it.isNotBlank() },
                createdAt = now(),
            ),
        )
    }

    override suspend fun removeByUrl(url: String) = dao.deleteByUrl(url)

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun deleteAll() = dao.deleteAll()

    override suspend fun suggest(query: String, limit: Int): List<BookmarkEntry> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return dao.suggest(escapeLike(trimmed), limit)
    }
}

object NoOpBookmarkRepository : BookmarkRepository {
    override fun observe(query: String): Flow<List<BookmarkEntry>> = flowOf(emptyList())

    override fun observeAll(): Flow<List<BookmarkEntry>> = flowOf(emptyList())

    override suspend fun add(url: String, title: String?) = Unit

    override suspend fun removeByUrl(url: String) = Unit

    override suspend fun delete(id: Long) = Unit

    override suspend fun deleteAll() = Unit

    override suspend fun suggest(query: String, limit: Int): List<BookmarkEntry> = emptyList()
}
