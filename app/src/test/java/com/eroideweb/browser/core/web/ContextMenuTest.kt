package com.eroideweb.browser.core.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextMenuTest {

    @Test
    fun `un link offre apertura copia condivisione e download`() {
        val actions = ContextMenu.actionsFor(ContextTarget(linkUrl = "https://example.com/a"), isPrivate = false)

        assertEquals(
            listOf(
                ContextAction.OPEN_LINK_NEW_TAB,
                ContextAction.OPEN_LINK_BACKGROUND,
                ContextAction.OPEN_LINK_PRIVATE,
                ContextAction.COPY_LINK,
                ContextAction.SHARE_LINK,
                ContextAction.DOWNLOAD_LINK,
            ),
            actions,
        )
    }

    @Test
    fun `in privato niente finestra privata`() {
        val actions = ContextMenu.actionsFor(ContextTarget(linkUrl = "https://example.com"), isPrivate = true)

        assertFalse(ContextAction.OPEN_LINK_PRIVATE in actions)
    }

    @Test
    fun `immagine con link mostra entrambe le parti`() {
        val actions = ContextMenu.actionsFor(
            ContextTarget(linkUrl = "https://example.com", imageUrl = "https://cdn.example.com/a.jpg"),
            isPrivate = false,
        )

        assertTrue(ContextAction.OPEN_LINK_NEW_TAB in actions)
        assertTrue(ContextAction.DOWNLOAD_IMAGE in actions)
        assertTrue(ContextAction.SHARE_IMAGE in actions)
    }

    @Test
    fun `immagini incorporate si possono solo salvare e link strani si ignorano`() {
        assertEquals(
            listOf(ContextAction.DOWNLOAD_IMAGE),
            ContextMenu.actionsFor(ContextTarget(imageUrl = "data:image/png;base64,AAAA"), isPrivate = false),
        )
        assertTrue(ContextMenu.actionsFor(ContextTarget(linkUrl = "javascript:alert(1)"), false).isEmpty())
    }
}
