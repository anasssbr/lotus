package com.dn0ne.player.app.data.remote.download

import com.dn0ne.player.app.domain.download.OnlineTrack
import java.io.File

interface OnlineMusicProvider {
    /** Prepares the downloader ahead of the first search. Safe to call more than once. */
    suspend fun warmUp() {}

    suspend fun search(query: String): List<OnlineTrack>

    suspend fun getStreamUrl(track: OnlineTrack): String?

    suspend fun download(track: OnlineTrack, onProgress: (Float) -> Unit): File
}
