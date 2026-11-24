package com.eroideweb.browser.feature.browser.web

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.eroideweb.browser.core.perf.PerfStats
import com.eroideweb.browser.core.url.urlHost
import com.eroideweb.browser.feature.browser.PageError
import com.eroideweb.browser.feature.browser.PageErrorKind
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * Navigazione di una scheda: link esterni, blocco delle sotto-risorse,
 * eventi di caricamento ed errori.
 */
internal class TabWebViewClient(
    private val tabId: String,
    private val context: Context,
    private val host: TabWebViews,
    private val requestFilter: RequestFilter?,
) : WebViewClient() {

    /**
     * Host della pagina aggiornato sul thread UI (una volta per navigazione):
     * shouldInterceptRequest gira sul thread di rete e non deve chiamare metodi
     * della WebView né rifare il parsing dell'URL a ogni sotto-risorsa.
     */
    private val pageHostRef = AtomicReference<String?>(null)

    /** Inizio dell'ultimo caricamento, per la diagnostica. */
    private var loadStartedAt = 0L

    private fun setPage(url: String) {
        pageHostRef.set(urlHost(url))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        return handleExternalUrl(request.url)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val filter = requestFilter ?: return null
        // Il documento principale non si blocca mai: il sito deve aprirsi.
        if (request.isForMainFrame) return null
        val host = request.url.host?.lowercase()?.trimEnd('.')?.takeIf { it.isNotEmpty() } ?: return null
        val blocked = try {
            filter.shouldBlock(host, pageHostRef.get())
        } catch (_: RuntimeException) {
            false
        }
        if (!blocked) return null
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        setPage(url)
        loadStartedAt = SystemClock.elapsedRealtime()
        host.callbacks?.onPageStarted(tabId, url)
        host.notifyNavigation(tabId, view, url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        setPage(url)
        if (loadStartedAt > 0) {
            PerfStats.recordPageLoad(SystemClock.elapsedRealtime() - loadStartedAt)
            loadStartedAt = 0
        }
        host.onPageFinished(tabId, view, url)
    }

    override fun onPageCommitVisible(view: WebView, url: String) {
        host.onPageCommitVisible(tabId, view)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        setPage(url)
        host.notifyNavigation(tabId, view, url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        webResourceError: WebResourceError,
    ) {
        // Gli errori delle sotto-risorse (immagini, script) non
        // devono coprire la pagina con una schermata di errore.
        if (!request.isForMainFrame) return
        val code = webResourceError.errorCode
        host.callbacks?.onPageError(tabId, PageError(kind = kindFor(code), code = code))
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        sslError: android.net.http.SslError,
    ) {
        // Mai proseguire su un certificato non valido.
        handler.cancel()
        val code = ERROR_FAILED_SSL_HANDSHAKE
        host.callbacks?.onPageError(tabId, PageError(kind = PageErrorKind.INSECURE, code = code))
    }

    override fun onReceivedHttpAuthRequest(
        view: WebView,
        handler: HttpAuthHandler,
        host: String,
        realm: String,
    ) {
        this.host.callbacks?.onHttpAuthRequest(tabId, host, realm, handler) ?: handler.cancel()
    }

    /** Senza questo, la morte del renderer chiuderebbe l'app intera. */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        host.onRendererGone(tabId, view)
        return true
    }

    /** Traduce i codici WebView in messaggi comprensibili. */
    private fun kindFor(code: Int): PageErrorKind = when (code) {
        ERROR_HOST_LOOKUP,
        ERROR_CONNECT,
        ERROR_IO,
        ERROR_TIMEOUT,
        -> PageErrorKind.NO_CONNECTION

        ERROR_FAILED_SSL_HANDSHAKE -> PageErrorKind.INSECURE

        else -> PageErrorKind.GENERIC
    }

    /** Link esterni (tel:, mailto:, app link) si aprono fuori dal browser. */
    private fun handleExternalUrl(uri: Uri): Boolean {
        return when (uri.scheme?.lowercase()) {
            null, "http", "https", "about", "file", "data", "javascript", "blob" -> false
            else -> try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            } catch (_: ActivityNotFoundException) {
                true
            }
        }
    }
}
