package com.eroideweb.browser.ui.common

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sign

/** Oltre questa frazione della larghezza, lasciare il dito chiude l'elemento. */
private const val DISMISS_FRACTION = 0.35f

/** Un lancio più veloce di così (px/s) chiude anche se il dito si è mosso poco. */
private const val DISMISS_VELOCITY = 1400f

private const val RESET_DELAY_MS = 600L

/**
 * Trascina orizzontalmente per chiudere (schede, voci di cronologia).
 *
 * Lo spostamento vive solo nella fase di disegno (`graphicsLayer`): durante il
 * gesto non c'è nessuna ricomposizione, quindi resta fluido anche con molte
 * card. Il gesto verticale resta libero per lo scorrimento della lista.
 */
@Composable
fun SwipeToDismiss(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    val dismiss by rememberUpdatedState(onDismiss)

    Box(
        modifier = modifier
            .onSizeChanged { width = it.width }
            .graphicsLayer {
                translationX = offset
                val progress = if (width > 0) (abs(offset) / width).coerceIn(0f, 1f) else 0f
                alpha = 1f - progress * 0.75f
                rotationZ = offset / (width.coerceAtLeast(1)) * 4f
            }
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = enabled,
                state = rememberDraggableState { delta -> offset += delta },
                onDragStopped = { velocity ->
                    val full = width.toFloat()
                    val farEnough = full > 0 && abs(offset) > full * DISMISS_FRACTION
                    val flung = abs(velocity) > DISMISS_VELOCITY &&
                        (offset == 0f || sign(velocity) == sign(offset))
                    if (full > 0 && (farEnough || flung)) {
                        val direction = if (offset != 0f) sign(offset) else sign(velocity)
                        animate(offset, direction * full * 1.1f, animationSpec = tween(160)) { value, _ ->
                            offset = value
                        }
                        dismiss()
                        // Di norma l'elemento sparisce dalla lista e questa attesa viene
                        // annullata; se invece resta, dopo un attimo torna al suo posto.
                        delay(RESET_DELAY_MS)
                        offset = 0f
                    } else {
                        animate(offset, 0f, animationSpec = spring()) { value, _ -> offset = value }
                    }
                },
            ),
    ) {
        content()
    }
}
