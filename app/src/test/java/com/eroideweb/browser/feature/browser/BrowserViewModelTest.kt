package com.eroideweb.browser.feature.browser

import com.eroideweb.browser.core.adblock.AdBlockEngine
import com.eroideweb.browser.core.adblock.AdBlockProvider
import com.eroideweb.browser.core.adblock.BlockRules
import com.eroideweb.browser.core.session.SessionSnapshot
import com.eroideweb.browser.core.session.SessionTab
import com.eroideweb.browser.core.settings.BrowserSettings
import com.eroideweb.browser.core.settings.SearchEngine
import com.eroideweb.browser.core.settings.ToolbarPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
    ): BrowserViewModel = BrowserViewModel(
        settingsRepository = FakeSettingsRepository(settings),
        sessionRepository = session,
        historyRepository = history,
        bookmarkRepository = bookmarks,
        adBlocker = adBlocker,
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
    fun `cambio posizione barra aggiorna subito lo stato`() {
        val vm = viewModel()
        assertEquals(ToolbarPosition.BOTTOM, vm.state.value.toolbarPosition)

        vm.setToolbarPosition(ToolbarPosition.TOP)

        assertEquals(ToolbarPosition.TOP, vm.state.value.toolbarPosition)
    }

    @Test
    fun `cambio motore aggiorna subito lo stato`() {
        val vm = viewModel()

        vm.setSearchEngine(SearchEngine.BRAVE)

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
    fun `il blocco globale disattivato ferma ogni filtro`() {
        val engine = AdBlockEngine(BlockRules(setOf("ads.tracker.com"), emptySet()))
        val vm = viewModel(adBlocker = FakeAdBlockProvider(engine))
        vm.submitAddressBar("https://sito.it")

        vm.setAdBlockEnabled(false)

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
}
