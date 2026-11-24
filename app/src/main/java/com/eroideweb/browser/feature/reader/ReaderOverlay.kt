package com.eroideweb.browser.feature.reader

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.eroideweb.browser.R
import com.eroideweb.browser.core.reader.ReaderArticle
import com.eroideweb.browser.core.reader.ReaderDocument
import com.eroideweb.browser.core.reader.ReaderPalette
import com.eroideweb.browser.core.settings.ReaderTheme
import com.eroideweb.browser.ui.theme.LocalDarkTheme

/** Passi della dimensione del testo in lettura. */
private const val READER_ZOOM_STEP = 10
private const val READER_ZOOM_MIN = 70
private const val READER_ZOOM_MAX = 200

/**
 * Modalità lettura: il testo dell'articolo, senza pubblicità né menu, in una
 * WebView dedicata con JavaScript spento. I link toccati tornano al browser.
 */
@Composable
fun ReaderOverlay(
    article: ReaderArticle,
    theme: ReaderTheme,
    serif: Boolean,
    textZoom: Int,
    onClose: () -> Unit,
    onOpenLink: (String) -> Unit,
    onTextZoomChange: (Int) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onSerifChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolved = if (theme == ReaderTheme.AUTO) {
        if (LocalDarkTheme.current) ReaderTheme.DARK else ReaderTheme.LIGHT
    } else {
        theme
    }
    val palette = ReaderPalette.of(resolved)
    val background = Color(android.graphics.Color.parseColor(palette.background))
    val content = Color(android.graphics.Color.parseColor(palette.text))
    val html = remember(article, resolved, serif) { ReaderDocument.build(article, resolved, serif) }
    val openLink by rememberUpdatedState(onOpenLink)

    Surface(modifier = modifier.fillMaxSize(), color = background, contentColor = content) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            ReaderToolbar(
                title = article.title,
                theme = theme,
                serif = serif,
                textZoom = textZoom,
                onClose = onClose,
                onTextZoomChange = onTextZoomChange,
                onThemeChange = onThemeChange,
                onSerifChange = onSerifChange,
            )
            HorizontalDivider(color = content.copy(alpha = 0.12f))
            AndroidView(
                factory = { context -> createReaderWebView(context) { url -> openLink(url) } },
                update = { view ->
                    view.settings.textZoom = textZoom
                    // Ricarica solo se cambia il documento (tema o carattere), non lo zoom.
                    if (view.tag != html) {
                        view.tag = html
                        view.setBackgroundColor(android.graphics.Color.parseColor(palette.background))
                        view.loadDataWithBaseURL(article.url, html, "text/html", "utf-8", null)
                    }
                },
                onRelease = { view -> view.destroy() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createReaderWebView(
    context: android.content.Context,
    onLink: (String) -> Unit,
): WebView = WebView(context).apply {
    settings.apply {
        javaScriptEnabled = false
        domStorageEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            onLink(request.url.toString())
            return true
        }
    }
}

@Composable
private fun ReaderToolbar(
    title: String,
    theme: ReaderTheme,
    serif: Boolean,
    textZoom: Int,
    onClose: () -> Unit,
    onTextZoomChange: (Int) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onSerifChange: (Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.reader_close))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val smaller = stringResource(R.string.reader_text_smaller_cd)
        val larger = stringResource(R.string.reader_text_larger_cd)
        val style = stringResource(R.string.reader_style_cd)
        TextButton(
            onClick = { onTextZoomChange((textZoom - READER_ZOOM_STEP).coerceAtLeast(READER_ZOOM_MIN)) },
            enabled = textZoom > READER_ZOOM_MIN,
            modifier = Modifier.semantics { contentDescription = smaller },
        ) {
            Text(stringResource(R.string.reader_text_smaller), fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = { onTextZoomChange((textZoom + READER_ZOOM_STEP).coerceAtMost(READER_ZOOM_MAX)) },
            enabled = textZoom < READER_ZOOM_MAX,
            modifier = Modifier.semantics { contentDescription = larger },
        ) {
            Text(stringResource(R.string.reader_text_larger), fontWeight = FontWeight.Bold)
        }
        Box {
            TextButton(
                onClick = { menuOpen = true },
                modifier = Modifier.semantics { contentDescription = style },
            ) {
                Text(stringResource(R.string.reader_style), fontWeight = FontWeight.Bold)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                ReaderTheme.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(readerThemeLabel(option))) },
                        leadingIcon = { RadioButton(selected = option == theme, onClick = null) },
                        onClick = { onThemeChange(option) },
                    )
                }
                HorizontalDivider()
                listOf(true, false).forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (option) R.string.reader_font_serif else R.string.reader_font_sans,
                                ),
                            )
                        },
                        leadingIcon = { RadioButton(selected = option == serif, onClick = null) },
                        onClick = { onSerifChange(option) },
                    )
                }
            }
        }
    }
}

fun readerThemeLabel(theme: ReaderTheme): Int = when (theme) {
    ReaderTheme.AUTO -> R.string.reader_theme_auto
    ReaderTheme.LIGHT -> R.string.reader_theme_light
    ReaderTheme.SEPIA -> R.string.reader_theme_sepia
    ReaderTheme.DARK -> R.string.reader_theme_dark
}
