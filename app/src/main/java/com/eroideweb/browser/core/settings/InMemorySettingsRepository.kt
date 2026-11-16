package com.eroideweb.browser.core.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Impostazioni in memoria: usate dalla finestra privata, che vive in un
 * processo separato e non deve toccare il DataStore condiviso.
 */
class InMemorySettingsRepository(
    initial: BrowserSettings = BrowserSettings(),
) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<BrowserSettings> = state.asStateFlow()

    override suspend fun setToolbarPosition(position: ToolbarPosition) {
        state.update { it.copy(toolbarPosition = position) }
    }

    override suspend fun setSearchEngine(engine: SearchEngine) {
        state.update { it.copy(searchEngine = engine) }
    }

    override suspend fun setAdBlockEnabled(enabled: Boolean) {
        state.update { it.copy(adBlockEnabled = enabled) }
    }

    override suspend fun setAdBlockDisabledForSite(host: String, disabled: Boolean) {
        state.update { current ->
            current.copy(
                adBlockDisabledSites = if (disabled) {
                    current.adBlockDisabledSites + host
                } else {
                    current.adBlockDisabledSites - host
                },
            )
        }
    }
}
