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

    override suspend fun update(transform: (BrowserSettings) -> BrowserSettings) {
        state.update(transform)
    }

    /** Sostituisce tutte le impostazioni (es. quelle ereditate dalla finestra normale). */
    fun replace(settings: BrowserSettings) {
        state.value = settings
    }
}
