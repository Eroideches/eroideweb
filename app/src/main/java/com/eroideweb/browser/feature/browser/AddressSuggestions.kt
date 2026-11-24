package com.eroideweb.browser.feature.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eroideweb.browser.R
import com.eroideweb.browser.core.settings.SearchEngine
import com.eroideweb.browser.core.suggest.Suggestion
import com.eroideweb.browser.core.suggest.SuggestionSource
import com.eroideweb.browser.ui.common.GlassCapsule
import com.eroideweb.browser.ui.common.SiteIcon
import com.eroideweb.browser.ui.icons.EroideIcons

/**
 * Suggerimenti mentre si scrive: prima la ricerca sul motore scelto, poi
 * preferiti, segnalibri e pagine visitate. Tutto locale, nessuna rete.
 *
 * Con la barra in basso l'ordine è rovesciato: il suggerimento migliore
 * resta vicino al dito e alla tastiera.
 */
@Composable
fun AddressSuggestions(
    query: String,
    suggestions: List<Suggestion>,
    searchEngine: SearchEngine,
    reversed: Boolean,
    onOpen: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows: List<@Composable () -> Unit> = buildList {
        add {
            SuggestionRow(
                leading = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                title = query,
                subtitle = stringResource(R.string.suggestion_search_on, searchEngine.displayName),
                onClick = { onSearch(query) },
            )
        }
        suggestions.forEach { suggestion ->
            add {
                SuggestionRow(
                    leading = { SiteIcon(url = suggestion.url, title = suggestion.title, size = 20.dp) },
                    title = suggestion.title?.takeIf { it.isNotBlank() } ?: suggestion.url,
                    subtitle = suggestion.url
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .removePrefix("www."),
                    badge = when (suggestion.source) {
                        SuggestionSource.FAVORITE -> EroideIcons.FavoriteFilled
                        SuggestionSource.BOOKMARK -> EroideIcons.BookmarkFilled
                        SuggestionSource.HISTORY -> EroideIcons.History
                    },
                    onClick = { onOpen(suggestion.url) },
                )
            }
        }
    }
    GlassCapsule(modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            (if (reversed) rows.asReversed() else rows).forEach { row -> row() }
        }
    }
}

@Composable
private fun SuggestionRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
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
        }
        if (badge != null) {
            Icon(
                imageVector = badge,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp),
            )
        }
    }
}
