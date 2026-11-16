package com.eroideweb.browser.feature.browser.web

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.eroideweb.browser.feature.browser.PageError
import com.eroideweb.browser.feature.browser.PageErrorKind
import java.io.ByteArrayInputStream

/** Decide se una richiesta di rete va bloccata (pubblicità, tracker). */
fun interface RequestFilter {
    fun shouldBlock(requestUrl: String, pageUrl: String?): Boolean
}

/**
 * Gestisce una WebView per scheda.
 *
 * Ogni scheda viva mantiene il proprio DOM e la propria cronologia: si passa
 * da una all'altra senza ricaricare. Le schede oltre [maxLiveTabs] vengono
 * "ibernate": lo stato della WebView viene salvato con `saveState()` e la
 * vista distrutta, così la RAM resta sotto controllo. Al ritorno la scheda
 * viene ripristinata da quello stato (posizione di scroll compresa).
 *
 * Vive nel layer UI: il ViewModel conosce solo i metadati delle schede.
 */
class TabWebViews(
    private val context: Context,
    private val requestFilter: RequestFilter? = null,
    private val maxLiveTabs: Int = 4,
) {

    interface Callbacks {
        fun onPageStarted(tabId: String, url: String)
        fun onPageFinished(tabId: String, url: String, title: String?)
        fun onProgressChanged(tabId: String, progress: Int)
        fun onNavigationStateChanged(
            tabId: String,
            canGoBack: Boolean,
            canGoForward: Boolean,
            url: String?,
        )
        fun onPageError(tabId: String, error: PageError)

        /** Stato salvato di una scheda ibernata, oppure `null`. */
        fun consumeSavedState(tabId: String): Bundle?

        /** Stato da conservare (ibernazione o ricreazione dell'activity). */
        fun saveState(tabId: String, state: Bundle)

        /** Crea una scheda per un `target=_blank` e ne restituisce l'id. */
        fun onCreateWindowTab(): String?

        /** Il sito vuole scaricare un file. */
        fun onDownloadRequested(
            tabId: String,
            url: String,
            userAgent: String?,
            contentDisposition: String?,
            mimeType: String?,
            contentLength: Long,
        )
    }

    val container: FrameLayout = FrameLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    var callbacks: Callbacks? = null

    /** Ordine di accesso: la prima entry è la scheda usata meno di recente. */
    private val live = object : LinkedHashMap<String, WebView>(maxLiveTabs, 0.75f, true) {}

    var currentTabId: String? = null
        private set

    /** Mostra la scheda [tabId]; la crea al primo accesso. */
    fun show(tabId: String, url: String?) {
        currentTabId = tabId
        val existing = live[tabId]
        if (existing != null) {
            attach(existing)
            return
        }
        if (url.isNullOrBlank()) {
            // Nuova scheda sulla pagina iniziale: nessuna WebView da mostrare.
            container.removeAllViews()
            return
        }
        val view = createWebView(tabId)
        live[tabId] = view
        attach(view)
        val saved = callbacks?.consumeSavedState(tabId)
        val restored = saved != null && view.restoreState(saved) != null
        if (!restored) view.loadUrl(url)
        evictIfNeeded()
    }

    /** Carica [url] nella scheda (o la crea, se non è viva). */
    fun load(tabId: String, url: String) {
        currentTabId = tabId
        val view = live[tabId] ?: createWebView(tabId).also { live[tabId] = it }
        attach(view)
        view.loadUrl(url)
        evictIfNeeded()
    }

    fun reload(tabId: String) {
        live[tabId]?.reload()
    }

    fun stopLoading(tabId: String) {
        live[tabId]?.stopLoading()
    }

    fun canGoBack(tabId: String): Boolean = live[tabId]?.canGoBack() == true

    fun goBack(tabId: String) {
        live[tabId]?.takeIf { it.canGoBack() }?.goBack()
    }

    fun canGoForward(tabId: String): Boolean = live[tabId]?.canGoForward() == true

    fun goForward(tabId: String) {
        live[tabId]?.takeIf { it.canGoForward() }?.goForward()
    }

    /** Distrugge la WebView di una scheda chiusa. */
    fun close(tabId: String) {
        val view = live.remove(tabId) ?: return
        container.removeView(view)
        view.stopLoading()
        view.destroy()
    }

    /** Chiude le WebView delle schede che non esistono più nel modello. */
    fun retainOnly(tabIds: Set<String>) {
        live.keys.filterNot { it in tabIds }.forEach(::close)
    }

    fun pauseAll() {
        live.values.forEach { view ->
            view.onPause()
            view.pauseTimers()
        }
    }

    fun resumeAll() {
        live.values.forEach { view ->
            view.onResume()
            view.resumeTimers()
        }
    }

    /**
     * Salva lo stato di tutte le WebView vive e le distrugge.
     * Serve alla ricreazione dell'activity (rotazione): il ViewModel conserva
     * gli stati e la nuova composizione li ripristina senza ricaricare.
     */
    fun destroy() {
        live.forEach { (tabId, view) ->
            val bundle = Bundle()
            view.saveState(bundle)
            callbacks?.saveState(tabId, bundle)
            container.removeView(view)
            view.stopLoading()
            view.destroy()
        }
        live.clear()
        currentTabId = null
    }

    private fun attach(view: WebView) {
        if (view.parent === container) return
        container.removeAllViews()
        container.addView(view)
    }

    private fun evictIfNeeded() {
        while (live.size > maxLiveTabs) {
            val victim = live.entries.firstOrNull { it.key != currentTabId } ?: return
            val tabId = victim.key
            val view = live.remove(tabId) ?: return
            val bundle = Bundle()
            view.saveState(bundle)
            callbacks?.saveState(tabId, bundle)
            container.removeView(view)
            view.stopLoading()
            view.destroy()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(tabId: String): WebView = WebView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        configureSettings()

        webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                if (!request.isForMainFrame) return false
                return handleExternalUrl(context, request.url)
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val filter = requestFilter ?: return null
                // Il documento principale non si blocca mai: il sito deve aprirsi.
                if (request.isForMainFrame) return null
                val blocked = try {
                    filter.shouldBlock(request.url.toString(), view.url)
                } catch (_: RuntimeException) {
                    false
                }
                if (!blocked) return null
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    ByteArrayInputStream(ByteArray(0)),
                )
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                callbacks?.onPageStarted(tabId, url)
                notifyNavigation(tabId, view, url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                callbacks?.onPageFinished(tabId, url, view.title)
                notifyNavigation(tabId, view, url)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                notifyNavigation(tabId, view, url)
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
                callbacks?.onPageError(tabId, PageError(kind = kindFor(code), code = code))
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                sslError: android.net.http.SslError,
            ) {
                // Mai proseguire su un certificato non valido.
                handler.cancel()
                val code = WebViewClient.ERROR_FAILED_SSL_HANDSHAKE
                callbacks?.onPageError(tabId, PageError(kind = PageErrorKind.INSECURE, code = code))
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                callbacks?.onProgressChanged(tabId, newProgress)
            }

            /**
             * `target=_blank` e `window.open()`: invece di sostituire la pagina
             * corrente apriamo una scheda nuova, come farebbe un browser desktop.
             */
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                val newTabId = callbacks?.onCreateWindowTab() ?: return false
                val newView = createWebView(newTabId)
                live[newTabId] = newView
                currentTabId = newTabId
                attach(newView)
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                transport.webView = newView
                resultMsg.sendToTarget()
                evictIfNeeded()
                return true
            }
        }

        setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            callbacks?.onDownloadRequested(
                tabId = tabId,
                url = url,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimeType,
                contentLength = contentLength,
            )
        }
    }

    private fun notifyNavigation(tabId: String, view: WebView, url: String?) {
        callbacks?.onNavigationStateChanged(
            tabId = tabId,
            canGoBack = view.canGoBack(),
            canGoForward = view.canGoForward(),
            url = url,
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.configureSettings() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            // I cookie di terze parti saranno bloccati in Fase 5.
        }
        isVerticalScrollBarEnabled = true
    }

    /** Traduce i codici WebView in messaggi comprensibili. */
    private fun kindFor(code: Int): PageErrorKind = when (code) {
        WebViewClient.ERROR_HOST_LOOKUP,
        WebViewClient.ERROR_CONNECT,
        WebViewClient.ERROR_IO,
        WebViewClient.ERROR_TIMEOUT,
        -> PageErrorKind.NO_CONNECTION

        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> PageErrorKind.INSECURE

        else -> PageErrorKind.GENERIC
    }

    /** Link esterni (tel:, mailto:, app link) si aprono fuori dal browser. */
    private fun handleExternalUrl(context: Context, uri: Uri): Boolean {
        return when (uri.scheme?.lowercase()) {
            null, "http", "https", "about", "file", "data", "javascript" -> false
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
