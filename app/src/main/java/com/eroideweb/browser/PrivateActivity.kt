package com.eroideweb.browser

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eroideweb.browser.feature.browser.BrowserScreen
import com.eroideweb.browser.feature.browser.BrowserViewModel
import com.eroideweb.browser.feature.library.LibraryViewModel
import com.eroideweb.browser.feature.settings.SettingsViewModel
import com.eroideweb.browser.ui.theme.EroideWebTheme

/**
 * Finestra privata. Gira in un processo separato (`:private`) con una
 * directory dati WebView dedicata (impostata in [EroideWebApp]): cookie,
 * cache e storage sono fisicamente isolati da quelli della navigazione normale.
 *
 * All'apertura e alla chiusura il profilo viene svuotato: non resta traccia
 * né in memoria né su disco.
 */
class PrivateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Se una sessione precedente è stata interrotta, qui si riparte puliti.
        wipePrivateProfile()

        val container = (application as EroideWebApp).container

        setContent {
            EroideWebTheme {
                val browserViewModel: BrowserViewModel = viewModel(
                    factory = container.browserViewModelFactory,
                )
                val libraryViewModel: LibraryViewModel = viewModel(
                    factory = container.libraryViewModelFactory,
                )
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = container.settingsViewModelFactory,
                )
                BrowserScreen(
                    viewModel = browserViewModel,
                    libraryViewModel = libraryViewModel,
                    settingsViewModel = settingsViewModel,
                    downloadRepository = container.downloadRepository,
                    onExit = { finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            wipePrivateProfile()
        }
    }

    /** Cookie, storage locale e cache HTTP: tutto dentro la directory privata. */
    private fun wipePrivateProfile() {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            WebView(applicationContext).apply {
                clearCache(true)
                clearFormData()
                clearHistory()
                destroy()
            }
        }
    }
}
