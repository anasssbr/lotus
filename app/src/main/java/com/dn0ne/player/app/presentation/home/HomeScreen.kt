package com.dn0ne.player.app.presentation.home

import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dn0ne.player.R
import com.dn0ne.player.app.domain.listening.DailyListening
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.presentation.components.CoverArt
import com.dn0ne.player.app.presentation.components.background.deepPaletteBase
import com.dn0ne.player.app.presentation.components.background.paletteCardColor
import com.dn0ne.player.app.presentation.components.design.EditorialFaint
import com.dn0ne.player.app.presentation.components.design.EditorialSecondary
import com.dn0ne.player.app.presentation.components.design.EditorialSectionHeader
import com.dn0ne.player.app.presentation.components.design.EditorialSpacing
import com.dn0ne.player.app.presentation.components.design.EditorialTiny
import com.dn0ne.player.app.presentation.components.design.EditorialType
import com.dn0ne.player.app.presentation.components.design.GlowCover
import com.dn0ne.player.app.presentation.components.design.editorialClickable
import com.dn0ne.player.app.presentation.components.design.editorialEntrance
import com.dn0ne.player.app.presentation.components.rememberReduceMotion
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    state: HomeState,
    userName: String,
    nowPlayingTrack: Track?,
    isPlaying: Boolean,
    position: Long,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTrackClick: (List<Track>, Track) -> Unit,
    onPlayMixClick: () -> Unit,
    onShuffleAllClick: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onTogglePlayback: () -> Unit,
    onScrollOffsetChange: (Float) -> Unit = {},
    paletteColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val cardColor = remember(paletteColor) {
        paletteCardColor(paletteColor)
    }
    val listState = rememberLazyListState()
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current
    val collapse by remember(density) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / with(density) { 80.dp.toPx() })
                    .coerceIn(0f, 1f)
            }
        }
    }

    LaunchedEffect(listState, reduceMotion) {
        if (reduceMotion) {
            onScrollOffsetChange(0f)
            return@LaunchedEffect
        }
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull()?.offset ?: 0
        }
            .distinctUntilChanged()
            .collect { offset ->
                onScrollOffsetChange((-offset).toFloat())
            }
    }

    val heroTrack = nowPlayingTrack
    val picks = remember(state.recentlyPlayed, heroTrack) {
        state.recentlyPlayed
            .filter { it.uri != heroTrack?.uri }
            .distinctBy { it.uri }
    }
    val quickPicks = picks.take(6)
    val jumpBackItems = remember(state.recentlyPlayed) {
        buildJumpBackItems(state.recentlyPlayed)
    }
    val mix = state.mix
    val mostPlayed = state.mostPlayed.take(5)
    val rediscover = state.rediscover.take(10)
    val hasListeningData = state.week.any { it.seconds > 0L }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = EditorialSpacing.screen,
                end = EditorialSpacing.screen,
                top = 20.dp,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(EditorialSpacing.section)
        ) {
            item(key = "header") {
                HomeHeader(
                    userName = userName,
                    todaySeconds = state.todaySeconds,
                    trackCount = state.libraryTrackCount,
                    streakDays = state.streakDays,
                    cardColor = cardColor,
                    onSearchClick = onSearchClick,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.editorialEntrance(index = 0)
                )
            }

            if (heroTrack != null || (state.hasTracks && !state.isLoading)) {
                item(key = "hero") {
                    Column(modifier = Modifier.editorialEntrance(index = 1)) {
                        EditorialSectionHeader(
                            title = stringResource(R.string.home_continue_listening)
                        )
                        Spacer(modifier = Modifier.height(EditorialSpacing.md))
                        ContinueListeningHero(
                            track = heroTrack,
                            isPlaying = isPlaying,
                            position = position,
                            paletteColor = paletteColor,
                            onOpen = onOpenNowPlaying,
                            onTogglePlayback = onTogglePlayback,
                            onShuffleAll = onShuffleAllClick
                        )
                    }
                }
            }

            if (quickPicks.isNotEmpty()) {
                item(key = "quick-picks") {
                    QuickPicksSection(
                        tracks = quickPicks,
                        cardColor = cardColor,
                        onTrackClick = onTrackClick,
                        modifier = Modifier.editorialEntrance(index = 2)
                    )
                }
            }

            if (jumpBackItems.isNotEmpty()) {
                item(key = "jump-back") {
                    JumpBackSection(
                        items = jumpBackItems,
                        onTrackClick = onTrackClick,
                        modifier = Modifier.editorialEntrance(index = 3)
                    )
                }
            }

            if (mix.isNotEmpty()) {
                item(key = "mix") {
                    MyMixSection(
                        mix = mix,
                        paletteColor = paletteColor,
                        onPlayClick = onPlayMixClick,
                        onShuffleClick = {
                            val shuffled = mix.shuffled()
                            if (shuffled.isNotEmpty()) {
                                onTrackClick(shuffled, shuffled.first())
                            }
                        },
                        modifier = Modifier.editorialEntrance(index = 4)
                    )
                }
            }

            if (hasListeningData) {
                item(key = "listening") {
                    ListeningTimeSection(
                        week = state.week,
                        todaySeconds = state.todaySeconds,
                        yesterdaySeconds = state.yesterdaySeconds,
                        cardColor = cardColor,
                        modifier = Modifier.editorialEntrance(index = 5)
                    )
                }
            }

            if (mostPlayed.isNotEmpty()) {
                item(key = "most-played") {
                    MostPlayedSection(
                        tracks = mostPlayed,
                        onTrackClick = onTrackClick,
                        modifier = Modifier.editorialEntrance(index = 6)
                    )
                }
            }

            if (rediscover.size >= 3) {
                item(key = "rediscover") {
                    RediscoverSection(
                        tracks = rediscover,
                        onTrackClick = onTrackClick,
                        modifier = Modifier.editorialEntrance(index = 7)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = collapse >= 0.99f,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(160)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            CompactHomeHeader(
                title = greetingText(),
                cardColor = cardColor,
                paletteColor = paletteColor,
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun HomeHeader(
    userName: String,
    todaySeconds: Long,
    trackCount: Int,
    streakDays: Int,
    cardColor: Color,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val greeting = greetingText()
    val title = if (userName.isBlank()) greeting else "$greeting, ${userName.trim()}"
    val dateLabel = remember {
        LocalDate.now()
            .format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
            .uppercase(Locale.getDefault())
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateLabel,
                    style = EditorialType.tiny,
                    color = EditorialTiny,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(EditorialSpacing.xs))

                Text(
                    text = title,
                    style = EditorialType.display,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(EditorialSpacing.sm))

            RoundIconButton(
                icon = Icons.Rounded.Search,
                background = cardColor,
                onClick = onSearchClick
            )

            Spacer(modifier = Modifier.width(EditorialSpacing.xs))

            RoundIconButton(
                icon = Icons.Rounded.Settings,
                background = cardColor,
                onClick = onSettingsClick
            )
        }

        if (todaySeconds > 0L || trackCount > 0 || streakDays >= 1) {
            Spacer(modifier = Modifier.height(EditorialSpacing.md))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(EditorialSpacing.xs)
            ) {
                if (todaySeconds > 0L) {
                    StatChip(
                        icon = Icons.Rounded.Schedule,
                        text = formatListeningTime(todaySeconds),
                        background = cardColor
                    )
                }

                if (trackCount > 0) {
                    StatChip(
                        icon = Icons.Rounded.LibraryMusic,
                        text = stringResource(R.string.home_tracks_count, trackCount),
                        background = cardColor
                    )
                }

                if (streakDays >= 1) {
                    StatChip(
                        icon = Icons.Rounded.LocalFireDepartment,
                        text = pluralStringResource(
                            R.plurals.home_day_streak,
                            streakDays,
                            streakDays
                        ),
                        background = cardColor
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactHomeHeader(
    title: String,
    cardColor: Color,
    paletteColor: Color?,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(deepPaletteBase(paletteColor).copy(alpha = 0.94f))
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = EditorialSpacing.screen),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = EditorialType.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        RoundIconButton(
            icon = Icons.Rounded.Search,
            background = cardColor,
            onClick = onSearchClick,
            size = 38.dp
        )

        Spacer(modifier = Modifier.width(EditorialSpacing.xs))

        RoundIconButton(
            icon = Icons.Rounded.Settings,
            background = cardColor,
            onClick = onSettingsClick,
            size = 38.dp
        )
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    background: Color,
    onClick: () -> Unit,
    size: Dp = 44.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .editorialClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    text: String,
    background: Color
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = text,
            style = EditorialType.stat,
            color = EditorialSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun ContinueListeningHero(
    track: Track?,
    isPlaying: Boolean,
    position: Long,
    paletteColor: Color?,
    onOpen: () -> Unit,
    onTogglePlayback: () -> Unit,
    onShuffleAll: () -> Unit
) {
    val start = remember(paletteColor) { paletteTint(paletteColor, 0.55f) }
    val end = remember(paletteColor) { deepPaletteBase(paletteColor).copy(alpha = 0.94f) }
    val brush = remember(start, end) { Brush.linearGradient(listOf(start, end)) }
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    if (track == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(brush)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.home_pick_something),
                style = EditorialType.cardTitle.copy(fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(EditorialSpacing.md))

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(primary)
                    .editorialClickable(onClick = onShuffleAll)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = null,
                    tint = onPrimary,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = stringResource(R.string.home_shuffle_all),
                    style = EditorialType.secondary.copy(fontWeight = FontWeight.SemiBold),
                    color = onPrimary
                )
            }
        }
        return
    }

    val duration = track.duration.toLong().coerceAtLeast(1L)
    val fraction = (position.toFloat() / duration).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(brush)
            .editorialClickable(onClick = onOpen)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlowCover(
            uri = track.coverArtUri,
            modifier = Modifier.size(140.dp),
            cornerRadius = 20.dp
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.title ?: stringResource(R.string.unknown_title),
                style = EditorialType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = track.artist ?: stringResource(R.string.unknown_artist),
                style = EditorialType.secondary,
                color = EditorialSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                val corner = CornerRadius(size.height / 2f)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.22f),
                    cornerRadius = corner
                )
                drawRoundRect(
                    color = primary,
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = corner
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${formatClock(position)} / ${formatClock(duration)}",
                style = EditorialType.tiny.copy(letterSpacing = 0.4.sp),
                color = EditorialTiny,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(primary)
                    .editorialClickable(onClick = onTogglePlayback),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickPicksSection(
    tracks: List<Track>,
    cardColor: Color,
    onTrackClick: (List<Track>, Track) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        EditorialSectionHeader(title = stringResource(R.string.home_quick_picks))

        Spacer(modifier = Modifier.height(EditorialSpacing.md))

        Column(verticalArrangement = Arrangement.spacedBy(EditorialSpacing.xs)) {
            tracks.chunked(2).forEach { rowTracks ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EditorialSpacing.xs)
                ) {
                    rowTracks.forEach { track ->
                        QuickPickTile(
                            track = track,
                            background = cardColor,
                            onClick = { onTrackClick(tracks, track) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (rowTracks.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPickTile(
    track: Track,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .editorialClickable(onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlowCover(
            uri = track.coverArtUri,
            modifier = Modifier.size(56.dp),
            cornerRadius = 16.dp
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = track.title ?: stringResource(R.string.unknown_title),
            style = EditorialType.tileTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
        )
    }
}

private sealed interface JumpBackItem {
    val key: String
    val coverUri: Uri
    val title: String?
    val subtitle: String?
    val tracks: List<Track>
}

private data class JumpBackAlbum(
    override val key: String,
    override val coverUri: Uri,
    override val title: String?,
    override val subtitle: String?,
    override val tracks: List<Track>
) : JumpBackItem

private data class JumpBackTrack(
    val track: Track
) : JumpBackItem {
    override val key: String get() = "track:${track.uri}"
    override val coverUri: Uri get() = track.coverArtUri
    override val title: String? get() = track.title
    override val subtitle: String? get() = track.artist
    override val tracks: List<Track> get() = listOf(track)
}

private fun buildJumpBackItems(recent: List<Track>): List<JumpBackItem> {
    if (recent.isEmpty()) return emptyList()

    val albums = recent
        .filter { !it.album.isNullOrBlank() }
        .groupBy { it.album!! }
        .map { (album, tracks) ->
            JumpBackAlbum(
                key = "album:$album",
                coverUri = tracks.first().coverArtUri,
                title = album,
                subtitle = tracks.first().artist,
                tracks = tracks
            )
        }
        .take(3)

    val albumNames = albums.mapNotNull { it.title }.toSet()

    val tracks = recent
        .filter { it.album.isNullOrBlank() || it.album !in albumNames }
        .distinctBy { it.uri }
        .take(8)
        .map { JumpBackTrack(it) }

    return albums + tracks
}

@Composable
private fun JumpBackSection(
    items: List<JumpBackItem>,
    onTrackClick: (List<Track>, Track) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        EditorialSectionHeader(title = stringResource(R.string.home_jump_back_in))

        Spacer(modifier = Modifier.height(EditorialSpacing.md))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(EditorialSpacing.sm)) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.key }
            ) { index, item ->
                val cardSize = if (index == 0) 170.dp else 150.dp

                Column(
                    modifier = Modifier
                        .width(cardSize)
                        .editorialClickable {
                            item.tracks.firstOrNull()?.let { track ->
                                onTrackClick(item.tracks, track)
                            }
                        }
                ) {
                    GlowCover(
                        uri = item.coverUri,
                        modifier = Modifier.size(cardSize),
                        cornerRadius = if (index == 0) 24.dp else 20.dp
                    )

                    Spacer(modifier = Modifier.height(EditorialSpacing.xs))

                    Text(
                        text = item.title ?: stringResource(R.string.unknown_title),
                        style = EditorialType.cardTitle.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = item.subtitle ?: stringResource(R.string.unknown_artist),
                        style = EditorialType.secondary.copy(fontSize = 12.sp),
                        color = EditorialSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MyMixSection(
    mix: List<Track>,
    paletteColor: Color?,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val start = remember(paletteColor) { paletteTint(paletteColor, 0.85f) }
    val end = remember(paletteColor) { deepPaletteBase(paletteColor).copy(alpha = 0.92f) }
    val brush = remember(start, end) { Brush.linearGradient(listOf(start, end)) }
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Column(modifier = modifier) {
        EditorialSectionHeader(title = stringResource(R.string.home_my_mix))

        Spacer(modifier = Modifier.height(EditorialSpacing.md))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(brush)
                .editorialClickable(onClick = onPlayClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MixMosaic(
                mix = mix,
                modifier = Modifier.size(112.dp)
            )

            Spacer(modifier = Modifier.width(EditorialSpacing.md))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.home_my_mix),
                    style = EditorialType.mixTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.home_my_mix_subtitle, mix.size),
                    style = EditorialType.secondary.copy(fontSize = 12.sp),
                    color = EditorialSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(primary)
                            .editorialClickable(onClick = onPlayClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .editorialClickable(onClick = onShuffleClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = stringResource(R.string.home_shuffle_mix),
                            tint = primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MixMosaic(
    mix: List<Track>,
    modifier: Modifier = Modifier
) {
    val ghost = remember(mix) { mix.drop(4).ifEmpty { mix } }

    Box(modifier = modifier) {
        MosaicGrid(
            tracks = ghost,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = 6f
                    alpha = 0.7f
                    scaleX = 0.96f
                    scaleY = 0.96f
                }
        )

        MosaicGrid(
            tracks = mix,
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
private fun MosaicGrid(
    tracks: List<Track>,
    modifier: Modifier = Modifier
) {
    val cells = remember(tracks) {
        if (tracks.isEmpty()) {
            emptyList()
        } else {
            List(4) { index -> tracks[index % tracks.size] }
        }
    }

    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            MosaicCell(track = cells.getOrNull(0), modifier = Modifier.weight(1f))
            MosaicCell(track = cells.getOrNull(1), modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            MosaicCell(track = cells.getOrNull(2), modifier = Modifier.weight(1f))
            MosaicCell(track = cells.getOrNull(3), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MosaicCell(
    track: Track?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.06f))
    ) {
        track?.let {
            CoverArt(
                uri = it.coverArtUri,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ListeningTimeSection(
    week: List<DailyListening>,
    todaySeconds: Long,
    yesterdaySeconds: Long,
    cardColor: Color,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(week.size) {
        mutableIntStateOf(week.lastIndex.coerceAtLeast(0))
    }
    val selected = week.getOrNull(selectedIndex)
    val todayEpochDay = remember { LocalDate.now().toEpochDay() }
    val primary = MaterialTheme.colorScheme.primary
    val delta = todaySeconds - yesterdaySeconds
    val deltaText = when {
        delta > 0L -> stringResource(
            R.string.home_vs_yesterday,
            "+${formatListeningTime(delta)}"
        )

        delta < 0L -> stringResource(
            R.string.home_vs_yesterday,
            "−${formatListeningTime(-delta)}"
        )

        else -> stringResource(R.string.home_same_as_yesterday)
    }
    val deltaColor = if (delta >= 0L) primary else Color(0xFFE07A7A)

    Column(modifier = modifier) {
        EditorialSectionHeader(title = stringResource(R.string.home_listening_time))

        Spacer(modifier = Modifier.height(EditorialSpacing.md))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(cardColor)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatListeningTime(todaySeconds),
                    style = EditorialType.metric,
                    color = primary,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.width(EditorialSpacing.xs))

                Text(
                    text = stringResource(R.string.home_today).uppercase(Locale.getDefault()),
                    style = EditorialType.tiny,
                    color = EditorialTiny,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = deltaText,
                style = EditorialType.secondary.copy(fontSize = 12.sp),
                color = deltaColor
            )

            Spacer(modifier = Modifier.height(20.dp))

            WeeklyBars(
                week = week,
                todayEpochDay = todayEpochDay,
                selectedIndex = selectedIndex,
                onSelect = { selectedIndex = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Text(
                        text = dayLabel(day.epochDay),
                        style = EditorialType.tiny.copy(letterSpacing = 0.5.sp),
                        color = if (day.epochDay == todayEpochDay) primary else EditorialTiny,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (selected != null) {
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "${dayFullLabel(selected.epochDay)} · ${formatListeningTime(selected.seconds)}",
                    style = EditorialType.secondary.copy(fontSize = 12.sp),
                    color = EditorialSecondary
                )
            }
        }
    }
}

@Composable
private fun WeeklyBars(
    week: List<DailyListening>,
    todayEpochDay: Long,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    val progress = remember(week) {
        week.map { Animatable(if (reduceMotion) 1f else 0f) }
    }

    LaunchedEffect(week, reduceMotion) {
        if (reduceMotion) {
            progress.forEach { it.snapTo(1f) }
        } else {
            coroutineScope {
                progress.forEachIndexed { index, animatable ->
                    launch {
                        delay(index * 40L)
                        animatable.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 400,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                }
            }
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val inactive = Color.White.copy(alpha = 0.35f)
    val selected = Color.White.copy(alpha = 0.55f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .pointerInput(week) {
                detectTapGestures { offset ->
                    if (week.isNotEmpty()) {
                        val slot = size.width.toFloat() / week.size
                        onSelect(
                            (offset.x / slot).toInt().coerceIn(0, week.lastIndex)
                        )
                    }
                }
            }
    ) {
        if (week.isEmpty()) return@Canvas

        val maxSeconds = week.maxOf { it.seconds }.coerceAtLeast(1L)
        val slot = size.width / week.size
        val barWidth = (slot * 0.44f).coerceAtMost(20.dp.toPx())
        val corner = CornerRadius(barWidth / 2f)

        week.forEachIndexed { index, day ->
            val fraction = (day.seconds.toFloat() / maxSeconds).coerceIn(0.03f, 1f)
            val barHeight = size.height * fraction * progress[index].value
            val left = slot * index + (slot - barWidth) / 2f
            val color = when {
                day.epochDay == todayEpochDay -> primary
                index == selectedIndex -> selected
                else -> inactive
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = corner
            )
        }
    }
}

@Composable
private fun MostPlayedSection(
    tracks: List<HomeTrack>,
    onTrackClick: (List<Track>, Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackList = remember(tracks) { tracks.map { it.track } }

    Column(modifier = modifier) {
        EditorialSectionHeader(title = stringResource(R.string.home_most_played))

        Spacer(modifier = Modifier.height(EditorialSpacing.md))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            tracks.forEachIndexed { index, item ->
                val first = index == 0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .editorialClickable {
                            onTrackClick(trackList, item.track)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        style = if (first) EditorialType.rankFirst else EditorialType.rank,
                        color = if (first) MaterialTheme.colorScheme.primary else EditorialFaint,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(if (first) 58.dp else 50.dp)
                    )

                    Spacer(modifier = Modifier.width(EditorialSpacing.sm))

                    GlowCover(
                        uri = item.track.coverArtUri,
                        modifier = Modifier.size(if (first) 60.dp else 52.dp),
                        cornerRadius = 16.dp
                    )

                    Spacer(modifier = Modifier.width(EditorialSpacing.sm))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.track.title
                                ?: stringResource(R.string.unknown_title),
                            style = EditorialType.cardTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = item.track.artist
                                ?: stringResource(R.string.unknown_artist),
                            style = EditorialType.secondary.copy(fontSize = 12.sp),
                            color = EditorialSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    item.playCount?.let { count ->
                        Spacer(modifier = Modifier.width(EditorialSpacing.xs))

                        Text(
                            text = pluralStringResource(
                                R.plurals.home_play_count,
                                count,
                                count
                            ),
                            style = EditorialType.tiny.copy(letterSpacing = 0.3.sp),
                            color = EditorialTiny,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RediscoverSection(
    tracks: List<Track>,
    onTrackClick: (List<Track>, Track) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        EditorialSectionHeader(title = stringResource(R.string.home_rediscover))

        Spacer(modifier = Modifier.height(EditorialSpacing.md))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(EditorialSpacing.sm)) {
            itemsIndexed(
                items = tracks,
                key = { _, track -> track.uri.toString() }
            ) { _, track ->
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .editorialClickable { onTrackClick(tracks, track) }
                ) {
                    GlowCover(
                        uri = track.coverArtUri,
                        modifier = Modifier.size(120.dp),
                        cornerRadius = 20.dp
                    )

                    Spacer(modifier = Modifier.height(EditorialSpacing.xs))

                    Text(
                        text = track.title ?: stringResource(R.string.unknown_title),
                        style = EditorialType.tileTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = track.artist ?: stringResource(R.string.unknown_artist),
                        style = EditorialType.secondary.copy(fontSize = 12.sp),
                        color = EditorialSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun greetingText(): String {
    val hour = remember { LocalTime.now().hour }

    return stringResource(
        when (hour) {
            in 5..11 -> R.string.home_good_morning
            in 12..17 -> R.string.home_good_afternoon
            else -> R.string.home_good_evening
        }
    )
}

private fun paletteTint(color: Color?, alpha: Float): Color {
    if (color == null || color == Color.Unspecified || color.alpha <= 0.01f) {
        return Color(0xFF16201F).copy(alpha = alpha)
    }

    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color.toArgb(), hsv)

    return Color.hsl(hsv[0], 0.55f, 0.26f).copy(alpha = alpha)
}

private fun formatClock(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%d:%02d".format(Locale.US, minutes, seconds)
}

private fun formatListeningTime(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
    }
}

private fun dayLabel(epochDay: Long): String {
    return LocalDate.ofEpochDay(epochDay)
        .dayOfWeek
        .getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
}

private fun dayFullLabel(epochDay: Long): String {
    return LocalDate.ofEpochDay(epochDay)
        .dayOfWeek
        .getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
}
