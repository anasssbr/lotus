package com.dn0ne.player.app.data.repository

import com.dn0ne.player.app.domain.listening.DailyListening
import com.dn0ne.player.app.domain.listening.MostPlayedTrack
import com.dn0ne.player.app.domain.listening.RecentlyPlayedTrack

/**
 * Local listening history. Tracks are identified by their URI, and no query ever
 * touches the media library, so tracks deleted from the device can never crash the app.
 */
interface ListeningHistoryRepository {

    /**
     * Inserts or updates one playback session. [secondsPlayed] is the real time the
     * track was playing, and [playedAt] is when the session started.
     */
    suspend fun recordPlay(
        eventId: String,
        trackUri: String,
        title: String?,
        artist: String?,
        playedAt: Long,
        secondsPlayed: Long
    )

    /** Adds real listening time to a local calendar day (epoch day). */
    suspend fun addListeningSeconds(epochDay: Long, seconds: Long)

    /** Most recent counted plays, one row per track. */
    suspend fun getRecentlyPlayed(limit: Int = 20): List<RecentlyPlayedTrack>

    /** Counted plays grouped by track inside the last [days] days. */
    suspend fun getMostPlayedLastNDays(days: Int, limit: Int = 20): List<MostPlayedTrack>

    /** Listening seconds for the last [days] days, oldest first. */
    suspend fun getListeningSecondsPerDay(days: Int = 7): List<DailyListening>

    /** Last counted play timestamp per track URI. Missing entries were never played. */
    suspend fun getLastPlayedTimestamps(): Map<String, Long>

    /** Candidate URIs that have no counted play newer than [cutoffMillis]. */
    suspend fun getTracksNotPlayedSince(
        cutoffMillis: Long,
        candidateTrackUris: List<String>
    ): List<String>
}
