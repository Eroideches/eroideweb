package com.eroideweb.browser

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eroideweb.browser.core.url.IncomingLink
import com.eroideweb.browser.feature.browser.BrowserScreen
import com.eroideweb.browser.feature.browser.BrowserViewModel
import com.eroideweb.browser.feature.library.LibraryViewModel
import com.eroideweb.browser.feature.settings.SettingsViewModel
import com.eroideweb.browser.ui.theme.ThemedContent

/**
 * Activity principale. Le schede vivono nel ViewModel (metadati) e in
 * [com.eroideweb.browser.feature.browser.web.TabWebViews] (WebView reali).
 *
 * Riceve anche i link delle altre app quando EroideWeb è il browser
 * predefinito, le condivisioni di testo e il "Cerca con EroideWeb".
 */
class MainActivity : ComponentActivity() {

    private val container by lazy { (application as EroideWebApp).container }

    /** Stessa istanza che usa Compose (stesso proprietario, stessa chiave). */
    private val browserViewModel: BrowserViewModel by lazy {
        ViewModelProvider(this, container.browserViewModelFactory)[BrowserViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
                    faviconStore = container.faviconStore,
                    settings = settings,
                    onExit = { finish() },
                    // Una scheda aperta da un'altra app riporta là, senza chiudere il browser.
                    onBackToCaller = { moveTaskToBack(true) },
                )
            }
        }

        // Dopo una rotazione o un ripristino l'Intent è già stato gestito.
        if (savedInstanceState == null) handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return
        // Riaperta dalle app recenti: l'Intent è quello vecchio, non va riaperto.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val link = IncomingLink.from(
            action = intent.action,
            data = intent.dataString,
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT),
            searchQuery = intent.getStringExtra(SearchManager.QUERY),
            processText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString(),
        ) ?: return
        browserViewModel.openIncoming(link)
    }
}
