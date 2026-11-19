package com.dn0ne.player.app.presentation.components.playback

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.dn0ne.player.R
import com.dn0ne.player.app.domain.playback.PlaybackMode
import com.dn0ne.player.app.domain.track.Playlist
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.presentation.components.CoverArt
import com.dn0ne.player.app.presentation.components.TrackMenuButton
import com.dn0ne.player.app.presentation.components.background.deepPaletteBase
import com.dn0ne.player.app.presentation.components.isSystemInLandscapeOrientation
import com.dn0ne.player.app.presentation.components.settings.Theme
import com.dn0ne.player.core.data.Settings
import com.dn0ne.player.ui.theme.LotusTeal
import com.dn0ne.player.ui.theme.onPrimaryDark
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerSheet(
    playbackStateFlow: StateFlow<PlaybackState>,
    onPlayerExpandedChange: (Boolean) -> Unit,
    onLyricsSheetExpandedChange: (Boolean) -> Unit,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onSeekToNextClick: () -> Unit,
    onSeekToPreviousClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onReset: () -> Unit,
    onPlaybackModeClick: () -> Unit,
    onCoverArtLoaded: (ImageBitmap?) -> Unit,
    onPlayNextClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onViewTrackInfoClick: () -> Unit,
    onGoToAlbumClick: () -> Unit,
    onGoToArtistClick: () -> Unit,
    onDeleteFromDeviceClick: (Track) -> Unit,
    onDeleteTracksClick: (List<Track>) -> Unit,
    onLyricsClick: () -> Unit,
    positionProvider: () -> Long,
    onRemoveFromQueueClick: (Int) -> Unit,
    onReorderingQueue: (Int, Int) -> Unit,
    onTrackClick: (Track, Playlist) -> Unit,
    onSettingsClick: () -> Unit,
    settings: Settings,
    dominantColor: Color? = null,
    showBottomBar: Boolean = false,
    modifier: Modifier = Modifier
) {
    val playbackState by playbackStateFlow.collectAsState()
    val isExpanded by remember {
        derivedStateOf {
            playbackState.isPlayerExpanded
        }
    }
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val baseColor = remember(dominantColor) {
        deepPaletteBase(dominantColor)
    }
    val thresholdY = with(density) { 200.dp.toPx() }
    val decay = rememberSplineBasedDecay<Float>()
    val translationY = remember {
        Animatable(0f).apply {
            if (isExpanded) {
                updateBounds(
                    lowerBound = 0f,
                    upperBound = thresholdY
                )
            } else {
                updateBounds(
                    lowerBound = -thresholdY,
                    upperBound = thresholdY
                )
            }
        }
    }
    val draggableState = rememberDraggableState { dragAmount ->
        coroutineScope.launch {
            translationY.snapTo(
                translationY.value + (dragAmount * (1 - (translationY.value / thresholdY).absoluteValue))
            )
        }
    }

    AnimatedContent(
        targetState = isExpanded,
        label = "player-content",
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.translationY = translationY.value

                val cornerRadius =
                    lerp(0.dp, 36.dp, this.translationY.absoluteValue / (thresholdY * 0.5f))
                this.clip = true
                this.shape = if (isExpanded) {
                    RoundedCornerShape(cornerRadius)
                } else {
                    RoundedCornerShape(
                        topStart = 36.dp,
                        topEnd = 36.dp,
                        bottomStart = cornerRadius,
                        bottomEnd = cornerRadius
                    )
                }
            }
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    val decayY = decay.calculateTargetValue(
                        initialValue = translationY.value,
                        initialVelocity = velocity
                    )

                    coroutineScope.launch {
                        if (!isExpanded) {
                            val shouldStopPlayback = decayY > thresholdY * .5f
                            if (shouldStopPlayback) {
                                onReset()
                                return@launch
                            }
                        }

                        val shouldChangeExpandedState = decayY.absoluteValue > (thresholdY * 0.5f)
                        if (shouldChangeExpandedState) {
                            onPlayerExpandedChange(!isExpanded)
                            translationY.apply {
                                animateTo(0f)
                                if (isExpanded) {
                                    updateBounds(
                                        lowerBound = 0f,
                                        upperBound = thresholdY
                                    )
                                } else {
                                    updateBounds(
                                        lowerBound = -thresholdY,
                                        upperBound = thresholdY
                                    )
                                }
                            }
                        } else {
                            translationY.animateTo(0f)
                        }
                    }
                }
            )

    ) { state ->
        when (state) {
            false -> {
                BottomPlayer(
                    playbackStateFlow = playbackStateFlow,
                    onClick = {
                        onPlayerExpandedChange(true)
                        translationY.updateBounds(
                            lowerBound = 0f,
                            upperBound = thresholdY
                        )
                    },
                    onPlayClick = onPlayClick,
                    onPauseClick = onPauseClick,
                    onSeekToNextClick = onSeekToNextClick,
                    onCoverArtLoaded = onCoverArtLoaded,
                    applyNavigationBarsPadding = !showBottomBar
                )
            }

            true -> {
                val appearance by settings.appearance.collectAsState()
                val amoledDarkTheme by settings.amoledDarkTheme.collectAsState()
                val isDarkTheme = when (appearance) {
                    Theme.Appearance.System -> isSystemInDarkTheme()
                    Theme.Appearance.Light -> false
                    Theme.Appearance.Dark -> true
                }

                ExpandedPlayer(
                    playbackStateFlow = playbackStateFlow,
                    baseColor = baseColor,
                    onSeekTo = onSeekTo,
                    onPauseClick = onPauseClick,
                    onPlayClick = onPlayClick,
                    onSeekToNextClick = onSeekToNextClick,
                    onSeekToPreviousClick = onSeekToPreviousClick,
                    onHideClick = {
                        onPlayerExpandedChange(false)
                        translationY.updateBounds(
                            lowerBound = -thresholdY,
                            upperBound = 0f
                        )
                    },
                    onPlaybackModeClick = onPlaybackModeClick,
                    onCoverArtLoaded = onCoverArtLoaded,
                    onPlayNextClick = onPlayNextClick,
                    onAddToQueueClick = onAddToQueueClick,
                    onAddToPlaylistClick = onAddToPlaylistClick,
                    onViewTrackInfoClick = onViewTrackInfoClick,
                    onGoToAlbumClick = onGoToAlbumClick,
                    onGoToArtistClick = onGoToArtistClick,
                    onDeleteFromDeviceClick = onDeleteFromDeviceClick,
                    onDeleteTracksClick = onDeleteTracksClick,
                    onLyricsSheetExpandedChange = onLyricsSheetExpandedChange,
                    onLyricsClick = onLyricsClick,
                    positionProvider = positionProvider,
                    lyricsTextStyle = MaterialTheme.typography.headlineMedium
                        .copy(
                            fontSize = settings.lyricsFontSize,
                            lineHeight = settings.lyricsLineHeight,
                            letterSpacing = settings.lyricsLetterSpacing,
                            textAlign = settings.lyricsAlignment,
                            fontWeight = FontWeight(settings.lyricsFontWeight),
                        ),
                    lyricsContainerColor = when {
                        settings.useDarkPaletteOnLyricsSheet && !isDarkTheme -> {
                            MaterialTheme.colorScheme.primary
                        }

                        else -> {
                            if (!amoledDarkTheme) {
                                MaterialTheme.colorScheme.inversePrimary
                            } else {
                                MaterialTheme.colorScheme.background
                            }
                        }
                    },
                    lyricsContentColor = when {
                        settings.useDarkPaletteOnLyricsSheet && !isDarkTheme -> {
                            MaterialTheme.colorScheme.surface
                        }
                        else -> MaterialTheme.colorScheme.inverseSurface
                    },
                    onRemoveFromQueueClick = onRemoveFromQueueClick,
                    onReorderingQueue = onReorderingQueue,
                    onTrackClick = onTrackClick,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.clickable(
                        onClick = {},
                        interactionSource = null,
                        indication = null
                    )
                )
            }
        }
    }
}

@Composable
fun BottomPlayer(
    playbackStateFlow: StateFlow<PlaybackState>,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onSeekToNextClick: () -> Unit,
    onCoverArtLoaded: (ImageBitmap?) -> Unit,
    applyNavigationBarsPadding: Boolean = true,
    modifier: Modifier = Modifier
) {
    val playbackState by playbackStateFlow.collectAsState()
    val currentTrack by remember {
        derivedStateOf {
            playbackState.currentTrack!!
        }
    }
    val isPlaying by remember {
        derivedStateOf {
            playbackState.isPlaying
        }
    }
    val position by remember {
        derivedStateOf {
            playbackState.position
        }
    }

    val progress = animateFloatAsState(
        targetValue = (position.toFloat() / currentTrack.duration).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "bottom-player-progress"
    ).value
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (applyNavigationBarsPadding) Modifier.navigationBarsPadding() else Modifier
            )
            .clickable {
                onClick()
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(accent.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .drawBehind {
                        drawRoundRect(
                            color = accent.copy(alpha = 0.24f),
                            topLeft = Offset(0f, -2.5.dp.toPx()),
                            size = Size(size.width, size.height + 5.dp.toPx()),
                            cornerRadius = CornerRadius(size.height)
                        )
                    }
                    .background(color = accent)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = currentTrack,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    fadeIn() + slideInHorizontally(
                        initialOffsetX = { it / 5 }
                    ) togetherWith fadeOut() + slideOutHorizontally(
                        targetOffsetX = { -it / 5 }
                    )
                },
                label = "bottom-player-track-change-animation"
            ) { currentTrack ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    CoverArt(
                        uri = currentTrack.coverArtUri,
                        onCoverArtLoaded = onCoverArtLoaded,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        val context = LocalContext.current
                        Text(
                            text = currentTrack.title
                                ?: context.resources.getString(R.string.unknown_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentTrack.artist
                                ?: context.resources.getString(R.string.unknown_artist),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledIconButton(
                onClick = if (isPlaying) onPauseClick else onPlayClick,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause
                    else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onSeekToNextClick,
                modifier = Modifier.size(38.dp)
            ) {
                val layoutDirection = LocalLayoutDirection.current
                val isRtl = layoutDirection == LayoutDirection.Rtl
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer {
                            if (isRtl) {
                                rotationY = 180f
                            }
                        }
                )
            }
        }
    }
}

@Composable
fun ExpandedPlayer(
    playbackStateFlow: StateFlow<PlaybackState>,
    baseColor: Color,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onSeekToNextClick: () -> Unit,
    onSeekToPreviousClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onHideClick: () -> Unit,
    onPlaybackModeClick: () -> Unit,
    onCoverArtLoaded: (ImageBitmap?) -> Unit,
    onPlayNextClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onViewTrackInfoClick: () -> Unit,
    onGoToAlbumClick: () -> Unit,
    onGoToArtistClick: () -> Unit,
    onDeleteFromDeviceClick: (Track) -> Unit,
    onDeleteTracksClick: (List<Track>) -> Unit,
    onLyricsSheetExpandedChange: (Boolean) -> Unit,
    onLyricsClick: () -> Unit,
    positionProvider: () -> Long,
    lyricsTextStyle: TextStyle,
    lyricsContainerColor: Color,
    lyricsContentColor: Color,
    onRemoveFromQueueClick: (Int) -> Unit,
    onReorderingQueue: (Int, Int) -> Unit,
    onTrackClick: (Track, Playlist) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onHideClick()
    }

    val playbackState by playbackStateFlow.collectAsState()
    val showLyricsSheet by remember {
        derivedStateOf {
            playbackState.isLyricsSheetExpanded
        }
    }
    var showQueue by remember {
        mutableStateOf(false)
    }
    var showSleepTimerSheet by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
    ) {
        val currentTrack by remember {
            derivedStateOf {
                playbackState.currentTrack!!
            }
        }
        val playbackMode by remember {
            derivedStateOf {
                playbackState.playbackMode
            }
        }
        val context = LocalContext.current

        var isArtworkExpanded by remember {
            mutableStateOf(false)
        }
        LaunchedEffect(currentTrack.uri) {
            isArtworkExpanded = false
        }
        val expansion by animateFloatAsState(
            targetValue = if (isArtworkExpanded) 1f else 0f,
            animationSpec = tween(durationMillis = 350),
            label = "artwork-expansion-animation"
        )
        val favorites = remember {
            mutableStateMapOf<String, Boolean>()
        }

        if (isSystemInLandscapeOrientation()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {

                AnimatedContent(
                    targetState = currentTrack,
                    label = "cover-art-animation"
                ) { track ->
                    CoverArt(
                        uri = track.coverArtUri,
                        onCoverArtLoaded = onCoverArtLoaded,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 28.dp)
                            .padding(vertical = 28.dp)
                            .clip(ShapeDefaults.ExtraLarge)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .padding(horizontal = 16.dp)
                            .align(Alignment.TopCenter),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onHideClick
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = context.resources.getString(R.string.close_player_sheet),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }


                        Row {
                            IconButton(
                                onClick = {
                                    showSleepTimerSheet = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = context.resources.getString(R.string.set_sleep_timer),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(
                                visible = playbackState.playbackMode != PlaybackMode.Shuffle,
                                enter = expandHorizontally(),
                                exit = shrinkHorizontally()
                            ) {
                                IconButton(
                                    onClick = {
                                        if (playbackState.playbackMode != PlaybackMode.Shuffle) {
                                            showQueue = true
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                        contentDescription = context.resources.getString(R.string.show_queue),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    onLyricsSheetExpandedChange(true)
                                    onLyricsClick()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lyrics,
                                    contentDescription = context.resources.getString(R.string.show_lyrics),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = onPlaybackModeClick
                            ) {
                                Icon(
                                    imageVector = when (playbackMode) {
                                        PlaybackMode.Repeat -> Icons.Rounded.Repeat
                                        PlaybackMode.RepeatOne -> Icons.Rounded.RepeatOne
                                        PlaybackMode.Shuffle -> Icons.Rounded.Shuffle
                                    },
                                    contentDescription = context.resources.getString(R.string.playback_mode_toggle),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            TrackMenuButton(
                                onPlayNextClick = onPlayNextClick,
                                onAddToQueueClick = onAddToQueueClick,
                                onAddToPlaylistClick = onAddToPlaylistClick,
                                onViewTrackInfoClick = onViewTrackInfoClick,
                                onGoToAlbumClick = onGoToAlbumClick,
                                onGoToArtistClick = onGoToArtistClick,
                                onDeleteFromDeviceClick = {
                                    onDeleteFromDeviceClick(currentTrack)
                                }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .offset(y = 28.dp)
                            .padding(horizontal = 28.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AnimatedContent(
                            targetState = currentTrack,
                            label = "title-artist-text-animation",
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { track ->
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = track.title
                                        ?: context.resources.getString(R.string.unknown_title),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.basicMarquee()
                                )

                                Text(
                                    text = track.artist
                                        ?: context.resources.getString(R.string.unknown_artist),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.basicMarquee()
                                )
                            }

                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        PlaybackControl(
                            playbackStateFlow = playbackStateFlow,
                            onPlayClick = onPlayClick,
                            onPauseClick = onPauseClick,
                            onSeekTo = onSeekTo,
                            onSeekToNextClick = onSeekToNextClick,
                            onSeekToPreviousClick = onSeekToPreviousClick,
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }
                }
            }
        } else {
            val openLyricsAction by rememberUpdatedState(
                newValue = {
                    onLyricsSheetExpandedChange(true)
                    onLyricsClick()
                }
            )
            val swipeThreshold = with(LocalDensity.current) {
                80.dp.toPx()
            }
            val swipeSlop = with(LocalDensity.current) {
                14.dp.toPx()
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var total = 0f
                            var isUpward: Boolean? = null

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull {
                                    it.id == down.id
                                } ?: break
                                if (!change.pressed || change.isConsumed) break

                                total += change.position.y - change.previousPosition.y

                                if (isUpward == null && total.absoluteValue > swipeSlop) {
                                    isUpward = total < 0f
                                }

                                if (isUpward == true) {
                                    change.consume()
                                    if (total < -swipeThreshold) {
                                        openLyricsAction()
                                        break
                                    }
                                }
                            }
                        }
                    }
            ) {
                val density = LocalDensity.current
                val screenWidthPx = with(density) {
                    maxWidth.roundToPx()
                }
                val screenHeightPx = with(density) {
                    maxHeight.roundToPx()
                }
                val statusBarTop = WindowInsets.statusBars
                    .asPaddingValues()
                    .calculateTopPadding()
                val squareSize = maxWidth * 0.88f
                val coverBottom = statusBarTop + 56.dp + squareSize

                var bottomBarTop by remember {
                    mutableStateOf(0f)
                }
                val titleBlockHeight = lerp(88.dp, 104.dp, expansion)
                val titleTop = lerp(
                    start = coverBottom + 14.dp,
                    stop = with(density) { bottomBarTop.toDp() } - titleBlockHeight - 12.dp,
                    fraction = expansion
                )

                NowPlayingStage(
                    track = currentTrack,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    expansionFraction = expansion,
                    baseColor = baseColor,
                    onToggleExpanded = {
                        isArtworkExpanded = !isArtworkExpanded
                    },
                    onCoverArtLoaded = onCoverArtLoaded,
                    modifier = Modifier.fillMaxSize()
                )

                NowPlayingScrim(modifier = Modifier.fillMaxSize())

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(top = statusBarTop + 8.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TranslucentRoundButton(onClick = onHideClick) {
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = context.resources.getString(R.string.close_player_sheet),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TranslucentRoundButton(
                            onClick = {
                                showSleepTimerSheet = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = context.resources.getString(R.string.set_sleep_timer),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = playbackState.playbackMode != PlaybackMode.Shuffle,
                            enter = expandHorizontally(),
                            exit = shrinkHorizontally()
                        ) {
                            TranslucentRoundButton(
                                onClick = {
                                    if (playbackState.playbackMode != PlaybackMode.Shuffle) {
                                        showQueue = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = context.resources.getString(R.string.show_queue),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        TranslucentRoundButton(
                            onClick = {
                                onLyricsSheetExpandedChange(true)
                                onLyricsClick()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lyrics,
                                contentDescription = context.resources.getString(R.string.show_lyrics),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        TranslucentRoundButton(onClick = onPlaybackModeClick) {
                            Icon(
                                imageVector = when (playbackMode) {
                                    PlaybackMode.Repeat -> Icons.Rounded.Repeat
                                    PlaybackMode.RepeatOne -> Icons.Rounded.RepeatOne
                                    PlaybackMode.Shuffle -> Icons.Rounded.Shuffle
                                },
                                contentDescription = context.resources.getString(R.string.playback_mode_toggle),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        TranslucentRoundButton {
                            TrackMenuButton(
                                onPlayNextClick = onPlayNextClick,
                                onAddToQueueClick = onAddToQueueClick,
                                onAddToPlaylistClick = onAddToPlaylistClick,
                                onViewTrackInfoClick = onViewTrackInfoClick,
                                onGoToAlbumClick = onGoToAlbumClick,
                                onGoToArtistClick = onGoToArtistClick,
                                onDeleteFromDeviceClick = {
                                    onDeleteFromDeviceClick(currentTrack)
                                },
                                tint = Color.White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = titleTop)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(titleBlockHeight)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = currentTrack.title
                                    ?: context.resources.getString(R.string.unknown_title),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = lerp(26.sp, 33.sp, expansion),
                                    lineHeight = lerp(32.sp, 40.sp, expansion),
                                    letterSpacing = lerp(0.06.em, 0.01.em, expansion),
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = currentTrack.artist
                                    ?: context.resources.getString(R.string.unknown_artist),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = lerp(15.sp, 17.sp, expansion),
                                    lineHeight = lerp(20.sp, 22.sp, expansion)
                                ),
                                color = Color.White.copy(alpha = 0.68f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                val shareText = buildString {
                                    append(
                                        currentTrack.title
                                            ?: context.resources.getString(R.string.unknown_title)
                                    )
                                    currentTrack.artist?.let {
                                        append(" - ")
                                        append(it)
                                    }
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(
                                    Intent.createChooser(intent, null)
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = context.resources.getString(R.string.share_track),
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        val favoriteKey = currentTrack.uri.toString()
                        val isFavorite = favorites[favoriteKey] == true
                        IconButton(
                            onClick = {
                                favorites[favoriteKey] = !isFavorite
                            }
                        ) {
                            Icon(
                                imageVector = if (isFavorite) {
                                    Icons.Rounded.Favorite
                                } else {
                                    Icons.Rounded.FavoriteBorder
                                },
                                contentDescription = context.resources.getString(
                                    if (isFavorite) {
                                        R.string.unfavorite_track
                                    } else {
                                        R.string.favorite_track
                                    }
                                ),
                                tint = if (isFavorite) {
                                    LotusTeal
                                } else {
                                    Color.White.copy(alpha = 0.85f)
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .onGloballyPositioned {
                            bottomBarTop = it.positionInParent().y
                        }
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlaybackControl(
                        playbackStateFlow = playbackStateFlow,
                        onPlayClick = onPlayClick,
                        onPauseClick = onPauseClick,
                        onSeekTo = onSeekTo,
                        onSeekToNextClick = onSeekToNextClick,
                        onSeekToPreviousClick = onSeekToPreviousClick,
                        playButtonColor = lerpColor(LotusTeal, Color.White, expansion),
                        playIconColor = lerpColor(onPrimaryDark, Color.Black, expansion),
                        prevNextColor = Color.White,
                        seekBarFilledColor = LotusTeal,
                        seekBarRestColor = Color.White.copy(alpha = 0.28f),
                        seekBarTextColor = Color.White.copy(alpha = 0.65f),
                        seekBarHandleColor = LotusTeal,
                        seekBarHandleSize = 12.dp,
                        seekBarStrokeWidth = 3.dp,
                        seekBarWaveHeight = 4.dp,
                        seekBarTextStyle = MaterialTheme.typography.labelMedium,
                        enableSeekBarWaving = false,
                        seekBarCircleHandle = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(50.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TranslucentRoundButton(
                            onClick = {
                                showQueue = true
                            },
                            size = 34.dp
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = context.resources.getString(R.string.show_queue),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        TranslucentRoundButton(
                            onClick = onAddToPlaylistClick,
                            size = 34.dp
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlaylistAdd,
                                contentDescription = context.resources.getString(R.string.add_to_playlist),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        TranslucentRoundButton(
                            onClick = onSettingsClick,
                            size = 34.dp
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Equalizer,
                                contentDescription = context.resources.getString(R.string.equalizer),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable {
                                onLyricsSheetExpandedChange(true)
                                onLyricsClick()
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = stringResource(R.string.open_lyrics),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = showLyricsSheet,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + slideInVertically(
                initialOffsetY = { it / 10 }),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + slideOutVertically(
                targetOffsetY = { it / 10 }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            LyricsSheet(
                playbackStateFlow = playbackStateFlow,
                lyricsTextStyle = lyricsTextStyle,
                baseColor = baseColor,
                onBackClick = {
                    onLyricsSheetExpandedChange(false)
                },
                containerColor = lyricsContainerColor,
                contentColor = lyricsContentColor,
                onSeekTo = onSeekTo,
                positionProvider = positionProvider,
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = showQueue,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + slideInVertically(
                initialOffsetY = { it / 10 }),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + slideOutVertically(
                targetOffsetY = { it / 10 }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val playlist by remember {
                derivedStateOf {
                    playbackState.playlist
                }
            }
            val currentTrack by remember {
                derivedStateOf {
                    playbackState.currentTrack
                }
            }

            val listState = rememberLazyListState()
            Queue(
                listState = listState,
                playlist = playlist,
                currentTrack = currentTrack,
                onRemoveFromQueueClick = onRemoveFromQueueClick,
                onReorderingQueue = onReorderingQueue,
                onDeleteTracksClick = onDeleteTracksClick,
                onTrackClick = onTrackClick,
                onBackClick = {
                    showQueue = false
                }
            )
        }

        if (showSleepTimerSheet) {
            SleepTimerBottomSheet(
                onDismissRequest = {
                    showSleepTimerSheet = false
                }
            )
        }
    }
}

@Composable
fun PlaybackControl(
    playbackStateFlow: StateFlow<PlaybackState>,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekToNextClick: () -> Unit,
    onSeekToPreviousClick: () -> Unit,
    modifier: Modifier = Modifier,
    playButtonColor: Color = Color.White,
    playIconColor: Color = Color.Black,
    playButtonSize: Dp = 64.dp,
    playIconSize: Dp = 36.dp,
    prevNextColor: Color = Color.White,
    prevNextIconSize: Dp = 38.dp,
    seekBarFilledColor: Color = Color.White,
    seekBarRestColor: Color = Color.White.copy(alpha = 0.28f),
    seekBarTextColor: Color = Color.White.copy(alpha = 0.72f),
    seekBarHandleColor: Color = Color.White,
    seekBarHandleSize: Dp = 20.dp,
    seekBarStrokeWidth: Dp = 3.dp,
    seekBarWaveHeight: Dp = 6.dp,
    seekBarTextStyle: TextStyle = MaterialTheme.typography.titleMedium,
    enableSeekBarWaving: Boolean = true,
    seekBarCircleHandle: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val playbackState by playbackStateFlow.collectAsState()
        val position by remember {
            derivedStateOf {
                playbackState.position
            }
        }
        val currentTrack by remember {
            derivedStateOf {
                playbackState.currentTrack!!
            }
        }
        val isPlaying by remember {
            derivedStateOf {
                playbackState.isPlaying
            }
        }

        WavingSeekBar(
            position = position,
            duration = currentTrack.duration.toLong(),
            onPositionChange = {
                onSeekTo(it)
            },
            isPlaying = isPlaying,
            enableWaving = enableSeekBarWaving,
            waveHeight = seekBarWaveHeight,
            strokeWidth = seekBarStrokeWidth,
            handleSize = seekBarHandleSize,
            filledColor = seekBarFilledColor,
            restColor = seekBarRestColor,
            textColor = seekBarTextColor,
            handleColor = seekBarHandleColor,
            textStyle = seekBarTextStyle,
            circleHandle = seekBarCircleHandle,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val layoutDirection = LocalLayoutDirection.current
            val isRtl = layoutDirection == LayoutDirection.Rtl

            IconButton(
                onClick = onSeekToPreviousClick,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = null,
                    tint = prevNextColor,
                    modifier = Modifier
                        .size(prevNextIconSize)
                        .graphicsLayer {
                            if (isRtl) {
                                rotationY = 180f
                            }
                        }
                )
            }

            Box(
                modifier = Modifier
                    .size(playButtonSize)
                    .clip(CircleShape)
                    .background(playButtonColor)
                    .clickable {
                        if (isPlaying) onPauseClick() else onPlayClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause
                    else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = playIconColor,
                    modifier = Modifier.size(playIconSize)
                )
            }

            IconButton(
                onClick = onSeekToNextClick,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = prevNextColor,
                    modifier = Modifier
                        .size(prevNextIconSize)
                        .graphicsLayer {
                            if (isRtl) {
                                rotationY = 180f
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun TranslucentRoundButton(
    onClick: () -> Unit = {},
    size: Dp = 40.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
