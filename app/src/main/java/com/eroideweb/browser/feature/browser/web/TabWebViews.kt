package com.eroideweb.browser.feature.browser.web

import android.content.Context
import android.os.Bundle
import android.os.Message
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.webkit.ScriptHandler
import com.eroideweb.browser.R
import com.eroideweb.browser.core.reader.ReaderArticle
import com.eroideweb.browser.feature.browser.PageError

/** Decide se una richiesta di rete va bloccata (pubblicità, tracker). */
fun interface RequestFilter {
    fun shouldBlock(requestUrl: String, pageUrl: String?): Boolean
}

/** Una pagina precedente della scheda (tocco lungo su Indietro). */
data class TabHistoryEntry(
    /** Passi da fare con `goBackOrForward` (negativi = indietro). */
    val offset: Int,
    val url: String,
    val title: String?,
)

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
 *
 * Il tema chiaro/scuro delle pagine dipende dal contesto con cui nasce la
 * WebView: se [display] cambia si crea un'istanza nuova (gli stati salvati
 * fanno ripartire le schede da dove erano, come dopo una rotazione).
 */
class TabWebViews(
    private val context: Context,
    private val requestFilter: RequestFilter? = null,
    private val display: WebDisplay = WebDisplay(darkPages = false, forceDark = false),
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

        /** Risultati di "Trova nella pagina". */
        fun onFindResult(tabId: String, activeMatchOrdinal: Int, matchCount: Int, isDoneCounting: Boolean)

        /** La scheda chiede la versione desktop (letto alla creazione della WebView). */
        fun isDesktopMode(tabId: String): Boolean

        /** La pagina [url] sembra un articolo leggibile in modalità lettura. */
        fun onReaderModeAvailability(tabId: String, url: String, available: Boolean)
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

    /** Script del viewport desktop, per le schede in versione desktop. */
    private val desktopScripts = mutableMapOf<String, ScriptHandler>()

    /** UA di default della WebView, letto dalla prima vista creata. */
    private var defaultUserAgent: String? = null

    /**
     * Le WebView nascono con un tema chiaro o scuro: la pagina lo legge come
     * `prefers-color-scheme` e lo scurimento automatico funziona solo sul scuro.
     */
    private val webContext = ContextThemeWrapper(
        context,
        if (display.darkPages) R.style.Theme_EroideWeb_Web_Dark else R.style.Theme_EroideWeb_Web_Light,
    )

    private val cssDarkFallback = WebViewConfigurator.needsCssDarkFallback(display)

    /** Viste che non supportano lo script a inizio documento: filtro applicato a pagina visibile. */
    private val lateDarkFallback = mutableSetOf<String>()

    /** Dimensione del testo delle pagine (percentuale), applicata subito a tutte le schede. */
    var textZoom: Int = 100
        set(value) {
            if (field == value) return
            field = value
            live.values.forEach { it.settings.textZoom = value }
        }

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

    /** Pagine precedenti della scheda, dalla più recente. */
    fun backHistory(tabId: String, limit: Int = 12): List<TabHistoryEntry> {
        val list = live[tabId]?.copyBackForwardList() ?: return emptyList()
        val current = list.currentIndex
        if (current <= 0) return emptyList()
        return (current - 1 downTo maxOf(0, current - limit)).mapNotNull { index ->
            val item = list.getItemAtIndex(index) ?: return@mapNotNull null
            TabHistoryEntry(offset = index - current, url = item.url, title = item.title)
        }
    }

    fun goToHistoryOffset(tabId: String, offset: Int) {
        live[tabId]?.takeIf { it.canGoBackOrForward(offset) }?.goBackOrForward(offset)
    }

    // ----------------------------------------------------- trova nella pagina

    fun find(tabId: String, query: String) {
        val view = live[tabId] ?: return
        if (query.isEmpty()) view.clearMatches() else view.findAllAsync(query)
    }

    fun findNext(tabId: String, forward: Boolean) {
        live[tabId]?.findNext(forward)
    }

    fun clearFind(tabId: String) {
        live[tabId]?.clearMatches()
    }

    // ------------------------------------------------------ modalità lettura

    /** Estrae l'articolo della scheda con Readability; `null` se non c'è testo da leggere. */
    fun extractArticle(tabId: String, onResult: (ReaderArticle?) -> Unit) {
        val view = live[tabId]
        val url = view?.url
        if (view == null || url == null) {
            onResult(null)
            return
        }
        view.evaluateJavascript(ReaderScripts.extractorScript(context)) { raw ->
            onResult(ReaderScripts.parseArticle(raw, url))
        }
    }

    // ------------------------------------------------------- versione desktop

    /** Cambia UA e viewport della scheda e ricarica la pagina. */
    fun setDesktopMode(tabId: String, enabled: Boolean) {
        val view = live[tabId] ?: return
        applyDesktopMode(tabId, view, enabled)
        view.reload()
    }

    // ----------------------------------------------------------- ciclo di vita

    /** Distrugge la WebView di una scheda chiusa. */
    fun close(tabId: String) {
        val view = live.remove(tabId) ?: return
        destroyView(tabId, view)
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
            destroyView(tabId, view)
        }
        live.clear()
        currentTabId = null
    }

    // ------------------------------------------------- usati dai client WebView

    /** Fine caricamento: avvisa il ViewModel e controlla se la pagina è "da leggere". */
    internal fun onPageFinished(tabId: String, view: WebView, url: String) {
        callbacks?.onPageFinished(tabId, url, view.title)
        notifyNavigation(tabId, view, url)
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        view.evaluateJavascript(ReaderScripts.readerableScript(context)) { result ->
            callbacks?.onReaderModeAvailability(tabId, url, result == "true")
        }
    }

    /** Prima pittura della pagina: momento giusto per il filtro scuro sulle WebView vecchie. */
    internal fun onPageCommitVisible(tabId: String, view: WebView) {
        if (tabId in lateDarkFallback) WebViewConfigurator.applyCssDarkFallbackNow(view)
    }

    internal fun notifyNavigation(tabId: String, view: WebView, url: String?) {
        callbacks?.onNavigationStateChanged(
            tabId = tabId,
            canGoBack = view.canGoBack(),
            canGoForward = view.canGoForward(),
            url = url,
        )
    }

    /** `target=_blank` / `window.open()`: la pagina nuova va in una scheda nuova. */
    internal fun openWindowTab(resultMsg: Message): Boolean {
        val newTabId = callbacks?.onCreateWindowTab() ?: return false
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val newView = createWebView(newTabId)
        live[newTabId] = newView
        currentTabId = newTabId
        attach(newView)
        transport.webView = newView
        resultMsg.sendToTarget()
        evictIfNeeded()
        return true
    }

    // ---------------------------------------------------------------- interni

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
            destroyView(tabId, view)
        }
    }

    private fun destroyView(tabId: String, view: WebView) {
        desktopScripts.remove(tabId)
        lateDarkFallback.remove(tabId)
        container.removeView(view)
        view.stopLoading()
        view.destroy()
    }

    private fun applyDesktopMode(tabId: String, view: WebView, enabled: Boolean) {
        val base = defaultUserAgent ?: view.settings.userAgentString.also { defaultUserAgent = it }
        val script = WebViewConfigurator.applyDesktopMode(
            view = view,
            enabled = enabled,
            defaultUserAgent = base,
            previousScript = desktopScripts.remove(tabId),
        )
        if (script != null) desktopScripts[tabId] = script
    }

    private fun createWebView(tabId: String): WebView = WebView(webContext).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        // Dentro apply `display` e `context` sarebbero quelli della WebView.
        WebViewConfigurator.configure(this, this@TabWebViews.display, this@TabWebViews.textZoom)
        applyDesktopMode(tabId, this, callbacks?.isDesktopMode(tabId) == true)
        if (cssDarkFallback && !WebViewConfigurator.installCssDarkFallback(this)) {
            lateDarkFallback += tabId
        }

        webViewClient = TabWebViewClient(tabId, this@TabWebViews.context, this@TabWebViews, requestFilter)
        webChromeClient = TabChromeClient(tabId, this@TabWebViews)

        setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            callbacks?.onFindResult(tabId, activeMatchOrdinal, numberOfMatches, isDoneCounting)
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
}
