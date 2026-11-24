package com.eroideweb.browser.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eroideweb.browser.R

/**
 * Errore di caricamento spiegato in italiano semplice: niente codici HTTP,
 * niente URL tecnici. Chi usa il browser deve capire cosa è successo subito.
 */
@Composable
fun ErrorPage(
    error: PageError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title: String
    val hint: String?
    when (error.kind) {
        PageErrorKind.NO_CONNECTION -> {
            title = stringResource(R.string.error_no_connection)
            hint = stringResource(R.string.error_no_connection_hint)
        }

        PageErrorKind.INSECURE -> {
            title = stringResource(R.string.error_insecure)
            hint = stringResource(R.string.error_insecure_hint)
        }

        PageErrorKind.GENERIC -> {
            title = stringResource(R.string.error_generic)
            hint = null
        }

        PageErrorKind.CRASHED -> {
            title = stringResource(R.string.error_crashed)
            hint = stringResource(R.string.error_crashed_hint)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}
