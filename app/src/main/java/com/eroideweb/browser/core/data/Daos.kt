package com.eroideweb.browser.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query(
        """
        SELECT * FROM history
        WHERE :query = ''
           OR url LIKE '%' || :query || '%'
           OR title LIKE '%' || :query || '%'
        ORDER BY visitedAt DESC
        LIMIT 500
        """,
    )
    fun observe(query: String): Flow<List<HistoryEntry>>

    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun deleteAll()

    /** Mantiene solo le [keep] visite più recenti: il DB non cresce all'infinito. */
    @Query(
        """
        DELETE FROM history
        WHERE id NOT IN (SELECT id FROM history ORDER BY visitedAt DESC LIMIT :keep)
        """,
    )
    suspend fun trimTo(keep: Int)

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int

    /**
     * Suggerimenti per la barra: una riga per URL, le più visitate prima.
     * [query] arriva già con `%`, `_` e `\` protetti da escape.
     */
    @Query(
        """
        SELECT url, title, MAX(visitedAt) AS lastVisitedAt, COUNT(*) AS visitCount
        FROM history
        WHERE url LIKE '%' || :query || '%' ESCAPE '\'
           OR title LIKE '%' || :query || '%' ESCAPE '\'
        GROUP BY url
        ORDER BY visitCount DESC, lastVisitedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun suggest(query: String, limit: Int): List<HistorySuggestion>

    /** Cancella le visite da [since] in poi (Elimina dati di navigazione). */
    @Query("DELETE FROM history WHERE visitedAt >= :since")
    suspend fun deleteSince(since: Long)
}

@Dao
interface BookmarkDao {

    @Query(
        """
        SELECT * FROM bookmarks
        WHERE :query = ''
           OR url LIKE '%' || :query || '%'
           OR title LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        """,
    )
    fun observe(query: String): Flow<List<BookmarkEntry>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntry>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: BookmarkEntry): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()

    /** Suggerimenti per la barra. [query] arriva con l'escape già applicato. */
    @Query(
        """
        SELECT * FROM bookmarks
        WHERE url LIKE '%' || :query || '%' ESCAPE '\'
           OR title LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun suggest(query: String, limit: Int): List<BookmarkEntry>
}

@Dao
abstract class FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY position ASC, createdAt ASC")
    abstract fun observeAll(): Flow<List<FavoriteEntry>>

    @Query("SELECT * FROM favorites ORDER BY position ASC, createdAt ASC")
    abstract suspend fun all(): List<FavoriteEntry>

    @Query("SELECT COALESCE(MAX(position), -1) FROM favorites")
    abstract suspend fun maxPosition(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(entry: FavoriteEntry): Long

    @Query("UPDATE favorites SET url = :url, title = :title WHERE id = :id")
    abstract suspend fun update(id: Long, url: String, title: String?)

    @Query("UPDATE favorites SET position = :position WHERE id = :id")
    abstract suspend fun setPosition(id: Long, position: Int)

    @Query("DELETE FROM favorites WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("DELETE FROM favorites WHERE url = :url")
    abstract suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM favorites")
    abstract suspend fun deleteAll()

    /** Aggiunge in fondo alla griglia, in una sola transazione. */
    @Transaction
    open suspend fun append(url: String, title: String?, createdAt: Long): Long =
        insert(FavoriteEntry(url = url, title = title, position = maxPosition() + 1, createdAt = createdAt))

    /** Scambia di posto [id] con il vicino a distanza [delta] (-1 = prima, +1 = dopo). */
    @Transaction
    open suspend fun move(id: Long, delta: Int) {
        val ordered = all()
        val from = ordered.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, ordered.lastIndex)
        if (to == from) return
        val reordered = ordered.toMutableList().apply { add(to, removeAt(from)) }
        reordered.forEachIndexed { index, entry ->
            if (entry.position != index) setPosition(entry.id, index)
        }
    }
}

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntry>>

    @Insert
    suspend fun insert(entry: DownloadEntry): Long

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Query("UPDATE downloads SET status = :status, bytesDownloaded = :bytes, totalBytes = :total WHERE managerId = :managerId")
    suspend fun updateProgress(managerId: Long, status: String, bytes: Long, total: Long)

    @Query("SELECT * FROM downloads WHERE status = 'IN_PROGRESS'")
    suspend fun pending(): List<DownloadEntry>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun entry(id: Long): DownloadEntry?

    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadEntry>
}
