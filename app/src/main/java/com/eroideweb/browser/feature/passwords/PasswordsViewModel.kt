package com.eroideweb.browser.feature.passwords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eroideweb.browser.core.password.PasswordRepository
import com.eroideweb.browser.core.password.SavedLogin
import com.eroideweb.browser.core.settings.SettingsRepository
import com.eroideweb.browser.core.settings.setOfferToSavePasswords
import com.eroideweb.browser.core.settings.setPasswordNeverSite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PasswordsPage {
    LIST,
    DETAIL,
    NEVER_SITES,
}

data class PasswordsUiState(
    val logins: List<SavedLogin> = emptyList(),
    val query: String = "",
    val page: PasswordsPage = PasswordsPage.LIST,
    val selectedId: Long? = null,
    /** Password mostrata nel dettaglio; si cancella appena si esce. */
    val revealedPassword: String? = null,
    /** Fino a quando lo sblocco del telefono vale ancora (millisecondi). */
    val unlockedUntil: Long = 0,
    val offerToSave: Boolean = true,
    val neverSites: Set<String> = emptySet(),
) {
    val filtered: List<SavedLogin>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return logins
            return logins.filter { it.origin.contains(q, ignoreCase = true) || it.username.contains(q, ignoreCase = true) }
        }

    val selected: SavedLogin? get() = logins.firstOrNull { it.id == selectedId }

    override fun toString(): String =
        "PasswordsUiState(page=$page, logins=${logins.size}, selectedId=$selectedId, revealed=${revealedPassword != null})"
}

/**
 * Schermata Password. Vedere o copiare una password richiede lo sblocco del
 * telefono (impronta, volto o PIN), valido [UNLOCK_WINDOW_MS] poi si richiede.
 */
class PasswordsViewModel(
    private val repository: PasswordRepository,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(PasswordsUiState())
    val state: StateFlow<PasswordsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll().collect { logins -> _state.update { it.copy(logins = logins) } }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update {
                    it.copy(offerToSave = settings.offerToSavePasswords, neverSites = settings.passwordNeverSites)
                }
            }
        }
    }

    fun onOpen() {
        _state.update { it.copy(page = PasswordsPage.LIST, query = "", selectedId = null, revealedPassword = null) }
    }

    /** Uscendo dalla schermata nessuna password resta in memoria. */
    fun onClose() {
        _state.update { it.copy(revealedPassword = null, selectedId = null, page = PasswordsPage.LIST) }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun openDetail(id: Long) {
        _state.update { it.copy(page = PasswordsPage.DETAIL, selectedId = id, revealedPassword = null) }
    }

    fun openNeverSites() {
        _state.update { it.copy(page = PasswordsPage.NEVER_SITES) }
    }

    /** @return `false` se si è già sull'elenco (la schermata va chiusa). */
    fun navigateUp(): Boolean {
        if (_state.value.page == PasswordsPage.LIST) return false
        _state.update { it.copy(page = PasswordsPage.LIST, selectedId = null, revealedPassword = null) }
        return true
    }

    fun isUnlocked(): Boolean = now() < _state.value.unlockedUntil

    /** Sblocco del telefono riuscito. */
    fun onUnlocked() {
        _state.update { it.copy(unlockedUntil = now() + UNLOCK_WINDOW_MS) }
    }

    /** Mostra la password selezionata (solo a telefono sbloccato da poco). */
    fun reveal() {
        val id = _state.value.selectedId ?: return
        if (!isUnlocked()) return
        viewModelScope.launch {
            val password = repository.password(id)
            _state.update { if (it.selectedId == id) it.copy(revealedPassword = password) else it }
        }
    }

    fun hide() {
        _state.update { it.copy(revealedPassword = null) }
    }

    /** Password da copiare; `null` se il telefono non è stato sbloccato da poco. */
    suspend fun passwordForCopy(id: Long): String? {
        if (!isUnlocked()) return null
        return repository.password(id)
    }

    /** @param onResult `false` se esiste già un'altra voce con quell'utente sullo stesso sito. */
    fun update(id: Long, username: String, password: String, onResult: (Boolean) -> Unit) {
        if (password.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val ok = repository.update(id, username, password)
            if (ok) _state.update { if (it.selectedId == id) it.copy(revealedPassword = null) else it }
            onResult(ok)
        }
    }

    fun delete(id: Long) {
        _state.update { it.copy(page = PasswordsPage.LIST, selectedId = null, revealedPassword = null) }
        viewModelScope.launch { repository.delete(id) }
    }

    fun deleteAll() {
        _state.update { it.copy(page = PasswordsPage.LIST, selectedId = null, revealedPassword = null) }
        viewModelScope.launch { repository.deleteAll() }
    }

    fun setOfferToSave(enabled: Boolean) {
        _state.update { it.copy(offerToSave = enabled) }
        viewModelScope.launch { settingsRepository.setOfferToSavePasswords(enabled) }
    }

    fun removeNeverSite(origin: String) {
        _state.update { it.copy(neverSites = it.neverSites - origin) }
        viewModelScope.launch { settingsRepository.setPasswordNeverSite(origin, never = false) }
    }

    companion object {
        const val UNLOCK_WINDOW_MS = 60_000L
    }
}
