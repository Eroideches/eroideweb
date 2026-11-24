package com.eroideweb.browser.feature.browser

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eroideweb.browser.PrivateActivity
import com.eroideweb.browser.R
import com.eroideweb.browser.core.download.DownloadRepository
import com.eroideweb.browser.core.download.DownloadRequest
import com.eroideweb.browser.core.settings.ToolbarPosition
import com.eroideweb.browser.core.translate.TranslateUrl
import com.eroideweb.browser.feature.browser.menu.BrowserMenuActions
import com.eroideweb.browser.feature.browser.menu.BrowserMenuSheet
import com.eroideweb.browser.feature.browser.web.RequestFilter
import com.eroideweb.browser.feature.browser.web.TabWebViews
import com.eroideweb.browser.feature.library.LibraryScreen
import com.eroideweb.browser.feature.library.LibraryTab
import com.eroideweb.browser.feature.library.LibraryViewModel
import com.eroideweb.browser.feature.settings.SettingsScreen
import com.eroideweb.browser.feature.settings.SettingsViewModel
import com.eroideweb.browser.ui.common.GlassCapsule
import kotlinx.coroutines.launch

/**
 * Schermata del browser.
 *
 * La barra occupa spazio reale (non galleggia sopra la pagina): prima copriva
 * pulsanti e sezioni dei siti, ed era inaccettabile. Il look "glass" resta,
 * ma il contenuto non finisce mai sotto la barra.
 *
 * Due righe: indirizzo (o "Trova nella pagina") e azioni ← → + schede ☰.
 * Con la barra in alto la riga azioni resta in basso, a portata di pollice.
 *
 * Le WebView vivono in [TabWebViews] (layer UI): il ViewModel conosce solo i
 * metadati delle schede, così resta testabile su JVM. Lo stato delle WebView
 * sopravvive alla rotazione tramite `webViewStates` nel ViewModel.
 */
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    libraryViewModel: LibraryViewModel,
    settingsViewModel: SettingsViewModel,
    downloadRepository: DownloadRepository,
    onExit: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestFilter = remember(viewModel) {
        RequestFilter { requestUrl, pageUrl -> viewModel.shouldBlockRequest(requestUrl, pageUrl) }
    }
    val host = remember { TabWebViews(context, requestFilter) }
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val downloadScope = rememberCoroutineScope()
    val tab = state.activeTab
    val barAtTop = state.toolbarPosition == ToolbarPosition.TOP
    var showTranslateNotice by remember { mutableStateOf(false) }

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

            override fun onDownloadRequested(
                tabId: String,
                url: String,
                userAgent: String?,
                contentDisposition: String?,
                mimeType: String?,
                contentLength: Long,
            ) {
                downloadScope.launch {
                    val started = downloadRepository.enqueue(
                        DownloadRequest(
                            url = url,
                            userAgent = userAgent,
                            contentDisposition = contentDisposition,
                            mimeType = mimeType,
                            contentLength = contentLength,
                        ),
                    )
                    Toast.makeText(
                        context,
                        if (started) R.string.download_started else R.string.download_not_started,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }

            override fun onFindResult(
                tabId: String,
                activeMatchOrdinal: Int,
                matchCount: Int,
                isDoneCounting: Boolean,
            ) {
                viewModel.onFindResult(tabId, activeMatchOrdinal, matchCount)
            }

            override fun isDesktopMode(tabId: String): Boolean = viewModel.isDesktopMode(tabId)
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

    // Trova nella pagina: la WebView cerca a ogni modifica del testo e pulisce
    // le evidenziazioni della scheda giusta quando la barra si chiude.
    val findQuery = state.findInPage?.query
    var findTabId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(findQuery, tab.id) {
        if (findQuery != null) {
            host.find(tab.id, findQuery)
            findTabId = tab.id
        } else {
            findTabId?.let(host::clearFind)
            findTabId = null
        }
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

    val openLibrary: (LibraryTab) -> Unit = { libraryTab ->
        libraryViewModel.setTab(libraryTab)
        libraryViewModel.setVisible(true)
        viewModel.setLibraryVisible(true)
    }

    val closeLibrary = {
        viewModel.setLibraryVisible(false)
        libraryViewModel.setVisible(false)
    }

    val translate: () -> Unit = {
        val locale = context.resources.configuration.locales[0]
        val language = TranslateUrl.languageCode(locale.language, locale.country)
        val url = viewModel.translateActivePage(language)
        if (url != null) {
            host.load(tab.id, url)
        } else {
            Toast.makeText(context, R.string.translate_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    val menuActions = BrowserMenuActions(
        onShare = {
            viewModel.closeOverlay()
            tab.url?.let { BrowserActions.share(context, it, tab.title) }
        },
        onFind = viewModel::startFindInPage,
        onTranslate = {
            viewModel.closeOverlay()
            if (state.isActiveTranslated || state.translateNoticeAccepted) {
                translate()
            } else {
                showTranslateNotice = true
            }
        },
        onToggleDesktop = {
            viewModel.toggleDesktopMode()?.let { enabled ->
                host.setDesktopMode(tab.id, enabled)
                Toast.makeText(
                    context,
                    if (enabled) R.string.desktop_on else R.string.desktop_off,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
        onToggleBookmark = viewModel::toggleBookmark,
        onToggleAdBlockForSite = viewModel::toggleAdBlockForSite,
        onOpenHistory = { openLibrary(LibraryTab.HISTORY) },
        onOpenBookmarks = { openLibrary(LibraryTab.BOOKMARKS) },
        onOpenDownloads = { openLibrary(LibraryTab.DOWNLOADS) },
        onOpenPrivateWindow = {
            viewModel.closeOverlay()
            context.startActivity(Intent(context, PrivateActivity::class.java))
        },
        onOpenSettings = {
            settingsViewModel.onOpen()
            viewModel.openSettings()
        },
        onClosePrivateWindow = {
            viewModel.closeOverlay()
            onExit()
        },
    )

    val onRetry: () -> Unit = {
        viewModel.clearError(tab.id)
        tab.url?.let { url -> host.load(tab.id, url) }
    }

    // Riga in cima alla capsula: indirizzo, oppure "Trova nella pagina".
    val primaryRow: @Composable () -> Unit = {
        val find = state.findInPage
        if (find != null) {
            FindInPageBar(
                state = find,
                onQueryChange = viewModel::onFindQueryChange,
                onPrevious = { host.findNext(tab.id, forward = false) },
                onNext = { host.findNext(tab.id, forward = true) },
                onClose = viewModel::closeFindInPage,
            )
        } else {
            AddressBar(
                state = state,
                onTextChange = viewModel::onAddressBarTextChange,
                onFocusChange = viewModel::onAddressBarFocusChange,
                onSubmit = { text ->
                    viewModel.submitAddressBar(text)?.let { url -> host.load(tab.id, url) }
                },
                onReload = { host.reload(tab.id) },
                onStop = { host.stopLoading(tab.id) },
            )
        }
    }

    // Nascosta mentre si scrive: tastiera e suggerimenti hanno più spazio. Senza
    // animazione, così la WebView cambia dimensione una volta sola.
    val showNavigationRow = !state.isAddressFocused && state.findInPage == null
    val navigationRow: @Composable () -> Unit = {
        NavigationRow(
            tab = tab,
            tabCount = state.tabs.size,
            backHistory = { host.backHistory(tab.id) },
            onBack = { host.goBack(tab.id) },
            onForward = { host.goForward(tab.id) },
            onHistoryEntry = { entry -> host.goToHistoryOffset(tab.id, entry.offset) },
            onNewTab = viewModel::openNewTab,
            onShowTabs = viewModel::toggleTabSwitcher,
            onMenu = viewModel::openMenu,
        )
    }

    val capsuleModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 6.dp)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            if (barAtTop) {
                GlassCapsule(modifier = capsuleModifier) { primaryRow() }
                PageArea(
                    state = state,
                    host = host,
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                if (showNavigationRow) {
                    GlassCapsule(modifier = capsuleModifier) { navigationRow() }
                }
            } else {
                PageArea(
                    state = state,
                    host = host,
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                GlassCapsule(modifier = capsuleModifier) {
                    primaryRow()
                    if (showNavigationRow) navigationRow()
                }
            }
        }

        OverlayVisibility(visible = state.overlay == BrowserOverlay.LIBRARY) {
            LibraryScreen(
                viewModel = libraryViewModel,
                onOpenUrl = { url ->
                    closeLibrary()
                    viewModel.submitAddressBar(url)?.let { loadable -> host.load(tab.id, loadable) }
                },
                onDismiss = closeLibrary,
            )
        }

        OverlayVisibility(visible = state.overlay == BrowserOverlay.TABS) {
            TabSwitcher(
                tabs = state.tabs,
                activeTabId = state.activeTabId,
                onSelect = viewModel::selectTab,
                onClose = viewModel::closeTab,
                onNewTab = viewModel::openNewTab,
                onDismiss = { viewModel.setTabSwitcherVisible(false) },
                isPrivate = state.isPrivate,
            )
        }

        OverlayVisibility(visible = state.overlay == BrowserOverlay.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onDismiss = viewModel::closeOverlay,
            )
        }

        if (state.overlay == BrowserOverlay.MENU) {
            BrowserMenuSheet(
                state = state,
                actions = menuActions,
                onDismiss = viewModel::closeOverlay,
            )
        }
    }

    if (showTranslateNotice) {
        AlertDialog(
            onDismissRequest = { showTranslateNotice = false },
            title = { Text(stringResource(R.string.translate_notice_title)) },
            text = { Text(stringResource(R.string.translate_notice_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTranslateNotice = false
                        viewModel.acceptTranslateNotice()
                        translate()
                    },
                ) {
                    Text(stringResource(R.string.translate_notice_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTranslateNotice = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // Un solo gestore del tasto Indietro: l'ordine di chiusura resta prevedibile.
    BackHandler {
        when {
            state.overlay == BrowserOverlay.SETTINGS ->
                if (!settingsViewModel.navigateUp()) viewModel.closeOverlay()
            state.overlay == BrowserOverlay.LIBRARY -> closeLibrary()
            state.overlay != BrowserOverlay.NONE -> viewModel.closeOverlay()
            state.findInPage != null -> viewModel.closeFindInPage()
            state.isAddressFocused -> focusManager.clearFocus()
            host.canGoBack(tab.id) -> host.goBack(tab.id)
            else -> onExit()
        }
    }
}

/** Entrata/uscita morbida delle schermate a tutto schermo (libreria, schede, impostazioni). */
@Composable
private fun OverlayVisibility(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { height -> height / 16 },
        exit = fadeOut(tween(120)) + slideOutVertically(tween(150)) { height -> height / 24 },
    ) {
        content()
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
            StartPage(isPrivate = state.isPrivate)
        }

        tab.error?.let { error ->
            ErrorPage(
                error = error,
                onRetry = onRetry,
            )
        }
    }
}
