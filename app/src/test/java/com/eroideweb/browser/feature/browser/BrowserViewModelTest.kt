package com.eroideweb.browser.feature.browser

import com.eroideweb.browser.core.settings.BrowserSettings
import com.eroideweb.browser.core.settings.SearchEngine
import com.eroideweb.browser.core.settings.ToolbarPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun viewModel(settings: BrowserSettings = BrowserSettings()): BrowserViewModel =
        BrowserViewModel(FakeSettingsRepository(settings))

    @Test
    fun `submit normalizza il dominio in https`() {
        val vm = viewModel()
        vm.onAddressBarTextChange("example.com")

        val url = vm.submitAddressBar()

        assertEquals("https://example.com", url)
        assertEquals("https://example.com", vm.state.value.currentUrl)
        assertTrue(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.isSecure)
    }

    @Test
    fun `submit con testo vuoto non carica nulla`() {
        val vm = viewModel()
        vm.onAddressBarTextChange("   ")

        assertNull(vm.submitAddressBar())
        assertTrue(vm.state.value.isStartPage)
    }

    @Test
    fun `usa il motore di ricerca dalle impostazioni`() {
        val vm = viewModel(BrowserSettings(searchEngine = SearchEngine.DUCKDUCKGO))
        vm.onAddressBarTextChange("caffè")

        assertEquals("https://duckduckgo.com/?q=caff%C3%A8", vm.submitAddressBar())
    }

    @Test
    fun `fine caricamento aggiorna titolo e stato`() {
        val vm = viewModel()
        vm.onPageStarted("https://example.com")
        vm.onProgressChanged(40)
        vm.onPageFinished("https://example.com", "Example")

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(100, state.progress)
        assertEquals("Example", state.pageTitle)
        assertEquals("https://example.com", state.currentUrl)
    }

    @Test
    fun `il testo in editing non viene sovrascritto dal caricamento`() {
        val vm = viewModel()
        vm.onAddressBarTextChange("esempio")
        vm.onAddressBarFocusChange(true)
        vm.onPageStarted("https://example.com")

        assertEquals("esempio", vm.state.value.addressText)
        // Uscendo dalla barra si torna all'URL reale.
        vm.onAddressBarFocusChange(false)
        assertEquals("https://example.com", vm.state.value.addressText)
    }

    @Test
    fun `errore e retry azzerano lo stato di caricamento`() {
        val vm = viewModel()
        vm.onPageStarted("https://example.com")
        vm.onPageError(PageError(-2, "Host non trovato", "https://example.com"))

        assertEquals("Host non trovato", vm.state.value.error?.description)
        assertFalse(vm.state.value.isLoading)

        vm.clearError()
        assertNull(vm.state.value.error)
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
}
