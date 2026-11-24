package com.eroideweb.browser.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eroideweb.browser.core.adblock.AdBlocker
import com.eroideweb.browser.core.bookmark.BookmarkRepository
import com.eroideweb.browser.core.bookmark.RoomBookmarkRepository
import com.eroideweb.browser.core.browsingdata.AndroidBrowsingDataCleaner
import com.eroideweb.browser.core.browsingdata.BrowsingDataCleaner
import com.eroideweb.browser.core.data.EroideDatabase
import com.eroideweb.browser.core.download.AndroidDownloadRepository
import com.eroideweb.browser.core.download.DownloadRepository
import com.eroideweb.browser.core.favicon.FaviconStore
import com.eroideweb.browser.core.favorites.FavoriteRepository
import com.eroideweb.browser.core.favorites.NoOpFavoriteRepository
import com.eroideweb.browser.core.favorites.RoomFavoriteRepository
import com.eroideweb.browser.core.bookmark.NoOpBookmarkRepository
import com.eroideweb.browser.core.history.HistoryRepository
import com.eroideweb.browser.core.password.AesGcmCipher
import com.eroideweb.browser.core.password.KeystoreKeys
import com.eroideweb.browser.core.password.NoOpPasswordRepository
import com.eroideweb.browser.core.password.PasswordRepository
import com.eroideweb.browser.core.password.RoomPasswordRepository
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
import com.eroideweb.browser.feature.passwords.PasswordsViewModel
import com.eroideweb.browser.feature.settings.SettingsViewModel
import java.io.File

/**
 * Dependency injection manuale: niente framework, niente reflection.
 * Le dipendenze nuove si aggiungono qui, restando lazy dove possibile.
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

    val favoriteRepository: FavoriteRepository by lazy {
        if (privateMode) NoOpFavoriteRepository else RoomFavoriteRepository(database.favoriteDao())
    }

    /** Password cifrate con una chiave del Keystore; niente in modalità privata. */
    val passwordRepository: PasswordRepository by lazy {
        if (privateMode) {
            NoOpPasswordRepository
        } else {
            RoomPasswordRepository(database.credentialDao(), AesGcmCipher(KeystoreKeys::credentialsKey))
        }
    }

    /** Favicon viste navigando: su disco solo fuori dalla finestra privata. */
    val faviconStore: FaviconStore by lazy {
        FaviconStore(if (privateMode) null else File(appContext.filesDir, "favicons"))
    }

    val downloadRepository: DownloadRepository by lazy {
        // In privato il file si scarica ma non viene registrato nel database.
        AndroidDownloadRepository(appContext, if (privateMode) null else database.downloadDao())
    }

    /** Solo per la finestra normale: quella privata si svuota da sola. */
    val browsingDataCleaner: BrowsingDataCleaner? by lazy {
        if (privateMode) {
            null
        } else {
            AndroidBrowsingDataCleaner(
                context = appContext,
                history = historyRepository,
                downloads = downloadRepository,
                favicons = faviconStore,
                extraCleaners = mapOf(
                    AndroidBrowsingDataCleaner.ExtraData.PASSWORDS to { passwordRepository.deleteAll() },
                ),
            )
        }
    }

    val browserViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            BrowserViewModel(
                settingsRepository = settingsRepository,
                sessionRepository = sessionRepository,
                historyRepository = historyRepository,
                bookmarkRepository = bookmarkRepository,
                adBlocker = adBlocker,
                favoriteRepository = favoriteRepository,
                passwordRepository = passwordRepository,
                privateMode = privateMode,
            )
        }
    }

    val passwordsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { PasswordsViewModel(passwordRepository, settingsRepository) }
    }

    val libraryViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            LibraryViewModel(historyRepository, bookmarkRepository, downloadRepository)
        }
    }

    val settingsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            SettingsViewModel(
                repository = settingsRepository,
                adBlocker = adBlocker,
                privateMode = privateMode,
                cleaner = browsingDataCleaner,
            )
        }
    }
}
