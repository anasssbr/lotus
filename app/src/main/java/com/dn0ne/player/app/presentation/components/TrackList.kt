package com.dn0ne.player.app.presentation.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dn0ne.player.app.domain.track.Track

fun LazyListScope.trackList(
    trackList: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean = false,
    onTrackClick: (Track) -> Unit,
    onPlayNextClick: (Track) -> Unit,
    onAddToQueueClick: (Track) -> Unit,
    onAddToPlaylistClick: (Track) -> Unit,
    onViewTrackInfoClick: (Track) -> Unit,
    onGoToAlbumClick: (Track) -> Unit,
    onGoToArtistClick: (Track) -> Unit,
    onLongClick: (Track) -> Unit = {},
    onDeleteFromDeviceClick: (Track) -> Unit = {}
) {
    if (trackList.isEmpty()) {
        item {
            NothingYet()
        }
    }

    itemsIndexed(
        items = trackList,
        key = { _, track -> track.uri }
    ) { index, track ->
        val reduceMotion = rememberReduceMotion()

        TrackListItem(
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
            onDeleteFromDeviceClick = { onDeleteFromDeviceClick(track) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .staggeredFadeIn(index = index, enabled = !reduceMotion)
                .animateItem(fadeInSpec = null, fadeOutSpec = null)
        )
    }
}

fun LazyGridScope.trackList(
    trackList: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean = false,
    onTrackClick: (Track) -> Unit,
    onPlayNextClick: (Track) -> Unit,
    onAddToQueueClick: (Track) -> Unit,
    onAddToPlaylistClick: (Track) -> Unit,
    onViewTrackInfoClick: (Track) -> Unit,
    onGoToAlbumClick: (Track) -> Unit,
    onGoToArtistClick: (Track) -> Unit,
    onLongClick: (Track) -> Unit,
    onDeleteFromDeviceClick: (Track) -> Unit = {},
) {
    if (trackList.isEmpty()) {
        item {
            NothingYet()
        }
    }

    itemsIndexed(
        items = trackList,
        key = { _, track -> track.uri }
    ) { index, track ->
        val reduceMotion = rememberReduceMotion()

        TrackListItem(
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
            onDeleteFromDeviceClick = { onDeleteFromDeviceClick(track) },
            modifier = Modifier
                .fillMaxWidth()
                .staggeredFadeIn(index = index, enabled = !reduceMotion)
                .animateItem(fadeInSpec = null, fadeOutSpec = null)
        )
    }
}
