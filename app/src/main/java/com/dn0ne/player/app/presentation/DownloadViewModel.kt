package com.dn0ne.player.app.presentation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.dn0ne.player.R
import com.dn0ne.player.app.data.DownloadedMusicStore
import com.dn0ne.player.app.data.notification.DownloadNotifications
import com.dn0ne.player.app.data.remote.download.OnlineMusicProvider
import com.dn0ne.player.app.domain.download.OnlineTrack
import com.dn0ne.player.app.presentation.components.snackbar.SnackbarController
import com.dn0ne.player.app.presentation.components.snackbar.SnackbarEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<OnlineTrack> = emptyList(),
    val previewTrackId: String? = null,
    val isPreparingPreview: Boolean = false,
    val downloadingIds: Set<String> = emptySet(),
    val downloadProgress: Map<String, Float> = emptyMap(),
    val downloadedIds: Set<String> = emptySet()
)

class DownloadViewModel(
    private val context: Context,
    private val provider: OnlineMusicProvider,
    private val store: DownloadedMusicStore,
    private val notifications: DownloadNotifications
) : ViewModel() {
    private val logTag = "DownloadViewModel"
    private val _state = MutableStateFlow(DownloadState())
    val state = _state.asStateFlow()

    private var previewPlayer: ExoPlayer? = null
    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun onSearchClick() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(isSearching = true, hasSearched = true) }

            try {
                val results = provider.search(query)
                _state.update { it.copy(isSearching = false, results = results) }

                if (results.isEmpty()) {
                    SnackbarController.sendEvent(SnackbarEvent(message = R.string.download_no_results))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(logTag, "Search failed", e)
                _state.update { it.copy(isSearching = false, results = emptyList()) }
                SnackbarController.sendEvent(SnackbarEvent(message = R.string.download_search_failed))
            }
        }
    }

    fun onPreviewClick(track: OnlineTrack) {
        if (_state.value.previewTrackId == track.id) {
            stopPreview()
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(previewTrackId = track.id, isPreparingPreview = true) }

            try {
                val streamUrl = provider.getStreamUrl(track)
                if (streamUrl == null) {
                    _state.update { it.copy(previewTrackId = null, isPreparingPreview = false) }
                    SnackbarController.sendEvent(SnackbarEvent(message = R.string.download_preview_failed))
                    return@launch
                }

                val player = ensurePreviewPlayer()
                player.setMediaItem(MediaItem.fromUri(streamUrl))
                player.prepare()
                player.play()

                _state.update { it.copy(isPreparingPreview = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(logTag, "Preview failed", e)
                _state.update { it.copy(previewTrackId = null, isPreparingPreview = false) }
                SnackbarController.sendEvent(SnackbarEvent(message = R.string.download_preview_failed))
            }
        }
    }

    fun onDownloadClick(track: OnlineTrack) {
        val currentState = _state.value
        if (track.id in currentState.downloadingIds || track.id in currentState.downloadedIds) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    downloadingIds = it.downloadingIds + track.id,
                    downloadProgress = it.downloadProgress + (track.id to 0f)
                )
            }

            notifications.showProgress(track.title, 0f)

            try {
                var lastNotifiedProgress = -1

                val file = provider.download(track) { progress ->
                    _state.update {
                        it.copy(downloadProgress = it.downloadProgress + (track.id to progress))
                    }

                    val percent = progress.toInt()
                    if (percent != lastNotifiedProgress) {
                        lastNotifiedProgress = percent
                        notifications.showProgress(track.title, progress)
                    }
                }

                val uri = store.save(file, track)
                if (uri == null) error("Could not save the downloaded file")

                _state.update {
                    it.copy(
                        downloadingIds = it.downloadingIds - track.id,
                        downloadProgress = it.downloadProgress - track.id,
                        downloadedIds = it.downloadedIds + track.id
                    )
                }

                notifications.showComplete(track.title)
                SnackbarController.sendEvent(SnackbarEvent(message = R.string.download_complete))
            } catch (e: CancellationException) {
                notifications.cancel()
                throw e
            } catch (e: Throwable) {
                Log.w(logTag, "Download failed", e)
                notifications.cancel()
                _state.update {
                    it.copy(
                        downloadingIds = it.downloadingIds - track.id,
                        downloadProgress = it.downloadProgress - track.id
                    )
                }
                SnackbarController.sendEvent(SnackbarEvent(message = R.string.download_failed))
            }
        }
    }

    fun stopPreview() {
        previewPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        _state.update { it.copy(previewTrackId = null, isPreparingPreview = false) }
    }

    private fun ensurePreviewPlayer(): ExoPlayer {
        return previewPlayer ?: ExoPlayer.Builder(context).build().also { player ->
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _state.update { it.copy(previewTrackId = null) }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w(logTag, "Preview playback failed", error)
                    _state.update { it.copy(previewTrackId = null, isPreparingPreview = false) }
                    viewModelScope.launch {
                        SnackbarController.sendEvent(
                            SnackbarEvent(message = R.string.download_preview_failed)
                        )
                    }
                }
            })
            previewPlayer = player
        }
    }

    override fun onCleared() {
        previewPlayer?.release()
        previewPlayer = null
        super.onCleared()
    }
}
