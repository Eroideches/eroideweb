package com.eroideweb.browser.feature.library

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eroideweb.browser.R
import com.eroideweb.browser.core.data.BookmarkEntry
import com.eroideweb.browser.core.data.HistoryEntry

/**
 * Overlay a schermo intero con cronologia e segnalibri.
 * Toccando una voce si apre l'URL nella scheda attiva e si chiude la libreria.
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

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
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { confirmClear = true },
                    enabled = when (state.tab) {
                        LibraryTab.HISTORY -> state.history.isNotEmpty()
                        LibraryTab.BOOKMARKS -> state.bookmarks.isNotEmpty()
                    },
                ) {
                    Text(stringResource(R.string.library_clear))
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.library_close),
                    )
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.library_search_hint)) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.library_search_clear),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            Spacer(Modifier.height(8.dp))

            TabRow(selectedTabIndex = state.tab.ordinal) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { viewModel.setTab(tab) },
                        text = {
                            Text(
                                stringResource(
                                    when (tab) {
                                        LibraryTab.HISTORY -> R.string.library_history
                                        LibraryTab.BOOKMARKS -> R.string.library_bookmarks
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            when (state.tab) {
                LibraryTab.HISTORY -> HistoryList(
                    entries = state.history,
                    query = state.query,
                    onOpen = { onOpenUrl(it.url) },
                    onDelete = { viewModel.deleteHistory(it.id) },
                )

                LibraryTab.BOOKMARKS -> BookmarkList(
                    entries = state.bookmarks,
                    query = state.query,
                    onOpen = { onOpenUrl(it.url) },
                    onDelete = { viewModel.deleteBookmark(it.id) },
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = {
                Text(
                    stringResource(
                        if (state.tab == LibraryTab.HISTORY) {
                            R.string.library_clear_history_title
                        } else {
                            R.string.library_clear_bookmarks_title
                        },
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (state.tab == LibraryTab.HISTORY) {
                            R.string.library_clear_history_message
                        } else {
                            R.string.library_clear_bookmarks_message
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (state.tab == LibraryTab.HISTORY) {
                            viewModel.clearHistory()
                        } else {
                            viewModel.clearBookmarks()
                        }
                        confirmClear = false
                    },
                ) {
                    Text(stringResource(R.string.library_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun HistoryList(
    entries: List<HistoryEntry>,
    query: String,
    onOpen: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyState(
            text = stringResource(
                if (query.isBlank()) R.string.library_history_empty else R.string.library_no_results,
            ),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(entries, key = { it.id }) { entry ->
            EntryRow(
                title = entry.title?.takeIf { it.isNotBlank() } ?: entry.url,
                subtitle = entry.url,
                meta = relativeTime(entry.visitedAt),
                onOpen = { onOpen(entry) },
                onDelete = { onDelete(entry) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun BookmarkList(
    entries: List<BookmarkEntry>,
    query: String,
    onOpen: (BookmarkEntry) -> Unit,
    onDelete: (BookmarkEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyState(
            text = stringResource(
                if (query.isBlank()) R.string.library_bookmarks_empty else R.string.library_no_results,
            ),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(entries, key = { it.id }) { entry ->
            EntryRow(
                title = entry.title?.takeIf { it.isNotBlank() } ?: entry.url,
                subtitle = entry.url,
                meta = null,
                onOpen = { onOpen(entry) },
                onDelete = { onDelete(entry) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun EntryRow(
    title: String,
    subtitle: String,
    meta: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 4.dp, end = 0.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta != null) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.library_delete_entry),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun relativeTime(timestamp: Long): String =
    DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
