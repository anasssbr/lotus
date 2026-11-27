package com.dn0ne.player.app.presentation.components.design

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dn0ne.player.app.presentation.components.CoverArt
import com.dn0ne.player.app.presentation.components.rememberReduceMotion
import com.kmpalette.color
import com.kmpalette.rememberDominantColorState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shared editorial design tokens for the restyled Home and Library screens.
 *
 * Everything here is a plain constant or a small composable so both screens can
 * stay visually identical without introducing a theme layer.
 */
object EditorialSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** Horizontal screen padding. */
    val screen = 20.dp

    /** Vertical gap between major sections. */
    val section = 32.dp
}

object EditorialType {
    val display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp
    )

    val sectionTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp
    )

    val cardTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )

    val tileTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp
    )

    val mixTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    )

    val metric = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1).sp
    )

    val rank = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 32.sp,
        letterSpacing = (-1).sp
    )

    val rankFirst = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 40.sp,
        letterSpacing = (-1).sp
    )

    val secondary = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 17.sp
    )

    val tiny = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.5.sp
    )

    val stat = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    )
}

/** White at 65%, the standard secondary text tone over the dynamic background. */
val EditorialSecondary: Color
    get() = Color.White.copy(alpha = 0.65f)

/** White at 55%, used for the tiny uppercase labels. */
val EditorialTiny: Color
    get() = Color.White.copy(alpha = 0.55f)

/** White at 35%, used for inactive chart bars and rank numbers. */
val EditorialFaint: Color
    get() = Color.White.copy(alpha = 0.35f)

@Composable
fun EditorialSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = title,
            style = EditorialType.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (trailing != null) {
            Spacer(modifier = Modifier.weight(1f))
            trailing()
        }
    }
}

/**
 * Press feedback shared by every tappable surface: a spring scale to 0.97 with
 * no ripple, disabled when the system asks for reduced motion.
 */
@Composable
fun Modifier.editorialClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberReduceMotion()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !reduceMotion) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "editorial-press"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * First-appearance entrance: fade in while sliding up 24dp, staggered 60ms per
 * section for the first five sections. Runs once per visit and is skipped when
 * reduced motion is requested.
 */
@Composable
fun Modifier.editorialEntrance(
    index: Int,
    enabled: Boolean = true
): Modifier {
    val reduceMotion = rememberReduceMotion()
    val active = enabled && !reduceMotion
    var appeared by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(active) {
        if (active && !appeared) {
            delay(index.coerceIn(0, 4) * 60L)
            appeared = true
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (!active || appeared) 1f else 0f,
        animationSpec = tween(
            durationMillis = 420,
            easing = FastOutSlowInEasing
        ),
        label = "editorial-entrance-$index"
    )

    return this.graphicsLayer {
        alpha = progress
        translationY = if (active) (1f - progress) * 24.dp.toPx() else 0f
    }
}

/**
 * Cover art with a soft radial glow in the artwork's dominant colour behind it.
 * The caller decides the size; the glow bleeds slightly outside the cover.
 */
@Composable
fun GlowCover(
    uri: Uri,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    glowAlpha: Float = 0.25f
) {
    val dominantColorState = rememberDominantColorState()
    val coroutineScope = rememberCoroutineScope()
    val glowColor = dominantColorState.color

    Box(
        modifier = modifier.drawBehind {
            if (glowColor.alpha > 0.02f) {
                val radius = size.maxDimension * 0.78f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = glowAlpha),
                            glowColor.copy(alpha = glowAlpha * 0.4f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        }
    ) {
        CoverArt(
            uri = uri,
            onCoverArtLoaded = { bitmap ->
                bitmap?.let {
                    coroutineScope.launch {
                        dominantColorState.updateFrom(it)
                    }
                }
            },
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(cornerRadius))
        )
    }
}
