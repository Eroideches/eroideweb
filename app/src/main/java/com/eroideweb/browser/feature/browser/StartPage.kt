package com.eroideweb.browser.feature.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eroideweb.browser.R
import com.eroideweb.browser.core.data.FavoriteEntry
import com.eroideweb.browser.core.url.urlHost
import com.eroideweb.browser.ui.common.SiteIcon
import com.eroideweb.browser.ui.icons.EroideIcons
import com.eroideweb.browser.ui.theme.BrandBlue
import com.eroideweb.browser.ui.theme.BrandCyan
import com.eroideweb.browser.ui.theme.CyanDark
import com.eroideweb.browser.ui.theme.Ink950
import com.eroideweb.browser.ui.theme.LocalDarkTheme
import com.eroideweb.browser.ui.theme.Mist300
import com.eroideweb.browser.ui.theme.OnPaper
import com.eroideweb.browser.ui.theme.Paper
import com.eroideweb.browser.ui.theme.Slate600

/** Riquadri per riga nella griglia dei preferiti. */
private const val FAVORITES_PER_ROW = 4

/** Azioni sui preferiti della pagina iniziale. */
class StartPageActions(
    val onOpen: (String) -> Unit,
    /** @return `false` se l'indirizzo non è valido. */
    val onAdd: (url: String, title: String) -> Boolean,
    val onEdit: (id: Long, url: String, title: String) -> Boolean,
    val onRemove: (Long) -> Unit,
    val onMove: (id: Long, delta: Int) -> Unit,
)

/**
 * Pagina iniziale: nessuna richiesta di rete, solo UI. Segue il tema dell'app
 * (scuro: notte blu del marchio; chiaro: carta con il glifo in blu) e mostra i
 * siti preferiti come riquadri. In privato niente preferiti.
 */
@Composable
fun StartPage(
    isPrivate: Boolean = false,
    favorites: List<FavoriteEntry> = emptyList(),
    actions: StartPageActions? = null,
    modifier: Modifier = Modifier,
) {
    val dark = LocalDarkTheme.current || isPrivate
    val background = if (dark) {
        listOf(Ink950, Color(0xFF0A1A33), Color(0xFF062A45))
    } else {
        listOf(Paper, Color(0xFFEAF1FB), Color(0xFFDCEBFA))
    }
    val titleColor = if (dark) Color.White else OnPaper
    val taglineColor = if (dark) Mist300 else Slate600
    val accent = if (dark) BrandCyan else CyanDark
    val showFavorites = !isPrivate && actions != null
    val compact = showFavorites && favorites.isNotEmpty()
    var editing by remember { mutableStateOf<FavoriteEntry?>(null) }
    var adding by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(colors = background)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                // Il glifo è bianco: sul chiaro diventa blu del marchio.
                colorFilter = if (dark) null else ColorFilter.tint(BrandBlue),
                modifier = Modifier.size(if (compact) 120.dp else 200.dp),
            )
            Text(
                text = stringResource(
                    if (isPrivate) R.string.private_title else R.string.app_name,
                ),
                style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                color = titleColor,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    if (isPrivate) R.string.private_tagline else R.string.start_tagline,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = taglineColor,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            if (showFavorites && actions != null) {
                FavoritesGrid(
                    favorites = favorites,
                    dark = dark,
                    labelColor = titleColor,
                    actions = actions,
                    onEdit = { editing = it },
                    onAdd = { adding = true },
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (dark) Color.White.copy(alpha = 0.08f) else BrandBlue.copy(alpha = 0.08f),
                ) {
                    Text(
                        text = stringResource(R.string.start_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (actions != null) {
        editing?.let { entry ->
            FavoriteDialog(
                title = stringResource(R.string.favorite_edit_title),
                initialName = entry.title.orEmpty(),
                initialUrl = entry.url,
                onConfirm = { name, url -> actions.onEdit(entry.id, url, name) },
                onDismiss = { editing = null },
            )
        }
        if (adding) {
            FavoriteDialog(
                title = stringResource(R.string.favorite_add_title),
                initialName = "",
                initialUrl = "",
                onConfirm = { name, url -> actions.onAdd(url, name) },
                onDismiss = { adding = false },
            )
        }
    }
}

@Composable
private fun FavoritesGrid(
    favorites: List<FavoriteEntry>,
    dark: Boolean,
    labelColor: Color,
    actions: StartPageActions,
    onEdit: (FavoriteEntry) -> Unit,
    onAdd: () -> Unit,
) {
    val tileColor = if (dark) Color.White.copy(alpha = 0.10f) else BrandBlue.copy(alpha = 0.08f)
    // Il riquadro "+" chiude sempre la griglia.
    val cells: List<FavoriteEntry?> = favorites + listOf(null)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cells.chunked(FAVORITES_PER_ROW).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { entry ->
                    if (entry != null) {
                        val index = favorites.indexOf(entry)
                        FavoriteTile(
                            entry = entry,
                            tileColor = tileColor,
                            labelColor = labelColor,
                            canMoveBack = index > 0,
                            canMoveForward = index < favorites.lastIndex,
                            onOpen = { actions.onOpen(entry.url) },
                            onEdit = { onEdit(entry) },
                            onRemove = { actions.onRemove(entry.id) },
                            onMove = { delta -> actions.onMove(entry.id, delta) },
                        )
                    } else {
                        AddTile(tileColor = tileColor, labelColor = labelColor, onClick = onAdd)
                    }
                }
                repeat(FAVORITES_PER_ROW - row.size) { Spacer(Modifier.width(TILE_WIDTH)) }
            }
        }
    }
}

private val TILE_WIDTH = 76.dp

@Composable
private fun FavoriteTile(
    entry: FavoriteEntry,
    tileColor: Color,
    labelColor: Color,
    canMoveBack: Boolean,
    canMoveForward: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val label = entry.title?.takeIf { it.isNotBlank() } ?: urlHost(entry.url)?.removePrefix("www.") ?: entry.url
    Box {
        Column(
            modifier = Modifier
                .width(TILE_WIDTH)
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(role = Role.Button, onClick = onOpen, onLongClick = { menuOpen = true })
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tileColor),
                contentAlignment = Alignment.Center,
            ) {
                SiteIcon(url = entry.url, title = entry.title, size = 32.dp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.favorite_edit)) },
                leadingIcon = { Icon(EroideIcons.Edit, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onEdit()
                },
            )
            if (canMoveBack) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.favorite_move_back)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onMove(-1)
                    },
                )
            }
            if (canMoveForward) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.favorite_move_forward)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onMove(1)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.favorite_remove)) },
                leadingIcon = { Icon(EroideIcons.Delete, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun AddTile(tileColor: Color, labelColor: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(TILE_WIDTH)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(tileColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = labelColor)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.favorite_add),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
        )
    }
}

/** Nome e indirizzo di un preferito; l'indirizzo viene normalizzato come nella barra. */
@Composable
private fun FavoriteDialog(
    title: String,
    initialName: String,
    initialUrl: String,
    onConfirm: (name: String, url: String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.favorite_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        invalid = false
                    },
                    label = { Text(stringResource(R.string.favorite_url)) },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(stringResource(R.string.favorite_url_invalid)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (onConfirm(name, url)) onDismiss() else invalid = true
                },
                enabled = url.isNotBlank(),
            ) {
                Text(stringResource(R.string.favorite_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
