package com.eroideweb.browser.feature.browser

import com.eroideweb.browser.core.adblock.AdBlockEngine
import com.eroideweb.browser.core.adblock.AdBlockProvider
import com.eroideweb.browser.core.adblock.BlockRules
import com.eroideweb.browser.core.bookmark.NoOpBookmarkRepository
import com.eroideweb.browser.core.favorites.NoOpFavoriteRepository
import com.eroideweb.browser.core.suggest.SuggestionSource
import com.eroideweb.browser.core.url.IncomingLink
import com.eroideweb.browser.core.web.ContextTarget
import com.eroideweb.browser.core.web.SitePermission
import com.eroideweb.browser.core.history.NoOpHistoryRepository
import com.eroideweb.browser.core.session.NoOpSessionRepository
import com.eroideweb.browser.core.session.SessionSnapshot
import com.eroideweb.browser.core.session.SessionTab
import com.eroideweb.browser.core.reader.ReaderArticle
import com.eroideweb.browser.core.settings.BrowserSettings
import com.eroideweb.browser.core.settings.ReaderTheme
import com.eroideweb.browser.core.settings.SearchEngine
import com.eroideweb.browser.core.settings.ToolbarPosition
import com.eroideweb.browser.core.settings.setAdBlockEnabled
import com.eroideweb.browser.core.settings.setSearchEngine
import com.eroideweb.browser.core.settings.setToolbarPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {

    /** Un solo orologio virtuale: serve al debounce dei suggerimenti. */
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        settings: BrowserSettings = BrowserSettings(),
        session: FakeSessionRepository = FakeSessionRepository(),
        history: FakeHistoryRepository = FakeHistoryRepository(),
        bookmarks: FakeBookmarkRepository = FakeBookmarkRepository(),
        adBlocker: AdBlockProvider = FakeAdBlockProvider(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(settings),
        favorites: FakeFavoriteRepository = FakeFavoriteRepository(),
        passwords: FakePasswordRepository = FakePasswordRepository(),
    ): BrowserViewModel = BrowserViewModel(
        settingsRepository = settingsRepository,
        sessionRepository = session,
        historyRepository = history,
        bookmarkRepository = bookmarks,
        adBlocker = adBlocker,
        favoriteRepository = favorites,
        passwordRepository = passwords,
    )

    private val BrowserViewModel.activeTabId: String
        get() = state.value.activeTabId

    @Test
    fun `all avvio c e una sola scheda sulla pagina iniziale`() {
        val vm = viewModel()

        val state = vm.state.value
        assertEquals(1, state.tabs.size)
        assertTrue(state.isStartPage)
        assertNull(state.activeTab.url)
    }

    @Test
    fun `submit normalizza il dominio in https`() {
        val vm = viewModel()

        val url = vm.submitAddressBar("example.com")

        assertEquals("https://example.com", url)
        val state = vm.state.value
        assertEquals("https://example.com", state.activeTab.url)
        assertEquals("https://example.com", state.addressText)
        assertTrue(state.activeTab.isLoading)
        assertNull(state.activeTab.error)
        assertTrue(state.activeTab.isSecure)
    }

    @Test
    fun `submit con testo vuoto non carica nulla`() {
        val vm = viewModel()

        assertNull(vm.submitAddressBar("   "))
        assertTrue(vm.state.value.isStartPage)
    }

    @Test
    fun `usa il motore di ricerca dalle impostazioni`() {
        val vm = viewModel(BrowserSettings(searchEngine = SearchEngine.DUCKDUCKGO))

        assertEquals(
            "https://duckduckgo.com/?q=caff%C3%A8",
            vm.submitAddressBar("caffè"),
        )
    }

    /**
     * Regressione: la ricerca digitata mentre si è già su una pagina veniva
     * sostituita dall'URL corrente alla perdita del focus.
     */
    @Test
    fun `la ricerca digitata vince sull url della pagina corrente`() {
        val vm = viewModel()
        val tabId = vm.activeTabId
        vm.onPageStarted(tabId, "https://www.google.com/")
        vm.onPageFinished(tabId, "https://www.google.com/", "Google")
        vm.onAddressBarTextChange("meteo roma")
        vm.onAddressBarFocusChange(false)

        val url = vm.submitAddressBar("meteo roma")

        assertEquals("https://www.google.com/search?q=meteo+roma", url)
        assertEquals("https://www.google.com/search?q=meteo+roma", vm.state.value.addressText)
    }

    @Test
    fun `fine caricamento aggiorna titolo e stato`() {
        val vm = viewModel()
        val tabId = vm.activeTabId
        vm.onPageStarted(tabId, "https://example.com")
        vm.onProgressChanged(tabId, 40)
        vm.onPageFinished(tabId, "https://example.com", "Example")

        val tab = vm.state.value.activeTab
        assertFalse(tab.isLoading)
        assertEquals(100, tab.progress)
        assertEquals("Example", tab.title)
        assertEquals("https://example.com", tab.url)
    }

    @Test
    fun `il testo in editing non viene sovrascritto dal caricamento`() {
        val vm = viewModel()
        val tabId = vm.activeTabId
        vm.onAddressBarTextChange("esempio")
        vm.onAddressBarFocusChange(true)
        vm.onPageStarted(tabId, "https://example.com")

        assertEquals("esempio", vm.state.value.addressText)
        // Uscendo dalla barra si torna all'URL reale.
        vm.onAddressBarFocusChange(false)
        assertEquals("https://example.com", vm.state.value.addressText)
    }

    @Test
    fun `errore di rete e retry azzerano lo stato di caricamento`() {
        val vm = viewModel()
        val tabId = vm.activeTabId
        vm.onPageStarted(tabId, "https://example.com")
        vm.onPageError(tabId, PageError(kind = PageErrorKind.NO_CONNECTION, code = -2))

        assertEquals(PageErrorKind.NO_CONNECTION, vm.state.value.activeTab.error?.kind)
        assertFalse(vm.state.value.activeTab.isLoading)

        vm.clearError(tabId)
        assertNull(vm.state.value.activeTab.error)
    }

    @Test
    fun `le impostazioni cambiate altrove arrivano al browser`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settingsRepository = settings)
        assertEquals(ToolbarPosition.BOTTOM, vm.state.value.toolbarPosition)

        settings.setToolbarPosition(ToolbarPosition.TOP)
        settings.setSearchEngine(SearchEngine.BRAVE)

        assertEquals(ToolbarPosition.TOP, vm.state.value.toolbarPosition)
        assertEquals(SearchEngine.BRAVE, vm.state.value.searchEngine)
    }

    // --------------------------------------------------------------- schede

    @Test
    fun `nuova scheda diventa attiva e mostra la pagina iniziale`() {
        val vm = viewModel()
        vm.submitAddressBar("example.com")

        vm.openNewTab()

        val state = vm.state.value
        assertEquals(2, state.tabs.size)
        assertEquals(state.tabs.last().id, state.activeTabId)
        assertTrue(state.isStartPage)
        assertEquals("", state.addressText)
        // La scheda precedente conserva la sua pagina.
        assertEquals("https://example.com", state.tabs.first().url)
    }

    @Test
    fun `chiudere la scheda attiva passa alla vicina`() {
        val vm = viewModel()
        vm.submitAddressBar("example.com")
        val firstId = vm.activeTabId
        vm.openNewTab()
        vm.submitAddressBar("example.org")
        val secondId = vm.activeTabId

        vm.closeTab(secondId)

        assertEquals(firstId, vm.state.value.activeTabId)
        assertEquals("https://example.com", vm.state.value.addressText)
    }

    @Test
    fun `chiudere due volte la stessa scheda non rompe lo stato`() {
        val vm = viewModel()
        vm.submitAddressBar("example.com")
        vm.openNewTab()
        val id = vm.activeTabId

        vm.closeTab(id)
        vm.closeTab(id)

        assertEquals(1, vm.state.value.tabs.size)
    }

    @Test
    fun `chiudere l ultima scheda ne crea una vuota`() {
        val vm = viewModel()
        vm.submitAddressBar("example.com")

        vm.closeTab(vm.activeTabId)

        val state = vm.state.value
        assertEquals(1, state.tabs.size)
        assertTrue(state.isStartPage)
    }

    @Test
    fun `gli eventi di una scheda in background non toccano la barra`() {
        val vm = viewModel()
        vm.submitAddressBar("https://example.com")
        val backgroundId = vm.activeTabId
        vm.openNewTab()
        val foregroundId = vm.activeTabId

        vm.onPageFinished(backgroundId, "https://example.com/altro", "Altro")

        val state = vm.state.value
        assertEquals("", state.addressText)
        assertEquals(foregroundId, state.activeTabId)
        val backgroundTab = state.tabs.first { it.id == backgroundId }
        assertEquals("https://example.com/altro", backgroundTab.url)
        assertEquals("Altro", backgroundTab.title)
    }

    @Test
    fun `selezionare una scheda riporta il suo url nella barra`() {
        val vm = viewModel()
        vm.submitAddressBar("example.com")
        val firstId = vm.activeTabId
        vm.openNewTab()

        vm.selectTab(firstId)

        assertEquals(firstId, vm.state.value.activeTabId)
        assertEquals("https://example.com", vm.state.value.addressText)
        assertFalse(vm.state.value.isTabSwitcherVisible)
    }

    @Test
    fun `duplicare una scheda ne crea una con lo stesso url`() {
        val vm = viewModel()
        vm.submitAddressBar("example.com")
        val id = vm.activeTabId

        vm.duplicateTab(id)

        val state = vm.state.value
        assertEquals(2, state.tabs.size)
        assertEquals("https://example.com", state.activeTab.url)
        assertNotEquals(id, state.activeTabId)
    }

    // ------------------------------------------------------------- sessione

    @Test
    fun `la sessione salvata viene ripristinata all avvio`() {
        val session = FakeSessionRepository(
            SessionSnapshot(
                tabs = listOf(
                    SessionTab(id = "a", url = "https://example.com", title = "Example"),
                    SessionTab(id = "b", url = "https://example.org", title = "Org"),
                ),
                activeTabId = "b",
            ),
        )

        val vm = viewModel(session = session)

        val state = vm.state.value
        assertEquals(2, state.tabs.size)
        assertEquals("b", state.activeTabId)
        assertEquals("https://example.org", state.addressText)
        assertNull(session.lastSaved)
    }

    @Test
    fun `submit salva la sessione`() {
        val session = FakeSessionRepository()
        val vm = viewModel(session = session)

        vm.submitAddressBar("example.com")

        val saved = session.lastSaved
        assertEquals("https://example.com", saved?.tabs?.single()?.url)
        assertEquals(vm.activeTabId, saved?.activeTabId)
    }

    // ------------------------------------------------- cronologia e segnalibri

    @Test
    fun `una pagina visitata finisce in cronologia`() {
        val history = FakeHistoryRepository()
        val vm = viewModel(history = history)
        val tabId = vm.activeTabId

        vm.onPageFinished(tabId, "https://example.com/articolo", "Articolo")

        assertEquals(listOf("https://example.com/articolo" to "Articolo"), history.recorded)
    }

    @Test
    fun `la stessa pagina ricaricata non crea doppioni consecutivi`() {
        val history = FakeHistoryRepository()
        val vm = viewModel(history = history)
        val tabId = vm.activeTabId

        vm.onPageFinished(tabId, "https://example.com", "Example")
        vm.onPageFinished(tabId, "https://example.com", "Example")

        assertEquals(1, history.recorded.size)
    }

    @Test
    fun `le pagine non http non entrano in cronologia`() {
        val history = FakeHistoryRepository()
        val vm = viewModel(history = history)
        val tabId = vm.activeTabId

        vm.onPageFinished(tabId, "about:blank", null)

        assertTrue(history.recorded.isEmpty())
    }

    @Test
    fun `il segnalibro segue la scheda attiva`() {
        val bookmarks = FakeBookmarkRepository()
        val vm = viewModel(bookmarks = bookmarks)
        val tabId = vm.activeTabId
        vm.submitAddressBar("example.com")
        vm.onPageFinished(tabId, "https://example.com", "Example")

        vm.toggleBookmark()
        assertTrue(vm.state.value.isActiveBookmarked)

        vm.toggleBookmark()
        assertFalse(vm.state.value.isActiveBookmarked)
    }

    // -------------------------------------------------------------- adblock

    @Test
    fun `il blocco si disattiva solo per il sito attivo`() {
        val vm = viewModel()
        val tabId = vm.activeTabId
        vm.submitAddressBar("example.com")
        vm.onPageFinished(tabId, "https://example.com", "Example")
        assertTrue(vm.state.value.isAdBlockActiveForSite)

        vm.toggleAdBlockForSite()

        assertFalse(vm.state.value.isAdBlockActiveForSite)
        assertEquals(setOf("example.com"), vm.state.value.adBlockDisabledSites)

        vm.toggleAdBlockForSite()
        assertTrue(vm.state.value.isAdBlockActiveForSite)
    }

    @Test
    fun `una richiesta pubblicitaria viene bloccata salvo esclusioni`() {
        val engine = AdBlockEngine(BlockRules(setOf("ads.tracker.com"), emptySet()))
        val vm = viewModel(adBlocker = FakeAdBlockProvider(engine))
        val tabId = vm.activeTabId
        vm.submitAddressBar("https://sito.it")
        vm.onPageFinished(tabId, "https://sito.it", "Sito")

        assertTrue(vm.shouldBlockRequest("https://ads.tracker.com/b.js", "https://sito.it"))

        vm.toggleAdBlockForSite()
        assertFalse(vm.shouldBlockRequest("https://ads.tracker.com/b.js", "https://sito.it"))
        // l'esclusione vale solo per quel sito
        assertTrue(vm.shouldBlockRequest("https://ads.tracker.com/b.js", "https://altro.it"))
    }

    @Test
    fun `il blocco globale disattivato ferma ogni filtro`() = runTest {
        val engine = AdBlockEngine(BlockRules(setOf("ads.tracker.com"), emptySet()))
        val settings = FakeSettingsRepository()
        val vm = viewModel(adBlocker = FakeAdBlockProvider(engine), settingsRepository = settings)
        vm.submitAddressBar("https://sito.it")

        // Ora si spegne dalle Impostazioni: il browser lo riceve dal repository.
        settings.setAdBlockEnabled(false)

        assertFalse(vm.state.value.adBlockEnabled)
        assertFalse(vm.shouldBlockRequest("https://ads.tracker.com/b.js", "https://sito.it"))
    }

    @Test
    fun `senza liste caricate non si blocca nulla`() {
        val vm = viewModel()
        vm.submitAddressBar("https://sito.it")

        assertFalse(vm.shouldBlockRequest("https://ads.tracker.com/b.js", "https://sito.it"))
    }

    @Test
    fun `la finestra privata non lascia tracce`() {
        val vm = BrowserViewModel(
            settingsRepository = FakeSettingsRepository(),
            sessionRepository = NoOpSessionRepository,
            historyRepository = NoOpHistoryRepository,
            bookmarkRepository = NoOpBookmarkRepository,
            adBlocker = FakeAdBlockProvider(),
            favoriteRepository = NoOpFavoriteRepository,
            privateMode = true,
        )
        val tabId = vm.activeTabId

        vm.submitAddressBar("example.com")
        vm.onPageFinished(tabId, "https://example.com", "Example")
        vm.toggleBookmark()

        assertTrue(vm.state.value.isPrivate)
        assertTrue(vm.state.value.bookmarkedUrls.isEmpty())
        assertFalse(vm.state.value.isActiveBookmarked)
    }

    @Test
    fun `la libreria si apre e si chiude`() {
        val vm = viewModel()

        vm.setLibraryVisible(true)
        assertTrue(vm.state.value.isLibraryVisible)

        vm.setLibraryVisible(false)
        assertFalse(vm.state.value.isLibraryVisible)
    }

    @Test
    fun `la scheda aperta da una finestra nuova viene salvata e attivata`() {
        val vm = viewModel()

        val newId = vm.openTabFromWindow()

        val state = vm.state.value
        assertEquals(newId, state.activeTabId)
        assertEquals(2, state.tabs.size)
        assertTrue(state.isStartPage)
    }

    // ---------------------------------------------------------- pannelli

    @Test
    fun `menu impostazioni e schede sono pannelli esclusivi`() {
        val vm = viewModel()

        vm.openMenu()
        assertEquals(BrowserOverlay.MENU, vm.state.value.overlay)

        vm.openSettings()
        assertEquals(BrowserOverlay.SETTINGS, vm.state.value.overlay)

        vm.toggleTabSwitcher()
        assertEquals(BrowserOverlay.TABS, vm.state.value.overlay)

        vm.selectTab(vm.activeTabId)
        assertEquals(BrowserOverlay.NONE, vm.state.value.overlay)
    }

    @Test
    fun `chiudere un pannello diverso non tocca quello aperto`() {
        val vm = viewModel()
        vm.openSettings()

        vm.setLibraryVisible(false)

        assertEquals(BrowserOverlay.SETTINGS, vm.state.value.overlay)
        vm.closeOverlay()
        assertEquals(BrowserOverlay.NONE, vm.state.value.overlay)
    }

    // -------------------------------------------------- trova nella pagina

    @Test
    fun `trova nella pagina si apre solo su una pagina web`() {
        val vm = viewModel()

        vm.startFindInPage()
        assertNull(vm.state.value.findInPage)

        vm.submitAddressBar("example.com")
        vm.openMenu()
        vm.startFindInPage()

        assertEquals(FindInPageState(), vm.state.value.findInPage)
        assertEquals(BrowserOverlay.NONE, vm.state.value.overlay)
    }

    @Test
    fun `i risultati della ricerca aggiornano il contatore della scheda attiva`() {
        val vm = viewModel()
        vm.submitAddressBar("example.com")
        val tabId = vm.activeTabId
        vm.startFindInPage()
        vm.onFindQueryChange("caffè")

        vm.onFindResult(tabId, activeMatchOrdinal = 0, matchCount = 3)
        vm.onFindResult("altra-scheda", activeMatchOrdinal = 4, matchCount = 9)

        assertEquals(FindInPageState(query = "caffè", activeMatch = 1, matchCount = 3), vm.state.value.findInPage)

        vm.onFindQueryChange("")
        assertEquals(FindInPageState(), vm.state.value.findInPage)
    }

    @Test
    fun `nessun risultato mostra zero`() {
        val vm = viewModel()
        vm.submitAddressBar("example.com")
        vm.startFindInPage()
        vm.onFindQueryChange("introvabile")

        vm.onFindResult(vm.activeTabId, activeMatchOrdinal = 0, matchCount = 0)

        assertEquals(0, vm.state.value.findInPage?.activeMatch)
        assertEquals(0, vm.state.value.findInPage?.matchCount)
    }

    @Test
    fun `una pagina nuova o un cambio scheda chiudono la ricerca`() {
        val vm = viewModel()
        vm.submitAddressBar("example.com")
        val tabId = vm.activeTabId
        vm.startFindInPage()

        vm.onPageStarted(tabId, "https://example.com/altra")
        assertNull(vm.state.value.findInPage)

        vm.startFindInPage()
        vm.openNewTab()
        assertNull(vm.state.value.findInPage)
    }

    // --------------------------------------------------- versione desktop

    @Test
    fun `la versione desktop vale per la sola scheda e finisce in sessione`() {
        val session = FakeSessionRepository()
        val vm = viewModel(session = session)
        assertNull(vm.toggleDesktopMode())

        vm.submitAddressBar("example.com")
        val desktopTab = vm.activeTabId
        assertEquals(true, vm.toggleDesktopMode())
        vm.openNewTab()
        vm.submitAddressBar("example.org")

        assertTrue(vm.isDesktopMode(desktopTab))
        assertFalse(vm.isDesktopMode(vm.activeTabId))
        val saved = session.lastSaved?.tabs?.first { it.id == desktopTab }
        assertEquals(true, saved?.desktopMode)

        vm.selectTab(desktopTab)
        assertEquals(false, vm.toggleDesktopMode())
        assertFalse(vm.isDesktopMode(desktopTab))
    }

    @Test
    fun `duplicare e ripristinare conservano la versione desktop`() {
        val session = FakeSessionRepository(
            SessionSnapshot(
                tabs = listOf(SessionTab(id = "a", url = "https://example.com", title = null, desktopMode = true)),
                activeTabId = "a",
            ),
        )
        val vm = viewModel(session = session)
        assertTrue(vm.state.value.activeTab.desktopMode)

        vm.duplicateTab("a")

        assertTrue(vm.state.value.activeTab.desktopMode)
        assertNotEquals("a", vm.activeTabId)
    }

    // ---------------------------------------------------------- traduzione

    @Test
    fun `tradurre apre google traduttore e poi torna all originale`() {
        val vm = viewModel()
        vm.submitAddressBar("https://example.com/a")

        val translated = vm.translateActivePage("it")

        assertEquals(
            "https://translate.google.com/translate?sl=auto&tl=it&hl=it&u=https%3A%2F%2Fexample.com%2Fa",
            translated,
        )
        // Google reindirizza al proxy *.translate.goog.
        vm.onPageFinished(vm.activeTabId, "https://example-com.translate.goog/a?_x_tr_sl=auto&_x_tr_tl=it", "A")
        assertTrue(vm.state.value.isActiveTranslated)

        assertEquals("https://example.com/a", vm.translateActivePage("it"))
    }

    @Test
    fun `la pagina iniziale non si traduce`() {
        val vm = viewModel()

        assertNull(vm.translateActivePage("it"))
        assertFalse(vm.state.value.canTranslate)
    }

    @Test
    fun `l avviso sulla traduzione viene ricordato`() {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settingsRepository = settings)
        assertFalse(vm.state.value.translateNoticeAccepted)

        vm.acceptTranslateNotice()

        assertTrue(vm.state.value.translateNoticeAccepted)
        assertTrue(settings.current.translateNoticeAccepted)
    }

    // ----------------------------------------------------- modalità lettura

    private fun article(url: String) = ReaderArticle(
        url = url,
        title = "Titolo",
        byline = null,
        siteName = null,
        contentHtml = "<p>Testo</p>",
        language = "it",
        direction = null,
    )

    @Test
    fun `la lettura si offre solo per la pagina che l ha chiesta`() {
        val vm = viewModel()
        vm.submitAddressBar("https://example.com/a")
        val tabId = vm.activeTabId

        vm.onReaderModeAvailability(tabId, "https://example.com/vecchia", available = true)
        assertFalse(vm.state.value.activeTab.readerAvailable)

        vm.onReaderModeAvailability(tabId, "https://example.com/a", available = true)
        assertTrue(vm.state.value.activeTab.readerAvailable)

        vm.onPageStarted(tabId, "https://example.com/b")
        assertFalse(vm.state.value.activeTab.readerAvailable)
    }

    @Test
    fun `aprire e chiudere la lettura`() {
        val vm = viewModel()
        vm.submitAddressBar("https://example.com/a")

        vm.openReader(article("https://altro.it/"))
        assertFalse(vm.state.value.isReaderVisible)

        vm.openReader(article("https://example.com/a"))
        assertTrue(vm.state.value.isReaderVisible)
        assertEquals(BrowserOverlay.READER, vm.state.value.overlay)

        vm.closeReader()
        assertFalse(vm.state.value.isReaderVisible)
        assertNull(vm.state.value.readerArticle)
        assertEquals(BrowserOverlay.NONE, vm.state.value.overlay)
    }

    @Test
    fun `le preferenze di lettura finiscono nelle impostazioni`() {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settingsRepository = settings)

        vm.setReaderTextZoom(140)
        vm.setReaderTheme(ReaderTheme.SEPIA)
        vm.setReaderSerif(false)

        assertEquals(140, settings.current.readerTextZoom)
        assertEquals(ReaderTheme.SEPIA, settings.current.readerTheme)
        assertFalse(settings.current.readerSerif)
    }

    // ------------------------------------------- chiusura schede e Annulla

    @Test
    fun `chiudere dal selettore lo lascia aperto e offre annulla`() {
        val vm = viewModel()
        vm.submitAddressBar("example.com")
        val first = vm.activeTabId
        vm.openNewTab()
        vm.submitAddressBar("example.org")
        vm.toggleTabSwitcher()

        vm.closeTab(first)

        val state = vm.state.value
        assertEquals(BrowserOverlay.TABS, state.overlay)
        assertEquals(1, state.undoClose?.count)
        assertEquals(1, state.tabs.size)
    }

    @Test
    fun `annulla riapre la scheda al suo posto e la riattiva`() {
        val vm = viewModel()
        vm.submitAddressBar("a.com")
        val a = vm.activeTabId
        vm.openNewTab()
        vm.submitAddressBar("b.com")
        val b = vm.activeTabId
        vm.openNewTab()
        vm.submitAddressBar("c.com")
        vm.selectTab(b)

        vm.closeTab(b)
        vm.undoCloseTab()

        val state = vm.state.value
        assertEquals(listOf("https://a.com", "https://b.com", "https://c.com"), state.tabs.map { it.url })
        assertEquals(b, state.activeTabId)
        assertNull(state.undoClose)
        assertNotEquals(a, b)
    }

    @Test
    fun `chiudi tutte e annulla ripristina tutto senza la scheda vuota`() {
        val session = FakeSessionRepository()
        val vm = viewModel(session = session)
        vm.submitAddressBar("a.com")
        vm.openNewTab()
        vm.submitAddressBar("b.com")
        val active = vm.activeTabId

        vm.closeAllTabs()
        assertEquals(1, vm.state.value.tabs.size)
        assertTrue(vm.state.value.isStartPage)
        assertEquals(2, vm.state.value.undoClose?.count)

        vm.undoCloseTab()
        assertEquals(listOf("https://a.com", "https://b.com"), vm.state.value.tabs.map { it.url })
        assertEquals(active, vm.state.value.activeTabId)
        assertEquals(2, session.lastSaved?.tabs?.size)
    }

    @Test
    fun `l avviso scaduto non si puo piu annullare`() {
        val vm = viewModel()
        vm.submitAddressBar("a.com")
        vm.openNewTab()
        vm.submitAddressBar("b.com")
        vm.closeTab(vm.activeTabId)
        val undo = vm.state.value.undoClose!!

        vm.dismissUndo(undo.id)
        vm.undoCloseTab()

        assertNull(vm.state.value.undoClose)
        assertEquals(1, vm.state.value.tabs.size)
    }

    // ------------------------------------------------------------ preferiti

    @Test
    fun `la pagina attiva entra ed esce dai preferiti`() {
        val favorites = FakeFavoriteRepository()
        val vm = viewModel(favorites = favorites)
        vm.submitAddressBar("example.com")
        vm.onPageFinished(vm.activeTabId, "https://example.com", "Example")

        vm.toggleFavorite()
        assertTrue(vm.state.value.isActiveFavorite)
        assertEquals("Example", favorites.current.single().title)

        vm.toggleFavorite()
        assertFalse(vm.state.value.isActiveFavorite)
        assertTrue(favorites.current.isEmpty())
    }

    @Test
    fun `un preferito scritto a mano deve essere un indirizzo`() {
        val favorites = FakeFavoriteRepository()
        val vm = viewModel(favorites = favorites)

        assertFalse(vm.addFavorite("meteo di domani", "Meteo"))
        assertTrue(vm.addFavorite("repubblica.it", "  "))

        assertEquals("https://repubblica.it", favorites.current.single().url)
        assertNull(favorites.current.single().title)
    }

    @Test
    fun `i preferiti si spostano nella griglia`() {
        val favorites = FakeFavoriteRepository()
        val vm = viewModel(favorites = favorites)
        vm.addFavorite("a.it", "A")
        vm.addFavorite("b.it", "B")
        vm.addFavorite("c.it", "C")

        vm.moveFavorite(favorites.current[2].id, -1)

        assertEquals(listOf("A", "C", "B"), vm.state.value.favorites.map { it.title })
    }

    // -------------------------------------------------------- suggerimenti

    @Test
    fun `scrivere nella barra propone preferiti e pagine visitate`() {
        val history = FakeHistoryRepository()
        val favorites = FakeFavoriteRepository()
        val vm = viewModel(history = history, favorites = favorites)
        vm.addFavorite("repubblica.it", "la Repubblica")
        vm.submitAddressBar("https://www.reddit.com/r/italy")
        vm.onPageFinished(vm.activeTabId, "https://www.reddit.com/r/italy", "Reddit Italia")

        vm.onAddressBarFocusChange(true)
        vm.onAddressBarTextChange("re")
        dispatcher.scheduler.advanceTimeBy(200)
        dispatcher.scheduler.runCurrent()

        val suggestions = vm.state.value.suggestions
        assertEquals(2, suggestions.size)
        assertEquals(SuggestionSource.FAVORITE, suggestions.first().source)

        vm.onAddressBarFocusChange(false)
        assertTrue(vm.state.value.suggestions.isEmpty())
    }

    // ---------------------------------------------------- link da altre app

    @Test
    fun `un link da un altra app riusa la pagina iniziale vuota`() {
        val vm = viewModel()
        val first = vm.activeTabId

        val url = vm.openIncoming(IncomingLink.Url("https://example.com/a"))

        assertEquals("https://example.com/a", url)
        assertEquals(1, vm.state.value.tabs.size)
        assertEquals(first, vm.activeTabId)
        assertTrue(vm.state.value.activeTab.openedByApp)
    }

    @Test
    fun `con una pagina aperta il link va in una scheda nuova`() {
        val vm = viewModel()
        vm.submitAddressBar("example.org")

        vm.openIncoming(IncomingLink.Text("meteo roma"))

        assertEquals(2, vm.state.value.tabs.size)
        assertEquals("https://www.google.com/search?q=meteo+roma", vm.state.value.activeTab.url)
        assertFalse(vm.state.value.tabs.first().openedByApp)
    }

    @Test
    fun `il link arrivato all avvio si unisce alle schede salvate`() {
        val session = SlowSessionRepository(
            SessionSnapshot(
                tabs = listOf(SessionTab(id = "vecchia", url = "https://salvata.it", title = "Salvata")),
                activeTabId = "vecchia",
            ),
        )
        val vm = BrowserViewModel(
            settingsRepository = FakeSettingsRepository(),
            sessionRepository = session,
            historyRepository = FakeHistoryRepository(),
            bookmarkRepository = FakeBookmarkRepository(),
            adBlocker = FakeAdBlockProvider(),
            favoriteRepository = FakeFavoriteRepository(),
        )

        vm.openIncoming(IncomingLink.Url("https://nuova.it"))
        session.release()

        val state = vm.state.value
        assertEquals(listOf("https://salvata.it", "https://nuova.it"), state.tabs.map { it.url })
        assertEquals("https://nuova.it", state.activeTab.url)
    }

    @Test
    fun `senza ripristino all avvio le schede salvate restano chiuse`() {
        val session = FakeSessionRepository(
            SessionSnapshot(
                tabs = listOf(SessionTab(id = "a", url = "https://a.it", title = null)),
                activeTabId = "a",
            ),
        )
        val vm = viewModel(settings = BrowserSettings(restoreTabs = false), session = session)

        assertTrue(vm.state.value.isStartPage)
        assertEquals(1, vm.state.value.tabs.size)
    }

    @Test
    fun `chiusura senza annulla e reset dopo elimina dati`() {
        val vm = viewModel()
        vm.submitAddressBar("a.com")
        vm.openNewTab()
        vm.submitAddressBar("b.com")

        vm.closeTab(vm.activeTabId, offerUndo = false)
        assertNull(vm.state.value.undoClose)

        vm.openNewTab()
        vm.submitAddressBar("c.com")
        vm.resetTabs()
        assertEquals(1, vm.state.value.tabs.size)
        assertTrue(vm.state.value.isStartPage)
        assertNull(vm.state.value.undoClose)
    }

    // -------------------------------------------------------------- password

    private val site = "https://accounts.example.com"

    @Test
    fun `dopo un accesso nuovo si propone di salvare e poi si salva`() = runTest {
        val passwords = FakePasswordRepository()
        val vm = viewModel(passwords = passwords)
        vm.submitAddressBar("https://accounts.example.com/login")

        vm.onCredentialsSubmitted(vm.activeTabId, site, " mario ", "segreta")
        val prompt = vm.state.value.savePrompt!!
        assertFalse(prompt.isUpdate)
        assertEquals("mario", prompt.username)
        assertFalse(prompt.toString().contains("segreta"))

        vm.acceptSavePrompt()
        assertNull(vm.state.value.savePrompt)
        assertEquals("segreta", passwords.passwordOf(site, "mario"))
    }

    @Test
    fun `stessa password niente richiesta, password nuova si aggiorna`() = runTest {
        val passwords = FakePasswordRepository()
        passwords.save(site, "mario", "vecchia")
        val vm = viewModel(passwords = passwords)

        vm.onCredentialsSubmitted(vm.activeTabId, site, "mario", "vecchia")
        assertNull(vm.state.value.savePrompt)

        vm.onCredentialsSubmitted(vm.activeTabId, site, "mario", "nuova")
        assertTrue(vm.state.value.savePrompt!!.isUpdate)
    }

    @Test
    fun `mai per questo sito e proposta spenta fermano le richieste`() {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settingsRepository = settings)

        vm.onCredentialsSubmitted(vm.activeTabId, site, "mario", "uno")
        vm.neverSaveForSite()
        assertTrue(site in settings.current.passwordNeverSites)

        vm.onCredentialsSubmitted(vm.activeTabId, site, "luisa", "due")
        assertNull(vm.state.value.savePrompt)

        val off = viewModel(settings = BrowserSettings(offerToSavePasswords = false))
        off.onCredentialsSubmitted(off.activeTabId, site, "mario", "uno")
        assertNull(off.state.value.savePrompt)
    }

    @Test
    fun `accedi come compare solo con credenziali e compila al tocco`() = runTest {
        val passwords = FakePasswordRepository()
        passwords.save(site, "mario", "segreta")
        val vm = viewModel(passwords = passwords)
        vm.submitAddressBar("https://accounts.example.com/login")

        vm.onLoginFieldFocused(vm.activeTabId, "https://altro.example.org")
        assertNull(vm.state.value.fillOffer)

        vm.onLoginFieldFocused("scheda-in-background", site)
        assertNull(vm.state.value.fillOffer)

        vm.onLoginFieldFocused(vm.activeTabId, site)
        val offer = vm.state.value.fillOffer!!
        assertEquals("mario", offer.logins.single().username)

        val credentials = vm.loginToFill(offer.logins.single().id)
        assertEquals("mario" to "segreta", credentials)
        assertNull(vm.state.value.fillOffer)
        assertEquals(listOf(offer.logins.single().id), passwords.usedIds)
    }

    @Test
    fun `una pagina nuova toglie accedi come`() = runTest {
        val passwords = FakePasswordRepository()
        passwords.save(site, "mario", "segreta")
        val vm = viewModel(passwords = passwords)
        vm.submitAddressBar("https://accounts.example.com/login")
        vm.onLoginFieldFocused(vm.activeTabId, site)

        vm.onPageStarted(vm.activeTabId, "https://accounts.example.com/home")

        assertNull(vm.state.value.fillOffer)
    }

    @Test
    fun `in privato nessuna password`() {
        val vm = BrowserViewModel(
            settingsRepository = FakeSettingsRepository(),
            sessionRepository = NoOpSessionRepository,
            historyRepository = NoOpHistoryRepository,
            bookmarkRepository = NoOpBookmarkRepository,
            adBlocker = FakeAdBlockProvider(),
            favoriteRepository = NoOpFavoriteRepository,
            passwordRepository = FakePasswordRepository(),
            privateMode = true,
        )

        vm.onCredentialsSubmitted(vm.activeTabId, site, "mario", "segreta")
        vm.openPasswords()

        assertNull(vm.state.value.savePrompt)
        assertEquals(BrowserOverlay.NONE, vm.state.value.overlay)
    }

    // ---------------------------------------------- completezza web (fase 12)

    @Test
    fun `apri in una nuova scheda o in background`() {
        val vm = viewModel()
        vm.submitAddressBar("example.com")
        val first = vm.activeTabId
        vm.showContextMenu(ContextTarget(linkUrl = "https://example.com/b"))

        vm.openInNewTab("https://example.com/b", background = true)
        assertEquals(first, vm.activeTabId)
        assertEquals(2, vm.state.value.tabs.size)
        assertNull(vm.state.value.contextMenu)

        vm.openInNewTab("https://example.com/c")
        assertEquals("https://example.com/c", vm.state.value.activeTab.url)
        assertEquals(3, vm.state.value.tabs.size)
    }

    @Test
    fun `i permessi dei siti si chiedono una volta per sessione`() {
        val vm = viewModel()
        val tabId = vm.activeTabId

        assertNull(vm.requestSitePermission(tabId, "https://mappe.it", setOf(SitePermission.LOCATION)))
        val prompt = vm.state.value.permissionPrompt!!
        // Una seconda richiesta mentre si risponde viene rifiutata.
        assertEquals(false, vm.requestSitePermission(tabId, "https://altro.it", setOf(SitePermission.CAMERA)))

        vm.answerPermissionPrompt(prompt.id, granted = true)
        assertNull(vm.state.value.permissionPrompt)
        assertEquals(true, vm.requestSitePermission(tabId, "https://mappe.it", setOf(SitePermission.LOCATION)))

        vm.clearSitePermissions()
        assertNull(vm.requestSitePermission(tabId, "https://mappe.it", setOf(SitePermission.LOCATION)))
    }

    @Test
    fun `una richiesta ritirata chiude la domanda`() {
        val vm = viewModel()
        vm.requestSitePermission(vm.activeTabId, "https://video.it", setOf(SitePermission.CAMERA, SitePermission.MICROPHONE))

        vm.cancelPermissionPrompt(vm.activeTabId)

        assertNull(vm.state.value.permissionPrompt)
    }

    @Test
    fun `crash del renderer ricarica una volta poi mostra l errore`() {
        val vm = viewModel()
        vm.submitAddressBar("example.com")
        val tabId = vm.activeTabId

        assertTrue(vm.onRendererGone(tabId, repeated = false))
        assertNull(vm.state.value.activeTab.error)

        assertFalse(vm.onRendererGone(tabId, repeated = true))
        assertEquals(PageErrorKind.CRASHED, vm.state.value.activeTab.error?.kind)
    }

    // ------------------------------------------------- prestazioni (fase 13)

    @Test
    fun `gli aggiornamenti di pagina si salvano raggruppati`() {
        val session = FakeSessionRepository()
        val vm = viewModel(session = session)
        vm.submitAddressBar("example.com")
        val savedAfterSubmit = session.lastSaved

        vm.onPageFinished(vm.activeTabId, "https://example.com/", "Titolo nuovo")
        assertEquals(savedAfterSubmit, session.lastSaved)

        dispatcher.scheduler.advanceTimeBy(1_000)
        dispatcher.scheduler.runCurrent()
        assertEquals("Titolo nuovo", session.lastSaved?.tabs?.single()?.title)
    }

    @Test
    fun `andando in background il salvataggio in attesa parte subito`() {
        val session = FakeSessionRepository()
        val vm = viewModel(session = session)
        vm.submitAddressBar("example.com")
        vm.onPageFinished(vm.activeTabId, "https://example.com/", "Titolo")

        vm.flushSession()

        assertEquals("Titolo", session.lastSaved?.tabs?.single()?.title)
    }

    @Test
    fun `il filtro per host coincide con quello per url`() {
        val engine = AdBlockEngine(BlockRules(setOf("ads.tracker.com"), emptySet()))
        val vm = viewModel(adBlocker = FakeAdBlockProvider(engine))

        assertTrue(vm.shouldBlockHost("ads.tracker.com", "sito.it"))
        assertFalse(vm.shouldBlockHost("cdn.sito.it", "sito.it"))
        assertEquals(
            vm.shouldBlockRequest("https://ads.tracker.com/x.js", "https://sito.it/"),
            vm.shouldBlockHost("ads.tracker.com", "sito.it"),
        )
    }
}
