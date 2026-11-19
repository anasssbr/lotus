package com.dn0ne.player.app.presentation.components.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import com.dn0ne.player.app.domain.track.Track
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private class Artwork(
    val bitmap: ImageBitmap?,
    val colors: List<Color>,
    val lowRes: Boolean
)

@Composable
fun NowPlayingStage(
    track: Track,
    screenWidthPx: Int,
    screenHeightPx: Int,
    expansionFraction: Float,
    baseColor: Color,
    onToggleExpanded: () -> Unit,
    onCoverArtLoaded: (ImageBitmap?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Opaque base under both crossfade layers, so a track change can never reveal the
    // Home/Library screen behind the player while the new artwork is decoding.
    Box(modifier = modifier.background(baseColor)) {
        Crossfade(
            targetState = track,
            animationSpec = tween(durationMillis = 400),
            label = "now-playing-crossfade",
            modifier = Modifier.fillMaxSize()
        ) { currentTrack ->
            val artwork = rememberArtwork(
                track = currentTrack,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                onCoverArtLoaded = onCoverArtLoaded
            )

            StageLayer(
                artwork = artwork,
                expansionFraction = expansionFraction,
                onToggleExpanded = onToggleExpanded,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun rememberArtwork(
    track: Track,
    screenWidthPx: Int,
    screenHeightPx: Int,
    onCoverArtLoaded: (ImageBitmap?) -> Unit = {}
): Artwork {
    val context = LocalContext.current
    val key = track.artworkKey()

    var bitmap by remember(key) {
        mutableStateOf(ArtworkMemoryCache.get(key))
    }
    var lowRes by remember(key) {
        mutableStateOf(
            ArtworkMemoryCache.get(key)?.let { it.width < screenWidthPx * 0.8f } ?: false
        )
    }
    val colors = remember(key) {
        hashPalette(key)
    }

    LaunchedEffect(key) {
        if (ArtworkMemoryCache.has(key)) {
            val cached = ArtworkMemoryCache.getExact(key)
            bitmap = cached
            lowRes = cached != null && cached.width < screenWidthPx * 0.8f
            onCoverArtLoaded(cached)
            return@LaunchedEffect
        }

        val image = loadCoverBitmap(
            context = context,
            uri = track.coverArtUri,
            widthPx = screenWidthPx,
            heightPx = screenHeightPx
        )
        ArtworkMemoryCache.put(key, image)
        bitmap = image
        lowRes = image != null && image.width < screenWidthPx * 0.8f
        onCoverArtLoaded(image)
    }

    return Artwork(
        bitmap = bitmap,
        colors = colors,
        lowRes = lowRes
    )
}

private object ArtworkMemoryCache {
    private var key: String? = null
    private var bitmap: ImageBitmap? = null
    private var lastBitmap: ImageBitmap? = null

    fun get(key: String): ImageBitmap? {
        val cached = if (this.key == key) bitmap else null
        // Keep the previous cover visible until the new one finishes decoding.
        return cached ?: lastBitmap
    }

    fun has(key: String): Boolean = this.key == key

    fun getExact(key: String): ImageBitmap? = if (this.key == key) bitmap else null

    fun put(key: String, value: ImageBitmap?) {
        this.key = key
        this.bitmap = value
        if (value != null) {
            lastBitmap = value
        }
    }
}

@Composable
fun NowPlayingArtworkBackground(
    track: Track,
    screenWidthPx: Int,
    screenHeightPx: Int,
    baseColor: Color,
    dim: Float = 0.27f,
    dimOnDarkArtwork: Float? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(baseColor)) {
        Crossfade(
            targetState = track,
            animationSpec = tween(durationMillis = 400),
            label = "artwork-background-crossfade",
            modifier = Modifier.fillMaxSize()
        ) { currentTrack ->
            val artwork = rememberArtwork(
                track = currentTrack,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx
            )

            val luminance = remember(artwork.bitmap) {
                artwork.bitmap?.let { averageLuminance(it) }
            }
            val effectiveDim = if (
                dimOnDarkArtwork != null && luminance != null && luminance < 0.32f
            ) {
                dimOnDarkArtwork
            } else {
                dim
            }

            ArtworkBackground(
                artwork = artwork,
                dim = effectiveDim,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun averageLuminance(bitmap: ImageBitmap): Float {
    return try {
        val source = bitmap.asAndroidBitmap()
        val size = 12
        val small = Bitmap.createScaledBitmap(source, size, size, true)
        val pixels = IntArray(size * size)
        small.getPixels(pixels, 0, size, 0, 0, size, size)
        if (small !== source) {
            small.recycle()
        }

        var sum = 0f
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            sum += 0.2126f * r + 0.7152f * g + 0.0722f * b
        }

        sum / pixels.size
    } catch (e: Exception) {
        0.5f
    }
}

@Composable
private fun StageLayer(
    artwork: Artwork,
    expansionFraction: Float,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val statusBarTop = WindowInsets.statusBars
            .asPaddingValues()
            .calculateTopPadding()
        val squareSize = maxWidth * 0.88f
        val defaultTop = statusBarTop + 56.dp

        val coverWidth = lerp(squareSize, maxWidth, expansionFraction)
        val coverHeight = lerp(squareSize, maxHeight, expansionFraction)
        val coverTop = lerp(defaultTop, 0.dp, expansionFraction)
        val corner = lerp(24.dp, 0.dp, expansionFraction)
        val shadowStrength = 1f - expansionFraction
        val interactionSource = remember {
            MutableInteractionSource()
        }

        ArtworkBackground(
            artwork = artwork,
            dim = 0.20f * (1f - expansionFraction),
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = coverTop)
                .size(width = coverWidth, height = coverHeight)
                .drawBehind {
                    if (shadowStrength > 0.01f) {
                        drawSoftShadow(
                            cornerRadius = corner.toPx(),
                            strength = shadowStrength
                        )
                    }
                }
                .clip(RoundedCornerShape(corner))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    onToggleExpanded()
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArtwork(
                    artwork = artwork,
                    expansionFraction = expansionFraction
                )
            }
        }
    }
}

@Composable
private fun ArtworkBackground(
    artwork: Artwork,
    dim: Float,
    modifier: Modifier = Modifier
) {
    val bitmap = artwork.bitmap
    if (bitmap == null) {
        MeshBackground(
            colors = artwork.colors,
            modifier = modifier
        )
        return
    }

    Box(modifier = modifier) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.2f
                        scaleY = 1.2f
                    }
                    .blur(
                        radius = 40.dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
            )
        } else {
            val blurred = remember(bitmap) {
                blurredBitmapFor(bitmap)
            }

            Image(
                bitmap = blurred,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (dim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dim))
            )
        }
    }
}

private var blurredSource: android.graphics.Bitmap? = null
private var blurredResult: ImageBitmap? = null

private fun blurredBitmapFor(bitmap: ImageBitmap): ImageBitmap {
    val source = bitmap.asAndroidBitmap()
    val cached = blurredResult
    if (blurredSource === source && cached != null) {
        return cached
    }

    val result = createBlurredBitmap(bitmap)
    blurredSource = source
    blurredResult = result
    return result
}

@Composable
fun NowPlayingScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.55f to Color.Transparent,
                0.72f to Color.Black.copy(alpha = 0.25f),
                0.88f to Color.Black.copy(alpha = 0.45f),
                1f to Color.Black.copy(alpha = 0.60f)
            )
        )
    )
}

private fun DrawScope.drawSoftShadow(
    cornerRadius: Float,
    strength: Float
) {
    val spread = 22.dp.toPx()
    val offsetY = 6.dp.toPx()
    val steps = 12
    val color = Color.Black.copy(alpha = 0.04f * strength)

    for (i in steps downTo 1) {
        val grow = spread * i / steps
        drawRoundRect(
            color = color,
            topLeft = Offset(-grow, -grow + offsetY),
            size = Size(size.width + grow * 2f, size.height + grow * 2f),
            cornerRadius = CornerRadius(cornerRadius + grow)
        )
    }
}

private fun DrawScope.drawArtwork(
    artwork: Artwork,
    expansionFraction: Float
) {
    val bitmap = artwork.bitmap ?: return

    val imageWidth = bitmap.width.toFloat()
    val imageHeight = bitmap.height.toFloat()
    val cropScale = max(size.width / imageWidth, size.height / imageHeight)

    val scale = if (artwork.lowRes) {
        val cappedFit = min(size.width / imageWidth, 1.3f)
        cropScale + (cappedFit - cropScale) * expansionFraction
    } else {
        cropScale
    }

    val drawWidth = imageWidth * scale
    val drawHeight = imageHeight * scale
    val left = (size.width - drawWidth) / 2f
    val top = (size.height - drawHeight) / 2f
    val rect = Rect(left, top, left + drawWidth, top + drawHeight)

    val feather = if (artwork.lowRes) {
        smoothstep(0.35f, 1f, expansionFraction)
    } else 0f

    if (feather > 0.01f) {
        drawContext.canvas.saveLayer(rect, Paint())
        drawImageInRect(bitmap, rect)

        val fadeY = 0.16f * feather
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                fadeY to Color.Black,
                1f - fadeY to Color.Black,
                1f to Color.Transparent,
                startY = rect.top,
                endY = rect.bottom
            ),
            topLeft = rect.topLeft,
            size = rect.size,
            blendMode = BlendMode.DstIn
        )

        val fadeX = 0.12f * feather
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                fadeX to Color.Black,
                1f - fadeX to Color.Black,
                1f to Color.Transparent,
                startX = rect.left,
                endX = rect.right
            ),
            topLeft = rect.topLeft,
            size = rect.size,
            blendMode = BlendMode.DstIn
        )

        drawContext.canvas.restore()
    } else {
        drawImageInRect(bitmap, rect)
    }
}

private fun DrawScope.drawImageInRect(
    bitmap: ImageBitmap,
    rect: Rect
) {
    drawImage(
        image = bitmap,
        dstOffset = IntOffset(
            x = rect.left.roundToInt(),
            y = rect.top.roundToInt()
        ),
        dstSize = IntSize(
            width = rect.width.roundToInt(),
            height = rect.height.roundToInt()
        ),
        filterQuality = FilterQuality.High
    )
}

@Composable
private fun MeshBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val meshColors = remember(colors) {
        colors.map { meshColor(it) }
    }

    Canvas(modifier = modifier) {
        drawRect(color = Color(0xFF0B0B0B))

        if (meshColors.isEmpty()) return@Canvas

        val maxDimension = max(size.width, size.height)
        val spots = listOf(
            Offset(size.width * 0.15f, size.height * 0.10f) to 1.0f,
            Offset(size.width * 0.95f, size.height * 0.28f) to 0.9f,
            Offset(size.width * 0.05f, size.height * 0.72f) to 0.95f,
            Offset(size.width * 0.85f, size.height * 0.95f) to 0.85f
        )

        spots.forEachIndexed { index, (center, radius) ->
            val color = meshColors[index % meshColors.size]
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.95f),
                        color.copy(alpha = 0.5f),
                        color.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = maxDimension * radius
                )
            )
        }

        drawRect(color = Color.Black.copy(alpha = 0.08f))
    }
}

private fun hashPalette(key: String): List<Color> {
    var hash = key.hashCode()
    if (hash == Int.MIN_VALUE) hash = 0
    val baseHue = ((hash % 360) + 360) % 360

    return listOf(
        Color.hsl(baseHue.toFloat(), 0.58f, 0.40f),
        Color.hsl(((baseHue + 42) % 360).toFloat(), 0.52f, 0.34f),
        Color.hsl(((baseHue + 198) % 360).toFloat(), 0.50f, 0.36f),
        Color.hsl(((baseHue + 272) % 360).toFloat(), 0.48f, 0.30f)
    )
}

private fun meshColor(color: Color): Color {
    return lerpColor(color, Color(0xFF0A0A0A), 0.10f)
}

private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun createBlurredBitmap(bitmap: ImageBitmap): ImageBitmap {
    val source = bitmap.asAndroidBitmap()
    val targetWidth = max(1, source.width / 10)
    val targetHeight = max(1, source.height / 10)
    val small = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    val width = small.width
    val height = small.height

    val pixels = IntArray(width * height)
    small.getPixels(pixels, 0, width, 0, 0, width, height)

    val buffer = IntArray(width * height)
    repeat(3) {
        boxBlur(pixels, buffer, width, height, radius = 2, horizontal = true)
        boxBlur(buffer, pixels, width, height, radius = 2, horizontal = false)
    }

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(pixels, 0, width, 0, 0, width, height)
    if (small !== source) {
        small.recycle()
    }

    return output.asImageBitmap()
}

private fun boxBlur(
    src: IntArray,
    dst: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean
) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val window = radius * 2 + 1

    for (line in 0 until lines) {
        var red = 0
        var green = 0
        var blue = 0

        for (i in -radius..radius) {
            val pixel = src[pixelIndex(line, i.coerceIn(0, length - 1), width, horizontal)]
            red += (pixel shr 16) and 0xFF
            green += (pixel shr 8) and 0xFF
            blue += pixel and 0xFF
        }

        for (i in 0 until length) {
            dst[pixelIndex(line, i, width, horizontal)] =
                (0xFF shl 24) or
                        ((red / window) shl 16) or
                        ((green / window) shl 8) or
                        (blue / window)

            val added = src[
                pixelIndex(line, (i + radius + 1).coerceAtMost(length - 1), width, horizontal)
            ]
            val removed = src[
                pixelIndex(line, (i - radius).coerceAtLeast(0), width, horizontal)
            ]

            red += ((added shr 16) and 0xFF) - ((removed shr 16) and 0xFF)
            green += ((added shr 8) and 0xFF) - ((removed shr 8) and 0xFF)
            blue += (added and 0xFF) - (removed and 0xFF)
        }
    }
}

private fun pixelIndex(
    line: Int,
    position: Int,
    width: Int,
    horizontal: Boolean
): Int {
    return if (horizontal) line * width + position else position * width + line
}

private suspend fun loadCoverBitmap(
    context: Context,
    uri: Uri,
    widthPx: Int,
    heightPx: Int
): ImageBitmap? {
    return runCatching {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(CoilSize(widthPx, heightPx))
            .allowHardware(false)
            .build()
        when (val result = SingletonImageLoader.get(context).execute(request)) {
            is SuccessResult -> result.image.toBitmap().asImageBitmap()
            else -> null
        }
    }.getOrNull()
}

private fun Track.artworkKey(): String {
    return "${coverArtUri}|${title ?: ""}|${artist ?: ""}"
}
