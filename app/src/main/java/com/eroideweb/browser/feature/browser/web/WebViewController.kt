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

    internal fun attach(view: WebView) {
        webView = view
        pendingUrl?.let { url ->
            pendingUrl = null
            view.loadUrl(url)
        }
    }

    internal fun detach(view: WebView) {
        if (webView === view) webView = null
    }

    val canGoBack: Boolean get() = webView?.canGoBack() == true

    fun load(url: String) {
        val view = webView
        if (view == null) pendingUrl = url else view.loadUrl(url)
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
    }
}
