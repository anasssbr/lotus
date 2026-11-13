package com.dn0ne.player.app.data.playback

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.dn0ne.player.app.data.repository.ListeningHistoryRepository
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Watches the real player and records listening history.
 *
 * A play is only counted once the track has actually been playing for
 * min(30s, 50% of its duration). Time is accumulated with the player's own
 * playing state, so pausing, buffering or skipping never counts as listening.
 */
class ListeningRecorder(
    private val player: ExoPlayer,
    private val repository: ListeningHistoryRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null

    private var sessionMediaId: String? = null
    private var sessionTitle: String? = null
    private var sessionArtist: String? = null
    private var sessionStartedAt: Long = 0L
    private var sessionEventId: String? = null
    private var sessionPlayedMs: Long = 0L
    private var sessionCounted: Boolean = false
    private var lastPersistedSeconds: Long = 0L

    private var pendingDailyMs: Long = 0L
    private var pendingDailyEpochDay: Long = LocalDate.now().toEpochDay()

    private var lastTickRealtime: Long = SystemClock.elapsedRealtime()

    fun start() {
        if (tickerJob != null) return

        lastTickRealtime = SystemClock.elapsedRealtime()
        pendingDailyEpochDay = LocalDate.now().toEpochDay()

        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                try {
                    tick()
                } catch (e: Exception) {
                    Log.e(TAG, "Listening tick failed", e)
                }
            }
        }
    }

    fun release() {
        // The ticker runs on the main thread, so it cannot be mid-tick while we
        // are here. Cancel it and flush the last session on a background thread.
        tickerJob?.cancel()
        tickerJob = null

        runBlocking(Dispatchers.IO) {
            try {
                finishSession()
                flushDaily(force = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to flush listening history on release", e)
            }
        }
    }

    private suspend fun tick() {
        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastTickRealtime).coerceIn(0L, MAX_TICK_DELTA_MS)
        lastTickRealtime = now

        val mediaItem = player.currentMediaItem
        val mediaId = mediaItem?.mediaId?.takeIf { it.isNotBlank() }
            ?: mediaItem?.localConfiguration?.uri?.toString()

        if (mediaId == null) {
            finishSession()
            flushDaily(force = false)
            return
        }

        if (mediaId != sessionMediaId) {
            finishSession()
            beginSession(mediaId, mediaItem)
        }

        val isActuallyPlaying = player.playWhenReady &&
                player.playbackState == Player.STATE_READY &&
                player.isPlaying

        if (!isActuallyPlaying) {
            flushDaily(force = false)
            return
        }

        val today = LocalDate.now().toEpochDay()
        if (today != pendingDailyEpochDay) {
            flushDaily(force = true)
            pendingDailyEpochDay = today
        }

        sessionPlayedMs += delta
        pendingDailyMs += delta

        val thresholdMs = playThresholdMs()
        if (!sessionCounted && sessionPlayedMs >= thresholdMs) {
            sessionCounted = true
            persistSession()
            Log.d(
                TAG,
                "Play counted: ${sessionTitle ?: sessionMediaId} " +
                        "(${sessionPlayedMs / 1000}s played, threshold ${thresholdMs / 1000}s)"
            )
        } else if (sessionCounted && sessionPlayedMs / 1000 - lastPersistedSeconds >= PERSIST_INTERVAL_SECONDS) {
            persistSession()
        }

        flushDaily(force = false)
    }

    private fun beginSession(mediaId: String, mediaItem: MediaItem?) {
        sessionMediaId = mediaId
        sessionTitle = mediaItem?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
            ?: player.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
        sessionArtist = mediaItem?.mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: player.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }
        sessionStartedAt = System.currentTimeMillis()
        sessionEventId = UUID.randomUUID().toString()
        sessionPlayedMs = 0L
        sessionCounted = false
        lastPersistedSeconds = 0L

        Log.d(TAG, "Session started: ${sessionTitle ?: mediaId}")
    }

    private suspend fun finishSession() {
        val mediaId = sessionMediaId ?: return

        if (sessionCounted && sessionPlayedMs > 0L) {
            persistSession()
        }

        Log.d(
            TAG,
            "Session ended: ${sessionTitle ?: mediaId} " +
                    "(${sessionPlayedMs / 1000}s played, counted=$sessionCounted)"
        )

        sessionMediaId = null
        sessionTitle = null
        sessionArtist = null
        sessionEventId = null
        sessionStartedAt = 0L
        sessionPlayedMs = 0L
        sessionCounted = false
        lastPersistedSeconds = 0L
    }

    private suspend fun persistSession() {
        val mediaId = sessionMediaId ?: return
        val eventId = sessionEventId ?: return
        val seconds = sessionPlayedMs / 1000
        if (seconds <= 0L) return

        withContext(Dispatchers.IO) {
            repository.recordPlay(
                eventId = eventId,
                trackUri = mediaId,
                title = sessionTitle,
                artist = sessionArtist,
                playedAt = sessionStartedAt,
                secondsPlayed = seconds
            )
        }
        lastPersistedSeconds = seconds
    }

    private suspend fun flushDaily(force: Boolean) {
        val wholeSeconds = pendingDailyMs / 1000
        if (wholeSeconds <= 0L) return
        if (!force && wholeSeconds < DAILY_FLUSH_INTERVAL_SECONDS) return

        pendingDailyMs -= wholeSeconds * 1000
        withContext(Dispatchers.IO) {
            repository.addListeningSeconds(
                epochDay = pendingDailyEpochDay,
                seconds = wholeSeconds
            )
        }

        Log.d(
            TAG,
            "Daily listening +${wholeSeconds}s on epoch day $pendingDailyEpochDay"
        )
    }

    private fun playThresholdMs(): Long {
        val durationMs = player.duration
            .takeIf { it > 0L && it != C.TIME_UNSET }
            ?: DEFAULT_THRESHOLD_MS

        return minOf(MIN_PLAY_THRESHOLD_MS, durationMs / 2)
    }

    private companion object {
        const val TAG = "ListeningHistory"

        const val TICK_INTERVAL_MS = 1000L
        const val MAX_TICK_DELTA_MS = 2000L
        const val MIN_PLAY_THRESHOLD_MS = 30_000L
        const val DEFAULT_THRESHOLD_MS = 30_000L
        const val PERSIST_INTERVAL_SECONDS = 5L
        const val DAILY_FLUSH_INTERVAL_SECONDS = 10L
    }
}
