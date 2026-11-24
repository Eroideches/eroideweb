package com.eroideweb.browser.feature.browser

import com.eroideweb.browser.core.settings.BrowserSettings
import com.eroideweb.browser.core.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Implementazione in memoria: i test non toccano DataStore né Android. */
class FakeSettingsRepository(
    initial: BrowserSettings = BrowserSettings(),
) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    /** Ultimo valore salvato, per le verifiche. */
    val current: BrowserSettings get() = state.value

    override val settings: Flow<BrowserSettings> = state.asStateFlow()

    override suspend fun update(transform: (BrowserSettings) -> BrowserSettings) {
        state.update(transform)
    }
}
