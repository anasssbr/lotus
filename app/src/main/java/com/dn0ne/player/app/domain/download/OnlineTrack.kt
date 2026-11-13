package com.dn0ne.player.app.domain.download

data class OnlineTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    val webpageUrl: String
)
