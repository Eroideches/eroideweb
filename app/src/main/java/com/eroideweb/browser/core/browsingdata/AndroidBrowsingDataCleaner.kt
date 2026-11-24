package com.eroideweb.browser.core.browsingdata

import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import com.eroideweb.browser.core.download.DownloadRepository
import com.eroideweb.browser.core.favicon.FaviconStore
import com.eroideweb.browser.core.history.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Cancella i dati di navigazione della finestra normale. Le operazioni sulla
 * WebView (cookie, storage, cache) girano sul thread principale, come vuole
 * Android; database e file in background.
 *
 * Le schede aperte non si chiudono qui: le chiude il browser quando la
 * cancellazione è finita.
 */
class AndroidBrowsingDataCleaner(
    context: Context,
    private val history: HistoryRepository,
    private val downloads: DownloadRepository,
    private val favicons: FaviconStore,
    /** Password salvate e permessi dei siti: forniti dalle funzioni che li gestiscono. */
    private val extraCleaners: Map<ExtraData, suspend () -> Unit> = emptyMap(),
) : BrowsingDataCleaner {

    /** Dati gestiti da altri componenti (gestore password, permessi dei siti). */
    enum class ExtraData { PASSWORDS, SITE_PERMISSIONS }

    private val appContext = context.applicationContext

    override suspend fun clear(request: ClearDataRequest, now: Long) {
        if (request.history) {
            if (request.range == ClearRange.ALL) history.deleteAll() else history.deleteSince(request.range.since(now))
        }
        if (request.downloads) downloads.deleteAll()
        if (request.passwords) extraCleaners[ExtraData.PASSWORDS]?.invoke()
        if (request.sitePermissions) extraCleaners[ExtraData.SITE_PERMISSIONS]?.invoke()

        withContext(Dispatchers.Main) {
            if (request.cookiesAndSiteData) {
                removeAllCookies()
                WebStorage.getInstance().deleteAllData()
                WebViewDatabase.getInstance(appContext).clearHttpAuthUsernamePassword()
            }
            if (request.cache) {
                // La cache HTTP è unica per tutte le WebView dell'app: basta una vista temporanea.
                WebView(appContext).apply {
                    clearCache(true)
                    destroy()
                }
            }
            if (request.sitePermissions) GeolocationPermissions.getInstance().clearAll()
        }
        if (request.cache) favicons.clear()
    }

    private suspend fun removeAllCookies() = suspendCancellableCoroutine { continuation ->
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies {
            cookies.flush()
            if (continuation.isActive) continuation.resume(Unit)
        }
    }
}
