package com.eroideweb.browser.feature.passwords

import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eroideweb.browser.R
import com.eroideweb.browser.core.password.CredentialOrigin
import com.eroideweb.browser.core.password.SavedLogin
import com.eroideweb.browser.feature.settings.SettingsParagraph
import com.eroideweb.browser.feature.settings.SettingsRow
import com.eroideweb.browser.feature.settings.SettingsSwitchRow
import com.eroideweb.browser.ui.common.SiteIcon
import com.eroideweb.browser.ui.icons.EroideIcons
import kotlinx.coroutines.launch

/**
 * Gestore password: elenco per sito, dettaglio, modifica ed eliminazione.
 * La schermata non compare negli screenshot né nelle app recenti
 * (`FLAG_SECURE`) e ogni password visibile richiede lo sblocco del telefono.
 */
@Composable
fun PasswordsScreen(
    viewModel: PasswordsViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            viewModel.onClose()
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (!viewModel.navigateUp()) onDismiss() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    text = when (state.page) {
                        PasswordsPage.LIST -> stringResource(R.string.passwords_title)
                        PasswordsPage.DETAIL -> state.selected?.let { CredentialOrigin.displayName(it.origin) }.orEmpty()
                        PasswordsPage.NEVER_SITES -> stringResource(R.string.passwords_never_sites)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                )
            }
            when (state.page) {
                PasswordsPage.LIST -> PasswordList(state, viewModel)
                PasswordsPage.DETAIL -> state.selected?.let { PasswordDetail(it, state, viewModel) }
                PasswordsPage.NEVER_SITES -> NeverSites(state, viewModel)
            }
        }
    }
}

@Composable
private fun PasswordList(state: PasswordsUiState, viewModel: PasswordsViewModel) {
    var confirmDeleteAll by remember { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.passwords_offer_save),
                subtitle = stringResource(R.string.passwords_offer_save_summary),
                icon = EroideIcons.Key,
                checked = state.offerToSave,
                onCheckedChange = viewModel::setOfferToSave,
            )
        }
        item {
            SettingsRow(
                title = stringResource(R.string.passwords_never_sites),
                subtitle = stringResource(R.string.passwords_never_sites_count, state.neverSites.size),
                icon = EroideIcons.Policy,
                onClick = viewModel::openNeverSites,
            )
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.passwords_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.library_search_clear))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        val logins = state.filtered
        if (logins.isEmpty()) {
            item {
                SettingsParagraph(
                    title = null,
                    body = stringResource(
                        if (state.logins.isEmpty()) R.string.passwords_empty else R.string.library_no_results,
                    ),
                )
            }
        }
        items(logins, key = { it.id }) { login ->
            ListItem(
                headlineContent = {
                    Text(CredentialOrigin.displayName(login.origin), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(
                        login.username.ifEmpty { stringResource(R.string.password_no_username) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = { SiteIcon(url = login.origin, title = null, size = 28.dp) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.clickable { viewModel.openDetail(login.id) },
            )
        }
        item {
            SettingsParagraph(title = null, body = stringResource(R.string.passwords_local_note))
        }
        if (state.logins.isNotEmpty()) {
            item {
                TextButton(
                    onClick = { confirmDeleteAll = true },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.passwords_delete_all), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDeleteAll) {
        ConfirmDialog(
            title = stringResource(R.string.passwords_delete_all_title),
            message = stringResource(R.string.passwords_delete_all_message),
            onConfirm = {
                confirmDeleteAll = false
                viewModel.deleteAll()
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }
}

@Composable
private fun PasswordDetail(login: SavedLogin, state: PasswordsUiState, viewModel: PasswordsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authenticator = rememberDeviceAuthenticator()
    val unlockTitle = stringResource(R.string.passwords_unlock_title)
    val needLock = stringResource(R.string.passwords_need_lock)
    val copied = stringResource(R.string.passwords_copied)
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    /** Esegue [action] a telefono sbloccato, chiedendo lo sblocco se serve. */
    fun unlocked(action: () -> Unit) {
        if (viewModel.isUnlocked()) {
            action()
            return
        }
        if (!authenticator.isAvailable) {
            Toast.makeText(context, needLock, Toast.LENGTH_LONG).show()
            return
        }
        authenticator.authenticate(unlockTitle) { ok ->
            if (ok) {
                viewModel.onUnlocked()
                action()
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SiteIcon(url = login.origin, title = null, size = 40.dp)
                Text(
                    text = login.origin,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
        item {
            ListItem(
                overlineContent = { Text(stringResource(R.string.passwords_username)) },
                headlineContent = { Text(login.username.ifEmpty { stringResource(R.string.password_no_username) }) },
                trailingContent = {
                    if (login.username.isNotEmpty()) {
                        IconButton(onClick = {
                            copySensitive(context, "username", login.username)
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(EroideIcons.ContentCopy, contentDescription = stringResource(R.string.passwords_copy_username))
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
        item { HorizontalDivider(Modifier.padding(horizontal = 16.dp)) }
        item {
            val revealed = state.revealedPassword
            ListItem(
                overlineContent = { Text(stringResource(R.string.passwords_password)) },
                headlineContent = { Text(revealed ?: "••••••••••") },
                trailingContent = {
                    Row {
                        IconButton(onClick = {
                            if (revealed != null) viewModel.hide() else unlocked { viewModel.reveal() }
                        }) {
                            Icon(
                                imageVector = if (revealed != null) EroideIcons.VisibilityOff else EroideIcons.Visibility,
                                contentDescription = stringResource(
                                    if (revealed != null) R.string.passwords_hide else R.string.passwords_show,
                                ),
                            )
                        }
                        IconButton(onClick = {
                            unlocked {
                                scope.launch {
                                    viewModel.passwordForCopy(login.id)?.let { password ->
                                        copySensitive(context, "password", password)
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }) {
                            Icon(EroideIcons.ContentCopy, contentDescription = stringResource(R.string.passwords_copy_password))
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = { unlocked { editing = true } }) {
                    Text(stringResource(R.string.passwords_edit))
                }
                Button(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.passwords_delete))
                }
            }
        }
    }

    if (editing) {
        EditLoginDialog(
            login = login,
            viewModel = viewModel,
            onDismiss = { editing = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.passwords_delete_title),
            message = stringResource(R.string.passwords_delete_message, CredentialOrigin.displayName(login.origin)),
            onConfirm = {
                confirmDelete = false
                viewModel.delete(login.id)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun EditLoginDialog(login: SavedLogin, viewModel: PasswordsViewModel, onDismiss: () -> Unit) {
    var username by remember { mutableStateOf(login.username) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.passwords_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.passwords_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = false
                    },
                    label = { Text(stringResource(R.string.passwords_new_password)) },
                    singleLine = true,
                    isError = error,
                    supportingText = if (error) {
                        { Text(stringResource(R.string.passwords_edit_error)) }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.update(login.id, username, password) { ok -> if (ok) onDismiss() else error = true }
                },
                enabled = password.isNotEmpty(),
            ) {
                Text(stringResource(R.string.favorite_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun NeverSites(state: PasswordsUiState, viewModel: PasswordsViewModel) {
    val sites = remember(state.neverSites) { state.neverSites.sorted() }
    if (sites.isEmpty()) {
        SettingsParagraph(title = null, body = stringResource(R.string.passwords_never_sites_empty))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(sites, key = { it }) { origin ->
            SettingsRow(
                title = CredentialOrigin.displayName(origin),
                icon = EroideIcons.Public,
                trailing = {
                    IconButton(onClick = { viewModel.removeNeverSite(origin) }) {
                        Icon(EroideIcons.Delete, contentDescription = stringResource(R.string.passwords_never_remove, origin))
                    }
                },
            )
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.library_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
