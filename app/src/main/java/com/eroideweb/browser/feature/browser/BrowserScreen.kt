package com.eroideweb.browser.feature.browser

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eroideweb.browser.core.settings.ToolbarPosition
import com.eroideweb.browser.feature.browser.web.BrowserWebView
import com.eroideweb.browser.feature.browser.web.WebViewController

/**
 * Schermata del browser.
 *
 * La barra occupa spazio reale (non galleggia sopra la pagina): prima copriva
 * pulsanti e sezioni dei siti, ed era inaccettabile. Il look "glass" resta,
 * ma il contenuto non finisce mai sotto la barra.
 */
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onExit: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val controller = remember { WebViewController() }
    val focusManager = LocalFocusManager.current
    val barAtTop = state.toolbarPosition == ToolbarPosition.TOP

    val bar: @Composable () -> Unit = {
        AddressBar(
            state = state,
            onTextChange = viewModel::onAddressBarTextChange,
            onFocusChange = viewModel::onAddressBarFocusChange,
            onSubmit = { text -> viewModel.submitAddressBar(text)?.let(controller::load) },
            onReload = controller::reload,
            onStop = controller::stopLoading,
            onToolbarPositionChange = viewModel::setToolbarPosition,
            onSearchEngineChange = viewModel::setSearchEngine,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (barAtTop) {
            bar()
            PageArea(
                state = state,
                viewModel = viewModel,
                controller = controller,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            PageArea(
                state = state,
                viewModel = viewModel,
                controller = controller,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            bar()
        }
    }

    BackHandler {
        when {
            state.isAddressFocused -> focusManager.clearFocus()
            controller.canGoBack -> controller.goBack()
            else -> onExit()
        }
    }
}

@Composable
private fun PageArea(
    state: BrowserUiState,
    viewModel: BrowserViewModel,
    controller: WebViewController,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        if (state.isStartPage) {
            StartPage()
        } else {
            BrowserWebView(
                controller = controller,
                initialUrl = state.currentUrl,
                onPageStarted = viewModel::onPageStarted,
                onPageFinished = viewModel::onPageFinished,
                onProgressChanged = viewModel::onProgressChanged,
                onNavigationStateChanged = viewModel::onNavigationStateChanged,
                onPageError = viewModel::onPageError,
                modifier = Modifier.fillMaxSize(),
            )

            state.error?.let { error ->
                ErrorPage(
                    error = error,
                    onRetry = {
                        viewModel.clearError()
                        controller.reload()
                    },
                )
            }
        }
    }
}
