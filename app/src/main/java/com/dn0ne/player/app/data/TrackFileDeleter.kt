package com.dn0ne.player.app.data

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.dn0ne.player.app.domain.track.Track
import java.io.File

sealed interface TrackDeleteOutcome {
    data object Deleted : TrackDeleteOutcome

    data class RequiresSystemConfirmation(
        val intentSender: IntentSender,
        val tracks: List<Track>
    ) : TrackDeleteOutcome

    data class RequiresPermission(
        val intentSender: IntentSender,
        val tracks: List<Track>
    ) : TrackDeleteOutcome

    data class Failed(val reason: TrackDeleteFailure) : TrackDeleteOutcome
}

enum class TrackDeleteFailure {
    Permission,
    Unknown
}

class TrackFileDeleter(
    private val context: Context
) {
    fun delete(tracks: List<Track>): TrackDeleteOutcome {
        if (tracks.isEmpty()) return TrackDeleteOutcome.Deleted

        val mediaStoreTracks = tracks.filter { isMediaStoreUri(it.uri) }
        val otherTracks = tracks.filterNot { isMediaStoreUri(it.uri) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mediaStoreTracks.isNotEmpty()) {
            if (otherTracks.isNotEmpty()) {
                val result = deleteDirectly(otherTracks)
                if (result != TrackDeleteOutcome.Deleted) return result
            }

            return try {
                val request = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    mediaStoreTracks.map { it.uri }
                )
                TrackDeleteOutcome.RequiresSystemConfirmation(
                    intentSender = request.intentSender,
                    tracks = mediaStoreTracks
                )
            } catch (e: Exception) {
                TrackDeleteOutcome.Failed(TrackDeleteFailure.Unknown)
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return TrackDeleteOutcome.Failed(TrackDeleteFailure.Permission)
        }

        return deleteDirectly(tracks)
    }

    fun deleteDirectly(tracks: List<Track>): TrackDeleteOutcome {
        for (track in tracks) {
            try {
                if (!deleteSingle(track)) {
                    return TrackDeleteOutcome.Failed(TrackDeleteFailure.Unknown)
                }
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    e is RecoverableSecurityException
                ) {
                    return TrackDeleteOutcome.RequiresPermission(
                        intentSender = e.userAction.actionIntent.intentSender,
                        tracks = tracks
                    )
                }
                return TrackDeleteOutcome.Failed(TrackDeleteFailure.Permission)
            } catch (e: Exception) {
                return TrackDeleteOutcome.Failed(TrackDeleteFailure.Unknown)
            }
        }

        return TrackDeleteOutcome.Deleted
    }

    private fun deleteSingle(track: Track): Boolean {
        val uri = track.uri

        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val deleted = context.contentResolver.delete(uri, null, null)
            if (deleted > 0) return true

            if (DocumentsContract.isDocumentUri(context, uri)) {
                if (DocumentsContract.deleteDocument(context.contentResolver, uri)) {
                    return true
                }
            }
        } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let { path ->
                val file = File(path)
                if (!file.exists() || file.delete()) return true
            }
        }

        if (track.data.isNotBlank()) {
            val file = File(track.data)
            if (!file.exists() || file.delete()) return true
        }

        return false
    }

    private fun isMediaStoreUri(uri: Uri): Boolean =
        uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == "media"
}
