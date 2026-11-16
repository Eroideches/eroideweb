package com.eroideweb.browser.feature.browser.web

import android.webkit.WebView

/**
 * Piccolo ponte imperativo verso la WebView. Vive nel layer UI e non nel
 * ViewModel, così lo stato resta testabile su JVM.
 */
class WebViewController {

    private var webView: WebView? = null

    /** URL richiesto prima che la WebView esistesse (es. primo caricamento). */
    private var pendingUrl: String? = null

    /** Ultimo URL chiesto a questa istanza: evita doppi caricamenti. */
    private var lastRequestedUrl: String? = null

    internal fun attach(view: WebView) {
        webView = view
        pendingUrl?.let { url ->
            pendingUrl = null
            lastRequestedUrl = url
            view.loadUrl(url)
        }
    }

    val canGoBack: Boolean get() = webView?.canGoBack() == true

    fun load(url: String) {
        lastRequestedUrl = url
        val view = webView
        if (view == null) pendingUrl = url else view.loadUrl(url)
    }

    /**
     * Carica [url] solo se questa WebView non ha ancora una pagina.
     * Serve dopo una ricreazione dell'activity (rotazione): il ViewModel
     * conserva l'URL, la WebView nuova parte vuota.
     */
    fun ensureLoaded(url: String?) {
        if (url.isNullOrBlank()) return
        if (lastRequestedUrl == url) return
        val view = webView
        if (view == null) {
            pendingUrl = url
            return
        }
        if (view.url.isNullOrBlank()) {
            lastRequestedUrl = url
            view.loadUrl(url)
        }
    }

    fun reload() {
        webView?.reload()
    }

    fun stopLoading() {
        webView?.stopLoading()
    }

    fun goBack() {
        webView?.takeIf { it.canGoBack() }?.goBack()
    }

    fun goForward() {
        webView?.takeIf { it.canGoForward() }?.goForward()
    }

    fun currentUrl(): String? = webView?.url

    fun pause() {
        webView?.onPause()
        webView?.pauseTimers()
    }

    fun resume() {
        webView?.onResume()
        webView?.resumeTimers()
    }

    fun destroy() {
        webView?.let { view ->
            view.stopLoading()
            view.webViewClient = android.webkit.WebViewClient()
            view.destroy()
        }
        webView = null
        pendingUrl = null
        lastRequestedUrl = null
    }
}
