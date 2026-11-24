package com.eroideweb.browser.feature.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eroideweb.browser.R
import com.eroideweb.browser.core.url.urlHost
import com.eroideweb.browser.ui.common.SiteIcon
import com.eroideweb.browser.ui.common.SwipeToDismiss
import com.eroideweb.browser.ui.icons.EroideIcons

/**
 * Selettore schede a card: tocco per aprire, **trascina a destra o a
 * sinistra per chiudere** (con "Annulla"), tocco lungo per duplicare.
 * Le card si ricompattano con un'animazione quando una sparisce.
 */
@Composable
fun TabSwitcher(
    tabs: List<TabUi>,
    activeTabId: String,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onCloseAll: () -> Unit,
    onDuplicate: (String) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit,
    isPrivate: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (isPrivate) R.string.tabs_title_private else R.string.tabs_title,
                        tabs.size,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCloseAll) {
                    Text(stringResource(R.string.tabs_close_all))
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close_tabs),
                    )
                }
            }

            Text(
                text = stringResource(R.string.tabs_swipe_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tabs, key = { it.id }) { tab ->
                    SwipeToDismiss(
                        onDismiss = { onClose(tab.id) },
                        modifier = Modifier.animateItem(),
                    ) {
                        TabCard(
                            tab = tab,
                            isActive = tab.id == activeTabId,
                            onSelect = { onSelect(tab.id) },
                            onClose = { onClose(tab.id) },
                            onDuplicate = { onDuplicate(tab.id) },
                        )
                    }
                }
            }

            Button(
                onClick = onNewTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tab_new))
            }
        }
    }
}

@Composable
private fun TabCard(
    tab: TabUi,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onDuplicate: () -> Unit,
) {
    val url = tab.url
    val newPage = stringResource(R.string.tab_new_page)
    val title = tab.title?.takeIf { it.isNotBlank() } ?: url ?: newPage
    var menuOpen by remember { mutableStateOf(false) }
    val closeLabel = stringResource(R.string.action_close_tab)
    val duplicateLabel = stringResource(R.string.tab_duplicate)

    Box {
        // Evita che i siti con titoli enormi facciano crescere la card.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 168.dp)
                .clip(CardDefaults.shape)
                .combinedClickable(
                    onClick = onSelect,
                    onLongClick = { menuOpen = true },
                )
                .semantics {
                    // Chi usa TalkBack non può trascinare: chiusura e duplicato come azioni.
                    customActions = listOf(
                        CustomAccessibilityAction(closeLabel) { onClose(); true },
                        CustomAccessibilityAction(duplicateLabel) { onDuplicate(); true },
                    )
                },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            border = if (isActive) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    if (url != null) {
                        SiteIcon(
                            url = url,
                            title = tab.title,
                            size = 20.dp,
                            modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = closeLabel,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tab.desktopMode) {
                        Icon(
                            imageVector = EroideIcons.Desktop,
                            contentDescription = stringResource(R.string.menu_desktop),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 2.dp),
                        )
                    }
                    Text(
                        text = urlHost(url)?.removePrefix("www.") ?: url ?: newPage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(duplicateLabel) },
                leadingIcon = { Icon(EroideIcons.Tab, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onDuplicate()
                },
            )
            DropdownMenuItem(
                text = { Text(closeLabel) },
                leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onClose()
                },
            )
        }
    }
}
