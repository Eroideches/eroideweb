package com.eroideweb.browser.core.password

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialOriginTest {

    @Test
    fun `l origine e schema piu host senza porta standard`() {
        assertEquals("https://accounts.example.com", CredentialOrigin.from("https://Accounts.Example.com:443/login?x=1"))
        assertEquals("https://example.com:8443", CredentialOrigin.from("https://example.com:8443"))
        assertEquals("https://example.com", CredentialOrigin.from("https://example.com."))
    }

    @Test
    fun `le pagine in chiaro su internet non hanno password`() {
        assertNull(CredentialOrigin.from("http://example.com"))
        assertNull(CredentialOrigin.from("null"))
        assertNull(CredentialOrigin.from("file:///sdcard/a.html"))
        assertNull(CredentialOrigin.from("data:text/html,x"))
        assertNull(CredentialOrigin.from(""))
    }

    @Test
    fun `http e ammesso solo su questo dispositivo`() {
        assertEquals("http://localhost:8080", CredentialOrigin.from("http://localhost:8080"))
        assertEquals("http://127.0.0.1", CredentialOrigin.from("http://127.0.0.1:80/admin"))
    }

    @Test
    fun `nome da mostrare senza schema e senza www`() {
        assertEquals("example.com", CredentialOrigin.displayName("https://www.example.com"))
        assertEquals("localhost:8080", CredentialOrigin.displayName("http://localhost:8080"))
    }
}
