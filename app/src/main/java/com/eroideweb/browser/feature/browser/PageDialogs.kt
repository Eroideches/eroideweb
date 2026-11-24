package com.eroideweb.browser.feature.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eroideweb.browser.R
import com.eroideweb.browser.core.url.urlHost
import com.eroideweb.browser.core.web.ContextAction
import com.eroideweb.browser.core.web.ContextMenu
import com.eroideweb.browser.core.web.ContextTarget
import com.eroideweb.browser.core.web.SitePermission
import com.eroideweb.browser.ui.icons.EroideIcons

/** Menu del tocco lungo su un link o un'immagine. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextMenuSheet(
    target: ContextTarget,
    isPrivate: Boolean,
    onAction: (ContextAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val actions = ContextMenu.actionsFor(target, isPrivate)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
        ) {
            val headline = target.title?.takeIf { it.isNotBlank() }
                ?: target.linkUrl
                ?: target.imageUrl?.takeUnless { it.startsWith("data:") }
                ?: stringResource(R.string.context_image)
            Text(
                text = headline,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            (target.linkUrl ?: target.imageUrl)?.let { url ->
                urlHost(url)?.let { host ->
                    Text(
                        text = host,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 4.dp))
            actions.forEach { action ->
                val (icon, label) = contextActionPresentation(action)
                ListItem(
                    headlineContent = { Text(stringResource(label)) },
                    leadingContent = { Icon(icon, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable(role = Role.Button) { onAction(action) },
                )
            }
        }
    }
}

private fun contextActionPresentation(action: ContextAction): Pair<ImageVector, Int> = when (action) {
    ContextAction.OPEN_LINK_NEW_TAB -> EroideIcons.OpenInNew to R.string.context_open_new_tab
    ContextAction.OPEN_LINK_BACKGROUND -> EroideIcons.Tab to R.string.context_open_background
    ContextAction.OPEN_LINK_PRIVATE -> EroideIcons.Private to R.string.context_open_private
    ContextAction.COPY_LINK -> EroideIcons.ContentCopy to R.string.context_copy_link
    ContextAction.SHARE_LINK -> EroideIcons.Share to R.string.context_share_link
    ContextAction.DOWNLOAD_LINK -> EroideIcons.Download to R.string.context_download_link
    ContextAction.OPEN_IMAGE_NEW_TAB -> EroideIcons.Image to R.string.context_open_image
    ContextAction.DOWNLOAD_IMAGE -> EroideIcons.Download to R.string.context_download_image
    ContextAction.COPY_IMAGE_ADDRESS -> EroideIcons.ContentCopy to R.string.context_copy_image
    ContextAction.SHARE_IMAGE -> EroideIcons.Share to R.string.context_share_image
}

/** "sito.it vuole usare la tua posizione": Blocca / Consenti. */
@Composable
fun SitePermissionDialog(
    prompt: PermissionPrompt,
    onAnswer: (Boolean) -> Unit,
) {
    val site = urlHost(prompt.origin)?.removePrefix("www.") ?: prompt.origin
    val what = prompt.permissions.map { stringResource(permissionLabel(it)) }.joinToString(", ")
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        icon = {
            Icon(
                imageVector = if (SitePermission.LOCATION in prompt.permissions) EroideIcons.Public else EroideIcons.Shield,
                contentDescription = null,
            )
        },
        title = { Text(stringResource(R.string.permission_title, site)) },
        text = { Text(stringResource(R.string.permission_body, what)) },
        confirmButton = {
            TextButton(onClick = { onAnswer(true) }) { Text(stringResource(R.string.permission_allow)) }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.permission_block)) }
        },
    )
}

private fun permissionLabel(permission: SitePermission): Int = when (permission) {
    SitePermission.LOCATION -> R.string.permission_location
    SitePermission.CAMERA -> R.string.permission_camera
    SitePermission.MICROPHONE -> R.string.permission_microphone
    SitePermission.PROTECTED_MEDIA -> R.string.permission_protected_media
}

/** Accesso richiesto dal server (autenticazione HTTP, non un modulo della pagina). */
@Composable
fun HttpAuthDialog(
    host: String,
    realm: String,
    onLogin: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(EroideIcons.Key, contentDescription = null) },
        title = { Text(stringResource(R.string.http_auth_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (realm.isNotBlank()) {
                        stringResource(R.string.http_auth_body_realm, host, realm)
                    } else {
                        stringResource(R.string.http_auth_body, host)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.passwords_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.passwords_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onLogin(username, password) }) { Text(stringResource(R.string.http_auth_login)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
