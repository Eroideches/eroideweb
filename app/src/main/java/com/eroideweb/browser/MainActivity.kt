package com.eroideweb.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eroideweb.browser.feature.browser.BrowserScreen
import com.eroideweb.browser.feature.browser.BrowserViewModel
import com.eroideweb.browser.ui.theme.EroideWebTheme

/**
 * Unica activity dell'app. In Fase 1 ospita una sola scheda; il tab manager
 * arriverà in Fase 2.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as EroideWebApp).container

        setContent {
            EroideWebTheme {
                val browserViewModel: BrowserViewModel = viewModel(
                    factory = container.browserViewModelFactory,
                )
                BrowserScreen(
                    viewModel = browserViewModel,
                    onExit = { finish() },
                )
            }
        }
    }
}
