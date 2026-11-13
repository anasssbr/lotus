package com.dn0ne.player.app.domain.listening

/** A track that was actually played (a "play" is counted only after the threshold). */
data class RecentlyPlayedTrack(
    val trackUri: String,
    val title: String?,
    val artist: String?,
    val lastPlayedAt: Long,
    val secondsPlayed: Long
)

/** Aggregated play count for a track inside a time window. */
data class MostPlayedTrack(
    val trackUri: String,
    val title: String?,
    val artist: String?,
    val playCount: Int,
    val lastPlayedAt: Long
)

/** Total real listening time for one local calendar day. */
data class DailyListening(
    val epochDay: Long,
    val seconds: Long
)
