package com.eroideweb.browser.feature.browser.web

import android.graphics.Bitmap
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebView

/** Avanzamento del caricamento e finestre nuove (`target=_blank`, `window.open()`). */
internal class TabChromeClient(
    private val tabId: String,
    private val host: TabWebViews,
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        host.callbacks?.onProgressChanged(tabId, newProgress)
    }

    /** Favicon della pagina: arriva con la pagina stessa, nessuna richiesta in più. */
    override fun onReceivedIcon(view: WebView, icon: Bitmap) {
        val url = view.url ?: return
        host.callbacks?.onFavicon(tabId, url, icon)
    }

    /**
     * Invece di sostituire la pagina corrente apriamo una scheda nuova,
     * come farebbe un browser desktop.
     */
    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean = host.openWindowTab(resultMsg)
}
