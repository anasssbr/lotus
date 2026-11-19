package com.dn0ne.player.app.presentation.components.playlist

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.dn0ne.player.R
import com.dn0ne.player.app.domain.track.Track

@Composable
fun DeleteTracksDialog(
    tracks: List<Track>,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val singleTrack = tracks.singleOrNull()

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
                text = if (singleTrack != null) {
                    context.resources.getString(
                        R.string.delete_track_dialog_title,
                        singleTrack.title ?: context.resources.getString(R.string.unknown_title)
                    )
                } else {
                    context.resources.getString(
                        R.string.delete_tracks_dialog_title,
                        tracks.size
                    )
                }
            )
        },
        text = {
            Text(text = context.resources.getString(R.string.delete_track_dialog_text))
        }
    )
}
