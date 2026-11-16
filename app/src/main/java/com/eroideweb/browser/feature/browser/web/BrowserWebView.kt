package com.eroideweb.browser.feature.browser.web

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eroideweb.browser.feature.browser.PageError
import com.eroideweb.browser.feature.browser.PageErrorKind

/**
 * WebView configurata per un browser: JS e storage attivi (servono a quasi
 * tutti i siti), mixed content mai ammesso, nessuna finestra popup automatica,
 * niente accesso a file locali.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    controller: WebViewController,
    /** URL da (ri)caricare se la WebView è nuova, es. dopo una rotazione. */
    initialUrl: String?,
    onPageStarted: (String) -> Unit,
    onPageFinished: (String, String?) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onNavigationStateChanged: (canGoBack: Boolean, canGoForward: Boolean, url: String?) -> Unit,
    onPageError: (PageError) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Le callback possono cambiare tra una ricomposizione e l'altra (es. nuovo
    // ViewModel): la WebView creata una volta sola deve leggere sempre l'ultima.
    val started = rememberUpdatedState(onPageStarted)
    val finished = rememberUpdatedState(onPageFinished)
    val progress = rememberUpdatedState(onProgressChanged)
    val navigation = rememberUpdatedState(onNavigationStateChanged)
    val error = rememberUpdatedState(onPageError)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                configureSettings()

                webViewClient = object : WebViewClient() {

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = handleUrl(ctx, request.url)

                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        started.value(url)
                        navigation.value(view.canGoBack(), view.canGoForward(), url)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        finished.value(url, view.title)
                        navigation.value(view.canGoBack(), view.canGoForward(), url)
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        navigation.value(view.canGoBack(), view.canGoForward(), url)
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
                        error.value(PageError(kind = kindFor(code), code = code))
                    }

                    override fun onReceivedSslError(
                        view: WebView,
                        handler: SslErrorHandler,
                        sslError: android.net.http.SslError,
                    ) {
                        // Mai proseguire su un certificato non valido.
                        handler.cancel()
                        error.value(
                            PageError(kind = PageErrorKind.INSECURE, code = WebViewClient.ERROR_FAILED_SSL_HANDSHAKE),
                        )
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        progress.value(newProgress)
                    }
                }

                controller.attach(this)
            }
        },
    )

    // Dopo una ricreazione (rotazione) la WebView è nuova: ricarica l'URL corrente.
    LaunchedEffect(initialUrl) {
        controller.ensureLoaded(initialUrl)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> controller.pause()
                Lifecycle.Event.ON_RESUME -> controller.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(controller) {
        onDispose { controller.destroy() }
    }
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
        setSupportMultipleWindows(false)
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
private fun handleUrl(context: Context, uri: Uri): Boolean {
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
