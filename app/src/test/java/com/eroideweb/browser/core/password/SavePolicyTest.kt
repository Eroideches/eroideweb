package com.eroideweb.browser.core.password

import org.junit.Assert.assertEquals
import org.junit.Test

class SavePolicyTest {

    private val stored = listOf(StoredLogin(1, "mario@example.com", "vecchia"), StoredLogin(2, "", "solo-pin"))

    @Test
    fun `credenziali nuove si propongono da salvare`() {
        assertEquals(SaveDecision.Save, SavePolicy.decide(stored, "luisa@example.com", "segreta", neverForSite = false))
    }

    @Test
    fun `stessa password non si ripropone, password diversa si aggiorna`() {
        assertEquals(SaveDecision.Ignore, SavePolicy.decide(stored, "mario@example.com", "vecchia", false))
        assertEquals(SaveDecision.Update(1), SavePolicy.decide(stored, " mario@example.com ", "nuova", false))
        assertEquals(SaveDecision.Update(2), SavePolicy.decide(stored, "", "altro-pin", false))
    }

    @Test
    fun `siti esclusi e password vuote o assurde non si salvano`() {
        assertEquals(SaveDecision.Ignore, SavePolicy.decide(stored, "x", "y", neverForSite = true))
        assertEquals(SaveDecision.Ignore, SavePolicy.decide(stored, "x", "", false))
        assertEquals(SaveDecision.Ignore, SavePolicy.decide(stored, "x", "a".repeat(2000), false))
    }
}
