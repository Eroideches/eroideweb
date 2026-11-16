package com.eroideweb.browser.feature.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eroideweb.browser.core.settings.ToolbarPosition
import com.eroideweb.browser.feature.browser.web.BrowserWebView
import com.eroideweb.browser.feature.browser.web.WebViewController

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onExit: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val controller = remember { WebViewController() }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Il contenuto sta tra le barre di sistema: niente testo sotto
            // la status bar né sotto la gesture bar.
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (state.isStartPage) {
            StartPage()
        } else {
            BrowserWebView(
                controller = controller,
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

        AddressBar(
            state = state,
            onTextChange = viewModel::onAddressBarTextChange,
            onFocusChange = viewModel::onAddressBarFocusChange,
            onSubmit = {
                viewModel.submitAddressBar()?.let(controller::load)
            },
            onReload = controller::reload,
            onStop = controller::stopLoading,
            onToolbarPositionChange = viewModel::setToolbarPosition,
            onSearchEngineChange = viewModel::setSearchEngine,
            modifier = Modifier
                .align(
                    if (state.toolbarPosition == ToolbarPosition.TOP) {
                        Alignment.TopCenter
                    } else {
                        Alignment.BottomCenter
                    },
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }

    BackHandler {
        when {
            state.isAddressFocused -> focusManager.clearFocus()
            controller.canGoBack -> controller.goBack()
            else -> onExit()
        }
    }
}
