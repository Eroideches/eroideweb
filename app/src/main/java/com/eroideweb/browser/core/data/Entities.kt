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

/** Sito preferito, mostrato come riquadro nella pagina iniziale. */
@Entity(tableName = "favorites", indices = [Index(value = ["url"], unique = true)])
data class FavoriteEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String?,
    /** Ordine nella griglia, dal più piccolo. */
    val position: Int,
    val createdAt: Long,
)

/**
 * Una password salvata. [secret] è cifrato con AES-GCM (chiave nel Keystore):
 * nel database non c'è mai la password in chiaro.
 */
@Entity(
    tableName = "credentials",
    indices = [Index(value = ["origin", "username"], unique = true)],
)
data class CredentialEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Schema + host (+ porta), es. `https://accounts.example.com`. */
    val origin: String,
    /** Può essere vuoto (siti con sola password). */
    val username: String,
    val secret: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long? = null,
)

/** Riga di suggerimento dalla cronologia: una per URL, con frequenza e recenza. */
data class HistorySuggestion(
    val url: String,
    val title: String?,
    val lastVisitedAt: Long,
    val visitCount: Int,
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
