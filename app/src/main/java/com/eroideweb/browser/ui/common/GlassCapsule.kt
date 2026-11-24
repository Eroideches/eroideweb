package com.eroideweb.browser.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eroideweb.browser.ui.theme.LocalDarkTheme

/**
 * Capsula "liquid glass" disegnata sopra il contenuto.
 *
 * Limite onesto: il contenuto sotto è una WebView nativa, quindi non possiamo
 * sfocarlo in Compose. Il vetro è simulato con traslucenza, gradiente e bordo
 * luminoso — l'effetto è lo stesso a occhio, il blur ottico no.
 */
@Composable
fun GlassCapsule(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(28.dp)
    val base = if (dark) Color(0xFF0E1726) else Color(0xFFFFFFFF)
    val surfaceTop = base.copy(alpha = if (dark) 0.94f else 0.90f)
    val surfaceBottom = base.copy(alpha = if (dark) 0.78f else 0.72f)
    val shineTop = Color.White.copy(alpha = if (dark) 0.20f else 0.85f)
    val shineBottom = Color.White.copy(alpha = 0.04f)
    val contentColor = if (dark) Color(0xFFE6EDF7) else Color(0xFF14181F)

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(
            modifier = modifier
                .shadow(
                    elevation = 18.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.30f),
                    spotColor = Color.Black.copy(alpha = 0.30f),
                )
                .clip(shape)
                .background(Brush.verticalGradient(listOf(surfaceTop, surfaceBottom)))
                .border(1.dp, Brush.verticalGradient(listOf(shineTop, shineBottom)), shape),
            content = content,
        )
    }
}
