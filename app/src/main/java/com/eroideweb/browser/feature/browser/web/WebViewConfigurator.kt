package com.eroideweb.browser.feature.browser.web

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.eroideweb.browser.core.web.UserAgents

/**
 * Impostazioni comuni a tutte le WebView del browser: sicurezza, zoom,
 * User-Agent e versione desktop. Nessuno stato: lo stato per scheda vive in
 * [TabWebViews].
 */
internal object WebViewConfigurator {

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Pinch-to-zoom sempre disponibile (dove il sito lo consente), senza i
            // vecchi pulsanti +/- sovrapposti alla pagina.
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        view.isVerticalScrollBarEnabled = true
    }

    /**
     * User-Agent mobile pulito oppure desktop, più lo script che allarga il
     * viewport: senza, i siti "responsive" resterebbero in versione mobile anche
     * con uno UA desktop.
     *
     * @return lo script installato (da rimuovere quando si torna al mobile).
     */
    fun applyDesktopMode(
        view: WebView,
        enabled: Boolean,
        defaultUserAgent: String,
        previousScript: ScriptHandler?,
    ): ScriptHandler? {
        view.settings.userAgentString = if (enabled) {
            UserAgents.desktop(defaultUserAgent)
        } else {
            UserAgents.mobile(defaultUserAgent)
        }
        previousScript?.remove()
        if (!enabled || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return null
        }
        return WebViewCompat.addDocumentStartJavaScript(view, DESKTOP_VIEWPORT_JS, setOf("*"))
    }

    /**
     * Forza un viewport largo come quello di un portatile (solo frame principale).
     * L'osservatore copre i meta inseriti dopo l'avvio del documento e si
     * spegne poco dopo il caricamento.
     */
    private const val DESKTOP_VIEWPORT_JS = """
(function () {
  if (window.top !== window) return;
  var CONTENT = 'width=1024';
  function apply() {
    var metas = document.querySelectorAll('meta[name="viewport"]');
    for (var i = 0; i < metas.length; i++) {
      if (metas[i].getAttribute('content') !== CONTENT) metas[i].setAttribute('content', CONTENT);
    }
  }
  var observer = new MutationObserver(apply);
  observer.observe(document, { childList: true, subtree: true });
  document.addEventListener('DOMContentLoaded', function () {
    apply();
    setTimeout(function () { observer.disconnect(); }, 2000);
  });
})();
"""
}
