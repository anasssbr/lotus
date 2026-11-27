package com.dn0ne.player.app.presentation.components.background

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import com.dn0ne.player.app.domain.track.Track
import com.kmpalette.color
import com.kmpalette.generatePalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val DEBUG_TAG = "PaletteDebug"

private const val MIN_SWATCH_PIXELS = 16
private const val DOMINANT_MIN_SHARE = 0.03f
private const val MULTICOLOR_SHARE = 0.25f
private const val MIN_CHROMA = 0.12f
private const val MIN_SWATCH_LIGHTNESS = 0.08f
private const val MAX_SWATCH_LIGHTNESS = 0.88f
private const val BLOB_MIN_LIGHTNESS = 0.30f
private const val BLOB_MAX_LIGHTNESS = 0.58f

/**
 * The shared cover-art palette used by the dynamic background, the mini player tint,
 * Home cards, and the Now Playing/lyrics surfaces.
 *
 * [swatches] holds up to four distinct source colours (for tints and cards), while
 * [colors] holds the five blob colours used by the background itself. Every colour in
 * [colors] comes from the cover: hues are never rotated or invented.
 */
data class BackgroundPalette(
    val swatches: List<Color>,
    val colors: List<Color>,
    val base: Color,
    val highlight: Color,
    val trackUri: String?,
    val rawSwatches: List<Color> = emptyList(),
    val rawSwatchShares: List<Float> = emptyList()
) {
    /** Best single colour for card tints and the mini player/Now Playing base. */
    val primary: Color
        get() = swatches.firstOrNull() ?: colors.firstOrNull() ?: base
}

/**
 * Observes the current track and extracts its palette off the main thread.
 *
 * This is a Koin singleton, not a screen-scoped state: it keeps following the player
 * (notification, headphones, auto-advance, mini player) even while no screen asks for
 * colours. Results are cached per track URI in a small LRU and the next queue item is
 * preloaded when known.
 */
class PaletteProvider(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _currentTrack = MutableStateFlow<Track?>(null)
    private val _palette = MutableStateFlow<BackgroundPalette?>(null)
    val palette: StateFlow<BackgroundPalette?> = _palette.asStateFlow()

    private val cache = object : LinkedHashMap<String, BackgroundPalette>(20, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, BackgroundPalette>?
        ): Boolean = size > 20
    }

    private val preloading = mutableSetOf<String>()

    init {
        scope.launch {
            _currentTrack.collectLatest { track ->
                if (track == null) {
                    _palette.value = null
                    return@collectLatest
                }

                val key = track.uri.toString()
                val cached = synchronized(cache) { cache[key] }
                if (cached != null) {
                    publish(track, cached)
                    return@collectLatest
                }

                val result = extract(track) ?: return@collectLatest
                synchronized(cache) { cache[key] = result }
                publish(track, result)
            }
        }
    }

    /** Called for every media-item change, independent of any screen. */
    fun setCurrentTrack(track: Track?) {
        _currentTrack.value = track
    }

    /** Warms the cache for the next queue item without changing the current palette. */
    fun preload(track: Track?) {
        if (track == null) return

        val key = track.uri.toString()
        synchronized(cache) {
            if (cache.containsKey(key)) return
        }
        synchronized(preloading) {
            if (!preloading.add(key)) return
        }

        scope.launch {
            try {
                val result = extract(track) ?: return@launch
                synchronized(cache) { cache[key] = result }
            } finally {
                synchronized(preloading) { preloading.remove(key) }
            }
        }
    }

    /**
     * Publishes only a palette that still belongs to the selected track. A slow
     * extraction for a track that has already been skipped is dropped instead of
     * briefly showing the previous cover's colours.
     */
    private fun publish(track: Track, palette: BackgroundPalette) {
        if (_currentTrack.value?.uri != track.uri) return

        _palette.value = palette

        val rawText = palette.rawSwatches
            .zip(palette.rawSwatchShares)
            .joinToString { (color, share) -> "${color.hex()}(${formatShare(share)})" }

        Log.d(
            DEBUG_TAG,
            "track=\"${track.title ?: track.uri}\" " +
                "raw=[$rawText] " +
                "blobs=[${palette.colors.joinToString { it.hex() }}] " +
                "base=${palette.base.hex()} highlight=${palette.highlight.hex()}"
        )
    }

    private suspend fun extract(track: Track): BackgroundPalette? {
        val cover = loadArtwork(track.coverArtUri) ?: return hashPalette(track)

        val raw = try {
            cover.image
                .generatePalette(Dispatchers.Default)
                .swatches
                .map { CoverSwatch(it.color, it.population) }
                .sortedByDescending { it.population }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

        val swatchTotal = raw.sumOf { it.population }.coerceAtLeast(1)
        val built = buildPalette(raw, swatchTotal, cover.average)

        return BackgroundPalette(
            swatches = built.swatches,
            colors = built.colors,
            base = built.base,
            highlight = built.highlight,
            trackUri = track.uri.toString(),
            rawSwatches = raw.map { it.color },
            rawSwatchShares = raw.map {
                it.population.toFloat() / swatchTotal
            }
        )
    }

    /**
     * Hash palette is only used when the track has no artwork at all.
     */
    private fun hashPalette(track: Track): BackgroundPalette {
        val seed = "${track.title ?: ""}|${track.artist ?: ""}"
        val fallback = dynamicPaletteFrom(null, seed)
            ?: error("A palette always has a hash fallback")

        return BackgroundPalette(
            swatches = fallback.colors.distinct().take(4),
            colors = fallback.colors,
            base = fallback.base,
            highlight = fallback.highlight,
            trackUri = track.uri.toString(),
            rawSwatches = emptyList(),
            rawSwatchShares = emptyList()
        )
    }

    private suspend fun loadArtwork(uri: Uri): CoverImage? {
        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    // Load a large bitmap so the center square (minus 8% per side)
                    // still has at least 256 px of real cover pixels.
                    .size(CoilSize(640, 640))
                    .allowHardware(false)
                    .build()

                val result = SingletonImageLoader.get(context).execute(request)
                if (result !is SuccessResult) return@withContext null

                val cropped = cropForExtraction(result.image.toBitmap())
                CoverImage(
                    image = cropped.asImageBitmap(),
                    width = cropped.width,
                    height = cropped.height,
                    average = averageColor(cropped)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }
}

private data class BuiltPalette(
    val swatches: List<Color>,
    val colors: List<Color>,
    val base: Color,
    val highlight: Color
)

private data class SwatchInfo(
    val color: Color,
    val population: Int,
    val share: Float,
    val hue: Float,
    val saturation: Float,
    val lightness: Float
) {
    val score: Float get() = share * saturation

    val isUsable: Boolean
        get() = lightness in MIN_SWATCH_LIGHTNESS..MAX_SWATCH_LIGHTNESS &&
            saturation >= MIN_CHROMA
}

/**
 * Picks the dominant colour from real cover swatches.
 *
 * White/near-black/grey swatches are skipped when a usable swatch with at least 3%
 * of the pixels remains. Candidates are ranked by population share x saturation, so
 * a vivid dark red beats a larger but muted grey. If no usable swatch covers more
 * than ~25% of the image, the cover is treated as multicolour.
 */
private fun buildPalette(
    raw: List<CoverSwatch>,
    swatchTotal: Int,
    average: Color
): BuiltPalette {
    val source = raw
        .filter { it.color.alpha > 0.05f && it.population >= MIN_SWATCH_PIXELS }
        .map { it.toInfo(swatchTotal) }
        .sortedByDescending { it.share }
        .ifEmpty { listOf(CoverSwatch(average, swatchTotal).toInfo(swatchTotal)) }

    val valid = source.filter { it.isUsable }
    val significant = source.filter { it.share >= DOMINANT_MIN_SHARE }
    val validTotalShare = valid.sumOf { it.share.toDouble() }.toFloat()

    // Grey/white/black cover: there is no real colour to follow.
    if (valid.size < 2 || validTotalShare < DOMINANT_MIN_SHARE) {
        return buildNeutralPalette(significant.ifEmpty { source }, source)
    }

    // Quantisation can split one real colour into many small swatches, so the
    // single/multi decision is made on the strongest individual swatch: only a swatch
    // that owns more than ~25% of the colour mass is allowed to drive every blob.
    val dominant = valid.maxByOrNull { it.score } ?: valid.first()

    return if (dominant.share > MULTICOLOR_SHARE) {
        val ordered = listOf(dominant) + valid
            .filter { it.color != dominant.color }
            .sortedByDescending { it.score }
        buildDominantPalette(distinctSwatches(ordered))
    } else {
        buildMulticolorPalette(valid)
    }
}

/**
 * One clear dominant colour: three blobs use it at slightly different lightness,
 * one uses the second swatch and one uses the third.
 */
private fun buildDominantPalette(ordered: List<SwatchInfo>): BuiltPalette {
    val dominant = ordered.first()
    val center = dominant.lightness.coerceIn(0.34f, 0.52f)

    val dominantColors = listOf(
        faithful(dominant, center),
        faithful(dominant, center - 0.06f),
        faithful(dominant, center + 0.05f)
    )
    val second = ordered.getOrNull(1)?.let { faithful(it) }
        ?: faithful(dominant, center + 0.11f)
    val third = ordered.getOrNull(2)?.let { faithful(it) }
        ?: faithful(dominant, center - 0.11f)

    return BuiltPalette(
        swatches = ordered.take(4).map { it.color },
        colors = listOf(
            dominantColors[0],
            second,
            dominantColors[1],
            third,
            dominantColors[2]
        ),
        base = darkBase(dominant),
        highlight = lighter(dominant)
    )
}

/**
 * Multicolour cover: no single swatch owns more than ~25% of the pixels. Spread the
 * four most saturated distinct swatches evenly across the five blobs; the extra blob
 * repeats the strongest one.
 */
private fun buildMulticolorPalette(valid: List<SwatchInfo>): BuiltPalette {
    val strongest = valid.maxByOrNull { it.saturation } ?: valid.first()
    val ordered = listOf(strongest) + valid
        .filter { it.color != strongest.color }
        .sortedByDescending { it.saturation }

    val distinct = distinctSwatches(ordered).ifEmpty { listOf(strongest) }
    val colors = List(5) { index -> faithful(distinct[index % distinct.size]) }

    return BuiltPalette(
        swatches = distinct.map { it.color },
        colors = colors,
        base = darkBase(strongest),
        highlight = lighter(strongest)
    )
}

/**
 * Grey/black/white cover: stay neutral, but keep a slight tint from the most
 * saturated pixel. No hue is invented.
 */
private fun buildNeutralPalette(
    pool: List<SwatchInfo>,
    all: List<SwatchInfo>
): BuiltPalette {
    val tintSwatch = all.maxByOrNull { it.saturation } ?: pool.first()
    val tintHue = normalizeHue(tintSwatch.hue)
    val tintSaturation = tintSwatch.saturation.coerceIn(0.02f, 0.12f)

    fun neutral(lightness: Float): Color {
        return Color.hsl(
            tintHue,
            tintSaturation,
            lightness.coerceIn(BLOB_MIN_LIGHTNESS, BLOB_MAX_LIGHTNESS)
        )
    }

    val dominantLight = pool.first().lightness
    val center = dominantLight.coerceIn(0.34f, 0.52f)
    val secondLight = pool.getOrNull(1)?.lightness
    val thirdLight = pool.getOrNull(2)?.lightness

    return BuiltPalette(
        swatches = pool.take(4).map { it.color },
        colors = listOf(
            neutral(center),
            neutral(secondLight ?: (center + 0.10f)),
            neutral(center - 0.05f),
            neutral(thirdLight ?: (center + 0.05f)),
            neutral(center + 0.02f)
        ),
        base = Color.hsl(tintHue, tintSaturation.coerceAtMost(0.08f), 0.08f),
        highlight = Color.hsl(tintHue, tintSaturation.coerceAtMost(0.10f), 0.62f)
    )
}

/**
 * Keeps the swatch's own hue. A dark swatch is raised into the 0.30-0.38 lightness
 * band instead of being desaturated; because a near-black pixel has almost no
 * measurable chroma, its saturation is also floored so a dark red stays a vivid dark
 * red instead of turning brown or grey.
 */
private fun faithful(info: SwatchInfo, lightness: Float? = null): Color {
    val targetLight = (lightness ?: info.lightness)
        .coerceIn(BLOB_MIN_LIGHTNESS, BLOB_MAX_LIGHTNESS)
    val saturationFloor = if (info.lightness < BLOB_MIN_LIGHTNESS) 0.5f else 0f
    val saturation = (info.saturation * 1.15f).coerceIn(saturationFloor, 0.9f)
    return Color.hsl(normalizeHue(info.hue), saturation, targetLight)
}

/**
 * Very dark version of the strongest hue. Saturation stays at least 0.5 so the base
 * never mixes grey or black into the blobs.
 */
private fun darkBase(info: SwatchInfo): Color {
    val saturation = max(0.5f, (info.saturation * 1.15f).coerceAtMost(0.9f))
    return Color.hsl(normalizeHue(info.hue), saturation, 0.08f)
}

/** Brighter version of the strongest hue for the top highlight. */
private fun lighter(info: SwatchInfo): Color {
    val saturation = max(0.5f, (info.saturation * 1.05f).coerceAtMost(0.9f))
    return Color.hsl(normalizeHue(info.hue), saturation, 0.60f)
}

private fun distinctSwatches(source: List<SwatchInfo>): List<SwatchInfo> {
    val result = ArrayList<SwatchInfo>(4)
    for (candidate in source) {
        if (result.all { isDistinct(it, candidate) }) {
            result.add(candidate)
            if (result.size == 4) break
        }
    }
    return result
}

private fun isDistinct(a: SwatchInfo, b: SwatchInfo): Boolean {
    return hueDistance(a.hue, b.hue) >= 25f || abs(a.lightness - b.lightness) >= 0.12f
}

private fun CoverSwatch.toInfo(swatchTotal: Int): SwatchInfo {
    val hsl = hslOf(color)
    return SwatchInfo(
        color = color,
        population = population,
        share = population.toFloat() / swatchTotal.coerceAtLeast(1),
        hue = hsl[0],
        saturation = hsl[1],
        lightness = hsl[2]
    )
}

/**
 * RGB -> HSL. The palette works in HSL so dark saturated colours keep their
 * saturation when only their lightness is raised.
 */
private fun hslOf(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val lightness = (max + min) / 2f

    if (max == min) return floatArrayOf(0f, 0f, lightness)

    val delta = max - min
    val saturation = if (lightness > 0.5f) {
        delta / (2f - max - min)
    } else {
        delta / (max + min)
    }
    val hue = when (max) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return floatArrayOf(normalizeHue(hue), saturation, lightness)
}

/**
 * Center-square crop, then 8% off each side, so side bars and corner logos do not
 * dominate the palette. Tiny results are scaled up to the 256 px extraction floor.
 */
private fun cropForExtraction(bitmap: Bitmap): Bitmap {
    val side = min(bitmap.width, bitmap.height)
    val left = (bitmap.width - side) / 2
    val top = (bitmap.height - side) / 2

    val square = if (left == 0 && top == 0 && side == bitmap.width && side == bitmap.height) {
        bitmap
    } else {
        Bitmap.createBitmap(bitmap, left, top, side, side)
    }

    val inset = (side * 0.08f).toInt()
    val inner = side - inset * 2
    val cropped = if (inset > 0 && inner >= 64) {
        Bitmap.createBitmap(square, inset, inset, inner, inner)
    } else {
        square
    }

    return if (cropped.width < 256 || cropped.height < 256) {
        Bitmap.createScaledBitmap(cropped, 256, 256, true)
    } else {
        cropped
    }
}

private fun averageColor(bitmap: Bitmap): Color {
    val scaled = Bitmap.createScaledBitmap(bitmap, 1, 1, true)
    return try {
        Color(scaled.getPixel(0, 0))
    } finally {
        if (scaled !== bitmap) scaled.recycle()
    }
}

private fun formatShare(share: Float): String =
    String.format(Locale.US, "%.1f%%", share * 100f)

private fun Color.hex(): String = String.format(Locale.US, "#%06X", 0xFFFFFF and toArgb())

private data class CoverSwatch(val color: Color, val population: Int)

private class CoverImage(
    val image: ImageBitmap,
    val width: Int,
    val height: Int,
    val average: Color
)
