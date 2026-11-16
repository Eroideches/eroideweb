package com.eroideweb.browser.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
