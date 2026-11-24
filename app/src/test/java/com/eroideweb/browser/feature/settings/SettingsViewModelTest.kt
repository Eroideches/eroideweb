package com.eroideweb.browser.feature.settings

import com.eroideweb.browser.core.adblock.AdBlockEngine
import com.eroideweb.browser.core.adblock.BlockRules
import com.eroideweb.browser.core.browsingdata.BrowsingDataCleaner
import com.eroideweb.browser.core.browsingdata.ClearDataRequest
import com.eroideweb.browser.core.browsingdata.ClearRange
import com.eroideweb.browser.core.settings.AppTheme
import com.eroideweb.browser.core.settings.BrowserSettings
import com.eroideweb.browser.core.settings.SearchEngine
import com.eroideweb.browser.core.settings.ToolbarPosition
import com.eroideweb.browser.feature.browser.FakeAdBlockProvider
import com.eroideweb.browser.feature.browser.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `le modifiche si vedono subito e finiscono nel repository`() {
        val repository = FakeSettingsRepository()
        val vm = SettingsViewModel(repository, FakeAdBlockProvider())

        vm.setToolbarPosition(ToolbarPosition.TOP)
        vm.setSearchEngine(SearchEngine.DUCKDUCKGO)
        vm.setAdBlockEnabled(false)

        assertEquals(ToolbarPosition.TOP, vm.state.value.settings.toolbarPosition)
        assertEquals(SearchEngine.DUCKDUCKGO, repository.current.searchEngine)
        assertFalse(repository.current.adBlockEnabled)
    }

    @Test
    fun `riattivare il blocco su un sito lo toglie dagli esclusi`() {
        val repository = FakeSettingsRepository(
            BrowserSettings(adBlockDisabledSites = setOf("a.it", "b.it")),
        )
        val vm = SettingsViewModel(repository, FakeAdBlockProvider())

        vm.removeAdBlockException("a.it")

        assertEquals(setOf("b.it"), repository.current.adBlockDisabledSites)
        assertEquals(setOf("b.it"), vm.state.value.settings.adBlockDisabledSites)
    }

    @Test
    fun `indietro torna alla pagina principale e poi chiude`() {
        val vm = SettingsViewModel(FakeSettingsRepository(), FakeAdBlockProvider())

        vm.openPage(SettingsPage.SEARCH_ENGINE)
        assertTrue(vm.navigateUp())
        assertEquals(SettingsPage.ROOT, vm.state.value.page)
        assertFalse(vm.navigateUp())
    }

    @Test
    fun `all apertura riparte dalla radice e legge le regole caricate`() {
        val engine = AdBlockEngine(BlockRules(blocked = setOf("ads.com", "track.it"), allowed = emptySet()))
        val loading = SettingsViewModel(FakeSettingsRepository(), FakeAdBlockProvider())
        loading.onOpen()
        assertNull(loading.state.value.adBlockRuleCount)

        val vm = SettingsViewModel(FakeSettingsRepository(), FakeAdBlockProvider(engine))
        vm.openPage(SettingsPage.ABOUT)
        vm.onOpen()

        assertEquals(SettingsPage.ROOT, vm.state.value.page)
        assertEquals(2, vm.state.value.adBlockRuleCount)
    }

    @Test
    fun `tema scuro dei siti e dimensione del testo si salvano`() {
        val repository = FakeSettingsRepository()
        val vm = SettingsViewModel(repository, FakeAdBlockProvider())

        vm.setAppTheme(AppTheme.DARK)
        vm.setDarkenWebPages(true)
        vm.setTextZoom(130)

        assertEquals(AppTheme.DARK, repository.current.appTheme)
        assertTrue(repository.current.darkenWebPages)
        assertEquals(130, repository.current.textZoom)
    }

    @Test
    fun `la dimensione del testo viene limitata`() {
        val repository = FakeSettingsRepository()
        val vm = SettingsViewModel(repository, FakeAdBlockProvider())

        vm.setTextZoom(900)

        assertEquals(200, vm.state.value.settings.textZoom)
        assertEquals(200, repository.current.textZoom)
    }

    @Test
    fun `elimina dati usa le scelte e avvisa il browser`() {
        val cleaner = RecordingCleaner()
        val vm = SettingsViewModel(FakeSettingsRepository(), FakeAdBlockProvider(), cleaner = cleaner)
        vm.updateClearRequest { it.copy(range = ClearRange.ALL, openTabs = true) }

        vm.clearData()

        assertEquals(ClearRange.ALL, cleaner.requests.single().range)
        assertTrue(cleaner.requests.single().openTabs)
        val result = vm.state.value.clearResult!!
        assertTrue(result.request.openTabs)
        assertFalse(vm.state.value.isClearing)

        vm.onClearResultHandled(result.id)
        assertNull(vm.state.value.clearResult)
    }

    @Test
    fun `senza caselle o in privato non si cancella nulla`() {
        val cleaner = RecordingCleaner()
        val vm = SettingsViewModel(FakeSettingsRepository(), FakeAdBlockProvider(), cleaner = cleaner)
        vm.updateClearRequest { ClearDataRequest(history = false, cookiesAndSiteData = false, cache = false) }
        vm.clearData()

        val private = SettingsViewModel(FakeSettingsRepository(), FakeAdBlockProvider(), privateMode = true)
        private.clearData()

        assertTrue(cleaner.requests.isEmpty())
        assertFalse(private.canClearData)
    }

    @Test
    fun `cookie di terze parti e ripristino schede si salvano`() {
        val repository = FakeSettingsRepository()
        val vm = SettingsViewModel(repository, FakeAdBlockProvider())

        vm.setAllowThirdPartyCookies(true)
        vm.setRestoreTabs(false)

        assertTrue(repository.current.allowThirdPartyCookies)
        assertFalse(repository.current.restoreTabs)
    }

    private class RecordingCleaner : BrowsingDataCleaner {
        val requests = mutableListOf<ClearDataRequest>()

        override suspend fun clear(request: ClearDataRequest, now: Long) {
            requests += request
        }
    }
}
