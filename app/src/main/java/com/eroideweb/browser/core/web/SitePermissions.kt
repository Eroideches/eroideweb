package com.eroideweb.browser.core.web

/** Cosa può chiedere un sito. */
enum class SitePermission {
    LOCATION,
    CAMERA,
    MICROPHONE,
    /** Video protetti (DRM), es. piattaforme di streaming. */
    PROTECTED_MEDIA,
}

/**
 * Traduzione tra richieste della WebView e permessi Android. Logica pura,
 * testata (le stringhe sono quelle di `android.webkit.PermissionRequest`).
 */
object SitePermissions {

    const val RESOURCE_VIDEO_CAPTURE = "android.webkit.resource.VIDEO_CAPTURE"
    const val RESOURCE_AUDIO_CAPTURE = "android.webkit.resource.AUDIO_CAPTURE"
    const val RESOURCE_PROTECTED_MEDIA_ID = "android.webkit.resource.PROTECTED_MEDIA_ID"

    /** Permessi sito per le risorse richieste; quelle sconosciute (es. MIDI) vengono rifiutate. */
    fun fromResources(resources: Collection<String>): Set<SitePermission> = resources.mapNotNullTo(linkedSetOf()) {
        when (it) {
            RESOURCE_VIDEO_CAPTURE -> SitePermission.CAMERA
            RESOURCE_AUDIO_CAPTURE -> SitePermission.MICROPHONE
            RESOURCE_PROTECTED_MEDIA_ID -> SitePermission.PROTECTED_MEDIA
            else -> null
        }
    }

    /** Risorse WebView da concedere per i permessi accettati. */
    fun resourcesFor(permissions: Collection<SitePermission>): List<String> = permissions.mapNotNull {
        when (it) {
            SitePermission.CAMERA -> RESOURCE_VIDEO_CAPTURE
            SitePermission.MICROPHONE -> RESOURCE_AUDIO_CAPTURE
            SitePermission.PROTECTED_MEDIA -> RESOURCE_PROTECTED_MEDIA_ID
            SitePermission.LOCATION -> null
        }
    }

    /** Permessi Android da chiedere all'utente prima di concedere al sito. */
    fun androidPermissionsFor(permissions: Collection<SitePermission>): List<String> = permissions.flatMap {
        when (it) {
            SitePermission.LOCATION -> listOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
            )
            SitePermission.CAMERA -> listOf("android.permission.CAMERA")
            SitePermission.MICROPHONE -> listOf(
                "android.permission.RECORD_AUDIO",
                "android.permission.MODIFY_AUDIO_SETTINGS",
            )
            SitePermission.PROTECTED_MEDIA -> emptyList()
        }
    }.distinct()
}
