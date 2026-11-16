package com.eroideweb.browser.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Una visita in cronologia. Le schede private non arrivano mai qui. */
@Entity(tableName = "history", indices = [Index("visitedAt")])
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String?,
    val visitedAt: Long,
)

/** Un segnalibro: URL unico, quindi niente doppioni. */
@Entity(tableName = "bookmarks", indices = [Index(value = ["url"], unique = true)])
data class BookmarkEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String?,
    val createdAt: Long,
)

/** Download avviato dal browser e gestito dal DownloadManager di sistema. */
@Entity(tableName = "downloads", indices = [Index("createdAt")])
data class DownloadEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val localPath: String,
    val managerId: Long,
    val createdAt: Long,
    /** Stato serializzato: IN_PROGRESS, COMPLETED, FAILED. */
    val status: String = DownloadStatus.IN_PROGRESS.name,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = -1,
)

enum class DownloadStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED;

    companion object
}
