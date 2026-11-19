package com.dn0ne.player.app.presentation.components.background

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import com.dn0ne.player.app.domain.track.Track
import com.kmpalette.color
import com.kmpalette.generatePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * The shared cover-art palette used by the dynamic background, the mini player tint,
 * Home cards, and the Now Playing/lyrics surfaces.
 *
 * [swatches] holds up to four distinct source colours (for tints and cards), while
 * [colors] holds the five vivid blob colours used by the background itself.
 */
data class BackgroundPalette(
    val swatches: List<Color>,
    val colors: List<Color>,
    val base: Color,
    val highlight: Color,
    val trackUri: String?
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
                    _palette.value = cached
                    return@collectLatest
                }

                val result = extract(track) ?: return@collectLatest
                synchronized(cache) { cache[key] = result }
                _palette.value = result
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

    private suspend fun extract(track: Track): BackgroundPalette? {
        val bitmap = loadArtwork(track.coverArtUri)
        val swatches = if (bitmap != null) {
            runCatching {
                val palette = bitmap.generatePalette(Dispatchers.Default)
                palette.swatches
                    .sortedByDescending { it.population }
                    .map { it.color }
            }.getOrNull().orEmpty()
        } else {
            emptyList()
        }

        return buildPalette(track, distinctSwatches(swatches))
    }

    private fun buildPalette(track: Track, swatches: List<Color>): BackgroundPalette {
        val seedKey = "${track.title ?: ""}|${track.artist ?: ""}"
        val vivid = dynamicPaletteFrom(swatches, seedKey)
            ?: dynamicPaletteFrom(null, seedKey)
            ?: error("A palette always has a hash fallback")

        val lightest = swatches.maxByOrNull { luminance(it) }
        val highlight = lightest?.let {
            val hsv = hsvOf(it)
            Color.hsl(normalizeHue(hsv[0]), max(0.55f, hsv[1]), 0.62f)
        } ?: vivid.highlight

        return BackgroundPalette(
            swatches = swatches.take(4),
            colors = vivid.colors,
            base = vivid.base,
            highlight = highlight,
            trackUri = track.uri.toString()
        )
    }

    private suspend fun loadArtwork(uri: Uri): ImageBitmap? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(CoilSize(256, 256))
                    .allowHardware(false)
                    .build()

                when (val result = SingletonImageLoader.get(context).execute(request)) {
                    is SuccessResult -> result.image.toBitmap().asImageBitmap()
                    else -> null
                }
            }.getOrNull()
        }
    }
}

/**
 * Keeps up to four visually distinct swatches, preserving their population order.
 * Nearly grey swatches are skipped so a colourful cover leads with its chromatic hues.
 */
private fun distinctSwatches(source: List<Color>): List<Color> {
    if (source.isEmpty()) return emptyList()

    val result = ArrayList<Color>(4)
    val hues = ArrayList<Float>(4)

    for (color in source) {
        if (color.alpha <= 0.05f) continue
        val hsv = hsvOf(color)
        if (hsv[1] < 0.10f) continue
        if (hues.any { hueDistance(it, hsv[0]) < 24f }) continue

        hues.add(hsv[0])
        result.add(color)
        if (result.size == 4) break
    }

    if (result.isNotEmpty()) return result

    // Grey-only cover: keep a few source colours so the hash fallback can still be seeded.
    return source.take(4)
}
