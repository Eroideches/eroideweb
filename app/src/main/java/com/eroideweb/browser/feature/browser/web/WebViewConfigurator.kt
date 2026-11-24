package com.eroideweb.browser.feature.browser.web

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.eroideweb.browser.core.web.UserAgents

/**
 * Aspetto delle pagine.
 * @property darkPages la pagina vede `prefers-color-scheme: dark` (tema app scuro o forzato).
 * @property forceDark scurisce anche i siti che non hanno un tema scuro.
 */
data class WebDisplay(
    val darkPages: Boolean,
    val forceDark: Boolean,
)

/**
 * Impostazioni comuni a tutte le WebView del browser: sicurezza, zoom,
 * User-Agent e versione desktop. Nessuno stato: lo stato per scheda vive in
 * [TabWebViews].
 */
internal object WebViewConfigurator {

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(view: WebView, display: WebDisplay, textZoom: Int) {
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
            this.textZoom = textZoom
        }
        view.isVerticalScrollBarEnabled = true
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            // Agisce solo se il tema della WebView è scuro (vedi Theme.EroideWeb.Web.Dark).
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings, display.forceDark)
        }
    }

    /**
     * Lo scurimento automatico della WebView richiede Android 10 (il tema deve
     * poter dire `isLightTheme`) e una WebView recente: altrimenti si usa un
     * filtro CSS, meno preciso ma efficace.
     */
    fun needsCssDarkFallback(display: WebDisplay): Boolean =
        display.forceDark && (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)
            )

    /** Installa il filtro CSS scuro a inizio documento, se la WebView lo consente. */
    fun installCssDarkFallback(view: WebView): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false
        WebViewCompat.addDocumentStartJavaScript(view, DARK_CSS_JS, setOf("*"))
        return true
    }

    /** Stesso filtro, applicato a pagina già visibile (WebView vecchie). */
    fun applyCssDarkFallbackNow(view: WebView) {
        view.evaluateJavascript(DARK_CSS_JS, null)
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
     * Inverte i colori della pagina e ri-inverte immagini e video, che così
     * restano naturali. Solo nel frame principale: i frame interni sono già
     * coperti dal filtro della pagina che li contiene.
     */
    private const val DARK_CSS_JS = """
(function () {
  if (window.top !== window) return;
  var CSS = 'html{filter:invert(1) hue-rotate(180deg)!important;background:#fff!important}' +
    'img,video,picture,canvas,iframe,embed,object,[style*="background-image"]' +
    '{filter:invert(1) hue-rotate(180deg)!important}';
  function add() {
    if (document.getElementById('eroide-dark')) return true;
    var parent = document.head || document.documentElement;
    if (!parent) return false;
    var style = document.createElement('style');
    style.id = 'eroide-dark';
    style.textContent = CSS;
    parent.appendChild(style);
    return true;
  }
  if (add()) return;
  var observer = new MutationObserver(function () { if (add()) observer.disconnect(); });
  observer.observe(document, { childList: true, subtree: true });
})();
"""

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
