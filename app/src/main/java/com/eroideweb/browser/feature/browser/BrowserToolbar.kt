package com.eroideweb.browser.feature.browser

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eroideweb.browser.R
import com.eroideweb.browser.feature.browser.web.TabHistoryEntry

/**
 * Riga azioni della barra: ← → + [schede] ☰, sempre a portata di pollice.
 * Tocco lungo su ← mostra le pagine precedenti della scheda.
 */
@Composable
fun NavigationRow(
    tab: TabUi,
    tabCount: Int,
    backHistory: () -> List<TabHistoryEntry>,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHistoryEntry: (TabHistoryEntry) -> Unit,
    onNewTab: () -> Unit,
    onShowTabs: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var history by remember { mutableStateOf<List<TabHistoryEntry>>(emptyList()) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            ToolbarButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                enabled = tab.canGoBack,
                onClick = onBack,
                onLongClick = { history = backHistory() },
            )
            DropdownMenu(
                expanded = history.isNotEmpty(),
                onDismissRequest = { history = emptyList() },
            ) {
                history.forEach { entry ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = entry.title?.takeIf { it.isNotBlank() } ?: entry.url,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 280.dp),
                            )
                        },
                        onClick = {
                            history = emptyList()
                            onHistoryEntry(entry)
                        },
                    )
                }
            }
        }
        ToolbarButton(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = stringResource(R.string.action_forward),
            enabled = tab.canGoForward,
            onClick = onForward,
        )
        ToolbarButton(
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.tab_new),
            onClick = onNewTab,
        )
        TabCounter(count = tabCount, onClick = onShowTabs)
        ToolbarButton(
            icon = Icons.Filled.Menu,
            contentDescription = stringResource(R.string.action_menu),
            onClick = onMenu,
        )
    }
}

/** Pulsante tondo della barra con tocco lungo opzionale. */
@Composable
private fun ToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val tint = LocalContentColor.current.copy(alpha = if (enabled) 1f else 0.38f)
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
    }
}

/**
 * Contatore schede: quadratino con il numero, come nei browser desktop.
 * Toccandolo si apre il selettore a card.
 */
@Composable
private fun TabCounter(count: Int, onClick: () -> Unit) {
    val description = stringResource(R.string.cd_tab_count, count)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(1.6.dp, LocalContentColor.current, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (count > 99) "∞" else count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = LocalContentColor.current,
                maxLines = 1,
            )
        }
    }
}
