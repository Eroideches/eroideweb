package com.eroideweb.browser.core.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SitePermissionsTest {

    @Test
    fun `le risorse note diventano permessi e le altre si rifiutano`() {
        val permissions = SitePermissions.fromResources(
            listOf(
                SitePermissions.RESOURCE_VIDEO_CAPTURE,
                SitePermissions.RESOURCE_AUDIO_CAPTURE,
                "android.webkit.resource.MIDI_SYSEX",
            ),
        )

        assertEquals(setOf(SitePermission.CAMERA, SitePermission.MICROPHONE), permissions)
        assertEquals(
            listOf(SitePermissions.RESOURCE_VIDEO_CAPTURE, SitePermissions.RESOURCE_AUDIO_CAPTURE),
            SitePermissions.resourcesFor(permissions),
        )
    }

    @Test
    fun `ogni permesso del sito chiede quelli giusti ad android`() {
        assertEquals(
            listOf("android.permission.CAMERA"),
            SitePermissions.androidPermissionsFor(setOf(SitePermission.CAMERA)),
        )
        assertTrue(
            "android.permission.ACCESS_COARSE_LOCATION" in
                SitePermissions.androidPermissionsFor(setOf(SitePermission.LOCATION)),
        )
        assertTrue(SitePermissions.androidPermissionsFor(setOf(SitePermission.PROTECTED_MEDIA)).isEmpty())
    }
}
