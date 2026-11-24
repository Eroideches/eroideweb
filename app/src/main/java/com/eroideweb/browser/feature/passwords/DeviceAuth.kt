package com.eroideweb.browser.feature.passwords

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.PersistableBundle
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Sblocco del telefono prima di mostrare o copiare una password: impronta,
 * volto o PIN/sequenza, come per il blocco schermo. Nessuna libreria in più:
 * BiometricPrompt di sistema da Android 11, conferma credenziali prima.
 */
class DeviceAuthenticator internal constructor(
    private val context: Context,
    private val launcher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    private val pending: MutableState<((Boolean) -> Unit)?>,
) {

    /** Serve un blocco schermo: senza, le password non si mostrano. */
    val isAvailable: Boolean
        get() = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun authenticate(title: String, onResult: (Boolean) -> Unit) {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            onResult(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val prompt = BiometricPrompt.Builder(context)
                .setTitle(title)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
                .build()
            prompt.authenticate(
                CancellationSignal(),
                context.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onResult(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        onResult(false)
                    }
                },
            )
            return
        }
        @Suppress("DEPRECATION")
        val intent = keyguard.createConfirmDeviceCredentialIntent(title, null)
        if (intent == null) {
            onResult(false)
            return
        }
        pending.value = onResult
        launcher.launch(intent)
    }
}

@Composable
fun rememberDeviceAuthenticator(): DeviceAuthenticator {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pending.value?.invoke(result.resultCode == Activity.RESULT_OK)
        pending.value = null
    }
    return remember(context, launcher) { DeviceAuthenticator(context, launcher, pending) }
}

/**
 * Copia negli appunti marcando il contenuto come sensibile: Android non lo
 * mostra nell'anteprima degli appunti né nei suggerimenti della tastiera.
 */
fun copySensitive(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = PersistableBundle().apply {
        val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            "android.content.extra.IS_SENSITIVE"
        }
        putBoolean(key, true)
    }
    clipboard.setPrimaryClip(clip)
}
