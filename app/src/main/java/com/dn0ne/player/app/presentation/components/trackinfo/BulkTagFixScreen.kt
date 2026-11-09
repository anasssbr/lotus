package com.dn0ne.player.app.presentation.components.trackinfo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import com.dn0ne.player.R
import com.dn0ne.player.app.presentation.components.topbar.LazyColumnWithCollapsibleTopBar

@Composable
fun BulkTagFixScreen(
    state: BulkTagFixState,
    onBackClick: () -> Unit,
    onScanClick: () -> Unit,
    onCancelScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var collapseFraction by remember {
        mutableFloatStateOf(0f)
    }

    BackHandler {
        onBackClick()
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
                text = context.resources.getString(R.string.missing_album_info),
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
        collapseFraction = {
            collapseFraction = it
        },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        contentHorizontalAlignment = Alignment.CenterHorizontally,
        contentVerticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = context.resources.getString(R.string.missing_album_info_explain),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (state.suggestions.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = context.resources.getString(
                                R.string.bulk_tag_fix_progress,
                                state.completedCount,
                                state.suggestions.size
                            )
                        )

                        if (state.isScanning) {
                            OutlinedButton(
                                onClick = onCancelScanClick
                            ) {
                                Text(text = context.resources.getString(R.string.stop_scanning))
                            }
                        } else {
                            Button(
                                onClick = onScanClick
                            ) {
                                Text(text = context.resources.getString(R.string.find_matches))
                            }
                        }
                    }

                    if (state.isScanning) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Text(
                        text = context.resources.getString(R.string.no_tracks_missing_album_info),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        items(
            items = state.suggestions,
            key = { it.track.uri.toString() }
        ) { suggestion ->
            BulkTagFixSuggestionCard(
                suggestion = suggestion,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BulkTagFixSuggestionCard(
    suggestion: BulkTagFixSuggestion,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(ShapeDefaults.Medium)
            .background(color = MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            AsyncImage(
                model = suggestion.track.coverArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(ShapeDefaults.Small)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = suggestion.track.title
                        ?: context.resources.getString(R.string.unknown_title),
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = suggestion.track.artist
                        ?: context.resources.getString(R.string.unknown_artist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (suggestion.status) {
                BulkTagFixStatus.Searching -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )

                BulkTagFixStatus.Found -> Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )

                BulkTagFixStatus.NotFound -> Icon(
                    imageVector = Icons.Rounded.SearchOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                BulkTagFixStatus.Skipped -> Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                BulkTagFixStatus.Pending -> Unit
            }
        }

        when (suggestion.status) {
            BulkTagFixStatus.Found -> {
                suggestion.searchResult?.let { searchResult ->
                    TagRow(
                        tag = context.resources.getString(R.string.album),
                        value = searchResult.album,
                        enableCopy = false,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    TagRow(
                        tag = context.resources.getString(R.string.album_artist),
                        value = searchResult.albumArtist,
                        enableCopy = false,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    searchResult.year?.let { year ->
                        TagRow(
                            tag = context.resources.getString(R.string.year),
                            value = year,
                            enableCopy = false,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            BulkTagFixStatus.Searching -> Text(
                text = context.resources.getString(R.string.bulk_tag_fix_searching),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            BulkTagFixStatus.NotFound -> Text(
                text = context.resources.getString(R.string.bulk_tag_fix_not_found),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            BulkTagFixStatus.Skipped -> Text(
                text = context.resources.getString(R.string.bulk_tag_fix_skipped),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            BulkTagFixStatus.Pending -> Text(
                text = context.resources.getString(R.string.bulk_tag_fix_pending),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
