package com.eroideweb.browser.core.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.eroideweb.browser.core.data.DownloadEntry
import com.eroideweb.browser.core.data.DownloadStatus
import java.io.File

/**
 * Apre un file scaricato con l'app adatta. FileProvider evita
 * `FileUriExposedException` e non richiede permessi di storage.
 */
fun openDownloadedFile(context: Context, entry: DownloadEntry): Boolean {
    if (entry.status != DownloadStatus.COMPLETED.name) return false
    val file = File(entry.localPath)
    if (!file.exists()) return false

    val uri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: IllegalArgumentException) {
        return false
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, entry.mimeType ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** Percentuale intera, 0 se la dimensione non è nota. */
fun DownloadEntry.progressPercent(): Int {
    if (totalBytes <= 0) return 0
    return ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
}
