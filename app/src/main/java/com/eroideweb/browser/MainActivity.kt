package com.eroideweb.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eroideweb.browser.feature.browser.BrowserScreen
import com.eroideweb.browser.feature.browser.BrowserViewModel
import com.eroideweb.browser.feature.library.LibraryViewModel
import com.eroideweb.browser.feature.settings.SettingsViewModel
import com.eroideweb.browser.ui.theme.ThemedContent

/**
 * Activity principale. Le schede vivono nel ViewModel (metadati) e in
 * [com.eroideweb.browser.feature.browser.web.TabWebViews] (WebView reali).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as EroideWebApp).container

        setContent {
            ThemedContent(activity = this, settings = container.settingsRepository.settings) { settings ->
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
                    settings = settings,
                    onExit = { finish() },
                )
            }
        }
    }
}
