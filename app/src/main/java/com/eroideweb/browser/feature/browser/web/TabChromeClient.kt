package com.eroideweb.browser.feature.browser.web

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
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

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = host.callbacks?.onShowFileChooser(filePathCallback, fileChooserParams) ?: false

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        val callbacks = host.callbacks
        if (callbacks == null) {
            callback.onCustomViewHidden()
            return
        }
        callbacks.onShowCustomView(view, callback)
    }

    override fun onHideCustomView() {
        host.callbacks?.onHideCustomView()
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        host.callbacks?.onGeolocationRequest(tabId, origin, callback)
            ?: callback.invoke(origin, false, false)
    }

    override fun onGeolocationPermissionsHidePrompt() {
        host.callbacks?.onGeolocationRequestCanceled(tabId)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        host.callbacks?.onPermissionRequest(tabId, request) ?: request.deny()
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest) {
        host.callbacks?.onPermissionRequestCanceled(tabId, request)
    }

    /** Senza poster il lettore video mostra un riquadro grigio con una freccia. */
    override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

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
