package com.eroideweb.browser.core.password

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Chiave delle password nell'Android Keystore: generata la prima volta, non
 * esportabile, legata a questo dispositivo. Senza sblocco a ogni uso: la
 * compilazione deve essere istantanea; per mostrare o copiare una password
 * serve comunque lo sblocco del telefono (lo chiede l'interfaccia).
 */
object KeystoreKeys {

    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "eroideweb.credentials.v1"

    @Volatile
    private var cached: SecretKey? = null

    @Synchronized
    fun credentialsKey(): SecretKey {
        cached?.let { return it }
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(ALIAS, null) as? SecretKey
        val key = existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
        cached = key
        return key
    }
}
