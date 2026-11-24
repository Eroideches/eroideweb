package com.eroideweb.browser.core.password

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AesGcmCipherTest {

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val cipher = AesGcmCipher { key }

    @Test
    fun `cifra e decifra senza perdere caratteri`() {
        val secret = "Pàssw0rd! ☕ con spazi e \"virgolette\""

        val encoded = cipher.encrypt(secret)

        assertTrue(encoded.startsWith("v1:"))
        assertFalse(encoded.contains(secret))
        assertEquals(secret, cipher.decrypt(encoded))
    }

    @Test
    fun `ogni cifratura usa un iv diverso`() {
        assertNotEquals(cipher.encrypt("uguale"), cipher.encrypt("uguale"))
    }

    @Test
    fun `testo manomesso o chiave sbagliata non si decifrano`() {
        val encoded = cipher.encrypt("segreta")
        val parts = encoded.split(":")
        val data = Base64.getDecoder().decode(parts[2])
        data[0] = (data[0].toInt() xor 1).toByte()
        val tampered = listOf(parts[0], parts[1], Base64.getEncoder().encodeToString(data)).joinToString(":")

        assertNull(cipher.decrypt(tampered))
        assertNull(AesGcmCipher { newKey() }.decrypt(encoded))
        assertNull(cipher.decrypt("v2:abc:def"))
        assertNull(cipher.decrypt("non-cifrato"))
        assertNull(cipher.decrypt("v1:%%%:%%%"))
    }
}
