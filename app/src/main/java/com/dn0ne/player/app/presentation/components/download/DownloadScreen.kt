package com.dn0ne.player.app.presentation.components.download

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.dn0ne.player.R
import com.dn0ne.player.app.domain.download.OnlineTrack
import com.dn0ne.player.app.presentation.DownloadState
import com.dn0ne.player.app.presentation.DownloadViewModel
import com.dn0ne.player.app.presentation.components.topbar.LazyColumnWithCollapsibleTopBar
import org.koin.androidx.compose.koinViewModel

@Composable
fun DownloadScreen(
    onBackClick: () -> Unit,
    onPreviewStarted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var collapseFraction by remember {
        mutableFloatStateOf(0f)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler {
        onBackClick()
    }

    LaunchedEffect(state.previewTrackId) {
        if (state.previewTrackId != null) {
            onPreviewStarted()
        }
    }

    LazyColumnWithCollapsibleTopBar(
        topBarContent = {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBackIosNew,
                    contentDescription = context.resources.getString(R.string.back)
                )
            }

            Text(
                text = context.resources.getString(R.string.download_title),
                fontSize = lerp(
                    MaterialTheme.typography.titleLarge.fontSize,
                    MaterialTheme.typography.displaySmall.fontSize,
                    collapseFraction
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp)
            )
        },
        maxTopBarHeight = 140.dp,
        maxTopBarHeightLandscape = 100.dp,
        collapseFraction = {
            collapseFraction = it
        },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        contentVerticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        item {
            SearchRow(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onSearchClick = viewModel::onSearchClick
            )
        }

        if (state.isSearching) {
            item {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }

        if (!state.hasSearched) {
            item {
                Text(
                    text = context.resources.getString(R.string.download_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        if (state.hasSearched && !state.isSearching && state.results.isEmpty()) {
            item {
                Text(
                    text = context.resources.getString(R.string.download_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        items(
            items = state.results,
            key = { it.id }
        ) { track ->
            DownloadTrackRow(
                track = track,
                state = state,
                onPreviewClick = { viewModel.onPreviewClick(track) },
                onDownloadClick = { viewModel.onDownloadClick(track) }
            )
        }
    }
}

@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit
) {
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = {
                Text(text = context.resources.getString(R.string.download_search_hint))
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchClick() }),
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        FilledIconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = context.resources.getString(R.string.download_search)
            )
        }
    }
}

@Composable
private fun DownloadTrackRow(
    track: OnlineTrack,
    state: DownloadState,
    onPreviewClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val context = LocalContext.current
    val isDownloading = track.id in state.downloadingIds
    val isDownloaded = track.id in state.downloadedIds
    val isPreviewing = state.previewTrackId == track.id
    val isPreparingPreview = isPreviewing && state.isPreparingPreview
    val progress = state.downloadProgress[track.id] ?: 0f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp)
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = buildString {
                    if (track.artist.isNotBlank()) {
                        append(track.artist)
                        append(" • ")
                    }
                    append(formatDuration(track.durationSeconds))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onPreviewClick,
            enabled = !isPreparingPreview
        ) {
            if (isPreparingPreview) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (isPreviewing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = context.resources.getString(R.string.download_preview)
                )
            }
        }

        when {
            isDownloaded -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = context.resources.getString(R.string.download_complete),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            isDownloading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                }
            }

            else -> {
                IconButton(onClick = onDownloadClick) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = context.resources.getString(R.string.download_button)
                    )
                }
            }
        }
    }
}

private fun formatDuration(durationSeconds: Int): String {
    if (durationSeconds <= 0) return "--:--"

    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60

    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
