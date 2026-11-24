package com.eroideweb.browser.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eroideweb.browser.core.adblock.AdBlockProvider
import com.eroideweb.browser.core.bookmark.BookmarkRepository
import com.eroideweb.browser.core.history.HistoryRepository
import com.eroideweb.browser.core.reader.ReaderArticle
import com.eroideweb.browser.core.session.SessionRepository
import com.eroideweb.browser.core.session.SessionSnapshot
import com.eroideweb.browser.core.session.SessionTab
import com.eroideweb.browser.core.settings.BrowserSettings
import com.eroideweb.browser.core.settings.ReaderTheme
import com.eroideweb.browser.core.settings.SettingsRepository
import com.eroideweb.browser.core.settings.setAdBlockDisabledForSite
import com.eroideweb.browser.core.settings.setReaderSerif
import com.eroideweb.browser.core.settings.setReaderTextZoom
import com.eroideweb.browser.core.settings.setReaderTheme
import com.eroideweb.browser.core.settings.setTranslateNoticeAccepted
import com.eroideweb.browser.core.translate.TranslateUrl
import com.eroideweb.browser.core.url.UrlNormalizer
import com.eroideweb.browser.core.url.urlHost
import com.eroideweb.browser.feature.browser.web.TabStateCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Stato della schermata browser e delle schede. Le operazioni imperative sulla
 * WebView (loadUrl, reload, back) passano da [TabWebViews], non da qui:
 * il ViewModel resta testabile su JVM.
 *
 * Le impostazioni si cambiano dalla schermata Impostazioni (SettingsViewModel):
 * qui arrivano dal flow del repository.
 */
class BrowserViewModel(
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val historyRepository: HistoryRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val adBlocker: AdBlockProvider,
    private val privateMode: Boolean = false,
) : ViewModel() {

    private val initialTabId = newTabId()

    private val _state = MutableStateFlow(
        BrowserUiState(
            tabs = listOf(TabUi(id = initialTabId)),
            activeTabId = initialTabId,
            isPrivate = privateMode,
        ),
    )
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    /**
     * Stati delle WebView tra due composizioni (rotazione). Non è stato
     * dell'interfaccia: la UI lo legge solo tramite le callback della webview.
     */
    val webViewStates = TabStateCache()

    /** Se l'utente ha già toccato qualcosa, il ripristino sessione non deve sovrascrivere. */
    private var hasLocalChanges = false

    /** Ultima visita registrata per scheda: ricariche ripetute non creano doppioni. */
    private val lastRecordedVisit = mutableMapOf<String, String>()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                applySettings(settings)
            }
        }
        viewModelScope.launch { restoreSession() }
        viewModelScope.launch {
            bookmarkRepository.observeAll().collect { bookmarks ->
                val urls = bookmarks.mapTo(mutableSetOf()) { it.url }
                _state.update { it.copy(bookmarkedUrls = urls) }
            }
        }
    }

    // ---------------------------------------------------------------- barra

    fun onAddressBarTextChange(text: String) {
        _state.update { it.copy(addressText = text) }
    }

    fun onAddressBarFocusChange(focused: Boolean) {
        _state.update { current ->
            current.copy(
                isAddressFocused = focused,
                // Uscendo dalla barra senza aver navigato, torna a mostrare l'URL reale.
                addressText = if (!focused) current.activeTab.url ?: current.addressText else current.addressText,
            )
        }
    }

    /**
     * Normalizza il testo digitato e prepara il caricamento nella scheda attiva.
     *
     * [rawInput] è passato esplicitamente: perdere il focus prima del submit
     * non deve cancellare quello che l'utente ha appena scritto.
     *
     * @return l'URL da passare alla WebView, oppure `null` se non c'è nulla da caricare.
     */
    fun submitAddressBar(rawInput: String): String? {
        val url = UrlNormalizer.toLoadableUrl(rawInput, _state.value.searchEngine) ?: return null
        navigateActiveTab(url)
        return url
    }

    // --------------------------------------------------------------- schede

    fun openNewTab() {
        val id = newTabId()
        hasLocalChanges = true
        _state.update { current ->
            current.copy(
                tabs = current.tabs + TabUi(id = id),
                activeTabId = id,
                addressText = "",
                isAddressFocused = false,
                overlay = BrowserOverlay.NONE,
                findInPage = null,
            )
        }
        persistSession()
    }

    /** Crea una scheda per un `target=_blank` e la rende attiva. */
    fun openTabFromWindow(): String {
        val id = newTabId()
        hasLocalChanges = true
        _state.update { current ->
            current.copy(
                tabs = current.tabs + TabUi(id = id),
                activeTabId = id,
                addressText = "",
                isAddressFocused = false,
                overlay = BrowserOverlay.NONE,
                findInPage = null,
            )
        }
        persistSession()
        return id
    }

    fun selectTab(tabId: String) {
        val tab = _state.value.tabs.firstOrNull { it.id == tabId } ?: return
        hasLocalChanges = true
        _state.update { current ->
            current.copy(
                activeTabId = tabId,
                addressText = tab.url.orEmpty(),
                isAddressFocused = false,
                overlay = BrowserOverlay.NONE,
                findInPage = if (current.activeTabId == tabId) current.findInPage else null,
            )
        }
        persistSession()
    }

    fun closeTab(tabId: String) {
        hasLocalChanges = true
        webViewStates.remove(tabId)
        lastRecordedVisit.remove(tabId)
        _state.update { current ->
            val index = current.tabs.indexOfFirst { it.id == tabId }
            // Doppio tocco sullo stesso pulsante: la scheda è già sparita.
            if (index < 0) return@update current
            val remaining = current.tabs.filterNot { it.id == tabId }
            if (remaining.isEmpty()) {
                val id = newTabId()
                return@update current.copy(
                    tabs = listOf(TabUi(id = id)),
                    activeTabId = id,
                    addressText = "",
                    overlay = BrowserOverlay.NONE,
                    findInPage = null,
                )
            }
            val closingActive = current.activeTabId == tabId
            val nextActive = if (closingActive) {
                // La scheda vicina a quella chiusa, come fanno i browser desktop.
                remaining[index.coerceAtMost(remaining.lastIndex)]
            } else {
                remaining.firstOrNull { it.id == current.activeTabId } ?: remaining.first()
            }
            current.copy(
                tabs = remaining,
                activeTabId = nextActive.id,
                addressText = nextActive.url.orEmpty(),
                overlay = if (current.overlay == BrowserOverlay.TABS) BrowserOverlay.NONE else current.overlay,
                findInPage = if (closingActive) null else current.findInPage,
            )
        }
        persistSession()
    }

    fun duplicateTab(tabId: String) {
        val tab = _state.value.tabs.firstOrNull { it.id == tabId } ?: return
        val id = newTabId()
        hasLocalChanges = true
        _state.update { current ->
            current.copy(
                tabs = current.tabs + TabUi(
                    id = id,
                    url = tab.url,
                    title = tab.title,
                    desktopMode = tab.desktopMode,
                ),
                activeTabId = id,
                addressText = tab.url.orEmpty(),
                overlay = BrowserOverlay.NONE,
                findInPage = null,
            )
        }
        persistSession()
    }

    fun setTabSwitcherVisible(visible: Boolean) {
        setOverlay(BrowserOverlay.TABS, visible)
    }

    fun toggleTabSwitcher() {
        setOverlay(BrowserOverlay.TABS, _state.value.overlay != BrowserOverlay.TABS)
    }

    // ------------------------------------------------------------ pannelli

    fun openMenu() {
        _state.update { it.copy(overlay = BrowserOverlay.MENU, isAddressFocused = false) }
    }

    fun openSettings() {
        _state.update { it.copy(overlay = BrowserOverlay.SETTINGS) }
    }

    fun setLibraryVisible(visible: Boolean) {
        setOverlay(BrowserOverlay.LIBRARY, visible)
    }

    /** Chiude il pannello o la schermata in primo piano. */
    fun closeOverlay() {
        _state.update { it.copy(overlay = BrowserOverlay.NONE, readerArticle = null) }
    }

    private fun setOverlay(overlay: BrowserOverlay, visible: Boolean) {
        _state.update { current ->
            when {
                visible -> current.copy(overlay = overlay)
                current.overlay == overlay -> current.copy(overlay = BrowserOverlay.NONE)
                else -> current
            }
        }
    }

    // ------------------------------------------------------- eventi pagina

    fun onPageStarted(tabId: String, url: String) {
        updateTab(tabId) { tab ->
            tab.copy(
                url = url,
                isLoading = true,
                progress = if (tab.progress == 0) 5 else tab.progress,
                error = null,
                isSecure = url.startsWith("https://"),
                readerAvailable = false,
            )
        }
        // Una pagina nuova nella scheda attiva rende inutili i risultati della ricerca.
        _state.update { current ->
            if (current.activeTabId == tabId && current.findInPage != null) {
                current.copy(findInPage = null)
            } else {
                current
            }
        }
    }

    fun onPageFinished(tabId: String, url: String, title: String?) {
        updateTab(tabId) { tab ->
            tab.copy(
                url = url,
                title = title ?: tab.title,
                isLoading = false,
                progress = 100,
                isSecure = url.startsWith("https://"),
            )
        }
        recordVisit(tabId, url, title)
        persistSession()
    }

    /**
     * Registra la visita una sola volta per pagina: `onPageFinished` può
     * arrivare più volte (redirect, annullamenti) e non vogliamo righe doppie.
     */
    private fun recordVisit(tabId: String, url: String, title: String?) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        if (lastRecordedVisit[tabId] == url) return
        lastRecordedVisit[tabId] = url
        viewModelScope.launch { historyRepository.record(url, title) }
    }

    fun onProgressChanged(tabId: String, progress: Int) {
        val clamped = progress.coerceIn(0, 100)
        updateTab(tabId) { tab ->
            tab.copy(progress = clamped, isLoading = clamped < 100)
        }
    }

    fun onNavigationStateChanged(
        tabId: String,
        canGoBack: Boolean,
        canGoForward: Boolean,
        url: String?,
    ) {
        updateTab(tabId) { tab ->
            tab.copy(
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                url = url ?: tab.url,
                isSecure = url?.startsWith("https://") ?: tab.isSecure,
            )
        }
    }

    fun onPageError(tabId: String, error: PageError) {
        updateTab(tabId) { tab ->
            tab.copy(isLoading = false, error = error)
        }
    }

    fun clearError(tabId: String) {
        updateTab(tabId) { tab ->
            tab.copy(error = null, isLoading = true)
        }
    }

    // ------------------------------------------------------------ libreria

    /** Aggiunge o toglie dai segnalibri la pagina attiva. */
    fun toggleBookmark() {
        val tab = _state.value.activeTab
        val url = tab.url ?: return
        viewModelScope.launch {
            if (url in _state.value.bookmarkedUrls) {
                bookmarkRepository.removeByUrl(url)
            } else {
                bookmarkRepository.add(url, tab.title)
            }
        }
    }

    // --------------------------------------------------- trova nella pagina

    /** Apre la barra di ricerca nella pagina (solo su una pagina web vera). */
    fun startFindInPage() {
        _state.update { current ->
            if (!current.hasWebPage) return@update current
            current.copy(
                findInPage = current.findInPage ?: FindInPageState(),
                overlay = BrowserOverlay.NONE,
                isAddressFocused = false,
            )
        }
    }

    fun onFindQueryChange(query: String) {
        _state.update { current ->
            val find = current.findInPage ?: return@update current
            current.copy(
                findInPage = if (query.isEmpty()) {
                    FindInPageState()
                } else {
                    find.copy(query = query)
                },
            )
        }
    }

    /** Risultato asincrono di `findAllAsync`/`findNext` della WebView. */
    fun onFindResult(tabId: String, activeMatchOrdinal: Int, matchCount: Int) {
        _state.update { current ->
            val find = current.findInPage ?: return@update current
            if (current.activeTabId != tabId || find.query.isEmpty()) return@update current
            current.copy(
                findInPage = find.copy(
                    activeMatch = if (matchCount > 0) activeMatchOrdinal + 1 else 0,
                    matchCount = matchCount,
                ),
            )
        }
    }

    fun closeFindInPage() {
        _state.update { it.copy(findInPage = null) }
    }

    // ----------------------------------------------------- versione desktop

    /**
     * Alterna la versione desktop della scheda attiva.
     * @return il nuovo stato, `null` se la scheda è sulla pagina iniziale.
     */
    fun toggleDesktopMode(): Boolean? {
        val tab = _state.value.activeTab
        if (tab.url == null) return null
        val enabled = !tab.desktopMode
        hasLocalChanges = true
        updateTab(tab.id) { it.copy(desktopMode = enabled) }
        _state.update { it.copy(overlay = BrowserOverlay.NONE) }
        persistSession()
        return enabled
    }

    fun isDesktopMode(tabId: String): Boolean =
        _state.value.tabs.firstOrNull { it.id == tabId }?.desktopMode == true

    // ------------------------------------------------------ modalità lettura

    /** Esito del controllo "è un articolo?" a fine caricamento. */
    fun onReaderModeAvailability(tabId: String, url: String, available: Boolean) {
        updateTab(tabId) { tab ->
            // Una risposta in ritardo per una pagina già lasciata non conta.
            if (tab.url == url) tab.copy(readerAvailable = available) else tab
        }
    }

    /** Mostra l'articolo estratto, se appartiene ancora alla pagina attiva. */
    fun openReader(article: ReaderArticle) {
        _state.update { current ->
            if (current.activeTab.url != article.url) return@update current
            current.copy(
                readerArticle = article,
                overlay = BrowserOverlay.READER,
                findInPage = null,
                isAddressFocused = false,
            )
        }
    }

    fun closeReader() {
        _state.update { current ->
            current.copy(
                readerArticle = null,
                overlay = if (current.overlay == BrowserOverlay.READER) BrowserOverlay.NONE else current.overlay,
            )
        }
    }

    fun setReaderTextZoom(percent: Int) {
        viewModelScope.launch { settingsRepository.setReaderTextZoom(percent) }
    }

    fun setReaderTheme(theme: ReaderTheme) {
        viewModelScope.launch { settingsRepository.setReaderTheme(theme) }
    }

    fun setReaderSerif(serif: Boolean) {
        viewModelScope.launch { settingsRepository.setReaderSerif(serif) }
    }

    // ------------------------------------------------------------ traduzione

    fun acceptTranslateNotice() {
        _state.update { it.copy(translateNoticeAccepted = true) }
        viewModelScope.launch { settingsRepository.setTranslateNoticeAccepted(true) }
    }

    /**
     * Prepara la traduzione della pagina attiva in [targetLanguage], oppure il
     * ritorno all'originale se la pagina è già tradotta.
     * @return l'URL da caricare, `null` se non c'è nulla da fare.
     */
    fun translateActivePage(targetLanguage: String): String? {
        val url = _state.value.activeTab.url ?: return null
        val target = if (TranslateUrl.isTranslated(url)) {
            TranslateUrl.originalUrl(url)
        } else {
            TranslateUrl.forPage(url, targetLanguage)
        } ?: return null
        navigateActiveTab(target)
        return target
    }

    // ------------------------------------------------------------ settings

    /** Attiva o disattiva il blocco solo per il sito della scheda attiva. */
    fun toggleAdBlockForSite() {
        val host = urlHost(_state.value.activeTab.url) ?: return
        val nowDisabled = host !in _state.value.adBlockDisabledSites
        _state.update { current ->
            current.copy(
                adBlockDisabledSites = if (nowDisabled) {
                    current.adBlockDisabledSites + host
                } else {
                    current.adBlockDisabledSites - host
                },
            )
        }
        viewModelScope.launch { settingsRepository.setAdBlockDisabledForSite(host, nowDisabled) }
    }

    /**
     * Interrogato dal thread di rete della WebView per ogni sotto-risorsa.
     * Deve restare economico: solo letture immutabili e lookup in hash set.
     */
    fun shouldBlockRequest(requestUrl: String, pageUrl: String?): Boolean {
        val engine = adBlocker.engineOrNull() ?: return false
        val current = _state.value
        if (!current.adBlockEnabled) return false
        val pageHost = urlHost(pageUrl)
        if (pageHost != null && pageHost in current.adBlockDisabledSites) return false
        return engine.isBlocked(requestUrl, pageUrl)
    }

    private fun applySettings(settings: BrowserSettings) {
        _state.update {
            it.copy(
                searchEngine = settings.searchEngine,
                toolbarPosition = settings.toolbarPosition,
                adBlockEnabled = settings.adBlockEnabled,
                adBlockDisabledSites = settings.adBlockDisabledSites,
                translateNoticeAccepted = settings.translateNoticeAccepted,
            )
        }
    }

    // -------------------------------------------------------------- interni

    /** Porta la scheda attiva su [url]: la WebView la carica subito dopo. */
    private fun navigateActiveTab(url: String) {
        hasLocalChanges = true
        val tabId = _state.value.activeTabId
        updateTab(tabId) { tab ->
            tab.copy(
                url = url,
                isLoading = true,
                progress = 0,
                error = null,
                isSecure = url.startsWith("https://"),
            )
        }
        _state.update {
            it.copy(addressText = url, isAddressFocused = false, overlay = BrowserOverlay.NONE, findInPage = null)
        }
        persistSession()
    }

    /**
     * Applica [transform] alla sola scheda indicata. Il testo della barra
     * segue gli eventi della scheda attiva, mai di una scheda in background.
     */
    private fun updateTab(tabId: String, transform: (TabUi) -> TabUi) {
        _state.update { current ->
            val index = current.tabs.indexOfFirst { it.id == tabId }
            if (index < 0) return@update current
            val before = current.tabs[index]
            val after = transform(before)
            if (after == before) return@update current

            val tabs = current.tabs.toMutableList().apply { this[index] = after }
            val isActive = current.activeTabId == tabId
            current.copy(
                tabs = tabs,
                addressText = if (isActive && !current.isAddressFocused) {
                    after.url ?: current.addressText
                } else {
                    current.addressText
                },
            )
        }
    }

    private suspend fun restoreSession() {
        val snapshot = sessionRepository.load() ?: return
        if (hasLocalChanges || snapshot.tabs.isEmpty()) return

        val tabs = snapshot.tabs.map {
            TabUi(id = it.id, url = it.url, title = it.title, desktopMode = it.desktopMode)
        }
        val active = tabs.firstOrNull { it.id == snapshot.activeTabId } ?: tabs.first()
        _state.update { current ->
            current.copy(
                tabs = tabs,
                activeTabId = active.id,
                addressText = active.url.orEmpty(),
            )
        }
    }

    private fun persistSession() {
        val current = _state.value
        val snapshot = SessionSnapshot(
            tabs = current.tabs.map {
                SessionTab(id = it.id, url = it.url, title = it.title, desktopMode = it.desktopMode)
            },
            activeTabId = current.activeTabId,
        )
        viewModelScope.launch { sessionRepository.save(snapshot) }
    }

    private fun newTabId(): String = UUID.randomUUID().toString()
}
