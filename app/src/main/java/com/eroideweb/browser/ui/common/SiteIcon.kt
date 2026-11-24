package com.eroideweb.browser.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eroideweb.browser.core.favicon.FaviconStore
import com.eroideweb.browser.core.url.urlHost
import kotlinx.coroutines.flow.MutableStateFlow

/** Archivio delle favicon disponibile a tutta la UI del browser. */
val LocalFaviconStore = staticCompositionLocalOf<FaviconStore?> { null }

/** Colori delle iniziali quando un sito non ha ancora un'icona. */
private val AvatarColors = listOf(
    Color(0xFF0A5BD3),
    Color(0xFF00799E),
    Color(0xFF7B4FD6),
    Color(0xFFC2410C),
    Color(0xFF15803D),
    Color(0xFFBE185D),
    Color(0xFF4D7C0F),
    Color(0xFF0E7490),
)

private val NoVersion = MutableStateFlow(0)

/**
 * Icona di un sito: favicon se l'abbiamo già vista, altrimenti l'iniziale su
 * un colore stabile (sempre lo stesso per lo stesso sito).
 */
@Composable
fun SiteIcon(
    url: String?,
    title: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val store = LocalFaviconStore.current
    val host = urlHost(url)
    val version by (store?.version ?: NoVersion).collectAsState()
    val icon: ImageBitmap? by produceState(host?.let { store?.cached(it) }, host, version, store) {
        value = if (host != null && store != null) store.load(host) else null
    }
    val shape = RoundedCornerShape(size / 4)
    val current = icon
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(shape),
        )
    } else {
        val label = host?.removePrefix("www.") ?: title.orEmpty()
        val letter = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '•'
        val color = AvatarColors[(label.hashCode() and Int.MAX_VALUE) % AvatarColors.size]
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = letter.toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp,
            )
        }
    }
}
