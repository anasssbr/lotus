package com.dn0ne.player.app.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dn0ne.player.R
import com.dn0ne.player.app.domain.listening.DailyListening
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.presentation.components.CoverArt
import java.time.LocalDate
import java.util.Locale

@Composable
fun HomeScreen(
    state: HomeState,
    onSearchClick: () -> Unit,
    onTrackClick: (List<Track>, Track) -> Unit,
    onPlayMixClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 220.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        item(key = "header") {
            HomeHeader(onSearchClick = onSearchClick)
        }

        item(key = "recent") {
            RecentlyPlayedSection(
                state = state,
                onTrackClick = onTrackClick
            )
        }

        item(key = "mix") {
            MixSection(
                mix = state.mix,
                hasTracks = state.hasTracks,
                onPlayClick = onPlayMixClick,
                onTrackClick = { track -> onTrackClick(state.mix, track) }
            )
        }

        item(key = "listening") {
            ListeningTimeSection(
                todaySeconds = state.todaySeconds,
                week = state.week
            )
        }

        item(key = "most-played") {
            MostPlayedSection(
                tracks = state.mostPlayed,
                onTrackClick = onTrackClick
            )
        }
    }
}

@Composable
private fun HomeHeader(onSearchClick: () -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.home_welcome),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onSearchClick)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = stringResource(R.string.home_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentlyPlayedSection(
    state: HomeState,
    onTrackClick: (List<Track>, Track) -> Unit
) {
    Column {
        Text(
            text = stringResource(
                if (state.recentlyPlayedIsFallback) R.string.home_recently_added
                else R.string.home_recently_played
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (state.recentlyPlayedIsFallback) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_recently_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.recentlyPlayed.isEmpty()) {
            EmptyHint(text = stringResource(R.string.home_no_tracks))
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(
                    items = state.recentlyPlayed,
                    key = { it.uri.toString() }
                ) { track ->
                    RecentlyPlayedCard(
                        track = track,
                        onClick = { onTrackClick(state.recentlyPlayed, track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentlyPlayedCard(
    track: Track,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        CoverArt(
            uri = track.coverArtUri,
            modifier = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(20.dp))
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title ?: stringResource(R.string.unknown_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = track.artist ?: stringResource(R.string.unknown_artist),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun MixSection(
    mix: List<Track>,
    hasTracks: Boolean,
    onPlayClick: () -> Unit,
    onTrackClick: (Track) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.home_my_mix),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        MixCard(
            mix = mix,
            hasTracks = hasTracks,
            onPlayClick = onPlayClick
        )

        if (mix.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))

            mix.take(5).forEach { track ->
                HomeTrackRow(
                    track = track,
                    onClick = { onTrackClick(track) }
                )
            }
        }
    }
}

@Composable
private fun MixCard(
    mix: List<Track>,
    hasTracks: Boolean,
    onPlayClick: () -> Unit
) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0E4A44),
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                )
                .clickable(enabled = mix.isNotEmpty(), onClick = onPlayClick)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                if (mix.isNotEmpty()) {
                    CoverArt(
                        uri = mix.first().coverArtUri,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_my_mix),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (mix.isNotEmpty()) {
                        stringResource(R.string.home_my_mix_subtitle, mix.size)
                    } else {
                        stringResource(
                            if (hasTracks) R.string.home_no_history else R.string.home_no_tracks
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            FilledIconButton(
                onClick = onPlayClick,
                enabled = mix.isNotEmpty(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.home_my_mix),
                    modifier = Modifier.size(28.dp)
                )
            }
    }
}

@Composable
private fun HomeTrackRow(
    track: Track,
    playCount: Int? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(
            uri = track.coverArtUri,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title ?: stringResource(R.string.unknown_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = track.artist ?: stringResource(R.string.unknown_artist),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        playCount?.let { count ->
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = pluralStringResource(R.plurals.home_play_count, count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ListeningTimeSection(
    todaySeconds: Long,
    week: List<DailyListening>
) {
    Column {
        Text(
            text = stringResource(R.string.home_listening_time),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatListeningTime(todaySeconds),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = stringResource(R.string.home_today),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (week.isEmpty()) {
                EmptyHint(text = stringResource(R.string.home_no_history))
            } else {
                WeeklyBars(week = week)
            }
        }
    }
}

@Composable
private fun WeeklyBars(week: List<DailyListening>) {
    val maxSeconds = (week.maxOfOrNull { it.seconds } ?: 0L).coerceAtLeast(1L)
    val todayEpochDay = LocalDate.now().toEpochDay()
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        week.forEach { day ->
            val isToday = day.epochDay == todayEpochDay

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val fraction = (day.seconds.toFloat() / maxSeconds).coerceIn(0.04f, 1f)
                        val barHeight = size.height * fraction
                        val barWidth = size.width

                        drawRoundRect(
                            color = if (isToday) activeColor else inactiveColor,
                            topLeft = Offset(0f, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = dayLabel(day.epochDay),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) activeColor else labelColor
                )
            }
        }
    }
}

@Composable
private fun MostPlayedSection(
    tracks: List<HomeTrack>,
    onTrackClick: (List<Track>, Track) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.home_most_played),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (tracks.isEmpty()) {
            EmptyHint(text = stringResource(R.string.home_no_history))
        } else {
            val trackList = tracks.map { it.track }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                tracks.forEach { item ->
                    MostPlayedRow(
                        item = item,
                        onClick = { onTrackClick(trackList, item.track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MostPlayedRow(
    item: HomeTrack,
    onClick: () -> Unit
) {
    HomeTrackRow(
        track = item.track,
        playCount = item.playCount,
        onClick = onClick
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
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
