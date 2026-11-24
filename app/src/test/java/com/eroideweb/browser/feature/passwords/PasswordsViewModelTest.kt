package com.eroideweb.browser.feature.passwords

import com.eroideweb.browser.core.settings.BrowserSettings
import com.eroideweb.browser.feature.browser.FakePasswordRepository
import com.eroideweb.browser.feature.browser.FakeSettingsRepository
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
class PasswordsViewModelTest {

    private var clock = 1_000_000L
    private val site = "https://example.com"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun viewModel(
        passwords: FakePasswordRepository = FakePasswordRepository(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): Pair<PasswordsViewModel, FakePasswordRepository> {
        passwords.save(site, "mario", "segreta")
        return PasswordsViewModel(passwords, settings) { clock } to passwords
    }

    @Test
    fun `senza sblocco la password non si vede e non si copia`() = runTest {
        val (vm, _) = viewModel()
        val id = vm.state.value.logins.single().id
        vm.openDetail(id)

        vm.reveal()

        assertNull(vm.state.value.revealedPassword)
        assertNull(vm.passwordForCopy(id))
    }

    @Test
    fun `lo sblocco vale un minuto`() = runTest {
        val (vm, _) = viewModel()
        val id = vm.state.value.logins.single().id
        vm.openDetail(id)

        vm.onUnlocked()
        vm.reveal()
        assertEquals("segreta", vm.state.value.revealedPassword)
        assertEquals("segreta", vm.passwordForCopy(id))

        clock += PasswordsViewModel.UNLOCK_WINDOW_MS + 1
        assertFalse(vm.isUnlocked())
        assertNull(vm.passwordForCopy(id))
    }

    @Test
    fun `uscire dalla schermata cancella la password mostrata`() = runTest {
        val (vm, _) = viewModel()
        vm.openDetail(vm.state.value.logins.single().id)
        vm.onUnlocked()
        vm.reveal()

        vm.onClose()

        assertNull(vm.state.value.revealedPassword)
        assertFalse(vm.state.value.toString().contains("segreta"))
    }

    @Test
    fun `modifica eliminazione e ricerca`() = runTest {
        val (vm, passwords) = viewModel()
        passwords.save(site, "luisa", "altra")
        passwords.save("https://altro.it", "anna", "terza")
        val mario = vm.state.value.logins.first { it.username == "mario" }.id

        var result: Boolean? = null
        vm.update(mario, "luisa", "x") { result = it }
        assertEquals(false, result)

        vm.update(mario, "mario.rossi", "nuova") { result = it }
        assertEquals(true, result)
        assertEquals("nuova", passwords.passwordOf(site, "mario.rossi"))

        vm.setQuery("altro")
        assertEquals(listOf("anna"), vm.state.value.filtered.map { it.username })

        vm.openDetail(mario)
        vm.delete(mario)
        assertEquals(PasswordsPage.LIST, vm.state.value.page)
        assertEquals(2, vm.state.value.logins.size)
    }

    @Test
    fun `impostazioni del gestore`() = runTest {
        val settings = FakeSettingsRepository(BrowserSettings(passwordNeverSites = setOf("https://a.it")))
        val (vm, _) = viewModel(settings = settings)

        vm.setOfferToSave(false)
        vm.removeNeverSite("https://a.it")

        assertFalse(settings.current.offerToSavePasswords)
        assertTrue(settings.current.passwordNeverSites.isEmpty())
        assertFalse(vm.navigateUp())
        vm.openNeverSites()
        assertTrue(vm.navigateUp())
    }
}
