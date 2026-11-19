package com.dn0ne.player.app.presentation.components.background

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Paint as AndroidPaint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private val NearBlack = Color(0xFF090C0C)
private const val TWO_PI = 6.2831855f

/** Base loop length for the slowest blob. */
private const val BASE_LOOP_SECONDS = 24f

private const val BLOB_COUNT = 5

// Tune the transition here without touching the draw code.
private const val COLOR_TRANSITION_MS = 1000
private const val BLOB_MOVE_MS = 1000
private const val LIGHT_WAVE_MS = 700
private const val STAGGER_MS = 80

/** Android "reduce animations": colour crossfade only, no motion or wave. */
private const val REDUCED_COLOR_MS = 400

private val BlobX = floatArrayOf(0.10f, 0.93f, 0.50f, 0.06f, 0.90f)
private val BlobY = floatArrayOf(0.12f, 0.28f, 0.55f, 0.80f, 0.96f)
private val BlobRadius = floatArrayOf(0.90f, 0.80f, 1.05f, 0.86f, 0.94f)
private val BlobAlpha = floatArrayOf(0.92f, 0.86f, 0.95f, 0.88f, 0.90f)
private val BlobPhaseOffset = floatArrayOf(0.0f, 2.1f, 4.2f, 1.3f, 3.3f)
private val BlobStaticPhase = floatArrayOf(0.06f, 0.42f, 0.68f, 0.24f, 0.86f)

/** Two slow groups: ~24 s and ~28 s per loop. */
private val BlobSpeed = floatArrayOf(1f, 0.85f, 1f, 0.85f, 1f)

private val BlobMoveSpec = spring<Float>(
    dampingRatio = 0.75f,
    stiffness = 200f * (1000f / BLOB_MOVE_MS)
)

enum class DynamicScrimProfile {
    Home,
    Library
}

data class DynamicPalette(
    val colors: List<Color>,
    val base: Color,
    val highlight: Color
)

/**
 * Very dark tint of [color], used for opaque player roots and the bottom area.
 */
fun deepPaletteBase(color: Color?): Color {
    if (color == null || color == Color.Unspecified || color.alpha <= 0.01f) {
        return NearBlack
    }
    val hsv = hsvOf(color)
    return Color.hsl(normalizeHue(hsv[0]), 0.5f, 0.08f)
}

/**
 * Solid card surface: a dark tint of [color] at ~80% alpha.
 */
fun paletteCardColor(color: Color?): Color {
    val base = if (color == null || color == Color.Unspecified || color.alpha <= 0.01f) {
        Color(0xFF141A1A)
    } else {
        val hsv = hsvOf(color)
        Color.hsl(normalizeHue(hsv[0]), 0.45f, 0.16f)
    }
    return base.copy(alpha = 0.80f)
}

/**
 * Builds a vivid background palette from up to four distinct cover swatches.
 *
 * Swatches keep their hue but get boosted to at least 0.65 saturation and a lightness
 * between 0.38 and 0.55. Nearly grey covers fall back to the stable title+artist hash.
 */
fun dynamicPaletteFrom(
    swatches: List<Color>?,
    fallbackKey: String? = null
): DynamicPalette? {
    val source = swatches
        ?.filter { it.alpha > 0.01f && it != Color.Unspecified }
        .orEmpty()

    if (source.isEmpty() && fallbackKey == null) return null

    val dominantHsv = source.firstOrNull()?.let { hsvOf(it) }
    val nearlyGrey = dominantHsv == null || dominantHsv[1] < 0.15f

    val vividColors = if (nearlyGrey) {
        hashVividPalette(fallbackKey ?: "")
    } else {
        buildVividPalette(source)
    }

    val hue = dominantHsv?.get(0) ?: hsvOf(vividColors.first())[0]
    val base = Color.hsl(normalizeHue(hue), 0.5f, 0.08f)
    val highlight = Color.hsl(normalizeHue(hue), 0.72f, 0.60f)

    return DynamicPalette(
        colors = List(5) { index -> vividColors[index % vividColors.size] },
        base = base,
        highlight = highlight
    )
}

private fun buildVividPalette(source: List<Color>): List<Color> {
    val hues = ArrayList<Float>(4)
    val result = ArrayList<Color>(4)
    var dominantHue = 0f

    source.forEachIndexed { index, color ->
        val hsv = hsvOf(color)
        if (index == 0) dominantHue = hsv[0]
        if (hsv[1] < 0.18f) return@forEachIndexed

        val hue = hsv[0]
        if (hues.any { hueDistance(it, hue) < 40f }) return@forEachIndexed

        hues.add(hue)
        result.add(vivid(hue, result.size))
        if (result.size == 4) return@forEachIndexed
    }

    if (result.size < 3) {
        for (delta in floatArrayOf(0f, -30f, 30f, 150f)) {
            if (result.size >= 4) break
            val hue = normalizeHue(dominantHue + delta)
            if (hues.any { hueDistance(it, hue) < 22f }) continue
            hues.add(hue)
            result.add(vivid(hue, result.size))
        }
    }

    return if (result.isEmpty()) hashVividPalette("") else result
}

private fun vivid(hue: Float, index: Int): Color {
    val lightness = when (index % 3) {
        0 -> 0.46f
        1 -> 0.40f
        else -> 0.52f
    }
    return Color.hsl(normalizeHue(hue), 0.78f, lightness)
}

private fun hashVividPalette(key: String): List<Color> {
    var hash = key.hashCode()
    if (hash == Int.MIN_VALUE) hash = 0
    val base = ((hash % 360) + 360) % 360

    return listOf(
        Color.hsl(base.toFloat(), 0.76f, 0.46f),
        Color.hsl(normalizeHue(base + 42f), 0.74f, 0.50f),
        Color.hsl(normalizeHue(base + 198f), 0.78f, 0.44f),
        Color.hsl(normalizeHue(base + 272f), 0.72f, 0.48f)
    )
}

internal fun hsvOf(color: Color): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv
}

internal fun normalizeHue(hue: Float): Float {
    val result = hue % 360f
    return if (result < 0f) result + 360f else result
}

internal fun hueDistance(a: Float, b: Float): Float {
    val diff = abs(normalizeHue(a) - normalizeHue(b))
    return if (diff > 180f) 360f - diff else diff
}

internal fun luminance(color: Color): Float {
    return 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
}

/**
 * True while the app is resumed and the system has not disabled animations.
 */
@Composable
fun rememberDynamicBackgroundAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isResumed = remember {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed.value = true
                Lifecycle.Event.ON_PAUSE -> isResumed.value = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val reduceMotion = remember(context) {
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

    return isResumed.value && !reduceMotion
}

/**
 * Shared transition state for the dynamic background and the mini player tint.
 *
 * The values are read inside draw lambdas, so an animation frame invalidates drawing
 * instead of recomposing the screen. A new track retargets from the currently
 * interpolated colours, so rapid skips never jump back to the previous track.
 */
@Stable
class DynamicBackgroundAnimator internal constructor() {
    internal val fromColors = Array(BLOB_COUNT) { NearBlack }
    internal val toColors = Array(BLOB_COUNT) { NearBlack }
    internal val colorProgress = Array(BLOB_COUNT) { Animatable(1f) }

    internal var fromBase = NearBlack
    internal var toBase = NearBlack
    internal val baseProgress = Animatable(1f)

    internal var fromHighlight = NearBlack
    internal var toHighlight = NearBlack

    internal val fromOffsets = Array(BLOB_COUNT) { Offset.Zero }
    internal val toOffsets = Array(BLOB_COUNT) { Offset.Zero }
    internal val moveProgress = Array(BLOB_COUNT) { Animatable(1f) }

    internal var waveColor = Color.Transparent
    internal val waveProgress = Animatable(1f)

    internal var initialized = false
    internal var revision by mutableIntStateOf(0)

    val baseColor: Color
        get() = lerpColor(fromBase, toBase, baseProgress.value)

    val highlightColor: Color
        get() = lerpColor(fromHighlight, toHighlight, baseProgress.value)

    fun colorAt(index: Int): Color {
        return lerpColor(
            fromColors[index],
            toColors[index],
            colorProgress[index].value
        )
    }

    fun offsetAt(index: Int): Offset {
        val from = fromOffsets[index]
        val to = toOffsets[index]
        val progress = moveProgress[index].value
        return Offset(
            from.x + (to.x - from.x) * progress,
            from.y + (to.y - from.y) * progress
        )
    }

    internal suspend fun transitionTo(
        palette: BackgroundPalette,
        enabled: Boolean,
        animate: Boolean
    ) {
        if (!initialized) {
            snapTo(palette)
            initialized = true
            revision++
            return
        }

        // Capture the currently visible values before resetting progress. This is what
        // makes a mid-transition track change continue from where the eye already is.
        val baseNow = lerpColor(fromBase, toBase, baseProgress.value)
        val highlightNow = lerpColor(fromHighlight, toHighlight, baseProgress.value)
        val colorsNow = Array(BLOB_COUNT) {
            lerpColor(fromColors[it], toColors[it], colorProgress[it].value)
        }
        val offsetsNow = Array(BLOB_COUNT) { offsetAt(it) }

        fromBase = baseNow
        toBase = palette.base
        fromHighlight = highlightNow
        toHighlight = palette.highlight

        for (index in 0 until BLOB_COUNT) {
            fromColors[index] = colorsNow[index]
            toColors[index] = palette.colors[index % palette.colors.size]
            colorProgress[index].snapTo(0f)

            fromOffsets[index] = offsetsNow[index]
            toOffsets[index] = if (enabled && animate) {
                randomBlobOffset()
            } else {
                offsetsNow[index]
            }
            moveProgress[index].snapTo(0f)
        }

        waveColor = palette.highlight
        waveProgress.snapTo(0f)
        baseProgress.snapTo(0f)
        revision++

        if (!enabled) {
            snapProgress()
            return
        }

        val colorSpec = tween<Float>(
            durationMillis = if (animate) COLOR_TRANSITION_MS else REDUCED_COLOR_MS,
            easing = FastOutSlowInEasing
        )

        coroutineScope {
            launch { baseProgress.animateTo(1f, colorSpec) }

            for (index in 0 until BLOB_COUNT) {
                launch {
                    if (animate) delay(index * STAGGER_MS.toLong())
                    colorProgress[index].animateTo(1f, colorSpec)
                }

                if (animate) {
                    launch {
                        delay(index * STAGGER_MS.toLong())
                        moveProgress[index].animateTo(1f, BlobMoveSpec)
                    }
                }
            }

            if (animate) {
                launch {
                    waveProgress.animateTo(
                        1f,
                        tween(LIGHT_WAVE_MS, easing = FastOutSlowInEasing)
                    )
                }
            } else {
                waveProgress.snapTo(1f)
            }
        }
    }

    internal suspend fun reset() {
        fromBase = NearBlack
        toBase = NearBlack
        baseProgress.snapTo(1f)
        fromHighlight = NearBlack
        toHighlight = NearBlack

        for (index in 0 until BLOB_COUNT) {
            fromColors[index] = NearBlack
            toColors[index] = NearBlack
            colorProgress[index].snapTo(1f)
            fromOffsets[index] = Offset.Zero
            toOffsets[index] = Offset.Zero
            moveProgress[index].snapTo(1f)
        }

        waveProgress.snapTo(1f)
        initialized = false
        revision++
    }

    private suspend fun snapTo(palette: BackgroundPalette) {
        fromBase = palette.base
        toBase = palette.base
        baseProgress.snapTo(1f)
        fromHighlight = palette.highlight
        toHighlight = palette.highlight

        for (index in 0 until BLOB_COUNT) {
            val color = palette.colors[index % palette.colors.size]
            fromColors[index] = color
            toColors[index] = color
            colorProgress[index].snapTo(1f)
            fromOffsets[index] = Offset.Zero
            toOffsets[index] = Offset.Zero
            moveProgress[index].snapTo(1f)
        }

        waveProgress.snapTo(1f)
    }

    private suspend fun snapProgress() {
        baseProgress.snapTo(1f)
        for (index in 0 until BLOB_COUNT) {
            colorProgress[index].snapTo(1f)
            moveProgress[index].snapTo(1f)
        }
        waveProgress.snapTo(1f)
        revision++
    }
}

@Composable
fun rememberDynamicBackgroundAnimator(
    palette: BackgroundPalette?,
    enabled: Boolean,
    animationsEnabled: Boolean
): DynamicBackgroundAnimator {
    val animator = remember { DynamicBackgroundAnimator() }
    val latestEnabled by rememberUpdatedState(enabled)
    val latestAnimations by rememberUpdatedState(animationsEnabled)

    LaunchedEffect(palette) {
        if (palette == null) {
            animator.reset()
        } else {
            animator.transitionTo(palette, latestEnabled, latestAnimations)
        }
    }

    return animator
}

/**
 * Fixed, full-screen background behind Home and Library content.
 *
 * Five vivid radial blobs drift at two slow speeds over a deep tinted base. A track
 * change ripples the blob colours in with a short stagger, moves each blob to a new
 * position with a soft spring, and sends one light wave from the centre. The base is
 * shared with the mini player tint through [animator].
 */
@Composable
fun DynamicBackground(
    palette: BackgroundPalette?,
    animator: DynamicBackgroundAnimator,
    enabled: Boolean,
    animationsEnabled: Boolean,
    isPlaying: Boolean = true,
    scrollOffset: State<Float>? = null,
    profile: DynamicScrimProfile = DynamicScrimProfile.Home,
    modifier: Modifier = Modifier
) {
    val grainBrush = remember { createGrainBrush() }

    if (!enabled || palette == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(NearBlack)
                .drawBehind {
                    drawRect(brush = grainBrush)
                }
        )
        return
    }

    val phase = remember { mutableFloatStateOf(0f) }
    val speed = animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.05f,
        animationSpec = tween(1000),
        label = "dynamic-background-breathing"
    )

    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) return@LaunchedEffect

        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.1f)
                    phase.floatValue =
                        (phase.floatValue + dt * speed.value / BASE_LOOP_SECONDS) % 1f
                }
                lastNanos = now
            }
        }
    }

    val scrim = remember(palette, profile) { scrimBrush(palette, profile) }
    val blobPaints = remember { List(BLOB_COUNT) { BlobPaint(BlobRadius[it]) } }
    val highlightPaint = remember { BlobPaint(0.85f) }
    val wavePaint = remember { BlobPaint(0.60f) }

    Box(modifier = modifier.fillMaxSize()) {
        DynamicBackgroundLayer(
            animator = animator,
            scrim = scrim,
            animated = animationsEnabled,
            phase = phase,
            scrollOffset = scrollOffset,
            blobPaints = blobPaints,
            highlightPaint = highlightPaint,
            wavePaint = wavePaint,
            modifier = Modifier.fillMaxSize()
        )

        // Static grain overlay; the shader is a repeated 128 px tile so this is one draw.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(brush = grainBrush)
                }
        )
    }
}

@Composable
private fun DynamicBackgroundLayer(
    animator: DynamicBackgroundAnimator,
    scrim: Brush,
    animated: Boolean,
    phase: State<Float>,
    scrollOffset: State<Float>?,
    blobPaints: List<BlobPaint>,
    highlightPaint: BlobPaint,
    wavePaint: BlobPaint,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.drawBehind {
            // Subscribe drawing to instant snaps (first launch, disabled animations)
            // as well as to the animated progress values below.
            @Suppress("UNUSED_VARIABLE")
            val revision = animator.revision

            val width = size.width
            val height = size.height
            val canvas = drawContext.canvas.nativeCanvas

            drawRect(color = animator.baseColor)

            val driftX = width * 0.065f
            val driftY = height * 0.045f

            // Bounded, smooth response to scrolling: the blobs move against the list
            // but never more than ~18 dp, and never jump at item boundaries.
            val scrollPx = if (animated) scrollOffset?.value ?: 0f else 0f
            val parallax = if (scrollPx == 0f) {
                0f
            } else {
                18.dp.toPx() * (scrollPx / (abs(scrollPx) + 1800f))
            }
            val phaseValue = if (animated) phase.value else 0f

            for (index in 0 until BLOB_COUNT) {
                val angle = if (animated) {
                    phaseValue * TWO_PI * BlobSpeed[index] + BlobPhaseOffset[index]
                } else {
                    BlobStaticPhase[index] * TWO_PI
                }

                val move = animator.offsetAt(index)
                val centerX = (
                    BlobX[index] * width +
                    move.x * width +
                    sin(angle) * driftX
                ).coerceIn(width * 0.04f, width * 0.96f)
                val centerY = (
                    BlobY[index] * height +
                    move.y * height +
                    cos(angle * 0.85f + BlobPhaseOffset[index] * 1.3f) * driftY +
                    parallax
                ).coerceIn(height * 0.04f, height * 0.96f)

                val paint = blobPaints[index]
                paint.color = animator.colorAt(index)
                paint.prepare(width, BlobAlpha[index])

                canvas.save()
                canvas.translate(centerX, centerY)
                canvas.drawRect(
                    -centerX,
                    -centerY,
                    width - centerX,
                    height - centerY,
                    paint.paint
                )
                canvas.restore()
            }

            highlightPaint.color = animator.highlightColor
            highlightPaint.prepare(width, 0.85f)
            val highlightX = width * 0.20f + sin(phaseValue * TWO_PI) * driftX * 0.4f
            val highlightY = height * 0.04f

            canvas.save()
            canvas.translate(highlightX, highlightY)
            canvas.drawRect(
                -highlightX,
                -highlightY,
                width - highlightX,
                height - highlightY,
                highlightPaint.paint
            )
            canvas.restore()

            drawRect(brush = scrim)

            // One expanding, fading highlight per track change.
            val wave = animator.waveProgress.value
            if (wave < 0.999f) {
                wavePaint.color = animator.waveColor
                wavePaint.prepare(width, 0.35f * (1f - wave))
                val centerX = width * 0.5f
                val centerY = height * 0.5f
                val scale = wave.coerceIn(0f, 1f)

                canvas.save()
                canvas.translate(centerX, centerY)
                canvas.scale(scale, scale)
                canvas.drawRect(
                    -centerX,
                    -centerY,
                    width - centerX,
                    height - centerY,
                    wavePaint.paint
                )
                canvas.restore()
            }
        }
    )
}

/**
 * Vertical fade used behind the mini player: transparent at the top, the shared
 * animated base colour from the halfway point down.
 */
@Stable
class VerticalFadePaint {
    var color: Color = NearBlack

    private val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    private var shaderColor: Color = Color.Unspecified
    private var shaderHeight: Float = -1f
    private val argb = IntArray(3)
    private val stops = floatArrayOf(0f, 0.5f, 1f)

    fun draw(scope: DrawScope) {
        val height = scope.size.height
        if (color != shaderColor || height != shaderHeight) {
            argb[0] = Color.Transparent.toArgb()
            argb[1] = color.toArgb()
            argb[2] = color.toArgb()
            paint.shader = LinearGradient(
                0f,
                0f,
                0f,
                height,
                argb,
                stops,
                Shader.TileMode.CLAMP
            )
            shaderColor = color
            shaderHeight = height
        }

        paint.alpha = 255
        scope.drawContext.canvas.nativeCanvas.drawRect(
            0f,
            0f,
            scope.size.width,
            height,
            paint
        )
    }
}

/**
 * Soft radial blob. The shader is rebuilt only when its colour or radius changes,
 * so a settled background draws without allocating anything.
 */
private class BlobPaint(
    private val radiusFactor: Float
) {
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    var color: Color = Color.Black

    private var shaderColor: Color = Color.Unspecified
    private var shaderRadius: Float = -1f
    private val argb = IntArray(3)
    private val stops = floatArrayOf(0f, 0.55f, 1f)

    fun prepare(width: Float, alpha: Float) {
        val radius = width * radiusFactor
        if (color != shaderColor || radius != shaderRadius) {
            argb[0] = color.toArgb()
            argb[1] = color.copy(alpha = 0.42f).toArgb()
            argb[2] = color.copy(alpha = 0f).toArgb()
            paint.shader = RadialGradient(
                0f,
                0f,
                radius,
                argb,
                stops,
                Shader.TileMode.CLAMP
            )
            shaderColor = color
            shaderRadius = radius
        }

        paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
    }
}

/**
 * Scrim alphas for the current profile, raised when the blended background is bright
 * enough that white text would drop below a 4.5:1 contrast ratio.
 */
private fun scrimAlphas(
    palette: BackgroundPalette,
    profile: DynamicScrimProfile
): Pair<Float, Float> {
    val baseLuminance = luminance(palette.base)
    val blobLuminance = palette.colors
        .map { luminance(it) }
        .average()
        .toFloat()
    val blended = baseLuminance * 0.30f + blobLuminance * 0.70f * 0.85f

    val needed = if (blended > 0.35f) {
        (1f - 0.1833f / blended).coerceIn(0f, 0.82f)
    } else {
        0f
    }

    val top = if (profile == DynamicScrimProfile.Library) 0.40f else 0.27f
    val bottom = if (profile == DynamicScrimProfile.Library) 0.40f else 0.45f

    return max(top, needed) to max(bottom, needed)
}

private fun scrimBrush(
    palette: BackgroundPalette,
    profile: DynamicScrimProfile
): Brush {
    val (top, bottom) = scrimAlphas(palette, profile)
    return Brush.verticalGradient(
        0f to Color.Black.copy(alpha = top),
        0.55f to Color.Black.copy(alpha = top),
        1f to Color.Black.copy(alpha = bottom)
    )
}

private fun randomBlobOffset(): Offset {
    val angle = Random.nextFloat() * TWO_PI
    val distance = 0.15f + Random.nextFloat() * 0.10f
    return Offset(
        cos(angle) * distance,
        sin(angle) * distance * 0.8f
    )
}

/**
 * One 128 x 128 noise tile, generated once and repeated by a bitmap shader.
 * Average alpha is roughly 3%, which keeps the grain almost invisible.
 */
private fun createGrainBrush(): ShaderBrush {
    val size = 128
    val random = Random(0x5EED)
    val pixels = IntArray(size * size) { index ->
        if (index % 2 == 0) {
            val alpha = random.nextInt(0, 17)
            (alpha shl 24) or 0x00FFFFFF
        } else {
            val alpha = random.nextInt(0, 12)
            (alpha shl 24)
        }
    }
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)

    return ShaderBrush(
        BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    )
}
