package com.dn0ne.player.app.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.sin

private const val TWO_PI = 6.2831855f

/**
 * Tiny three-bar equalizer drawn over the current track's cover.
 *
 * Bars freeze into a static shape when paused or when animations are reduced. The
 * animation state is read inside the draw scope, so only the canvas is invalidated
 * each frame (no recomposition, no allocations).
 */
@Composable
fun PlayingEqualizer(
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 3
) {
    val reduceMotion = rememberReduceMotion()
    val animate = playing && !reduceMotion

    val transition = if (animate) {
        rememberInfiniteTransition(label = "playing-equalizer")
    } else null
    val phase: State<Float>? = if (transition != null) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "playing-equalizer-phase"
        )
    } else null

    Canvas(modifier = modifier) {
        val currentPhase = phase?.value ?: 0.3f
        val gap = size.width * 0.16f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount

        repeat(barCount) { index ->
            val wave = 0.5f + 0.5f * sin(currentPhase * TWO_PI + index * 2.1f)
            val barHeight = size.height * (0.25f + 0.75f * wave)

            drawRoundRect(
                color = color,
                topLeft = Offset(
                    x = index * (barWidth + gap),
                    y = size.height - barHeight
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}
