package com.eroideweb.browser.feature.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.eroideweb.browser.R

/**
 * Riga dell'indirizzo, dentro la capsula "liquid glass" della barra:
 * icona di stato, campo di testo, ricarica/stop e avanzamento del caricamento.
 * Le altre azioni stanno nella riga sotto ([NavigationRow]) e nel pannello ☰.
 */
@Composable
fun AddressBar(
    state: BrowserUiState,
    onTextChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(state.addressText)) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val tab = state.activeTab

    // Quando la pagina cambia e la barra non è in editing, mostra l'URL reale.
    LaunchedEffect(state.addressText, isFocused) {
        if (!isFocused && fieldValue.text != state.addressText) {
            fieldValue = TextFieldValue(state.addressText, selection = TextRange(state.addressText.length))
        }
    }

    val submit = {
        // Il testo va catturato prima di perdere il focus: altrimenti la barra
        // ripristina l'URL della pagina e la ricerca digitata sparisce.
        val text = fieldValue.text
        focusManager.clearFocus()
        keyboard?.hide()
        onSubmit(text)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 14.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusIcon(tab = tab, isStartPage = state.isStartPage)

            BasicTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    fieldValue = newValue
                    onTextChange(newValue.text)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (fieldValue.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.address_bar_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .onFocusChanged { focusState ->
                        val nowFocused = focusState.isFocused
                        if (nowFocused != isFocused) {
                            isFocused = nowFocused
                            if (nowFocused) {
                                fieldValue = fieldValue.copy(
                                    selection = TextRange(0, fieldValue.text.length),
                                )
                            }
                            onFocusChange(nowFocused)
                        }
                    },
            )

            if (isFocused) {
                TextButton(
                    onClick = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    },
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            } else if (!state.isStartPage) {
                IconButton(onClick = { if (tab.isLoading) onStop() else onReload() }) {
                    Icon(
                        imageVector = if (tab.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                        contentDescription = stringResource(
                            if (tab.isLoading) R.string.action_stop else R.string.action_reload,
                        ),
                    )
                }
            }
        }

        // Spazio riservato: l'altezza della barra non cambia durante il caricamento.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
        ) {
            if (tab.isLoading && tab.progress in 1..99) {
                LinearProgressIndicator(
                    progress = { tab.progress / 100f },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            }
        }
    }
}

@Composable
private fun StatusIcon(tab: TabUi, isStartPage: Boolean) {
    val colors = MaterialTheme.colorScheme
    val (icon: ImageVector, tint: Color, description: String) = when {
        isStartPage -> Triple(
            Icons.Filled.Search,
            colors.onSurfaceVariant,
            stringResource(R.string.cd_search),
        )
        tab.isSecure -> Triple(
            Icons.Filled.Lock,
            colors.primary,
            stringResource(R.string.cd_secure),
        )
        else -> Triple(
            Icons.Filled.Warning,
            colors.error,
            stringResource(R.string.cd_insecure),
        )
    }
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.size(18.dp),
    )
}
