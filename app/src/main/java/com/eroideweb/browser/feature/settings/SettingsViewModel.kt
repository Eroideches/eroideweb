package com.eroideweb.browser.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eroideweb.browser.core.adblock.AdBlockProvider
import com.eroideweb.browser.core.settings.BrowserSettings
import com.eroideweb.browser.core.settings.SearchEngine
import com.eroideweb.browser.core.settings.SettingsRepository
import com.eroideweb.browser.core.settings.ToolbarPosition
import com.eroideweb.browser.core.settings.setAdBlockDisabledForSite
import com.eroideweb.browser.core.settings.setAdBlockEnabled
import com.eroideweb.browser.core.settings.setSearchEngine
import com.eroideweb.browser.core.settings.setToolbarPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pagine della schermata Impostazioni. */
enum class SettingsPage {
    ROOT,
    SEARCH_ENGINE,
    ADBLOCK_SITES,
    ABOUT,
}

data class SettingsUiState(
    val settings: BrowserSettings = BrowserSettings(),
    val page: SettingsPage = SettingsPage.ROOT,
    /** Host bloccati dalle liste; `null` finché le liste non sono caricate. */
    val adBlockRuleCount: Int? = null,
    /** Nella finestra privata le modifiche valgono solo per quella finestra. */
    val isPrivate: Boolean = false,
)

/**
 * Impostazioni del browser. Ogni modifica è ottimistica (la UI non aspetta il
 * disco) e poi salvata nel repository, da cui la legge anche il browser.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val adBlocker: AdBlockProvider,
    privateMode: Boolean = false,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(isPrivate = privateMode))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
    }

    /** Chiamato all'apertura della schermata: riparte sempre dalla pagina principale. */
    fun onOpen() {
        _state.update {
            it.copy(page = SettingsPage.ROOT, adBlockRuleCount = adBlocker.engineOrNull()?.ruleCount)
        }
    }

    fun openPage(page: SettingsPage) {
        _state.update { it.copy(page = page) }
    }

    /** @return `false` se si è già sulla pagina principale (la schermata va chiusa). */
    fun navigateUp(): Boolean {
        if (_state.value.page == SettingsPage.ROOT) return false
        _state.update { it.copy(page = SettingsPage.ROOT) }
        return true
    }

    fun setToolbarPosition(position: ToolbarPosition) {
        updateLocal { it.copy(toolbarPosition = position) }
        viewModelScope.launch { repository.setToolbarPosition(position) }
    }

    fun setSearchEngine(engine: SearchEngine) {
        updateLocal { it.copy(searchEngine = engine) }
        viewModelScope.launch { repository.setSearchEngine(engine) }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        updateLocal { it.copy(adBlockEnabled = enabled) }
        viewModelScope.launch { repository.setAdBlockEnabled(enabled) }
    }

    /** Riattiva il blocco su un sito escluso in precedenza. */
    fun removeAdBlockException(host: String) {
        updateLocal { it.copy(adBlockDisabledSites = it.adBlockDisabledSites - host) }
        viewModelScope.launch { repository.setAdBlockDisabledForSite(host, disabled = false) }
    }

    private fun updateLocal(transform: (BrowserSettings) -> BrowserSettings) {
        _state.update { it.copy(settings = transform(it.settings)) }
    }
}
