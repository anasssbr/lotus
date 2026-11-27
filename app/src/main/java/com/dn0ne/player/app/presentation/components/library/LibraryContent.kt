package com.dn0ne.player.app.presentation.components.library

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dn0ne.player.R
import com.dn0ne.player.app.domain.sort.PlaylistSort
import com.dn0ne.player.app.domain.sort.SortOrder
import com.dn0ne.player.app.domain.sort.TrackSort
import com.dn0ne.player.app.domain.sort.sortedBy
import com.dn0ne.player.app.domain.track.Playlist
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.presentation.components.CoverArt
import com.dn0ne.player.app.presentation.components.PlayingEqualizer
import com.dn0ne.player.app.presentation.components.TrackMenuButton
import com.dn0ne.player.app.presentation.components.background.dynamicPaletteFrom
import com.dn0ne.player.app.presentation.components.design.EditorialSecondary
import com.dn0ne.player.app.presentation.components.design.EditorialSpacing
import com.dn0ne.player.app.presentation.components.design.EditorialTiny
import com.dn0ne.player.app.presentation.components.design.EditorialType
import com.dn0ne.player.app.presentation.components.design.GlowCover
import com.dn0ne.player.app.presentation.components.design.editorialEntrance
import com.dn0ne.player.app.presentation.components.playlist.FourArtsPreview
import com.dn0ne.player.app.presentation.components.playlist.playlistCoverCacheKey
import com.dn0ne.player.app.presentation.components.playlist.playlistCoverUri
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Editorial Library building blocks.
 *
 * Everything in this file is Library-only. Shared track rows and playlist cards used by
 * other screens stay untouched, so the rest of the app keeps its existing look.
 */

data class LibraryStickyHeader(
    val label: String,
    val isSectionStart: Boolean
)

/** Uppercase A-Z (or date) label used by the sticky headers and the fast scroller bubble. */
fun librarySectionLabel(track: Track, sort: TrackSort): String {
    return when (sort) {
        TrackSort.Title -> letterOrHash(track.title)
        TrackSort.Album -> letterOrHash(track.album)
        TrackSort.Artist -> letterOrHash(track.artist)
        TrackSort.Genre -> letterOrHash(track.genre)
        TrackSort.Year -> track.year?.trim()?.takeIf { it.isNotEmpty() } ?: "—"
        TrackSort.TrackNumber -> track.trackNumber?.trim()?.takeIf { it.isNotEmpty() } ?: "—"
        TrackSort.DateModified -> dateSectionLabel(track.dateModified)
    }
}

private fun letterOrHash(value: String?): String {
    val first = value?.trim()?.firstOrNull() ?: return "#"
    return first.uppercaseChar().toString()
}

private val libraryDateSectionFormatter by lazy {
    SimpleDateFormat("MMM yyyy", Locale.getDefault())
}

private fun dateSectionLabel(dateModifiedSeconds: Long): String {
    if (dateModifiedSeconds <= 0L) return "—"
    return libraryDateSectionFormatter.format(Date(dateModifiedSeconds * 1000L))
}

fun formatLibraryCount(count: Int): String {
    return NumberFormat.getIntegerInstance(Locale.getDefault()).format(count)
}

fun formatLibraryDuration(durationMs: Int): String {
    if (durationMs <= 0) return "--:--"

    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return if (minutes >= 60) {
        val hours = minutes / 60
        "$hours:${(minutes % 60).toString().padStart(2, '0')}:${
            seconds.toString().padStart(2, '0')
        }"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

fun Playlist.mostCommonCoverUri(): Uri? {
    return trackList
        .map { it.coverArtUri }
        .filter { it != Uri.EMPTY }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
}

@Composable
fun LibraryRoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun rememberGenreColor(name: String): Color {
    val fallback = MaterialTheme.colorScheme.primary
    return remember(name, fallback) {
        dynamicPaletteFrom(null, name)?.colors?.firstOrNull() ?: fallback
    }
}

@Composable
fun rememberArtistColors(name: String): List<Color> {
    val fallback = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary
    )
    return remember(name, fallback) {
        dynamicPaletteFrom(null, name)?.colors?.take(2)?.takeIf { it.size == 2 } ?: fallback
    }
}

/* ------------------------------------------------------------------ tracks */

@OptIn(ExperimentalFoundationApi::class)
fun LazyGridScope.libraryTracks(
    tracks: List<Track>,
    sort: TrackSort,
    currentTrack: Track?,
    isPlaying: Boolean,
    emptyMessage: String,
    onTrackClick: (Track) -> Unit,
    onPlayNextClick: (Track) -> Unit,
    onAddToQueueClick: (Track) -> Unit,
    onAddToPlaylistClick: (Track) -> Unit,
    onViewTrackInfoClick: (Track) -> Unit,
    onGoToAlbumClick: (Track) -> Unit,
    onGoToArtistClick: (Track) -> Unit,
    onLongClick: (Track) -> Unit,
    onDeleteFromDeviceClick: (Track) -> Unit
) {
    if (tracks.isEmpty()) {
        libraryEmptyItem(message = emptyMessage, icon = Icons.Rounded.SearchOff)
        return
    }

    itemsIndexed(
        items = tracks,
        key = { _, track -> track.uri }
    ) { index, track ->
        val label = librarySectionLabel(track, sort)
        val previousLabel = tracks.getOrNull(index - 1)?.let { librarySectionLabel(it, sort) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateItem(fadeInSpec = null, fadeOutSpec = null)
                .editorialEntrance(index = index, enabled = index < 5)
        ) {
            if (label != previousLabel) {
                LibrarySectionLabel(label = label)
            }

            LibraryTrackRow(
                track = track,
                isCurrent = currentTrack == track,
                isPlaying = isPlaying,
                onClick = { onTrackClick(track) },
                onLongClick = { onLongClick(track) },
                onPlayNextClick = { onPlayNextClick(track) },
                onAddToQueueClick = { onAddToQueueClick(track) },
                onAddToPlaylistClick = { onAddToPlaylistClick(track) },
                onViewTrackInfoClick = { onViewTrackInfoClick(track) },
                onGoToAlbumClick = { onGoToAlbumClick(track) },
                onGoToArtistClick = { onGoToArtistClick(track) },
                onDeleteFromDeviceClick = { onDeleteFromDeviceClick(track) }
            )
        }
    }
}

@Composable
fun LibrarySectionLabel(
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryTrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayNextClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onViewTrackInfoClick: () -> Unit,
    onGoToAlbumClick: () -> Unit,
    onGoToArtistClick: () -> Unit,
    onDeleteFromDeviceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onLongClick = onLongClick
            ) {
                onClick()
            }
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else Color.Transparent
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            GlowCover(
                uri = track.coverArtUri,
                modifier = Modifier.size(56.dp),
                cornerRadius = 16.dp
            )

            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                ) {
                    PlayingEqualizer(
                        playing = isPlaying,
                        modifier = Modifier.size(width = 13.dp, height = 9.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title ?: stringResource(R.string.unknown_title),
                style = EditorialType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = track.artist ?: stringResource(R.string.unknown_artist),
                style = EditorialType.secondary,
                color = EditorialSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatLibraryDuration(track.duration),
            style = EditorialType.secondary,
            color = EditorialTiny
        )

        TrackMenuButton(
            onPlayNextClick = onPlayNextClick,
            onAddToQueueClick = onAddToQueueClick,
            onAddToPlaylistClick = onAddToPlaylistClick,
            onViewTrackInfoClick = onViewTrackInfoClick,
            onGoToAlbumClick = onGoToAlbumClick,
            onGoToArtistClick = onGoToArtistClick,
            onDeleteFromDeviceClick = onDeleteFromDeviceClick,
            tint = EditorialSecondary
        )
    }
}

/* --------------------------------------------------------------- playlists */

@OptIn(ExperimentalFoundationApi::class)
fun LazyGridScope.libraryPlaylistCards(
    playlists: List<Playlist>,
    fallbackTitle: String,
    sort: PlaylistSort,
    sortOrder: SortOrder,
    emptyMessage: String,
    onCardClick: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit
) {
    if (playlists.isEmpty()) {
        libraryEmptyItem(message = emptyMessage, icon = Icons.AutoMirrored.Rounded.PlaylistPlay)
        return
    }

    itemsIndexed(
        items = playlists.sortedBy(sort, sortOrder),
        key = { _, playlist -> "playlist-${playlist.name}-${playlist.trackList.size}" }
    ) { index, playlist ->
        LibraryPlaylistCard(
            playlist = playlist,
            title = playlist.name ?: fallbackTitle,
            onClick = { onCardClick(playlist) },
            onLongClick = { onLongClick(playlist) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .editorialEntrance(index = index, enabled = index < 5)
                .animateItem(fadeInSpec = null, fadeOutSpec = null)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPlaylistCard(
    playlist: Playlist,
    title: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val customCoverUri = remember(playlist.coverPath) { playlistCoverUri(playlist.coverPath) }
    val customCoverCacheKey = remember(playlist.coverPath) {
        playlistCoverCacheKey(playlist.coverPath)
    }
    val previews = remember(playlist.trackList) {
        playlist.trackList.map { it.coverArtUri }.take(4)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onLongClick = onLongClick) { onClick() }
    ) {
        when {
            customCoverUri != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    CoverArt(
                        uri = customCoverUri,
                        cacheKey = customCoverCacheKey,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            previews.size >= 2 -> {
                FourArtsPreview(
                    coverArtPreviewUris = previews,
                    containerShape = RoundedCornerShape(20.dp),
                    artShape = RoundedCornerShape(12.dp),
                    spaceBetween = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            else -> {
                GlowCover(
                    uri = previews.firstOrNull() ?: Uri.EMPTY,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    cornerRadius = 20.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = title,
            style = EditorialType.cardTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = stringResource(R.string.home_tracks_count, playlist.trackList.size),
            style = EditorialType.secondary,
            color = EditorialSecondary,
            maxLines = 1
        )
    }
}

/* ------------------------------------------------------------------ albums */

@OptIn(ExperimentalFoundationApi::class)
fun LazyGridScope.libraryAlbumCards(
    playlists: List<Playlist>,
    fallbackTitle: String,
    fallbackArtist: String,
    sort: PlaylistSort,
    sortOrder: SortOrder,
    emptyMessage: String,
    onCardClick: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit
) {
    if (playlists.isEmpty()) {
        libraryEmptyItem(message = emptyMessage, icon = Icons.Rounded.Album)
        return
    }

    itemsIndexed(
        items = playlists.sortedBy(sort, sortOrder),
        key = { _, playlist -> "album-${playlist.name}-${playlist.trackList.size}" }
    ) { index, playlist ->
        LibraryAlbumCard(
            playlist = playlist,
            title = playlist.name ?: fallbackTitle,
            artist = playlist.trackList.firstOrNull()?.artist ?: fallbackArtist,
            onClick = { onCardClick(playlist) },
            onLongClick = { onLongClick(playlist) },
            modifier = Modifier
                .fillMaxWidth()
                // Second column starts a little lower, like a magazine spread.
                .padding(top = if (index % 2 == 1) 24.dp else 0.dp, bottom = 16.dp)
                .editorialEntrance(index = index, enabled = index < 5)
                .animateItem(fadeInSpec = null, fadeOutSpec = null)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryAlbumCard(
    playlist: Playlist,
    title: String,
    artist: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cover = remember(playlist.trackList) { playlist.mostCommonCoverUri() }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onLongClick = onLongClick) { onClick() }
    ) {
        GlowCover(
            uri = cover ?: Uri.EMPTY,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 20.dp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = title,
            style = EditorialType.cardTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = artist,
            style = EditorialType.secondary,
            color = EditorialSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = stringResource(R.string.home_tracks_count, playlist.trackList.size),
            style = EditorialType.tiny,
            color = EditorialTiny,
            maxLines = 1
        )
    }
}

/* ----------------------------------------------------------------- artists */

@OptIn(ExperimentalFoundationApi::class)
fun LazyGridScope.libraryArtistCards(
    playlists: List<Playlist>,
    fallbackTitle: String,
    sort: PlaylistSort,
    sortOrder: SortOrder,
    emptyMessage: String,
    onCardClick: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit
) {
    if (playlists.isEmpty()) {
        libraryEmptyItem(message = emptyMessage, icon = Icons.Rounded.Person)
        return
    }

    itemsIndexed(
        items = playlists.sortedBy(sort, sortOrder),
        key = { _, playlist -> "artist-${playlist.name}-${playlist.trackList.size}" }
    ) { index, playlist ->
        LibraryArtistCard(
            playlist = playlist,
            title = playlist.name ?: fallbackTitle,
            onClick = { onCardClick(playlist) },
            onLongClick = { onLongClick(playlist) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .editorialEntrance(index = index, enabled = index < 5)
                .animateItem(fadeInSpec = null, fadeOutSpec = null)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryArtistCard(
    playlist: Playlist,
    title: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cover = remember(playlist.trackList) { playlist.mostCommonCoverUri() }
    val colors = rememberArtistColors(title)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onLongClick = onLongClick) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (cover != null) {
            GlowCover(
                uri = cover,
                modifier = Modifier.size(96.dp),
                cornerRadius = 48.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                colors.first().copy(alpha = 0.85f),
                                colors.last().copy(alpha = 0.55f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = title,
            style = EditorialType.tileTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = stringResource(R.string.home_tracks_count, playlist.trackList.size),
            style = EditorialType.tiny,
            color = EditorialTiny,
            maxLines = 1
        )
    }
}

/* ------------------------------------------------------------------ genres */

@OptIn(ExperimentalFoundationApi::class)
fun LazyGridScope.libraryGenreCards(
    playlists: List<Playlist>,
    fallbackTitle: String,
    sort: PlaylistSort,
    sortOrder: SortOrder,
    emptyMessage: String,
    onCardClick: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit
) {
    if (playlists.isEmpty()) {
        libraryEmptyItem(message = emptyMessage, icon = Icons.Rounded.Category)
        return
    }

    itemsIndexed(
        items = playlists.sortedBy(sort, sortOrder),
        key = { _, playlist -> "genre-${playlist.name}-${playlist.trackList.size}" }
    ) { index, playlist ->
        LibraryGenreCard(
            playlist = playlist,
            title = playlist.name ?: fallbackTitle,
            onClick = { onCardClick(playlist) },
            onLongClick = { onLongClick(playlist) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .editorialEntrance(index = index, enabled = index < 5)
                .animateItem(fadeInSpec = null, fadeOutSpec = null)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryGenreCard(
    playlist: Playlist,
    title: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = rememberGenreColor(title)
    val previews = remember(playlist.trackList) {
        playlist.trackList.map { it.coverArtUri }.filter { it != Uri.EMPTY }.take(4)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        color.copy(alpha = 0.92f),
                        color.copy(alpha = 0.52f)
                    )
                )
            )
            .combinedClickable(onLongClick = onLongClick) { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = EditorialType.cardTitle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.home_tracks_count, playlist.trackList.size),
                style = EditorialType.secondary,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1
            )
        }

        if (previews.isNotEmpty()) {
            GenreCoverPeek(
                previews = previews,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 16.dp, y = 18.dp)
            )
        }
    }
}

@Composable
private fun GenreCoverPeek(
    previews: List<Uri>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(2) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) { column ->
                    val uri = previews.getOrNull(row * 2 + column)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.20f))
                    ) {
                        if (uri != null) {
                            CoverArt(
                                uri = uri,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ----------------------------------------------------------------- folders */

@OptIn(ExperimentalFoundationApi::class)
fun LazyGridScope.libraryFolderRows(
    playlists: List<Playlist>,
    fallbackTitle: String,
    sort: PlaylistSort,
    sortOrder: SortOrder,
    emptyMessage: String,
    onRowClick: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit
) {
    if (playlists.isEmpty()) {
        libraryEmptyItem(message = emptyMessage, icon = Icons.Rounded.Folder)
        return
    }

    itemsIndexed(
        items = playlists.sortedBy(sort, sortOrder),
        key = { _, playlist -> "folder-${playlist.name}-${playlist.trackList.size}" }
    ) { index, playlist ->
        LibraryFolderRow(
            playlist = playlist,
            title = playlist.name ?: fallbackTitle,
            onClick = { onRowClick(playlist) },
            onLongClick = { onLongClick(playlist) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
                .editorialEntrance(index = index, enabled = index < 5)
                .animateItem(fadeInSpec = null, fadeOutSpec = null)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryFolderRow(
    playlist: Playlist,
    title: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val path = remember(playlist.trackList) {
        playlist.trackList.firstOrNull()?.data?.substringBeforeLast('/') ?: ""
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onLongClick = onLongClick) { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = EditorialType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = stringResource(R.string.home_tracks_count, playlist.trackList.size),
                style = EditorialType.secondary,
                color = EditorialSecondary,
                maxLines = 1
            )

            if (path.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = path,
                    style = EditorialType.tiny,
                    color = EditorialTiny,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/* ------------------------------------------------------------- list layouts */

@OptIn(ExperimentalFoundationApi::class)
fun LazyGridScope.libraryPlaylistRows(
    playlists: List<Playlist>,
    fallbackTitle: String,
    sort: PlaylistSort,
    sortOrder: SortOrder,
    emptyMessage: String,
    subtitle: (Playlist) -> String,
    onRowClick: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit
) {
    if (playlists.isEmpty()) {
        libraryEmptyItem(message = emptyMessage, icon = Icons.AutoMirrored.Rounded.PlaylistPlay)
        return
    }

    itemsIndexed(
        items = playlists.sortedBy(sort, sortOrder),
        key = { _, playlist -> "row-${playlist.name}-${playlist.trackList.size}" }
    ) { index, playlist ->
        val cover = remember(playlist.trackList) { playlist.mostCommonCoverUri() }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onLongClick = { onLongClick(playlist) }) {
                    onRowClick(playlist)
                }
                .padding(vertical = 6.dp, horizontal = 4.dp)
                .animateItem(fadeInSpec = null, fadeOutSpec = null)
                .editorialEntrance(index = index, enabled = index < 5),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlowCover(
                uri = cover ?: Uri.EMPTY,
                modifier = Modifier.size(56.dp),
                cornerRadius = 16.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name ?: fallbackTitle,
                    style = EditorialType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle(playlist),
                    style = EditorialType.secondary,
                    color = EditorialSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/* ------------------------------------------------------------ empty states */

fun LazyGridScope.libraryEmptyItem(
    message: String,
    icon: ImageVector
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        LibraryEmptyState(message = message, icon = icon)
    }
}

@Composable
fun LibraryEmptyState(
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp, horizontal = EditorialSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = message,
            style = EditorialType.secondary,
            color = EditorialSecondary,
            textAlign = TextAlign.Center
        )
    }
}
