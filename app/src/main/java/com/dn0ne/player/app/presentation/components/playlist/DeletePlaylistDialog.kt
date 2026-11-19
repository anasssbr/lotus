package com.dn0ne.player.app.presentation.components.playlist

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.dn0ne.player.R
import com.dn0ne.player.app.domain.track.Playlist

@Composable
fun DeletePlaylistDialog(
    playlists: List<Playlist>,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val singlePlaylist = playlists.singleOrNull()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(text = context.resources.getString(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(text = context.resources.getString(R.string.cancel))
            }
        },
        title = {
            Text(
                text = if (singlePlaylist != null) {
                    context.resources.getString(
                        R.string.delete_playlist_dialog_title,
                        singlePlaylist.name ?: context.resources.getString(R.string.unknown)
                    )
                } else {
                    context.resources.getString(
                        R.string.delete_playlists_dialog_title,
                        playlists.size
                    )
                }
            )
        },
        text = {
            Text(text = context.resources.getString(R.string.delete_playlist_dialog_text))
        }
    )
}
