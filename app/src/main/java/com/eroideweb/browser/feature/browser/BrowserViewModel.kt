package com.eroideweb.browser.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eroideweb.browser.core.adblock.AdBlockProvider
import com.eroideweb.browser.core.bookmark.BookmarkRepository
import com.eroideweb.browser.core.favorites.FavoriteRepository
import com.eroideweb.browser.core.history.HistoryRepository
import com.eroideweb.browser.core.password.NoOpPasswordRepository
import com.eroideweb.browser.core.password.PasswordRepository
import com.eroideweb.browser.core.password.SaveDecision
import com.eroideweb.browser.core.reader.ReaderArticle
import com.eroideweb.browser.core.session.SessionRepository
import com.eroideweb.browser.core.session.SessionSnapshot
import com.eroideweb.browser.core.session.SessionTab
import com.eroideweb.browser.core.settings.BrowserSettings
import com.eroideweb.browser.core.settings.ReaderTheme
import com.eroideweb.browser.core.settings.SettingsRepository
import com.eroideweb.browser.core.settings.setAdBlockDisabledForSite
import com.eroideweb.browser.core.settings.setPasswordNeverSite
import com.eroideweb.browser.core.settings.setReaderSerif
import com.eroideweb.browser.core.settings.setReaderTextZoom
import com.eroideweb.browser.core.settings.setReaderTheme
import com.eroideweb.browser.core.settings.setTranslateNoticeAccepted
import com.eroideweb.browser.core.suggest.Suggestion
import com.eroideweb.browser.core.suggest.SuggestionCandidate
import com.eroideweb.browser.core.suggest.SuggestionRanker
import com.eroideweb.browser.core.suggest.SuggestionSource
import com.eroideweb.browser.core.translate.TranslateUrl
import com.eroideweb.browser.core.url.IncomingLink
import com.eroideweb.browser.core.url.UrlNormalizer
import com.eroideweb.browser.core.url.urlHost
import com.eroideweb.browser.feature.browser.web.TabStateCache
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
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
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class BrowserViewModel(
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val historyRepository: HistoryRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val adBlocker: AdBlockProvider,
    private val favoriteRepository: FavoriteRepository,
    private val passwordRepository: PasswordRepository = NoOpPasswordRepository,
    private val privateMode: Boolean = false,
) : ViewModel() {

    /** Ultime impostazioni lette (servono alle decisioni sulle password). */
    private var currentSettings = BrowserSettings()

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

    /** Schede dell'ultima chiusura, con la posizione che avevano. */
    private var closedGroup: List<Pair<Int, TabUi>> = emptyList()
    private var closedActiveTabId: String? = null

    /** Scheda vuota creata solo perché si erano chiuse tutte: "Annulla" la toglie. */
    private var placeholderTabId: String? = null
    private var undoCounter = 0L

    /** Testo per i suggerimenti: vuoto quando la barra non è in modifica. */
    private val suggestionQuery = MutableStateFlow("")

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
        viewModelScope.launch {
            favoriteRepository.observeAll().collect { favorites ->
                _state.update { it.copy(favorites = favorites) }
            }
        }
        viewModelScope.launch {
            suggestionQuery
                .debounce(SUGGESTION_DEBOUNCE_MS)
                .mapLatest { query -> loadSuggestions(query) }
                .collect { suggestions -> _state.update { it.copy(suggestions = suggestions) } }
        }
    }

    // ---------------------------------------------------------------- barra

    fun onAddressBarTextChange(text: String) {
        _state.update { it.copy(addressText = text) }
        if (_state.value.isAddressFocused) suggestionQuery.value = text
    }

    fun onAddressBarFocusChange(focused: Boolean) {
        _state.update { current ->
            current.copy(
                isAddressFocused = focused,
                // Uscendo dalla barra senza aver navigato, torna a mostrare l'URL reale.
                addressText = if (!focused) current.activeTab.url ?: current.addressText else current.addressText,
                suggestions = if (focused) current.suggestions else emptyList(),
            )
        }
        // I suggerimenti partono solo quando si scrive, non al tocco sulla barra.
        suggestionQuery.value = ""
    }

    private suspend fun loadSuggestions(query: String): List<Suggestion> {
        if (query.isBlank()) return emptyList()
        val candidates = buildList {
            _state.value.favorites.forEach { add(SuggestionCandidate(it.url, it.title, SuggestionSource.FAVORITE)) }
            bookmarkRepository.suggest(query, SUGGESTION_SOURCE_LIMIT).forEach {
                add(SuggestionCandidate(it.url, it.title, SuggestionSource.BOOKMARK))
            }
            historyRepository.suggest(query, SUGGESTION_SOURCE_LIMIT * 2).forEach {
                add(SuggestionCandidate(it.url, it.title, SuggestionSource.HISTORY, it.visitCount))
            }
        }
        return SuggestionRanker.rank(query, candidates, SUGGESTION_LIMIT)
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

    /**
     * Chiude una scheda. Resta riapribile con [undoCloseTab] finché l'avviso
     * è visibile: la WebView salva il suo stato e la scheda torna dov'era.
     */
    fun closeTab(tabId: String, offerUndo: Boolean = true) {
        val current = _state.value
        val index = current.tabs.indexOfFirst { it.id == tabId }
        // Doppio tocco sullo stesso pulsante: la scheda è già sparita.
        if (index < 0) return
        hasLocalChanges = true
        lastRecordedVisit.remove(tabId)
        if (offerUndo) {
            rememberClosed(listOf(index to current.tabs[index]), current.activeTabId)
        } else {
            dropClosedGroup()
            webViewStates.remove(tabId)
        }
        val undo = if (offerUndo) nextUndo(1) else null
        _state.update { state ->
            val remaining = state.tabs.filterNot { it.id == tabId }
            if (remaining.isEmpty()) {
                val id = newTabId()
                placeholderTabId = id
                return@update state.copy(
                    tabs = listOf(TabUi(id = id)),
                    activeTabId = id,
                    addressText = "",
                    overlay = BrowserOverlay.NONE,
                    findInPage = null,
                    undoClose = undo,
                )
            }
            val closingActive = state.activeTabId == tabId
            val nextActive = if (closingActive) {
                // La scheda vicina a quella chiusa, come fanno i browser desktop.
                remaining[index.coerceAtMost(remaining.lastIndex)]
            } else {
                remaining.firstOrNull { it.id == state.activeTabId } ?: remaining.first()
            }
            state.copy(
                tabs = remaining,
                activeTabId = nextActive.id,
                addressText = nextActive.url.orEmpty(),
                findInPage = if (closingActive) null else state.findInPage,
                undoClose = undo,
            )
        }
        persistSession()
    }

    /** Chiude tutte le schede in un colpo solo (annullabile anche questo). */
    fun closeAllTabs() {
        val current = _state.value
        if (current.tabs.all { it.url == null } && current.tabs.size == 1) return
        hasLocalChanges = true
        lastRecordedVisit.clear()
        rememberClosed(current.tabs.mapIndexed { index, tab -> index to tab }, current.activeTabId)
        val id = newTabId()
        placeholderTabId = id
        _state.update {
            it.copy(
                tabs = listOf(TabUi(id = id)),
                activeTabId = id,
                addressText = "",
                overlay = BrowserOverlay.NONE,
                findInPage = null,
                undoClose = nextUndo(current.tabs.size),
            )
        }
        persistSession()
    }

    /** Riapre le schede dell'ultima chiusura nella loro posizione. */
    fun undoCloseTab() {
        val group = closedGroup
        if (group.isEmpty()) return
        closedGroup = emptyList()
        _state.update { current ->
            val tabs = current.tabs.toMutableList()
            // La scheda vuota nata dalla chiusura non serve più.
            val placeholder = placeholderTabId
            if (placeholder != null && tabs.size == 1 && tabs[0].id == placeholder && tabs[0].url == null) {
                tabs.clear()
            }
            group.sortedBy { it.first }.forEach { (index, tab) ->
                if (tabs.none { it.id == tab.id }) {
                    tabs.add(index.coerceAtMost(tabs.size), tab.copy(isLoading = false, progress = 0, error = null))
                }
            }
            val active = tabs.firstOrNull { it.id == closedActiveTabId }
                ?: tabs.firstOrNull { it.id == current.activeTabId }
                ?: tabs.first()
            current.copy(
                tabs = tabs,
                activeTabId = active.id,
                addressText = active.url.orEmpty(),
                undoClose = null,
            )
        }
        placeholderTabId = null
        persistSession()
    }

    /**
     * Chiude tutto senza possibilità di annullare (dopo "Elimina dati di
     * navigazione"): nessuno stato delle pagine deve sopravvivere.
     */
    fun resetTabs() {
        hasLocalChanges = true
        lastRecordedVisit.clear()
        closedGroup = emptyList()
        placeholderTabId = null
        webViewStates.clear()
        val id = newTabId()
        _state.update {
            it.copy(
                tabs = listOf(TabUi(id = id)),
                activeTabId = id,
                addressText = "",
                overlay = BrowserOverlay.NONE,
                findInPage = null,
                readerArticle = null,
                undoClose = null,
            )
        }
        persistSession()
    }

    // ------------------------------------------------------ link da altre app

    /**
     * Link, condivisione o ricerca arrivati da un'altra app: si aprono in una
     * scheda nuova (o nella pagina iniziale vuota, se è quella attiva).
     * @return l'URL da caricare, `null` se non c'è niente di valido.
     */
    fun openIncoming(link: IncomingLink): String? {
        val url = when (link) {
            is IncomingLink.Url -> link.url
            is IncomingLink.Text -> UrlNormalizer.toLoadableUrl(link.text, _state.value.searchEngine)
        } ?: return null
        hasLocalChanges = true
        _state.update { current ->
            val active = current.activeTab
            val tabs = if (active.url == null) {
                current.tabs.map { if (it.id == active.id) TabUi(id = it.id, url = url, isLoading = true, openedByApp = true) else it }
            } else {
                current.tabs + TabUi(id = newTabId(), url = url, isLoading = true, openedByApp = true)
            }
            val target = if (active.url == null) active.id else tabs.last().id
            current.copy(
                tabs = tabs,
                activeTabId = target,
                addressText = url,
                isAddressFocused = false,
                overlay = BrowserOverlay.NONE,
                findInPage = null,
            )
        }
        persistSession()
        return url
    }

    /** L'avviso "Annulla" è scaduto: gli stati delle schede chiuse si possono buttare. */
    fun dismissUndo(id: Long) {
        if (_state.value.undoClose?.id != id) return
        dropClosedGroup()
        _state.update { it.copy(undoClose = null) }
    }

    private fun rememberClosed(group: List<Pair<Int, TabUi>>, activeTabId: String) {
        dropClosedGroup()
        closedGroup = group
        closedActiveTabId = activeTabId
    }

    private fun dropClosedGroup() {
        val openIds = _state.value.tabs.mapTo(mutableSetOf()) { it.id }
        closedGroup.forEach { (_, tab) ->
            if (tab.id !in openIds) webViewStates.remove(tab.id)
        }
        closedGroup = emptyList()
        placeholderTabId = null
    }

    private fun nextUndo(count: Int) = UndoClose(id = ++undoCounter, count = count)

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

    fun openPasswords() {
        if (privateMode) return
        _state.update { it.copy(overlay = BrowserOverlay.PASSWORDS) }
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
        // Una pagina nuova rende inutili ricerca nella pagina e "Accedi come …".
        _state.update { current ->
            current.copy(
                findInPage = if (current.activeTabId == tabId) null else current.findInPage,
                fillOffer = current.fillOffer?.takeIf { it.tabId != tabId },
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

    // -------------------------------------------------------------- password

    /** Campo di accesso toccato: se ci sono credenziali per l'origine, le propone. */
    fun onLoginFieldFocused(tabId: String, origin: String) {
        if (privateMode || _state.value.activeTabId != tabId) return
        val shown = _state.value.fillOffer
        if (shown != null && shown.tabId == tabId && shown.origin == origin) return
        viewModelScope.launch {
            val logins = passwordRepository.loginsFor(origin)
            if (logins.isEmpty()) return@launch
            _state.update { current ->
                if (current.activeTabId != tabId) current else current.copy(fillOffer = FillOffer(tabId, origin, logins))
            }
        }
    }

    fun dismissFillOffer() {
        _state.update { it.copy(fillOffer = null) }
    }

    /**
     * Credenziali da inserire nella pagina dopo il tocco su "Accedi come …".
     * @return utente e password, `null` se non leggibili.
     */
    suspend fun loginToFill(loginId: Long): Pair<String, String>? {
        val offer = _state.value.fillOffer ?: return null
        val login = offer.logins.firstOrNull { it.id == loginId } ?: return null
        val password = passwordRepository.password(loginId) ?: return null
        passwordRepository.markUsed(loginId)
        dismissFillOffer()
        return login.username to password
    }

    /** Accesso inviato: chiede se salvare o aggiornare, se è il caso. */
    fun onCredentialsSubmitted(tabId: String, origin: String, username: String, password: String) {
        if (privateMode || !currentSettings.offerToSavePasswords) return
        val never = origin in currentSettings.passwordNeverSites
        viewModelScope.launch {
            val decision = passwordRepository.decide(origin, username, password, never)
            val prompt = when (decision) {
                SaveDecision.Ignore -> return@launch
                SaveDecision.Save -> SavePrompt(tabId, origin, username.trim(), password, updateId = null)
                is SaveDecision.Update -> SavePrompt(tabId, origin, username.trim(), password, decision.id)
            }
            _state.update { it.copy(savePrompt = prompt, fillOffer = null) }
        }
    }

    fun acceptSavePrompt() {
        val prompt = _state.value.savePrompt ?: return
        _state.update { it.copy(savePrompt = null) }
        viewModelScope.launch { passwordRepository.save(prompt.origin, prompt.username, prompt.password) }
    }

    fun neverSaveForSite() {
        val prompt = _state.value.savePrompt ?: return
        _state.update { it.copy(savePrompt = null) }
        currentSettings = currentSettings.copy(passwordNeverSites = currentSettings.passwordNeverSites + prompt.origin)
        viewModelScope.launch { settingsRepository.setPasswordNeverSite(prompt.origin, never = true) }
    }

    fun dismissSavePrompt() {
        _state.update { it.copy(savePrompt = null) }
    }

    // ------------------------------------------------------------- preferiti

    /** Aggiunge o toglie dai preferiti la pagina attiva. */
    fun toggleFavorite() {
        if (privateMode) return
        val tab = _state.value.activeTab
        val url = tab.url ?: return
        val isFavorite = _state.value.isActiveFavorite
        viewModelScope.launch {
            if (isFavorite) favoriteRepository.removeByUrl(url) else favoriteRepository.add(url, tab.title)
        }
    }

    /**
     * Aggiunge un preferito scritto a mano (indirizzo o dominio).
     * @return `false` se l'indirizzo non è valido.
     */
    fun addFavorite(rawUrl: String, title: String): Boolean {
        if (privateMode) return false
        val url = favoriteUrl(rawUrl) ?: return false
        viewModelScope.launch { favoriteRepository.add(url, title.trim().ifEmpty { null }) }
        return true
    }

    fun updateFavorite(id: Long, rawUrl: String, title: String): Boolean {
        val url = favoriteUrl(rawUrl) ?: return false
        viewModelScope.launch { favoriteRepository.update(id, url, title.trim().ifEmpty { null }) }
        return true
    }

    fun removeFavorite(id: Long) {
        viewModelScope.launch { favoriteRepository.delete(id) }
    }

    fun moveFavorite(id: Long, delta: Int) {
        viewModelScope.launch { favoriteRepository.move(id, delta) }
    }

    /** Solo indirizzi web: una ricerca non è un preferito. */
    private fun favoriteUrl(rawUrl: String): String? {
        val url = UrlNormalizer.toLoadableUrl(rawUrl, _state.value.searchEngine) ?: return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        if (url == _state.value.searchEngine.searchUrl(rawUrl.trim())) return null
        return url
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
        currentSettings = settings
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

    /**
     * Riapre le schede della volta precedente. Se nel frattempo è già
     * successo qualcosa (tipicamente un link da un'altra app all'avvio), le
     * schede salvate si aggiungono prima di quelle nuove invece di sparire.
     */
    private suspend fun restoreSession() {
        if (privateMode) return
        if (!settingsRepository.settings.first().restoreTabs) return
        val snapshot = sessionRepository.load() ?: return
        if (snapshot.tabs.isEmpty()) return

        val restored = snapshot.tabs.map {
            TabUi(id = it.id, url = it.url, title = it.title, desktopMode = it.desktopMode)
        }
        _state.update { current ->
            if (!hasLocalChanges) {
                val active = restored.firstOrNull { it.id == snapshot.activeTabId } ?: restored.first()
                return@update current.copy(
                    tabs = restored,
                    activeTabId = active.id,
                    addressText = active.url.orEmpty(),
                )
            }
            // La scheda iniziale mai usata non serve più accanto a quelle salvate.
            val fresh = current.tabs.filterNot { it.id == initialTabId && it.url == null }
            val merged = restored.filterNot { saved -> fresh.any { it.id == saved.id } } + fresh
            val active = merged.firstOrNull { it.id == current.activeTabId }
                ?: merged.firstOrNull { it.id == snapshot.activeTabId }
                ?: merged.first()
            current.copy(
                tabs = merged,
                activeTabId = active.id,
                addressText = if (current.isAddressFocused) current.addressText else active.url.orEmpty(),
            )
        }
        if (hasLocalChanges) persistSession()
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

    private companion object {
        const val SUGGESTION_DEBOUNCE_MS = 100L
        const val SUGGESTION_LIMIT = 6
        const val SUGGESTION_SOURCE_LIMIT = 6
    }
}
