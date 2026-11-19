package com.dn0ne.player.app.presentation.components.selection

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dn0ne.player.app.domain.sort.PlaylistSort
import com.dn0ne.player.app.domain.sort.SortOrder
import com.dn0ne.player.app.domain.sort.sortedBy
import com.dn0ne.player.app.domain.track.Playlist
import com.dn0ne.player.app.presentation.components.CoverArt
import com.dn0ne.player.app.presentation.components.playlist.FourArtsPreview
import com.dn0ne.player.app.presentation.components.playlist.TrackCountBubble
import com.dn0ne.player.app.presentation.components.playlist.playlistCoverCacheKey
import com.dn0ne.player.app.presentation.components.playlist.playlistCoverUri
import com.kmpalette.rememberDominantColorState
import kotlinx.coroutines.launch

fun LazyGridScope.selectionCards(
    playlists: List<Playlist>,
    selectedPlaylists: List<Playlist>,
    fallbackPlaylistTitle: String,
    sort: PlaylistSort,
    sortOrder: SortOrder,
    onCardClick: (Playlist) -> Unit,
    showSinglePreview: Boolean = false,
) {
    items(
        items = playlists.sortedBy(sort, sortOrder),
        key = { "${it.name}-${it.trackList}" }
    ) { playlist ->
        SelectionCard(
            title = playlist.name
                ?: fallbackPlaylistTitle,
            trackCount = playlist.trackList.size,
            coverArtPreviewUris = playlist.trackList
                .take(if (showSinglePreview) 1 else 4)
                .map { it.coverArtUri },
            coverPath = playlist.coverPath,
            isSelected = playlist in selectedPlaylists,
            modifier = Modifier
                .clip(ShapeDefaults.Large)
                .clickable {
                    onCardClick(playlist)
                }
                .animateItem(fadeInSpec = null, fadeOutSpec = null)
        )
    }
}

@Composable
fun SelectionCard(
    title: String,
    trackCount: Int,
    coverArtPreviewUris: List<Uri>,
    isSelected: Boolean,
    coverPath: String? = null,
    modifier: Modifier = Modifier
) {
    val customCoverUri = remember(coverPath) {
        playlistCoverUri(coverPath)
    }
    val customCoverCacheKey = remember(coverPath) {
        playlistCoverCacheKey(coverPath)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            if (customCoverUri != null || coverArtPreviewUris.size <= 1) {
                val dominantColorState = rememberDominantColorState()
                val coroutineScope = rememberCoroutineScope()
                CoverArt(
                    uri = customCoverUri ?: coverArtPreviewUris.firstOrNull() ?: Uri.EMPTY,
                    cacheKey = customCoverCacheKey,
                    onCoverArtLoaded = { bitmap ->
                        bitmap?.let {
                            coroutineScope.launch {
                                dominantColorState.updateFrom(it)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeDefaults.Large)
                )

                TrackCountBubble(
                    trackCount = trackCount,
                    contentColor = dominantColorState.onColor,
                    containerColor = dominantColorState.color,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-4).dp)
                )
            } else {
                FourArtsPreview(
                    coverArtPreviewUris = coverArtPreviewUris,
                    modifier = Modifier.fillMaxWidth()
                )

                TrackCountBubble(
                    trackCount = trackCount,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-4).dp)
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(ShapeDefaults.Large)
                        .background(color = MaterialTheme.colorScheme.primary.copy(alpha = .5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}