package com.eroideweb.browser.core.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import com.eroideweb.browser.core.data.DownloadDao
import com.eroideweb.browser.core.data.DownloadEntry
import com.eroideweb.browser.core.data.DownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File

data class DownloadRequest(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val contentLength: Long,
)

/**
 * Download gestiti dal DownloadManager di sistema (notifiche, ripresa,
 * Wi-Fi/mobile gestiti da Android) e registrati nel database locale.
 * In modalità privata il DAO è `null`: il file si scarica ma non lascia traccia.
 */
interface DownloadRepository {
    fun observe(): Flow<List<DownloadEntry>>

    /** @return `false` se il download non può partire (URL non http, sistema assente). */
    suspend fun enqueue(request: DownloadRequest): Boolean

    /** Aggiorna lo stato dei download in corso interrogando il sistema. */
    suspend fun refresh()

    suspend fun delete(id: Long)

    suspend fun deleteAll()
}

class AndroidDownloadRepository(
    private val context: Context,
    private val dao: DownloadDao?,
) : DownloadRepository {

    private val manager: DownloadManager? =
        context.getSystemService(DownloadManager::class.java)

    override fun observe(): Flow<List<DownloadEntry>> =
        dao?.observeAll() ?: flowOf(emptyList())

    override suspend fun enqueue(request: DownloadRequest): Boolean {
        if (!request.url.startsWith("http://") && !request.url.startsWith("https://")) return false
        val dm = manager ?: return false

        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return false
        val suggested = URLUtil.guessFileName(request.url, request.contentDisposition, request.mimeType)
        val fileName = uniqueFileName(directory, suggested)

        val systemRequest = DownloadManager.Request(Uri.parse(request.url))
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        request.mimeType?.let { systemRequest.setMimeType(it) }
        request.userAgent?.let { systemRequest.addRequestHeader("User-Agent", it) }

        val managerId = try {
            dm.enqueue(systemRequest)
        } catch (_: IllegalArgumentException) {
            // Directory non disponibile o storage pieno: meglio niente che crash.
            return false
        }

        dao?.insert(
            DownloadEntry(
                url = request.url,
                fileName = fileName,
                mimeType = request.mimeType,
                localPath = File(directory, fileName).absolutePath,
                managerId = managerId,
                createdAt = System.currentTimeMillis(),
                status = DownloadStatus.IN_PROGRESS.name,
                totalBytes = request.contentLength,
            ),
        )
        return true
    }

    override suspend fun refresh() {
        val active = dao?.pending() ?: return
        val dm = manager ?: return
        active.forEach { entry ->
            val cursor = try {
                dm.query(DownloadManager.Query().setFilterById(entry.managerId))
            } catch (_: IllegalArgumentException) {
                return@forEach
            }
            cursor.use {
                if (!it.moveToFirst()) return@forEach
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                dao.updateProgress(
                    managerId = entry.managerId,
                    status = DownloadStatus.fromManager(status).name,
                    bytes = bytes.coerceAtLeast(0),
                    total = total,
                )
            }
        }
    }

    override suspend fun delete(id: Long) {
        val downloadDao = dao ?: return
        val entry = downloadDao.entry(id) ?: return
        runCatching { manager?.remove(entry.managerId) }
        downloadDao.deleteById(entry.id)
    }

    override suspend fun deleteAll() {
        val downloadDao = dao ?: return
        downloadDao.all().forEach { entry ->
            runCatching { manager?.remove(entry.managerId) }
        }
        downloadDao.deleteAll()
    }

    /** Non sovrascrivere file già presenti: "report.pdf" -> "report (1).pdf". */
    private fun uniqueFileName(directory: File, name: String): String {
        val file = File(directory, name)
        if (!file.exists()) return name
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        var index = 1
        while (true) {
            val candidate = if (extension.isEmpty()) "$base ($index)" else "$base ($index).$extension"
            if (!File(directory, candidate).exists()) return candidate
            index++
        }
    }
}

/** Mappa degli stati del DownloadManager su un enum serializzabile. */
fun DownloadStatus.Companion.fromManager(status: Int): DownloadStatus = when (status) {
    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETED
    DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
    else -> DownloadStatus.IN_PROGRESS
}
