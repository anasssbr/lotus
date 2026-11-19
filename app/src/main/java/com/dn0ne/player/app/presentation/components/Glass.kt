package com.dn0ne.player.app.presentation.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Shared white used for the frosted-glass fill and hairline border. */
val GlassWhite = Color.White

/** Subtle shadow that keeps titles readable over vivid backgrounds. */
val TitleShadow = androidx.compose.ui.graphics.Shadow(
    color = Color.Black.copy(alpha = 0.35f),
    offset = androidx.compose.ui.geometry.Offset(0f, 1f),
    blurRadius = 6f
)

/**
 * Cheap "glass" surface: a translucent white fill plus a hairline border.
 *
 * This is intentionally just two draw calls - no blur, no render effect - so it stays
 * affordable on long lists and grids.
 */
fun Modifier.glassSurface(
    shape: Shape,
    fillAlpha: Float = 0.07f,
    borderAlpha: Float = 0.12f,
    tint: Color = GlassWhite
): Modifier = this
    .background(color = tint.copy(alpha = fillAlpha), shape = shape)
    .border(width = 1.dp, color = tint.copy(alpha = borderAlpha), shape = shape)

/**
 * True when the user asked the system to remove animations.
 *
 * Used to disable decorative motion (equalizer bars, staggered list entrances,
 * background parallax) while keeping the app functional.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val resolver = context.contentResolver
        Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f || Settings.Global.getFloat(
            resolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f
        ) == 0f
    }
}

/**
 * One-shot staggered fade-in for the first [maxStaggered] list items.
 *
 * The animation is remembered per item, so scrolling back to an item does not replay it;
 * it only runs the first time the item enters the composition.
 */
@Composable
fun Modifier.staggeredFadeIn(
    index: Int,
    enabled: Boolean,
    maxStaggered: Int = 8,
    staggerMillis: Int = 25,
    durationMillis: Int = 200
): Modifier {
    val animate = enabled && index < maxStaggered
    val alpha = remember {
        Animatable(if (animate) 0f else 1f)
    }

    LaunchedEffect(animate) {
        if (animate) {
            delay(index * staggerMillis.toLong())
            alpha.animateTo(1f, animationSpec = tween(durationMillis))
        } else {
            alpha.snapTo(1f)
        }
    }

    return this.graphicsLayer {
        this.alpha = alpha.value
    }
}
