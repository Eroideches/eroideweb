package com.eroideweb.browser.feature.browser

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.eroideweb.browser.R
import com.eroideweb.browser.core.settings.SearchEngine
import com.eroideweb.browser.core.settings.ToolbarPosition
import com.eroideweb.browser.ui.common.GlassCapsule

/**
 * Barra degli indirizzi "liquid glass".
 * Contiene anche il menu rapido con posizione della barra e motore di ricerca:
 * è provvisorio, la schermata Impostazioni arriva in Fase 8.
 */
@Composable
fun AddressBar(
    state: BrowserUiState,
    onTextChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onToolbarPositionChange: (ToolbarPosition) -> Unit,
    onSearchEngineChange: (SearchEngine) -> Unit,
    tabCount: Int,
    onShowTabs: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleAdBlock: () -> Unit,
    onToggleAdBlockForSite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(state.addressText)) }
    var menuExpanded by remember { mutableStateOf(false) }
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

    GlassCapsule(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
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
            } else {
                IconButton(onClick = { if (tab.isLoading) onStop() else onReload() }) {
                    Icon(
                        imageVector = if (tab.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                        contentDescription = stringResource(
                            if (tab.isLoading) R.string.action_stop else R.string.action_reload,
                        ),
                    )
                }
                TabCounter(count = tabCount, onClick = onShowTabs)
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.action_menu),
                        )
                    }
                    AddressBarMenu(
                        expanded = menuExpanded,
                        toolbarPosition = state.toolbarPosition,
                        searchEngine = state.searchEngine,
                        canBookmark = state.activeTab.url != null,
                        isBookmarked = state.isActiveBookmarked,
                        adBlockEnabled = state.adBlockEnabled,
                        canToggleAdBlockSite = state.canToggleAdBlockForSite,
                        adBlockActiveForSite = state.isAdBlockActiveForSite,
                        onDismiss = { menuExpanded = false },
                        onToolbarPositionChange = onToolbarPositionChange,
                        onSearchEngineChange = onSearchEngineChange,
                        onOpenHistory = onOpenHistory,
                        onOpenBookmarks = onOpenBookmarks,
                        onToggleBookmark = onToggleBookmark,
                        onToggleAdBlock = onToggleAdBlock,
                        onToggleAdBlockForSite = onToggleAdBlockForSite,
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

/**
 * Contatore schede: quadratino con il numero, come nei browser desktop.
 * Toccandolo si apre il selettore a card.
 */
@Composable
private fun TabCounter(count: Int, onClick: () -> Unit) {
    val description = stringResource(R.string.cd_tab_count, count)
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(1.6.dp, LocalContentColor.current, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (count > 9) "9+" else count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = LocalContentColor.current,
            )
        }
    }
}

@Composable
private fun StatusIcon(tab: TabUi, isStartPage: Boolean) {
    val dark = MaterialTheme.colorScheme
    val (icon: ImageVector, tint: Color, description: String) = when {
        isStartPage -> Triple(
            Icons.Filled.Search,
            dark.onSurfaceVariant,
            stringResource(R.string.cd_search),
        )
        tab.isSecure -> Triple(
            Icons.Filled.Lock,
            dark.primary,
            stringResource(R.string.cd_secure),
        )
        else -> Triple(
            Icons.Filled.Warning,
            dark.error,
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

@Composable
private fun AddressBarMenu(
    expanded: Boolean,
    toolbarPosition: ToolbarPosition,
    searchEngine: SearchEngine,
    canBookmark: Boolean,
    isBookmarked: Boolean,
    adBlockEnabled: Boolean,
    canToggleAdBlockSite: Boolean,
    adBlockActiveForSite: Boolean,
    onDismiss: () -> Unit,
    onToolbarPositionChange: (ToolbarPosition) -> Unit,
    onSearchEngineChange: (SearchEngine) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleAdBlock: () -> Unit,
    onToggleAdBlockForSite: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (canBookmark) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (isBookmarked) R.string.menu_bookmark_remove else R.string.menu_bookmark_add,
                        ),
                    )
                },
                onClick = {
                    onToggleBookmark()
                    onDismiss()
                },
                trailingIcon = {
                    if (isBookmarked) Icon(Icons.Filled.Check, contentDescription = null)
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_history)) },
            onClick = {
                onOpenHistory()
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_bookmarks)) },
            onClick = {
                onOpenBookmarks()
                onDismiss()
            },
        )

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_adblock)) },
            onClick = {
                onToggleAdBlock()
                onDismiss()
            },
            trailingIcon = {
                if (adBlockEnabled) Icon(Icons.Filled.Check, contentDescription = null)
            },
        )
        if (canToggleAdBlockSite && adBlockEnabled) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_adblock_site)) },
                onClick = {
                    onToggleAdBlockForSite()
                    onDismiss()
                },
                trailingIcon = {
                    if (adBlockActiveForSite) Icon(Icons.Filled.Check, contentDescription = null)
                },
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        MenuLabel(stringResource(R.string.menu_toolbar_position))
        ToolbarPosition.entries.forEach { position ->
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            when (position) {
                                ToolbarPosition.TOP -> R.string.toolbar_top
                                ToolbarPosition.BOTTOM -> R.string.toolbar_bottom
                            },
                        ),
                    )
                },
                onClick = {
                    onToolbarPositionChange(position)
                    onDismiss()
                },
                trailingIcon = {
                    if (position == toolbarPosition) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                },
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        MenuLabel(stringResource(R.string.menu_search_engine))
        SearchEngine.entries.forEach { engine ->
            DropdownMenuItem(
                text = { Text(engine.displayName) },
                onClick = {
                    onSearchEngineChange(engine)
                    onDismiss()
                },
                trailingIcon = {
                    if (engine == searchEngine) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                },
            )
        }
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}
