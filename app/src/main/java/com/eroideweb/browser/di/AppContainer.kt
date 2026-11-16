package com.eroideweb.browser.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eroideweb.browser.core.bookmark.BookmarkRepository
import com.eroideweb.browser.core.bookmark.RoomBookmarkRepository
import com.eroideweb.browser.core.data.EroideDatabase
import com.eroideweb.browser.core.history.HistoryRepository
import com.eroideweb.browser.core.history.RoomHistoryRepository
import com.eroideweb.browser.core.session.DataStoreSessionRepository
import com.eroideweb.browser.core.session.SessionRepository
import com.eroideweb.browser.core.settings.DataStoreSettingsRepository
import com.eroideweb.browser.core.settings.SettingsRepository
import com.eroideweb.browser.feature.browser.BrowserViewModel
import com.eroideweb.browser.feature.library.LibraryViewModel

/**
 * Dependency injection manuale: niente framework, niente reflection.
 * Quando il grafo crescerà (Room, liste filtri, download) le dipendenze
 * si aggiungono qui, restando comunque lazy.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(appContext)

    val sessionRepository: SessionRepository = DataStoreSessionRepository(appContext)

    private val database by lazy { EroideDatabase.get(appContext) }

    val historyRepository: HistoryRepository by lazy {
        RoomHistoryRepository(database.historyDao())
    }

    val bookmarkRepository: BookmarkRepository by lazy {
        RoomBookmarkRepository(database.bookmarkDao())
    }

    val browserViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            BrowserViewModel(
                settingsRepository = settingsRepository,
                sessionRepository = sessionRepository,
                historyRepository = historyRepository,
                bookmarkRepository = bookmarkRepository,
            )
        }
    }

    val libraryViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { LibraryViewModel(historyRepository, bookmarkRepository) }
    }
}
