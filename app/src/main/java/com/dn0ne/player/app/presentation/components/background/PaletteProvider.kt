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
    val rawSwatches: List<Color> = emptyList()
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

        Log.d(
            DEBUG_TAG,
            "track=\"${track.title ?: track.uri}\" " +
                "raw=[${palette.rawSwatches.joinToString { it.hex() }}] " +
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

        val selected = selectSwatches(raw, cover.width * cover.height)
        val effective = selected.ifEmpty { listOf(CoverSwatch(cover.average, 1)) }
        return faithfulPalette(track, effective, raw.map { it.color })
    }

    /**
     * Builds the five blob colours plus base/highlight from cover colours only.
     * Three blobs use the dominant swatch at slightly different lightness, one uses
     * the second swatch and one uses the third.
     */
    private fun faithfulPalette(
        track: Track,
        selected: List<CoverSwatch>,
        raw: List<Color>
    ): BackgroundPalette {
        val dominantSaturation = hsvOf(selected.first().color)[1]
        val (colors, base, highlight) = if (dominantSaturation < 0.12f) {
            neutralPalette(selected)
        } else {
            chromaticPalette(selected)
        }

        return BackgroundPalette(
            swatches = selected.map { it.color },
            colors = colors,
            base = base,
            highlight = highlight,
            trackUri = track.uri.toString(),
            rawSwatches = raw
        )
    }

    private fun chromaticPalette(
        selected: List<CoverSwatch>
    ): Triple<List<Color>, Color, Color> {
        val dominant = selected.first().color
        val dominantHsv = hsvOf(dominant)
        val center = dominantHsv[2].coerceIn(0.34f, 0.52f)

        val dominantColors = listOf(
            faithful(dominant, center),
            faithful(dominant, center - 0.06f),
            faithful(dominant, center + 0.05f)
        )
        val second = selected.getOrNull(1)?.let { faithful(it.color) }
            ?: faithful(dominant, center + 0.11f)
        val third = selected.getOrNull(2)?.let { faithful(it.color) }
            ?: faithful(dominant, center - 0.11f)

        val colors = listOf(
            dominantColors[0],
            second,
            dominantColors[1],
            third,
            dominantColors[2]
        )

        // Very dark tint of the dominant hue, never pure black.
        val base = Color.hsl(
            normalizeHue(dominantHsv[0]),
            (dominantHsv[1] * 0.55f).coerceIn(0.10f, 0.45f),
            0.08f
        )

        // Brighter version of the same hue for the top highlight.
        val highlight = Color.hsl(
            normalizeHue(dominantHsv[0]),
            (dominantHsv[1] * 1.05f).coerceIn(0.20f, 0.75f),
            0.60f
        )

        return Triple(colors, base, highlight)
    }

    /**
     * Grey/black cover: stay neutral, but keep a slight tint from the most saturated
     * pixel. No hue is invented.
     */
    private fun neutralPalette(
        selected: List<CoverSwatch>
    ): Triple<List<Color>, Color, Color> {
        val tintSwatch = selected.maxByOrNull { hsvOf(it.color)[1] } ?: selected.first()
        val tintHsv = hsvOf(tintSwatch.color)
        val tintHue = normalizeHue(tintHsv[0])
        val tintSaturation = tintHsv[1].coerceIn(0.02f, 0.12f)

        fun neutral(lightness: Float): Color {
            return Color.hsl(
                tintHue,
                tintSaturation,
                lightness.coerceIn(0.30f, 0.58f)
            )
        }

        val dominantLight = hsvOf(selected.first().color)[2]
        val center = dominantLight.coerceIn(0.34f, 0.52f)
        val secondLight = selected.getOrNull(1)?.let { hsvOf(it.color)[2] }
        val thirdLight = selected.getOrNull(2)?.let { hsvOf(it.color)[2] }

        val colors = listOf(
            neutral(center),
            neutral(secondLight ?: (center + 0.10f)),
            neutral(center - 0.05f),
            neutral(thirdLight ?: (center + 0.05f)),
            neutral(center + 0.02f)
        )

        val base = Color.hsl(tintHue, tintSaturation.coerceAtMost(0.08f), 0.08f)
        val highlight = Color.hsl(tintHue, tintSaturation.coerceAtMost(0.10f), 0.62f)

        return Triple(colors, base, highlight)
    }

    /**
     * Keeps a swatch's own hue, applies only a gentle saturation boost and clamps the
     * final lightness so blobs stay readable.
     */
    private fun faithful(color: Color, lightness: Float? = null): Color {
        val hsv = hsvOf(color)
        return Color.hsl(
            normalizeHue(hsv[0]),
            (hsv[1] * 1.15f).coerceIn(0f, 0.9f),
            (lightness ?: hsv[2]).coerceIn(0.30f, 0.58f)
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
            rawSwatches = emptyList()
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

/**
 * Up to four distinct swatches ordered by population. Two swatches are considered
 * distinct when their hues differ by ~25 degrees or their lightness differs clearly.
 * A grey cover keeps its strongest tones without hue filtering.
 */
private fun selectSwatches(source: List<CoverSwatch>, totalPixels: Int): List<CoverSwatch> {
    if (source.isEmpty()) return emptyList()

    val minPopulation = max(16, (totalPixels * 0.005f).toInt())
    val candidates = source
        .filter { it.color.alpha > 0.05f && it.population >= minPopulation }
        .sortedByDescending { it.population }
        .ifEmpty {
            source.filter { it.color.alpha > 0.05f }
                .sortedByDescending { it.population }
        }

    if (candidates.isEmpty()) return emptyList()

    if (hsvOf(candidates.first().color)[1] < 0.12f) {
        return candidates.take(4)
    }

    val result = ArrayList<CoverSwatch>(4)
    for (candidate in candidates) {
        if (result.none { isDistinct(it.color, candidate.color) }) {
            result.add(candidate)
            if (result.size == 4) break
        }
    }
    return result
}

private fun isDistinct(a: Color, b: Color): Boolean {
    val hsvA = hsvOf(a)
    val hsvB = hsvOf(b)
    return hueDistance(hsvA[0], hsvB[0]) >= 25f || abs(hsvA[2] - hsvB[2]) >= 0.12f
}

private fun Color.hex(): String = String.format(Locale.US, "#%06X", 0xFFFFFF and toArgb())

private data class CoverSwatch(val color: Color, val population: Int)

private class CoverImage(
    val image: ImageBitmap,
    val width: Int,
    val height: Int,
    val average: Color
)
