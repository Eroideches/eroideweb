package com.eroideweb.browser.core.favorites

import android.database.sqlite.SQLiteConstraintException
import com.eroideweb.browser.core.data.FavoriteDao
import com.eroideweb.browser.core.data.FavoriteEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Siti preferiti della pagina iniziale. In modalità privata non esistono. */
interface FavoriteRepository {
    fun observeAll(): Flow<List<FavoriteEntry>>

    suspend fun add(url: String, title: String?)

    /** @return `false` se l'URL è già tra i preferiti di un'altra voce. */
    suspend fun update(id: Long, url: String, title: String?): Boolean

    suspend fun delete(id: Long)

    suspend fun removeByUrl(url: String)

    /** Sposta di [delta] posizioni nella griglia (-1 prima, +1 dopo). */
    suspend fun move(id: Long, delta: Int)

    suspend fun deleteAll()
}

class RoomFavoriteRepository(
    private val dao: FavoriteDao,
    private val now: () -> Long = System::currentTimeMillis,
) : FavoriteRepository {

    override fun observeAll(): Flow<List<FavoriteEntry>> = dao.observeAll()

    override suspend fun add(url: String, title: String?) {
        dao.append(url, title?.takeIf { it.isNotBlank() }, now())
    }

    override suspend fun update(id: Long, url: String, title: String?): Boolean = try {
        dao.update(id, url, title?.takeIf { it.isNotBlank() })
        true
    } catch (_: SQLiteConstraintException) {
        false
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun removeByUrl(url: String) = dao.deleteByUrl(url)

    override suspend fun move(id: Long, delta: Int) = dao.move(id, delta)

    override suspend fun deleteAll() = dao.deleteAll()
}

object NoOpFavoriteRepository : FavoriteRepository {
    override fun observeAll(): Flow<List<FavoriteEntry>> = flowOf(emptyList())

    override suspend fun add(url: String, title: String?) = Unit

    override suspend fun update(id: Long, url: String, title: String?): Boolean = false

    override suspend fun delete(id: Long) = Unit

    override suspend fun removeByUrl(url: String) = Unit

    override suspend fun move(id: Long, delta: Int) = Unit

    override suspend fun deleteAll() = Unit
}
