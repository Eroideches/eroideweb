package com.eroideweb.browser.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eroideweb.browser.core.session.DataStoreSessionRepository
import com.eroideweb.browser.core.session.SessionRepository
import com.eroideweb.browser.core.settings.DataStoreSettingsRepository
import com.eroideweb.browser.core.settings.SettingsRepository
import com.eroideweb.browser.feature.browser.BrowserViewModel

/**
 * Dependency injection manuale: niente framework, niente reflection.
 * Quando il grafo crescerà (Room, liste filtri, download) le dipendenze
 * si aggiungono qui, restando comunque lazy.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(appContext)

    val sessionRepository: SessionRepository = DataStoreSessionRepository(appContext)

    val browserViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { BrowserViewModel(settingsRepository, sessionRepository) }
    }
}
