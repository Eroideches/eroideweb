package com.eroideweb.browser.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eroideweb.browser.core.adblock.AdBlocker
import com.eroideweb.browser.core.bookmark.BookmarkRepository
import com.eroideweb.browser.core.bookmark.RoomBookmarkRepository
import com.eroideweb.browser.core.data.EroideDatabase
import com.eroideweb.browser.core.download.AndroidDownloadRepository
import com.eroideweb.browser.core.download.DownloadRepository
import com.eroideweb.browser.core.bookmark.NoOpBookmarkRepository
import com.eroideweb.browser.core.history.HistoryRepository
import com.eroideweb.browser.core.history.NoOpHistoryRepository
import com.eroideweb.browser.core.history.RoomHistoryRepository
import com.eroideweb.browser.core.session.DataStoreSessionRepository
import com.eroideweb.browser.core.session.NoOpSessionRepository
import com.eroideweb.browser.core.session.SessionRepository
import com.eroideweb.browser.core.settings.DataStoreSettingsRepository
import com.eroideweb.browser.core.settings.InMemorySettingsRepository
import com.eroideweb.browser.core.settings.SettingsRepository
import com.eroideweb.browser.feature.browser.BrowserViewModel
import com.eroideweb.browser.feature.library.LibraryViewModel

/**
 * Dependency injection manuale: niente framework, niente reflection.
 * Quando il grafo crescerà (Room, liste filtri, download) le dipendenze
 * si aggiungono qui, restando comunque lazy.
 */
class AppContainer(
    context: Context,
    /** True nel processo `:private`: niente DataStore, niente Room, niente tracce. */
    private val privateMode: Boolean = false,
) {

    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository = if (privateMode) {
        InMemorySettingsRepository()
    } else {
        DataStoreSettingsRepository(appContext)
    }

    val sessionRepository: SessionRepository = if (privateMode) {
        NoOpSessionRepository
    } else {
        DataStoreSessionRepository(appContext)
    }

    /** Liste EasyList/EasyPrivacy/EasyList Italy caricate in background. */
    val adBlocker: AdBlocker = AdBlocker(appContext)

    private val database by lazy { EroideDatabase.get(appContext) }

    val historyRepository: HistoryRepository by lazy {
        if (privateMode) NoOpHistoryRepository else RoomHistoryRepository(database.historyDao())
    }

    val bookmarkRepository: BookmarkRepository by lazy {
        if (privateMode) NoOpBookmarkRepository else RoomBookmarkRepository(database.bookmarkDao())
    }

    val downloadRepository: DownloadRepository by lazy {
        // In privato il file si scarica ma non viene registrato nel database.
        AndroidDownloadRepository(appContext, if (privateMode) null else database.downloadDao())
    }

    val browserViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            BrowserViewModel(
                settingsRepository = settingsRepository,
                sessionRepository = sessionRepository,
                historyRepository = historyRepository,
                bookmarkRepository = bookmarkRepository,
                adBlocker = adBlocker,
                privateMode = privateMode,
            )
        }
    }

    val libraryViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            LibraryViewModel(historyRepository, bookmarkRepository, downloadRepository)
        }
    }
}
