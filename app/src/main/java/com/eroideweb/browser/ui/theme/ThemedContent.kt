package com.eroideweb.browser.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eroideweb.browser.core.settings.BrowserSettings
import kotlinx.coroutines.flow.Flow

/**
 * Applica il tema scelto nelle Impostazioni (Sistema/Chiaro/Scuro) e allinea
 * le icone delle barre di sistema.
 *
 * Il contenuto parte solo quando le impostazioni sono lette (pochi
 * millisecondi): così non si vede mai un lampo del tema sbagliato.
 */
@Composable
fun ThemedContent(
    activity: ComponentActivity,
    settings: Flow<BrowserSettings>,
    content: @Composable (BrowserSettings) -> Unit,
) {
    val current by settings.collectAsStateWithLifecycle<BrowserSettings?>(initialValue = null)
    val loaded = current ?: return
    val dark = loaded.appTheme.isDark(isSystemInDarkTheme())

    DisposableEffect(dark) {
        val transparent = Color.TRANSPARENT
        val style = if (dark) {
            SystemBarStyle.dark(transparent)
        } else {
            SystemBarStyle.light(transparent, transparent)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    EroideWebTheme(darkTheme = dark) {
        content(loaded)
    }
}
