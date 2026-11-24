package com.eroideweb.browser.feature.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.eroideweb.browser.PrivateActivity
import com.eroideweb.browser.R
import com.eroideweb.browser.core.download.DownloadRepository
import com.eroideweb.browser.core.download.DataUrl
import com.eroideweb.browser.core.download.DownloadRequest
import com.eroideweb.browser.core.favicon.FaviconStore
import com.eroideweb.browser.core.url.urlHost
import com.eroideweb.browser.core.web.ContextAction
import com.eroideweb.browser.core.web.ContextTarget
import com.eroideweb.browser.core.web.SitePermission
import com.eroideweb.browser.core.web.SitePermissions
import com.eroideweb.browser.core.settings.BrowserSettings
import com.eroideweb.browser.core.settings.PrivateWindowSettings
import com.eroideweb.browser.core.settings.ToolbarPosition
import com.eroideweb.browser.core.translate.TranslateUrl
import com.eroideweb.browser.feature.browser.menu.BrowserMenuActions
import com.eroideweb.browser.feature.browser.menu.BrowserMenuSheet
import com.eroideweb.browser.feature.browser.web.RequestFilter
import com.eroideweb.browser.feature.browser.web.TabWebViews
import com.eroideweb.browser.feature.browser.web.WebDisplay
import com.eroideweb.browser.feature.library.LibraryScreen
import com.eroideweb.browser.feature.library.LibraryTab
import com.eroideweb.browser.feature.library.LibraryViewModel
import com.eroideweb.browser.feature.passwords.PasswordsScreen
import com.eroideweb.browser.feature.passwords.PasswordsViewModel
import com.eroideweb.browser.feature.reader.ReaderOverlay
import com.eroideweb.browser.feature.settings.SettingsPage
import com.eroideweb.browser.feature.settings.SettingsScreen
import com.eroideweb.browser.feature.settings.SettingsViewModel
import com.eroideweb.browser.ui.common.GlassCapsule
import com.eroideweb.browser.ui.common.LocalFaviconStore
import com.eroideweb.browser.ui.theme.LocalDarkTheme
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
    passwordsViewModel: PasswordsViewModel,
    downloadRepository: DownloadRepository,
    faviconStore: FaviconStore,
    settings: BrowserSettings,
    onExit: () -> Unit,
    onBackToCaller: () -> Unit = onExit,
) {
    CompositionLocalProvider(LocalFaviconStore provides faviconStore) {
        BrowserContent(
            viewModel = viewModel,
            libraryViewModel = libraryViewModel,
            settingsViewModel = settingsViewModel,
            passwordsViewModel = passwordsViewModel,
            downloadRepository = downloadRepository,
            faviconStore = faviconStore,
            settings = settings,
            onExit = onExit,
            onBackToCaller = onBackToCaller,
        )
    }
}

@Composable
private fun BrowserContent(
    viewModel: BrowserViewModel,
    libraryViewModel: LibraryViewModel,
    settingsViewModel: SettingsViewModel,
    passwordsViewModel: PasswordsViewModel,
    downloadRepository: DownloadRepository,
    faviconStore: FaviconStore,
    settings: BrowserSettings,
    onExit: () -> Unit,
    onBackToCaller: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestFilter = remember(viewModel) {
        RequestFilter { requestUrl, pageUrl -> viewModel.shouldBlockRequest(requestUrl, pageUrl) }
    }
    // Il tema delle pagine nasce con la WebView: se cambia, si ricreano le
    // WebView (gli stati salvati riportano ogni scheda dov'era).
    val appDark = LocalDarkTheme.current
    val webDisplay = WebDisplay(
        darkPages = appDark || settings.darkenWebPages,
        forceDark = settings.darkenWebPages,
    )
    val passwordsEnabled = !state.isPrivate
    val host = remember(webDisplay) {
        TabWebViews(context, requestFilter, webDisplay, passwordsEnabled = passwordsEnabled)
    }
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val downloadScope = rememberCoroutineScope()
    val tab = state.activeTab
    val barAtTop = state.toolbarPosition == ToolbarPosition.TOP
    var showTranslateNotice by remember { mutableStateOf(false) }

    // ---- stati delle richieste della pagina che passano dall'activity ----
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uris: Array<Uri>? = when {
            result.resultCode != Activity.RESULT_OK -> null
            data?.clipData != null -> data.clipData!!.let { clip -> Array(clip.itemCount) { clip.getItemAt(it).uri } }
            else -> WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
        }
        // Va sempre chiamato, anche se annullato: altrimenti il campo file non riapre più.
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    var fullscreen by remember { mutableStateOf<Pair<View, WebChromeClient.CustomViewCallback>?>(null) }
    var httpAuth by remember { mutableStateOf<Triple<String, String, HttpAuthHandler>?>(null) }

    /** Completamenti delle richieste dei siti in attesa della risposta dell'utente. */
    val pendingPermissions = remember { mutableMapOf<Long, (Boolean) -> Unit>() }
    var permissionToRequest by remember { mutableStateOf<Pair<Long, List<String>>?>(null) }
    val systemPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val request = permissionToRequest ?: return@rememberLauncherForActivityResult
        permissionToRequest = null
        val needed = request.second
        // Per la posizione basta quella approssimativa.
        val location = needed.filter { it.contains("LOCATION") }
        val others = needed - location.toSet()
        val granted = (location.isEmpty() || location.any { results[it] == true || isGranted(context, it) }) &&
            others.all { results[it] == true || isGranted(context, it) }
        if (!granted) Toast.makeText(context, R.string.permission_denied_by_system, Toast.LENGTH_SHORT).show()
        pendingPermissions.remove(request.first)?.invoke(granted)
        viewModel.answerPermissionPrompt(request.first, granted)
    }

    /** Chiede al sito-utente e, se serve, il permesso Android corrispondente. */
    fun askSitePermission(tabId: String, origin: String, permissions: Set<SitePermission>, complete: (Boolean) -> Unit) {
        val needed = SitePermissions.androidPermissionsFor(permissions)
        when (viewModel.requestSitePermission(tabId, origin, permissions)) {
            true -> {
                val missing = needed.filterNot { isGranted(context, it) }
                val locationOk = needed.none { it.contains("LOCATION") } ||
                    needed.filter { it.contains("LOCATION") }.any { isGranted(context, it) }
                if (missing.isEmpty() || (locationOk && missing.all { it.contains("LOCATION") })) complete(true) else complete(false)
            }
            false -> complete(false)
            null -> viewModel.state.value.permissionPrompt?.let { prompt -> pendingPermissions[prompt.id] = complete }
                ?: complete(false)
        }
    }

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
                val save: (DataUrl?) -> Unit = { content ->
                    if (content == null) {
                        Toast.makeText(context, R.string.download_not_started, Toast.LENGTH_SHORT).show()
                    } else {
                        downloadScope.launch {
                            val type = mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                                ?: content.mimeType
                            val name = downloadRepository.saveFile(
                                URLUtil.guessFileName(url, contentDisposition, type),
                                type,
                                content.bytes,
                            )
                            if (name != null) {
                                Toast.makeText(context, context.getString(R.string.download_saved, name), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, R.string.download_not_started, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                when {
                    // File creati dalla pagina: li legge la pagina stessa e ce li passa.
                    url.startsWith("blob:", ignoreCase = true) -> host.fetchBlob(tabId, url, save)
                    url.startsWith("data:", ignoreCase = true) -> save(DataUrl.parse(url))
                    else -> downloadScope.launch {
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

            override fun onReaderModeAvailability(tabId: String, url: String, available: Boolean) {
                viewModel.onReaderModeAvailability(tabId, url, available)
            }

            override fun onFavicon(tabId: String, url: String, icon: android.graphics.Bitmap) {
                urlHost(url)?.let { host -> faviconStore.put(host, icon) }
            }

            override fun onLoginFieldFocused(tabId: String, origin: String) {
                viewModel.onLoginFieldFocused(tabId, origin)
            }

            override fun onCredentialsSubmitted(tabId: String, origin: String, username: String, password: String) {
                viewModel.onCredentialsSubmitted(tabId, origin, username, password)
            }

            override fun onShowFileChooser(
                callback: ValueCallback<Array<Uri>>,
                params: WebChromeClient.FileChooserParams,
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val intent = params.createIntent().apply {
                    if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                }
                return try {
                    fileLauncher.launch(intent)
                    true
                } catch (_: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }

            override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                fullscreen?.second?.onCustomViewHidden()
                fullscreen = view to callback
            }

            override fun onHideCustomView() {
                fullscreen = null
            }

            override fun onContextMenu(tabId: String, target: ContextTarget) {
                if (tabId == viewModel.state.value.activeTabId) viewModel.showContextMenu(target)
            }

            override fun onGeolocationRequest(tabId: String, origin: String, callback: GeolocationPermissions.Callback) {
                askSitePermission(tabId, origin, setOf(SitePermission.LOCATION)) { granted ->
                    callback.invoke(origin, granted, false)
                }
            }

            override fun onGeolocationRequestCanceled(tabId: String) {
                viewModel.cancelPermissionPrompt(tabId)
            }

            override fun onPermissionRequest(tabId: String, request: PermissionRequest) {
                val permissions = SitePermissions.fromResources(request.resources.toList())
                if (permissions.isEmpty()) {
                    request.deny()
                    return
                }
                askSitePermission(tabId, request.origin.toString(), permissions) { granted ->
                    if (granted) {
                        request.grant(SitePermissions.resourcesFor(permissions).toTypedArray())
                    } else {
                        request.deny()
                    }
                }
            }

            override fun onPermissionRequestCanceled(tabId: String, request: PermissionRequest) {
                viewModel.cancelPermissionPrompt(tabId)
            }

            override fun onHttpAuthRequest(tabId: String, host: String, realm: String, handler: HttpAuthHandler) {
                httpAuth?.third?.cancel()
                httpAuth = Triple(host, realm, handler)
            }

            override fun onRendererGone(tabId: String, url: String?, repeated: Boolean) {
                if (viewModel.onRendererGone(tabId, repeated) && url != null) {
                    host.load(tabId, url)
                }
            }
        }
        onDispose {
            // Salva lo stato prima di distruggere: la rotazione non ricarica nulla.
            host.destroy()
            host.callbacks = null
        }
    }

    // Cambio scheda o primo caricamento della scheda attiva.
    LaunchedEffect(host, tab.id, tab.url) {
        host.show(tab.id, tab.url)
    }

    // Chiude le WebView delle schede eliminate dal modello.
    LaunchedEffect(host, state.tabs.map { it.id }) {
        host.retainOnly(state.tabs.map { it.id }.toSet())
    }

    // Dimensione del testo: si applica subito a tutte le schede, senza ricaricare.
    LaunchedEffect(host, settings.textZoom) {
        host.textZoom = settings.textZoom
    }

    LaunchedEffect(host, settings.allowThirdPartyCookies) {
        host.allowThirdPartyCookies = settings.allowThirdPartyCookies
    }

    // Fine di "Elimina dati di navigazione": avviso e, se chiesto, schede chiuse.
    val clearResult = settingsState.clearResult
    LaunchedEffect(clearResult?.id) {
        val result = clearResult ?: return@LaunchedEffect
        if (result.request.openTabs) viewModel.resetTabs()
        if (result.request.sitePermissions) viewModel.clearSitePermissions()
        Toast.makeText(context, R.string.clear_data_done, Toast.LENGTH_SHORT).show()
        settingsViewModel.onClearResultHandled(result.id)
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

    DisposableEffect(lifecycleOwner, host) {
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

    val onContextAction: (ContextTarget, ContextAction) -> Unit = { target, action ->
        val link = target.linkUrl
        val image = target.imageUrl
        when (action) {
            ContextAction.OPEN_LINK_NEW_TAB -> link?.let { viewModel.openInNewTab(it) }
            ContextAction.OPEN_LINK_BACKGROUND -> link?.let {
                viewModel.openInNewTab(it, background = true)
                Toast.makeText(context, R.string.context_opened_background, Toast.LENGTH_SHORT).show()
            }
            ContextAction.OPEN_LINK_PRIVATE -> link?.let { openPrivateWindow(context, settings, it) }
            ContextAction.COPY_LINK -> link?.let { copyText(context, it) }
            ContextAction.SHARE_LINK -> link?.let { BrowserActions.share(context, it, target.title) }
            ContextAction.DOWNLOAD_LINK -> link?.let { url ->
                downloadScope.launch {
                    val started = downloadRepository.enqueue(DownloadRequest(url, null, null, null, -1))
                    Toast.makeText(
                        context,
                        if (started) R.string.download_started else R.string.download_not_started,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            ContextAction.OPEN_IMAGE_NEW_TAB -> image?.let { viewModel.openInNewTab(it) }
            ContextAction.DOWNLOAD_IMAGE -> image?.let { url ->
                downloadScope.launch {
                    if (url.startsWith("data:", ignoreCase = true)) {
                        val content = DataUrl.parse(url)
                        val name = content?.let { downloadRepository.saveFile("immagine", it.mimeType, it.bytes) }
                        Toast.makeText(
                            context,
                            if (name != null) context.getString(R.string.download_saved, name) else context.getString(R.string.download_not_started),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        val started = downloadRepository.enqueue(DownloadRequest(url, null, null, null, -1))
                        Toast.makeText(
                            context,
                            if (started) R.string.download_started else R.string.download_not_started,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
            ContextAction.COPY_IMAGE_ADDRESS -> image?.let { copyText(context, it) }
            ContextAction.SHARE_IMAGE -> image?.let { BrowserActions.share(context, it, target.title) }
        }
        viewModel.dismissContextMenu()
    }

    val openReader: () -> Unit = {
        host.extractArticle(tab.id) { article ->
            if (article != null) {
                viewModel.openReader(article)
            } else {
                Toast.makeText(context, R.string.reader_unavailable, Toast.LENGTH_SHORT).show()
            }
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
        onToggleFavorite = viewModel::toggleFavorite,
        onToggleAdBlockForSite = viewModel::toggleAdBlockForSite,
        onOpenReader = {
            viewModel.closeOverlay()
            openReader()
        },
        onOpenHistory = { openLibrary(LibraryTab.HISTORY) },
        onOpenBookmarks = { openLibrary(LibraryTab.BOOKMARKS) },
        onOpenDownloads = { openLibrary(LibraryTab.DOWNLOADS) },
        onOpenPrivateWindow = {
            viewModel.closeOverlay()
            openPrivateWindow(context, settings, url = null)
        },
        onPrint = {
            viewModel.closeOverlay()
            val printed = host.print(tab.id, tab.title?.takeIf { it.isNotBlank() } ?: urlHost(tab.url) ?: "EroideWeb")
            if (!printed) Toast.makeText(context, R.string.print_unavailable, Toast.LENGTH_SHORT).show()
        },
        onOpenClearData = {
            settingsViewModel.onOpen(SettingsPage.CLEAR_DATA)
            viewModel.openSettings()
        },
        onOpenPasswords = {
            passwordsViewModel.onOpen()
            viewModel.openPasswords()
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

    val openInActiveTab: (String) -> Unit = { input ->
        focusManager.clearFocus()
        viewModel.submitAddressBar(input)?.let { url -> host.load(tab.id, url) }
    }

    val startPageActions = remember(viewModel, host) {
        StartPageActions(
            onOpen = { url -> viewModel.submitAddressBar(url)?.let { host.load(viewModel.state.value.activeTabId, it) } },
            onAdd = viewModel::addFavorite,
            onEdit = viewModel::updateFavorite,
            onRemove = viewModel::removeFavorite,
            onMove = viewModel::moveFavorite,
        )
    }

    // "Scheda chiusa · Annulla": un avviso per ogni chiusura, anche dal selettore.
    val snackbarHostState = remember { SnackbarHostState() }
    val undo = state.undoClose
    val closedOne = stringResource(R.string.tab_closed)
    val undoLabel = stringResource(R.string.action_undo)
    val closedMany = undo?.let { stringResource(R.string.tabs_closed, it.count) }
    LaunchedEffect(undo?.id) {
        val current = undo ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = if (current.count == 1) closedOne else closedMany.orEmpty(),
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoCloseTab()
        } else {
            viewModel.dismissUndo(current.id)
        }
    }

    // "Accedi come …": le credenziali vanno solo al frame che ha chiesto, se l'origine coincide.
    val fillScope = rememberCoroutineScope()
    val onFillLogin: (com.eroideweb.browser.core.password.SavedLogin) -> Unit = { login ->
        val offer = state.fillOffer
        if (offer != null) {
            fillScope.launch {
                viewModel.loginToFill(login.id)?.let { (username, password) ->
                    host.fillCredential(offer.tabId, offer.origin, username, password)
                }
            }
        }
    }

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
                onOpenReader = openReader,
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
                    startPageActions = startPageActions,
                    onOpenSuggestion = openInActiveTab,
                    suggestionsAtBottom = false,
                    onFillLogin = onFillLogin,
                    onDismissFill = viewModel::dismissFillOffer,
                    onSavePassword = viewModel::acceptSavePrompt,
                    onNeverSavePassword = viewModel::neverSaveForSite,
                    onDismissSavePassword = viewModel::dismissSavePrompt,
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
                    startPageActions = startPageActions,
                    onOpenSuggestion = openInActiveTab,
                    suggestionsAtBottom = true,
                    onFillLogin = onFillLogin,
                    onDismissFill = viewModel::dismissFillOffer,
                    onSavePassword = viewModel::acceptSavePrompt,
                    onNeverSavePassword = viewModel::neverSaveForSite,
                    onDismissSavePassword = viewModel::dismissSavePrompt,
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
                onCloseAll = viewModel::closeAllTabs,
                onDuplicate = viewModel::duplicateTab,
                onNewTab = viewModel::openNewTab,
                onDismiss = { viewModel.setTabSwitcherVisible(false) },
                isPrivate = state.isPrivate,
            )
        }

        OverlayVisibility(visible = state.overlay == BrowserOverlay.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onDismiss = viewModel::closeOverlay,
                onOpenPasswords = if (state.isPrivate) {
                    null
                } else {
                    {
                        passwordsViewModel.onOpen()
                        viewModel.openPasswords()
                    }
                },
            )
        }

        OverlayVisibility(visible = state.overlay == BrowserOverlay.PASSWORDS) {
            PasswordsScreen(
                viewModel = passwordsViewModel,
                onDismiss = viewModel::closeOverlay,
            )
        }

        val article = state.readerArticle
        OverlayVisibility(visible = state.isReaderVisible) {
            if (article != null) {
                ReaderOverlay(
                    article = article,
                    theme = settings.readerTheme,
                    serif = settings.readerSerif,
                    textZoom = settings.readerTextZoom,
                    onClose = viewModel::closeReader,
                    onOpenLink = { url ->
                        viewModel.closeReader()
                        viewModel.submitAddressBar(url)?.let { loadable -> host.load(tab.id, loadable) }
                    },
                    onTextZoomChange = viewModel::setReaderTextZoom,
                    onThemeChange = viewModel::setReaderTheme,
                    onSerifChange = viewModel::setReaderSerif,
                )
            }
        }

        if (state.overlay == BrowserOverlay.MENU) {
            BrowserMenuSheet(
                state = state,
                actions = menuActions,
                onDismiss = viewModel::closeOverlay,
            )
        }

        state.contextMenu?.let { target ->
            ContextMenuSheet(
                target = target,
                isPrivate = state.isPrivate,
                onAction = { action -> onContextAction(target, action) },
                onDismiss = viewModel::dismissContextMenu,
            )
        }

        // Video a schermo intero: sopra tutto, barre di sistema nascoste, schermo acceso.
        fullscreen?.let { (view, _) -> FullscreenView(view = view) }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 112.dp),
        )
    }

    // Richieste ritirate dal sito (pagina chiusa): nessun completamento in sospeso.
    LaunchedEffect(state.permissionPrompt) {
        if (state.permissionPrompt == null && permissionToRequest == null) pendingPermissions.clear()
    }

    // Nascosto mentre è aperta la richiesta di sistema, per non rispondere due volte.
    state.permissionPrompt?.takeIf { permissionToRequest?.first != it.id }?.let { prompt ->
        SitePermissionDialog(prompt = prompt) { allow ->
            if (!allow) {
                pendingPermissions.remove(prompt.id)?.invoke(false)
                viewModel.answerPermissionPrompt(prompt.id, false)
                return@SitePermissionDialog
            }
            val missing = SitePermissions.androidPermissionsFor(prompt.permissions).filterNot { isGranted(context, it) }
            val locationOnly = missing.isNotEmpty() && missing.all { it.contains("LOCATION") }
            val hasSomeLocation = SitePermissions.androidPermissionsFor(prompt.permissions)
                .filter { it.contains("LOCATION") }
                .any { isGranted(context, it) }
            if (missing.isEmpty() || (locationOnly && hasSomeLocation)) {
                pendingPermissions.remove(prompt.id)?.invoke(true)
                viewModel.answerPermissionPrompt(prompt.id, true)
            } else {
                permissionToRequest = prompt.id to SitePermissions.androidPermissionsFor(prompt.permissions)
                systemPermissionLauncher.launch(missing.toTypedArray())
            }
        }
    }

    httpAuth?.let { (authHost, realm, handler) ->
        HttpAuthDialog(
            host = authHost,
            realm = realm,
            onLogin = { username, password ->
                httpAuth = null
                handler.proceed(username, password)
            },
            onCancel = {
                httpAuth = null
                handler.cancel()
            },
        )
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
        val video = fullscreen
        when {
            video != null -> {
                video.second.onCustomViewHidden()
                fullscreen = null
            }
            state.overlay == BrowserOverlay.SETTINGS ->
                if (!settingsViewModel.navigateUp()) viewModel.closeOverlay()
            state.overlay == BrowserOverlay.LIBRARY -> closeLibrary()
            state.overlay == BrowserOverlay.READER -> viewModel.closeReader()
            state.overlay == BrowserOverlay.PASSWORDS ->
                if (!passwordsViewModel.navigateUp()) viewModel.closeOverlay()
            state.overlay != BrowserOverlay.NONE -> viewModel.closeOverlay()
            state.findInPage != null -> viewModel.closeFindInPage()
            state.isAddressFocused -> focusManager.clearFocus()
            host.canGoBack(tab.id) -> host.goBack(tab.id)
            // Aperta da un'altra app: si chiude e si torna là, come fa Chrome.
            tab.openedByApp -> {
                viewModel.closeTab(tab.id, offerUndo = false)
                onBackToCaller()
            }
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
    startPageActions: StartPageActions,
    onOpenSuggestion: (String) -> Unit,
    suggestionsAtBottom: Boolean,
    onFillLogin: (com.eroideweb.browser.core.password.SavedLogin) -> Unit,
    onDismissFill: () -> Unit,
    onSavePassword: () -> Unit,
    onNeverSavePassword: () -> Unit,
    onDismissSavePassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tab = state.activeTab
    Box(modifier) {
        // Il contenitore resta sempre in composizione: cambiando scheda le
        // WebView vive vengono solo agganciate e sganciate, mai ricreate.
        key(host) {
            AndroidView(
                factory = { host.container },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (tab.url == null) {
            StartPage(
                isPrivate = state.isPrivate,
                favorites = state.favorites,
                actions = startPageActions,
            )
        }

        tab.error?.let { error ->
            ErrorPage(
                error = error,
                onRetry = onRetry,
            )
        }

        // Password: proposta di salvataggio e "Accedi come …", in basso sopra la tastiera.
        val savePrompt = state.savePrompt
        val fillOffer = state.fillOffer
        if (savePrompt != null && savePrompt.tabId == tab.id) {
            SavePasswordCard(
                prompt = savePrompt,
                onSave = onSavePassword,
                onNever = onNeverSavePassword,
                onDismiss = onDismissSavePassword,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .fillMaxWidth(),
            )
        } else if (fillOffer != null && fillOffer.tabId == tab.id && !state.isAddressFocused) {
            FillOfferBar(
                offer = fillOffer,
                onFill = onFillLogin,
                onDismiss = onDismissFill,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .fillMaxWidth(),
            )
        }

        // Suggerimenti sopra la pagina mentre si scrive (vicino alla barra).
        val query = state.addressText.trim()
        if (state.isAddressFocused && query.isNotEmpty()) {
            AddressSuggestions(
                query = query,
                suggestions = state.suggestions,
                searchEngine = state.searchEngine,
                reversed = suggestionsAtBottom,
                onOpen = onOpenSuggestion,
                onSearch = onOpenSuggestion,
                modifier = Modifier
                    .align(if (suggestionsAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            )
        }
    }
}

/** Video a schermo intero della pagina: barre di sistema nascoste finché è aperto. */
@Composable
private fun FullscreenView(view: View) {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    key(view) {
        AndroidView(
            factory = { context ->
                FrameLayout(context).apply {
                    setBackgroundColor(AndroidColor.BLACK)
                    keepScreenOn = true
                    (view.parent as? ViewGroup)?.removeView(view)
                    addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            },
            onRelease = { frame -> frame.removeAllViews() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Finestra privata (altro processo) con le stesse impostazioni ed eventualmente un link da aprire. */
private fun openPrivateWindow(context: android.content.Context, settings: BrowserSettings, url: String?) {
    val intent = Intent(context, PrivateActivity::class.java)
    PrivateWindowSettings.toExtras(settings).forEach { (key, value) -> intent.putExtra(key, value) }
    if (url != null) intent.putExtra(PrivateActivity.EXTRA_OPEN_URL, url)
    context.startActivity(intent)
}

private fun copyText(context: android.content.Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("link", text))
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.context_copied, Toast.LENGTH_SHORT).show()
    }
}

private fun isGranted(context: android.content.Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
