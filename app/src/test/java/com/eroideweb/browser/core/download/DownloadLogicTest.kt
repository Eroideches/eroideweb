package com.eroideweb.browser.core.download

import android.app.DownloadManager
import com.eroideweb.browser.core.data.DownloadEntry
import com.eroideweb.browser.core.data.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** Logica pura dei download: nessun DownloadManager reale nei test. */
class DownloadLogicTest {

    @Test
    fun `solo il successo diventa completato`() {
        assertEquals(
            DownloadStatus.COMPLETED,
            DownloadStatus.fromManager(DownloadManager.STATUS_SUCCESSFUL),
        )
        assertEquals(
            DownloadStatus.FAILED,
            DownloadStatus.fromManager(DownloadManager.STATUS_FAILED),
        )
        assertEquals(
            DownloadStatus.IN_PROGRESS,
            DownloadStatus.fromManager(DownloadManager.STATUS_RUNNING),
        )
        assertEquals(
            DownloadStatus.IN_PROGRESS,
            DownloadStatus.fromManager(DownloadManager.STATUS_PENDING),
        )
        assertEquals(
            DownloadStatus.IN_PROGRESS,
            DownloadStatus.fromManager(DownloadManager.STATUS_PAUSED),
        )
    }

    @Test
    fun `la percentuale e limitata tra 0 e 100`() {
        assertEquals(50, entry(bytes = 50, total = 100).progressPercent())
        assertEquals(100, entry(bytes = 250, total = 100).progressPercent())
        assertEquals(0, entry(bytes = 10, total = -1).progressPercent())
        assertEquals(0, entry(bytes = 0, total = 0).progressPercent())
    }

    private fun entry(bytes: Long, total: Long) = DownloadEntry(
        url = "https://example.com/file.zip",
        fileName = "file.zip",
        mimeType = "application/zip",
        localPath = "/tmp/file.zip",
        managerId = 1,
        createdAt = 0,
        bytesDownloaded = bytes,
        totalBytes = total,
    )
}
