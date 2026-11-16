package com.eroideweb.browser.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eroideweb.browser.core.adblock.AdBlockProvider
import com.eroideweb.browser.core.bookmark.BookmarkRepository
import com.eroideweb.browser.core.history.HistoryRepository
import com.eroideweb.browser.core.session.SessionRepository
import com.eroideweb.browser.core.session.SessionSnapshot
import com.eroideweb.browser.core.session.SessionTab
import com.eroideweb.browser.core.settings.BrowserSettings
import com.eroideweb.browser.core.settings.SearchEngine
import com.eroideweb.browser.core.settings.SettingsRepository
import com.eroideweb.browser.core.settings.ToolbarPosition
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
        val current = _state.value
        val url = UrlNormalizer.toLoadableUrl(rawInput, current.searchEngine) ?: return null
        hasLocalChanges = true
        val tabId = current.activeTabId

        updateTab(tabId) { tab ->
            tab.copy(
                url = url,
                isLoading = true,
                progress = 0,
                error = null,
                canGoBack = false,
                canGoForward = false,
                isSecure = url.startsWith("https://"),
            )
        }
        _state.update {
            it.copy(addressText = url, isAddressFocused = false)
        }
        persistSession()
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
                isTabSwitcherVisible = false,
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
                isTabSwitcherVisible = false,
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
                isTabSwitcherVisible = false,
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
                    isTabSwitcherVisible = false,
                )
            }
            val nextActive = if (current.activeTabId == tabId) {
                // La scheda vicina a quella chiusa, come fanno i browser desktop.
                remaining[index.coerceAtMost(remaining.lastIndex)]
            } else {
                remaining.firstOrNull { it.id == current.activeTabId } ?: remaining.first()
            }
            current.copy(
                tabs = remaining,
                activeTabId = nextActive.id,
                addressText = nextActive.url.orEmpty(),
                isTabSwitcherVisible = false,
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
                tabs = current.tabs + TabUi(id = id, url = tab.url, title = tab.title),
                activeTabId = id,
                addressText = tab.url.orEmpty(),
                isTabSwitcherVisible = false,
            )
        }
        persistSession()
    }

    fun setTabSwitcherVisible(visible: Boolean) {
        _state.update { it.copy(isTabSwitcherVisible = visible) }
    }

    fun toggleTabSwitcher() {
        _state.update { it.copy(isTabSwitcherVisible = !it.isTabSwitcherVisible) }
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
            )
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

    // ------------------------------------------------------------ libreria

    fun setLibraryVisible(visible: Boolean) {
        _state.update { it.copy(isLibraryVisible = visible) }
    }

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

    // ------------------------------------------------------------ settings

    fun setToolbarPosition(position: ToolbarPosition) {
        // Aggiornamento ottimistico: la UI non deve aspettare il disco.
        _state.update { it.copy(toolbarPosition = position) }
        viewModelScope.launch { settingsRepository.setToolbarPosition(position) }
    }

    fun setSearchEngine(engine: SearchEngine) {
        _state.update { it.copy(searchEngine = engine) }
        viewModelScope.launch { settingsRepository.setSearchEngine(engine) }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        _state.update { it.copy(adBlockEnabled = enabled) }
        viewModelScope.launch { settingsRepository.setAdBlockEnabled(enabled) }
    }

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
            )
        }
    }

    // -------------------------------------------------------------- interni

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

        val tabs = snapshot.tabs.map { TabUi(id = it.id, url = it.url, title = it.title) }
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
            tabs = current.tabs.map { SessionTab(id = it.id, url = it.url, title = it.title) },
            activeTabId = current.activeTabId,
        )
        viewModelScope.launch { sessionRepository.save(snapshot) }
    }

    private fun newTabId(): String = UUID.randomUUID().toString()
}
