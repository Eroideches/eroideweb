package com.eroideweb.browser.feature.browser

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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eroideweb.browser.R
import com.eroideweb.browser.core.password.CredentialOrigin
import com.eroideweb.browser.core.password.SavedLogin
import com.eroideweb.browser.ui.common.GlassCapsule
import com.eroideweb.browser.ui.icons.EroideIcons

/**
 * "Accedi come …" sopra la tastiera: compare quando si tocca un campo di
 * accesso di un sito con password salvate. Compila solo al tocco.
 */
@Composable
fun FillOfferBar(
    offer: FillOffer,
    onFill: (SavedLogin) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var chooser by remember { mutableStateOf(false) }
    val first = offer.logins.first()
    GlassCapsule(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 14.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = EroideIcons.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.password_fill_as, loginLabel(first)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button) { onFill(first) }
                    .padding(vertical = 14.dp),
            )
            if (offer.logins.size > 1) {
                Box {
                    IconButton(onClick = { chooser = true }) {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.password_fill_choose))
                    }
                    DropdownMenu(expanded = chooser, onDismissRequest = { chooser = false }) {
                        offer.logins.forEach { login ->
                            DropdownMenuItem(
                                text = { Text(loginLabel(login)) },
                                onClick = {
                                    chooser = false
                                    onFill(login)
                                },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.password_fill_dismiss))
            }
        }
    }
}

/** "Salvare la password per sito?" / "Aggiornare la password?" dopo un accesso. */
@Composable
fun SavePasswordCard(
    prompt: SavePrompt,
    onSave: () -> Unit,
    onNever: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val site = CredentialOrigin.displayName(prompt.origin)
    GlassCapsule(modifier = modifier) {
        Column(modifier = Modifier.padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = EroideIcons.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(
                        if (prompt.isUpdate) R.string.password_update_title else R.string.password_save_title,
                        site,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = prompt.username.ifEmpty { stringResource(R.string.password_no_username) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 32.dp, top = 2.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!prompt.isUpdate) {
                    TextButton(onClick = onNever) { Text(stringResource(R.string.password_never)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.password_not_now)) }
                Button(onClick = onSave) {
                    Text(stringResource(if (prompt.isUpdate) R.string.password_update else R.string.password_save))
                }
            }
        }
    }
}

@Composable
private fun loginLabel(login: SavedLogin): String =
    login.username.ifEmpty { stringResource(R.string.password_no_username) }
