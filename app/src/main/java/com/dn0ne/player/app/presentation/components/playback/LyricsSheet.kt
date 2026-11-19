package com.dn0ne.player.app.presentation.components.playback

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.dn0ne.player.R
import com.dn0ne.player.app.domain.lyrics.LyricsWord
import com.dn0ne.player.app.presentation.components.CoverArt
import com.dn0ne.player.app.presentation.components.ProviderText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlin.math.abs

private val lyricsModePreferences = mutableStateMapOf<String, Boolean>()
private var wordHighlightEnabled by mutableStateOf(true)

private const val LYRICS_LOOKAHEAD_MS = 150L
private const val LYRICS_OFFSET_PREFS = "settings"
private const val LYRICS_OFFSET_KEY_PREFIX = "lyrics-offset-"
private const val LYRICS_WORD_SPEED_KEY = "lyrics-word-speed"

private const val WORD_SPEED_SLOWER = 1.15f
private const val WORD_SPEED_NORMAL = 1f
private const val WORD_SPEED_FASTER = 0.85f

private const val ACTIVE_LINE_SCALE = 1.04f

private fun lyricsOffsetKey(trackKey: String) = LYRICS_OFFSET_KEY_PREFIX + trackKey

private fun readLyricsOffset(context: android.content.Context, trackKey: String): Long {
    if (trackKey.isEmpty()) return 0L
    return context.getSharedPreferences(LYRICS_OFFSET_PREFS, android.content.Context.MODE_PRIVATE)
        .getLong(lyricsOffsetKey(trackKey), 0L)
}

private fun writeLyricsOffset(
    context: android.content.Context,
    trackKey: String,
    offsetMs: Long
) {
    if (trackKey.isEmpty()) return
    context.getSharedPreferences(LYRICS_OFFSET_PREFS, android.content.Context.MODE_PRIVATE)
        .edit()
        .putLong(lyricsOffsetKey(trackKey), offsetMs)
        .apply()
}

private fun readWordSpeed(context: android.content.Context): Float {
    return context.getSharedPreferences(LYRICS_OFFSET_PREFS, android.content.Context.MODE_PRIVATE)
        .getFloat(LYRICS_WORD_SPEED_KEY, WORD_SPEED_NORMAL)
}

private fun writeWordSpeed(context: android.content.Context, factor: Float) {
    context.getSharedPreferences(LYRICS_OFFSET_PREFS, android.content.Context.MODE_PRIVATE)
        .edit()
        .putFloat(LYRICS_WORD_SPEED_KEY, factor)
        .apply()
}

private fun activeLineIndex(
    lines: List<Pair<Int, String>>,
    positionMs: Long
): Int {
    var low = 0
    var high = lines.lastIndex
    var result = 0

    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lines[mid].first <= positionMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    return result
}

private fun formatOffset(offsetMs: Long): String {
    val seconds = offsetMs / 1000.0
    val sign = if (offsetMs > 0) "+" else ""
    return "$sign${String.format(java.util.Locale.US, "%.1f", seconds)}s"
}

@Composable
fun LyricsSheet(
    playbackStateFlow: StateFlow<PlaybackState>,
    lyricsTextStyle: TextStyle,
    baseColor: Color,
    containerColor: Color,
    contentColor: Color,
    onBackClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBackClick()
    }

    val playbackState by playbackStateFlow.collectAsState()
    val lyrics by remember {
        derivedStateOf {
            playbackState.lyrics
        }
    }
    val isLoadingLyrics by remember {
        derivedStateOf {
            playbackState.isLoadingLyrics
        }
    }
    val currentTrack = playbackState.currentTrack
    val context = LocalContext.current

    val trackKey = currentTrack?.uri?.toString().orEmpty()
    val hasSynced = lyrics?.synced?.isNotEmpty() == true

    val livePosition = remember {
        mutableLongStateOf(playbackState.position)
    }
    val isPlaying = playbackState.isPlaying
    val currentTrackUri = currentTrack?.uri
    LaunchedEffect(isPlaying, currentTrackUri) {
        if (!isPlaying) {
            livePosition.longValue = playbackState.position
            return@LaunchedEffect
        }

        while (true) {
            withFrameNanos { }
            livePosition.longValue = positionProvider()
        }
    }

    var offsetMs by remember(trackKey) {
        mutableLongStateOf(readLyricsOffset(context, trackKey))
    }
    LaunchedEffect(trackKey) {
        offsetMs = readLyricsOffset(context, trackKey)
    }

    var wordSpeed by remember {
        mutableFloatStateOf(readWordSpeed(context))
    }

    var showSynced by remember(trackKey) {
        mutableStateOf(lyricsModePreferences[trackKey] ?: hasSynced)
    }
    LaunchedEffect(trackKey, hasSynced) {
        val saved = lyricsModePreferences[trackKey]
        showSynced = if (saved != null) saved && hasSynced else hasSynced
    }

    var showModeMenu by remember {
        mutableStateOf(false)
    }
    var showTimingMenu by remember {
        mutableStateOf(false)
    }

    val lyricStyle = remember(lyricsTextStyle) {
        lyricsTextStyle.copy(
            textAlign = TextAlign.Start,
            fontWeight = FontWeight.Bold,
            lineHeight = lyricsTextStyle.fontSize * 1.5f
        )
    }

    val syncedLines = remember(lyrics) {
        lyrics?.synced?.let { raw ->
            if ((raw.firstOrNull()?.first ?: 0) > 3000) {
                listOf(0 to "") + raw
            } else raw
        }
    }

    val syncedWordTimes = remember(lyrics, syncedLines) {
        val rawLines = lyrics?.synced
        val rawWords = lyrics?.syncedWords
        if (rawLines == null || rawWords == null || syncedLines == null) {
            null
        } else if (syncedLines.size == rawLines.size + 1) {
            listOf<List<LyricsWord>?>(null) + rawWords
        } else {
            rawWords
        }
    }

    val activeIndex by remember(syncedLines) {
        derivedStateOf {
            syncedLines?.let {
                activeLineIndex(
                    lines = it,
                    positionMs = livePosition.longValue + LYRICS_LOOKAHEAD_MS + offsetMs
                )
            } ?: 0
        }
    }

    var tappedIndex by remember(syncedLines) {
        mutableStateOf(-1)
    }
    LaunchedEffect(syncedLines, activeIndex) {
        if (tappedIndex >= 0 && syncedLines != null) {
            val tappedTime = syncedLines.getOrNull(tappedIndex)?.first ?: 0
            if (activeIndex >= tappedIndex || livePosition.longValue >= tappedTime) {
                tappedIndex = -1
            }
        }
    }
    val displayedIndex = if (tappedIndex >= 0) tappedIndex else activeIndex

    val listState = rememberLazyListState()
    var pullDown by remember {
        mutableFloatStateOf(0f)
    }
    val closeThreshold = with(LocalDensity.current) {
        110.dp.toPx()
    }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && !listState.canScrollBackward) {
                    pullDown += available.y
                } else if (available.y < 0f) {
                    pullDown = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullDown > closeThreshold) {
                    onBackClick()
                }
                pullDown = 0f
                return Velocity.Zero
            }
        }
    }

    var autoFollow by remember {
        mutableStateOf(true)
    }
    var forceAlign by remember {
        mutableStateOf(false)
    }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    val isScrollInProgress = listState.isScrollInProgress

    LaunchedEffect(isDragged) {
        if (isDragged) {
            autoFollow = false
        }
    }

    LaunchedEffect(isScrollInProgress, isDragged, autoFollow) {
        if (autoFollow || isScrollInProgress || isDragged) {
            return@LaunchedEffect
        }
        delay(8000)
        autoFollow = true
        forceAlign = true
    }

    LaunchedEffect(lyrics) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(showSynced, displayedIndex, autoFollow, forceAlign, syncedLines) {
        if (!showSynced || syncedLines == null || !autoFollow) {
            return@LaunchedEffect
        }
        val viewport = snapshotFlow {
            listState.layoutInfo.viewportSize.height
        }.first { it > 0 }
        val targetOffset = viewport * 0.30f

        val visible = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == displayedIndex }
        if (visible == null) {
            listState.animateScrollToItem(
                index = displayedIndex,
                scrollOffset = -targetOffset.toInt()
            )
            forceAlign = false
            return@LaunchedEffect
        }

        val delta = visible.offset - targetOffset
        val shouldScroll = if (forceAlign) {
            abs(delta) > 1f
        } else {
            delta > 8f
        }

        if (shouldScroll) {
            listState.animateScrollBy(
                value = delta,
                animationSpec = tween(
                    durationMillis = 450,
                    easing = FastOutSlowInEasing
                )
            )
        }
        forceAlign = false
    }

    val selectMode: (Boolean) -> Unit = { synced ->
        showSynced = synced
        lyricsModePreferences[trackKey] = synced
        showModeMenu = false
    }

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(baseColor)
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) {
                maxWidth.roundToPx()
            }
            val heightPx = with(density) {
                maxHeight.roundToPx()
            }

            if (currentTrack != null) {
                NowPlayingArtworkBackground(
                    track = currentTrack,
                    screenWidthPx = widthPx,
                    screenHeightPx = heightPx,
                    baseColor = baseColor,
                    dim = 0.27f,
                    dimOnDarkArtwork = 0.15f,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(containerColor)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.12f),
                            0.35f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.45f)
                        )
                    )
            )

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.30f))
                            .clickable(onClick = onBackClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = context.resources.getString(R.string.close_lyrics_sheet),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    currentTrack?.let { track ->
                        CoverArt(
                            uri = track.coverArtUri,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (lyrics == null) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = context.resources.getString(
                                    if (isLoadingLyrics) {
                                        R.string.loading_lyrics
                                    } else {
                                        R.string.cant_find_lyrics
                                    }
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center
                            )

                            if (isLoadingLyrics) {
                                Spacer(modifier = Modifier.height(14.dp))

                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    val topFade = 48.dp.toPx()
                                    val bottomFade = 80.dp.toPx()
                                    val height = size.height
                                    val topStop = (topFade / height).coerceIn(0f, 0.4f)
                                    val bottomStop =
                                        (1f - bottomFade / height).coerceIn(0.6f, 1f)
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            topStop to Color.Black,
                                            bottomStop to Color.Black,
                                            1f to Color.Transparent
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                                .nestedScroll(nestedScrollConnection),
                            contentPadding = PaddingValues(
                                start = 24.dp,
                                end = 24.dp,
                                top = 48.dp,
                                bottom = 180.dp
                            )
                        ) {
                            if (showSynced && syncedLines != null) {
                                itemsIndexed(
                                    items = syncedLines,
                                    key = { index, (time, _) -> "synced-$index-$time" }
                                ) { index, (time, text) ->
                                    if (text.isBlank()) {
                                        GapDots(
                                            active = index == displayedIndex,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        val startMs = time.toLong()
                                        val nextStart = syncedLines.getOrNull(index + 1)
                                            ?.first
                                            ?.toLong()
                                        val endMs = ((nextStart ?: (startMs + 6000)) - 100)
                                            .coerceIn(startMs + 200, startMs + 6000)

                                        SyncedLyricsLine(
                                            text = text,
                                            style = lyricStyle,
                                            distance = abs(index - displayedIndex),
                                            livePosition = livePosition,
                                            positionShiftMs = LYRICS_LOOKAHEAD_MS + offsetMs,
                                            startTimeMs = startMs,
                                            endTimeMs = endMs,
                                            wordTimes = syncedWordTimes?.getOrNull(index),
                                            wordSpeedFactor = wordSpeed,
                                            karaokeEnabled = wordHighlightEnabled,
                                            onClick = {
                                                tappedIndex = index
                                                autoFollow = true
                                                forceAlign = true
                                                onSeekTo(time.toLong())
                                            },
                                            modifier = Modifier.fillMaxWidth(
                                                1f / ACTIVE_LINE_SCALE
                                            )
                                        )
                                    }
                                }
                            } else {
                                lyrics?.plain?.let { plain ->
                                    itemsIndexed(
                                        items = plain,
                                        key = { index, line -> "plain-$index-$line" }
                                    ) { _, line ->
                                        Text(
                                            text = line,
                                            style = lyricStyle.copy(
                                                shadow = lyricsShadow()
                                            ),
                                            color = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            if (lyrics?.areFromRemote == true) {
                                item(key = "provider") {
                                    ProviderText(
                                        providerText = context.resources.getString(R.string.lyrics_provided_by),
                                        uri = context.resources.getString(R.string.lrclib_uri),
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (lyrics != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!autoFollow) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable {
                                    autoFollow = true
                                    forceAlign = true
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text = context.resources.getString(R.string.lyrics_now),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(54.dp))

                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .pointerInput(trackKey) {
                                    detectTapGestures(
                                        onTap = {
                                            showModeMenu = true
                                        },
                                        onLongPress = {
                                            showTimingMenu = true
                                        }
                                    )
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = context.resources.getString(
                                    if (showSynced) R.string.synced else R.string.plain
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showModeMenu,
                            onDismissRequest = {
                                showModeMenu = false
                            },
                            modifier = Modifier.background(Color(0xFF161616))
                        ) {
                            if (hasSynced) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = context.resources.getString(R.string.synced),
                                            color = Color.White
                                        )
                                    },
                                    onClick = {
                                        selectMode(true)
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = context.resources.getString(R.string.plain),
                                        color = Color.White
                                    )
                                },
                                onClick = {
                                    selectMode(false)
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = context.resources.getString(
                                            R.string.word_highlight,
                                            context.resources.getString(
                                                if (wordHighlightEnabled) {
                                                    R.string.on
                                                } else {
                                                    R.string.off
                                                }
                                            )
                                        ),
                                        color = Color.White
                                    )
                                },
                                onClick = {
                                    wordHighlightEnabled = !wordHighlightEnabled
                                }
                            )
                        }

                        DropdownMenu(
                            expanded = showTimingMenu,
                            onDismissRequest = {
                                showTimingMenu = false
                            },
                            modifier = Modifier.background(Color(0xFF161616))
                        ) {
                            Text(
                                text = context.resources.getString(
                                    R.string.lyrics_timing,
                                    formatOffset(offsetMs)
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 10.dp,
                                    bottom = 4.dp
                                )
                            )

                            listOf(-500L, -100L, 100L, 500L).forEach { delta ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = formatOffset(delta),
                                            color = Color.White
                                        )
                                    },
                                    onClick = {
                                        val newOffset = (offsetMs + delta)
                                            .coerceIn(-3000L, 3000L)
                                        offsetMs = newOffset
                                        writeLyricsOffset(context, trackKey, newOffset)
                                    }
                                )
                            }

                            Text(
                                text = context.resources.getString(R.string.word_speed),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 10.dp,
                                    bottom = 4.dp
                                )
                            )

                            listOf(
                                WORD_SPEED_SLOWER to R.string.word_speed_slower,
                                WORD_SPEED_NORMAL to R.string.word_speed_normal,
                                WORD_SPEED_FASTER to R.string.word_speed_faster
                            ).forEach { (factor, labelRes) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = context.resources.getString(labelRes),
                                            color = if (wordSpeed == factor) {
                                                Color.White
                                            } else {
                                                Color.White.copy(alpha = 0.6f)
                                            }
                                        )
                                    },
                                    onClick = {
                                        wordSpeed = factor
                                        writeWordSpeed(context, factor)
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = context.resources.getString(R.string.reset),
                                        color = Color.White
                                    )
                                },
                                onClick = {
                                    offsetMs = 0L
                                    writeLyricsOffset(context, trackKey, 0L)
                                    showTimingMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun lyricsShadow(): Shadow {
    return Shadow(
        color = Color.Black.copy(alpha = 0.55f),
        offset = Offset(0f, 2f),
        blurRadius = 6f
    )
}

@Composable
private fun SyncedLyricsLine(
    text: String,
    style: TextStyle,
    distance: Int,
    livePosition: State<Long>,
    positionShiftMs: Long,
    startTimeMs: Long,
    endTimeMs: Long,
    wordTimes: List<LyricsWord>?,
    wordSpeedFactor: Float,
    karaokeEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = distance == 0
    val targetAlpha = when (distance) {
        0 -> 1f
        1 -> 0.80f
        2 -> 0.65f
        3 -> 0.55f
        else -> 0.50f
    }
    val targetBlur = distance.coerceIn(0, 4).toFloat()

    val lineAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "lyrics-line-alpha"
    )
    val lineBlur by animateFloatAsState(
        targetValue = targetBlur,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "lyrics-line-blur"
    )
    val lineScale by animateFloatAsState(
        targetValue = if (isActive) ACTIVE_LINE_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lyrics-line-scale"
    )

    val interactionSource = remember {
        MutableInteractionSource()
    }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(120),
        label = "lyrics-line-press"
    )

    val blurModifier = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lineBlur > 0.05f
    ) {
        Modifier.blur(lineBlur.dp)
    } else {
        Modifier
    }

    val karaoke = isActive && karaokeEnabled
    val lineStyle = style.copy(
        shadow = lyricsShadow()
    )

    val karaokePlan = remember(text, startTimeMs, endTimeMs, wordSpeedFactor, wordTimes) {
        buildKaraokePlan(
            text = text,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            wordSpeedFactor = wordSpeedFactor,
            wordTimes = wordTimes
        )
    }
    val karaokeText by remember(text, karaokePlan, karaoke) {
        derivedStateOf {
            if (karaoke) {
                buildKaraokeText(
                    text = text,
                    positionMs = livePosition.value + positionShiftMs,
                    plan = karaokePlan
                )
            } else {
                null
            }
        }
    }

    Text(
        text = karaokeText ?: AnnotatedString(text),
        style = lineStyle,
        color = Color.White.copy(alpha = if (karaoke) 1f else lineAlpha),
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                scaleX = lineScale * pressScale
                scaleY = lineScale * pressScale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .then(blurModifier)
            .padding(vertical = 6.dp)
    )
}

private const val KARAOKE_FADE_MS = 300f
private const val KARAOKE_MIN_WORD_MS = 120L
private const val KARAOKE_DIM_ALPHA = 0.40f

private val STRETCHED_VOWEL_REGEX = Regex("""([aeiouy])\1{2,}""")

private class KaraokePlan(
    val words: List<IntRange>,
    val starts: List<Long>
)

private fun buildKaraokePlan(
    text: String,
    startTimeMs: Long,
    endTimeMs: Long,
    wordSpeedFactor: Float,
    wordTimes: List<LyricsWord>?
): KaraokePlan {
    val words = mutableListOf<IntRange>()
    var index = 0
    while (index < text.length) {
        if (text[index].isWhitespace()) {
            index++
        } else {
            val wordStart = index
            while (index < text.length && !text[index].isWhitespace()) {
                index++
            }
            words.add(wordStart until index)
        }
    }

    if (words.isEmpty()) {
        return KaraokePlan(emptyList(), emptyList())
    }

    val lineDuration = (endTimeMs - startTimeMs).coerceAtLeast(1L)
    val latestStart = (startTimeMs + lineDuration - KARAOKE_FADE_MS.toLong())
        .coerceAtLeast(startTimeMs)

    val realStarts = wordTimes
        ?.takeIf { it.size == words.size }
        ?.takeIf { stored ->
            stored.withIndex().all { (wordIndex, word) ->
                normalizeKaraokeWord(
                    text.substring(words[wordIndex].first, words[wordIndex].last + 1)
                ) == normalizeKaraokeWord(word.text)
            }
        }
        ?.map { it.startMs.coerceIn(startTimeMs, latestStart) }
        ?.sorted()

    if (realStarts != null) {
        return KaraokePlan(words, realStarts)
    }

    val syllables = words.map {
        estimateSyllables(text.substring(it.first, it.last + 1))
    }
    val totalSyllables = syllables.sum().coerceAtLeast(1)
    val pauses = words.map {
        pauseAfterWord(text.substring(it.first, it.last + 1))
    }
    val totalPause = pauses.sum()

    val available = (lineDuration - KARAOKE_FADE_MS.toLong() - totalPause)
        .coerceAtLeast(0L)
    val starts = ArrayList<Long>(words.size)
    var cursor = startTimeMs

    words.indices.forEach { wordIndex ->
        starts.add(cursor.coerceAtMost(latestStart))

        val base = available * syllables[wordIndex] / totalSyllables
        val scaled = (base * wordSpeedFactor).toLong().coerceAtLeast(KARAOKE_MIN_WORD_MS)
        cursor += scaled + pauses[wordIndex]
    }

    return KaraokePlan(words, starts)
}

private fun buildKaraokeText(
    text: String,
    positionMs: Long,
    plan: KaraokePlan
): AnnotatedString {
    if (plan.words.isEmpty()) {
        return AnnotatedString(text)
    }

    val builder = AnnotatedString.Builder(text)

    plan.words.forEachIndexed { index, range ->
        val wordStart = plan.starts.getOrElse(index) { Long.MAX_VALUE }

        val progress = ((positionMs - wordStart).toFloat() / KARAOKE_FADE_MS)
            .coerceIn(0f, 1f)
        val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
        val alpha = KARAOKE_DIM_ALPHA + (1f - KARAOKE_DIM_ALPHA) * eased
        val glow = 0.30f * (1f - eased)
        val lift = 0.06f * (1f - eased)

        builder.addStyle(
            style = SpanStyle(
                color = Color.White.copy(alpha = alpha),
                shadow = if (glow > 0.01f) {
                    Shadow(
                        color = Color.White.copy(alpha = glow),
                        offset = Offset.Zero,
                        blurRadius = 8f
                    )
                } else {
                    null
                },
                baselineShift = if (lift > 0.001f) BaselineShift(lift) else null
            ),
            start = range.first,
            end = range.last + 1
        )
    }

    return builder.toAnnotatedString()
}

private fun normalizeKaraokeWord(word: String): String {
    return word.lowercase().trim { !it.isLetterOrDigit() }
}

private fun estimateSyllables(raw: String): Int {
    val letters = raw.lowercase().filter { it.isLetter() }
    if (letters.isEmpty()) return 1

    // Non-Latin scripts (Arabic, Japanese, Korean, ...) have no usable vowel
    // groups here, so fall back to one unit per character.
    if (letters.none { it in 'a'..'z' }) {
        return letters.length.coerceAtLeast(1)
    }

    // Stretched words ("ooooh", "yeaaah", "noooo") are held for about three beats.
    if (STRETCHED_VOWEL_REGEX.containsMatchIn(letters)) {
        return 3
    }

    var count = 0
    var previousVowel = false
    for (letter in letters) {
        val vowel = letter in "aeiouy"
        if (vowel && !previousVowel) {
            count++
        }
        previousVowel = vowel
    }

    // A trailing silent "e" does not add a syllable ("make", "the").
    if (letters.length > 2 && letters.last() == 'e') {
        val before = letters[letters.length - 2]
        if (before !in "aeiouy") {
            count--
        }
    }

    // "-ed" is only its own syllable after t/d ("wanted", "needed").
    if (letters.length > 3 && letters.endsWith("ed")) {
        val before = letters[letters.length - 3]
        if (before !in "aeiouy" && before != 't' && before != 'd') {
            count--
        }
    }

    // "-es" is only its own syllable after s/x/z/ch/sh ("boxes", "wishes").
    if (letters.length > 3 && letters.endsWith("es")) {
        val stem = letters.dropLast(2)
        val keepsSyllable = stem.endsWith("s") || stem.endsWith("x") ||
            stem.endsWith("z") || stem.endsWith("ch") || stem.endsWith("sh")
        if (!keepsSyllable) {
            count--
        }
    }

    return count.coerceAtLeast(1)
}

private fun pauseAfterWord(raw: String): Long {
    val word = raw.trimEnd()
    return when {
        word.endsWith("...") || word.endsWith("…") || word.endsWith(".") -> 200L
        word.endsWith(",") || word.endsWith("-") || word.endsWith("–") ||
            word.endsWith("—") -> 120L

        else -> 0L
    }
}

@Composable
private fun GapDots(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "gap-dots")

    Row(
        modifier = modifier.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val value by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 200)
                ),
                label = "gap-dot-$index"
            )

            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        Color.White.copy(alpha = if (active) value else 0.30f)
                    )
            )
        }
    }
}

@Composable
fun PlainLyricsLine(
    line: String,
    style: TextStyle,
    color: Color = LocalContentColor.current,
    modifier: Modifier = Modifier
) {
    Text(
        text = line,
        style = style,
        color = color,
        modifier = modifier
    )
}
