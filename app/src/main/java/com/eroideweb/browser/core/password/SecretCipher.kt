package com.eroideweb.browser.core.password

import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cifra e decifra le password salvate. */
interface SecretCipher {
    fun encrypt(plain: String): String

    /** `null` se il testo è rovinato o la chiave non è più quella giusta. */
    fun decrypt(encoded: String): String?
}

/**
 * AES-256-GCM: cifratura autenticata, un IV casuale per ogni password.
 * Formato: `v1:<iv base64>:<testo cifrato base64>`.
 *
 * La chiave arriva da [keyProvider]: sul telefono vive nell'Android Keystore e
 * non lascia mai l'hardware sicuro; nei test è una chiave AES normale.
 */
class AesGcmCipher(private val keyProvider: () -> SecretKey) : SecretCipher {

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // L'IV lo sceglie il provider (il Keystore non accetta IV forniti da fuori).
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return listOf(VERSION, encoder.encodeToString(cipher.iv), encoder.encodeToString(encrypted))
            .joinToString(SEPARATOR)
    }

    override fun decrypt(encoded: String): String? {
        val parts = encoded.split(SEPARATOR)
        if (parts.size != 3 || parts[0] != VERSION) return null
        return try {
            val iv = decoder.decode(parts[1])
            val data = decoder.decode(parts[2])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val VERSION = "v1"
        const val SEPARATOR = ":"
        val encoder: Base64.Encoder = Base64.getEncoder()
        val decoder: Base64.Decoder = Base64.getDecoder()
    }
}
