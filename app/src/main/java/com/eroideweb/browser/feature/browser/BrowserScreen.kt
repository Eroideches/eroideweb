package com.eroideweb.browser.feature.browser

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eroideweb.browser.core.settings.ToolbarPosition
import com.eroideweb.browser.feature.browser.web.TabWebViews
import com.eroideweb.browser.feature.library.LibraryScreen
import com.eroideweb.browser.feature.library.LibraryViewModel

/**
 * Schermata del browser.
 *
 * La barra occupa spazio reale (non galleggia sopra la pagina): prima copriva
 * pulsanti e sezioni dei siti, ed era inaccettabile. Il look "glass" resta,
 * ma il contenuto non finisce mai sotto la barra.
 *
 * Le WebView vivono in [TabWebViews] (layer UI): il ViewModel conosce solo i
 * metadati delle schede, così resta testabile su JVM. Lo stato delle WebView
 * sopravvive alla rotazione tramite `webViewStates` nel ViewModel.
 */
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    libraryViewModel: LibraryViewModel,
    onExit: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val host = remember { TabWebViews(context) }
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tab = state.activeTab
    val barAtTop = state.toolbarPosition == ToolbarPosition.TOP

    DisposableEffect(host, viewModel) {
        host.callbacks = object : TabWebViews.Callbacks {
            override fun onPageStarted(tabId: String, url: String) {
                viewModel.onPageStarted(tabId, url)
            }

            override fun onPageFinished(tabId: String, url: String, title: String?) {
                viewModel.onPageFinished(tabId, url, title)
            }

            override fun onProgressChanged(tabId: String, progress: Int) {
                viewModel.onProgressChanged(tabId, progress)
            }

            override fun onNavigationStateChanged(
                tabId: String,
                canGoBack: Boolean,
                canGoForward: Boolean,
                url: String?,
            ) {
                viewModel.onNavigationStateChanged(tabId, canGoBack, canGoForward, url)
            }

            override fun onPageError(tabId: String, error: PageError) {
                viewModel.onPageError(tabId, error)
            }

            override fun consumeSavedState(tabId: String): Bundle? =
                viewModel.webViewStates.consume(tabId)

            override fun saveState(tabId: String, state: Bundle) {
                viewModel.webViewStates.save(tabId, state)
            }

            override fun onCreateWindowTab(): String = viewModel.openTabFromWindow()
        }
        onDispose {
            // Salva lo stato prima di distruggere: la rotazione non ricarica nulla.
            host.destroy()
            host.callbacks = null
        }
    }

    // Cambio scheda o primo caricamento della scheda attiva.
    LaunchedEffect(tab.id, tab.url) {
        host.show(tab.id, tab.url)
    }

    // Chiude le WebView delle schede eliminate dal modello.
    LaunchedEffect(state.tabs.map { it.id }) {
        host.retainOnly(state.tabs.map { it.id }.toSet())
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> host.pauseAll()
                Lifecycle.Event.ON_RESUME -> host.resumeAll()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val bar: @Composable () -> Unit = {
        AddressBar(
            state = state,
            onTextChange = viewModel::onAddressBarTextChange,
            onFocusChange = viewModel::onAddressBarFocusChange,
            onSubmit = { text ->
                viewModel.submitAddressBar(text)?.let { url -> host.load(tab.id, url) }
            },
            onReload = { host.reload(tab.id) },
            onStop = { host.stopLoading(tab.id) },
            onToolbarPositionChange = viewModel::setToolbarPosition,
            onSearchEngineChange = viewModel::setSearchEngine,
            tabCount = state.tabs.size,
            onShowTabs = viewModel::toggleTabSwitcher,
            onOpenHistory = {
                libraryViewModel.setTab(com.eroideweb.browser.feature.library.LibraryTab.HISTORY)
                viewModel.setLibraryVisible(true)
            },
            onOpenBookmarks = {
                libraryViewModel.setTab(com.eroideweb.browser.feature.library.LibraryTab.BOOKMARKS)
                viewModel.setLibraryVisible(true)
            },
            onToggleBookmark = viewModel::toggleBookmark,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }

    val onRetry: () -> Unit = {
        viewModel.clearError(tab.id)
        tab.url?.let { url -> host.load(tab.id, url) }
    }

    val page: @Composable (Modifier) -> Unit = { pageModifier ->
        PageArea(
            state = state,
            host = host,
            onRetry = onRetry,
            modifier = pageModifier,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            if (barAtTop) {
                bar()
                page(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                page(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                bar()
            }
        }

        if (state.isLibraryVisible) {
            LibraryScreen(
                viewModel = libraryViewModel,
                onOpenUrl = { url ->
                    viewModel.setLibraryVisible(false)
                    viewModel.submitAddressBar(url)?.let { loadable -> host.load(tab.id, loadable) }
                },
                onDismiss = { viewModel.setLibraryVisible(false) },
            )
        }

        if (state.isTabSwitcherVisible) {
            TabSwitcher(
                tabs = state.tabs,
                activeTabId = state.activeTabId,
                onSelect = viewModel::selectTab,
                onClose = viewModel::closeTab,
                onNewTab = viewModel::openNewTab,
                onDismiss = { viewModel.setTabSwitcherVisible(false) },
            )
        }
    }

    BackHandler {
        when {
            state.isLibraryVisible -> viewModel.setLibraryVisible(false)
            state.isTabSwitcherVisible -> viewModel.setTabSwitcherVisible(false)
            state.isAddressFocused -> focusManager.clearFocus()
            host.canGoBack(tab.id) -> host.goBack(tab.id)
            else -> onExit()
        }
    }
}

@Composable
private fun PageArea(
    state: BrowserUiState,
    host: TabWebViews,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tab = state.activeTab
    Box(modifier) {
        // Il contenitore resta sempre in composizione: cambiando scheda le
        // WebView vive vengono solo agganciate e sganciate, mai ricreate.
        AndroidView(
            factory = { host.container },
            modifier = Modifier.fillMaxSize(),
        )

        if (tab.url == null) {
            StartPage()
        }

        tab.error?.let { error ->
            ErrorPage(
                error = error,
                onRetry = onRetry,
            )
        }
    }
}
