package com.eroideweb.browser.feature.browser.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eroideweb.browser.R
import com.eroideweb.browser.core.url.urlHost
import com.eroideweb.browser.feature.browser.BrowserUiState
import com.eroideweb.browser.ui.icons.EroideIcons

/** Azioni del pannello ☰. Ogni voce chiude il pannello da sola. */
class BrowserMenuActions(
    val onShare: () -> Unit,
    val onFind: () -> Unit,
    val onTranslate: () -> Unit,
    val onToggleDesktop: () -> Unit,
    val onToggleBookmark: () -> Unit,
    val onToggleAdBlockForSite: () -> Unit,
    val onOpenReader: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onOpenBookmarks: () -> Unit,
    val onOpenDownloads: () -> Unit,
    val onOpenPrivateWindow: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onClosePrivateWindow: () -> Unit,
)

/**
 * Pannello dal basso che sostituisce il vecchio menu ⋮: in alto le azioni
 * sulla pagina, sotto la libreria e le Impostazioni (dove ora vivono le
 * preferenze che stavano nel menu).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenuSheet(
    state: BrowserUiState,
    actions: BrowserMenuActions,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tab = state.activeTab
    val hasPage = state.hasWebPage

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
        ) {
            if (hasPage) {
                PageHeader(title = tab.title, url = tab.url.orEmpty(), isPrivate = state.isPrivate)
            } else if (state.isPrivate) {
                PageHeader(title = stringResource(R.string.private_title), url = "", isPrivate = true)
            }

            val quickActions = buildList {
                add(
                    QuickActionSpec(EroideIcons.Share, R.string.menu_share, enabled = hasPage, onClick = actions.onShare),
                )
                add(
                    QuickActionSpec(EroideIcons.FindInPage, R.string.menu_find, enabled = hasPage, onClick = actions.onFind),
                )
                add(
                    QuickActionSpec(
                        icon = EroideIcons.Translate,
                        label = if (state.isActiveTranslated) R.string.menu_translate_original else R.string.menu_translate,
                        enabled = state.canTranslate || state.isActiveTranslated,
                        selected = state.isActiveTranslated,
                        onClick = actions.onTranslate,
                    ),
                )
                add(
                    QuickActionSpec(
                        icon = EroideIcons.Desktop,
                        label = R.string.menu_desktop,
                        enabled = tab.url != null,
                        selected = tab.desktopMode,
                        toggle = true,
                        onClick = actions.onToggleDesktop,
                    ),
                )
                add(
                    QuickActionSpec(
                        icon = EroideIcons.Reader,
                        label = R.string.menu_reader,
                        enabled = tab.readerAvailable,
                        onClick = actions.onOpenReader,
                    ),
                )
                if (!state.isPrivate) {
                    add(
                        QuickActionSpec(
                            icon = if (state.isActiveBookmarked) EroideIcons.BookmarkFilled else EroideIcons.Bookmark,
                            label = R.string.menu_bookmark,
                            enabled = tab.url != null,
                            selected = state.isActiveBookmarked,
                            toggle = true,
                            onClick = actions.onToggleBookmark,
                        ),
                    )
                }
            }
            QuickActionGrid(quickActions)

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            AdBlockSiteRow(state = state, onToggle = actions.onToggleAdBlockForSite)

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            if (!state.isPrivate) {
                MenuRow(EroideIcons.History, stringResource(R.string.menu_history), actions.onOpenHistory)
                MenuRow(EroideIcons.Bookmarks, stringResource(R.string.menu_bookmarks), actions.onOpenBookmarks)
                MenuRow(EroideIcons.Download, stringResource(R.string.menu_downloads), actions.onOpenDownloads)
                MenuRow(EroideIcons.Private, stringResource(R.string.menu_private_window), actions.onOpenPrivateWindow)
            }
            MenuRow(EroideIcons.Settings, stringResource(R.string.menu_settings), actions.onOpenSettings)
            if (state.isPrivate) {
                MenuRow(Icons.Filled.Close, stringResource(R.string.menu_close_private), actions.onClosePrivateWindow)
            }
        }
    }
}

private class QuickActionSpec(
    val icon: ImageVector,
    val label: Int,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    /** Mostrata come interruttore (Desktop, Segnalibro) per i lettori di schermo. */
    val toggle: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun PageHeader(title: String?, url: String, isPrivate: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isPrivate) EroideIcons.Private else EroideIcons.Public,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = title?.takeIf { it.isNotBlank() } ?: urlHost(url) ?: url,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            urlHost(url)?.let { host ->
                Text(
                    text = host,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun QuickActionGrid(actions: List<QuickActionSpec>) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        actions.chunked(4).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { action ->
                    QuickAction(action, Modifier.weight(1f))
                }
                // Le righe incomplete restano allineate a griglia.
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun QuickAction(action: QuickActionSpec, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val contentAlpha = if (action.enabled) 1f else 0.38f
    val interaction = if (action.toggle) {
        Modifier.toggleable(
            value = action.selected,
            enabled = action.enabled,
            role = Role.Switch,
            onValueChange = { action.onClick() },
        )
    } else {
        Modifier.clickable(enabled = action.enabled, role = Role.Button, onClick = action.onClick)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(interaction)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (action.selected) colors.primaryContainer else colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = (if (action.selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
                    .copy(alpha = contentAlpha),
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(action.label),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface.copy(alpha = contentAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AdBlockSiteRow(state: BrowserUiState, onToggle: () -> Unit) {
    val enabled = state.adBlockEnabled && state.canToggleAdBlockForSite
    ListItem(
        headlineContent = { Text(stringResource(R.string.menu_adblock_site)) },
        supportingContent = if (!state.adBlockEnabled) {
            { Text(stringResource(R.string.menu_adblock_disabled_globally)) }
        } else {
            urlHost(state.activeTab.url)?.let { host -> { Text(host) } }
        },
        leadingContent = {
            Icon(EroideIcons.Shield, contentDescription = null)
        },
        trailingContent = {
            Switch(
                checked = state.isAdBlockActiveForSite,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.toggleable(
            value = state.isAdBlockActiveForSite,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = { onToggle() },
        ),
    )
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}
