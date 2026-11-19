package com.dn0ne.player.app.presentation.components.playlist

import android.net.Uri
import java.io.File

/**
 * Resolves a stored playlist cover path to a drawable file. Missing or empty files
 * fall back to the automatic mosaic.
 */
fun playlistCoverFile(path: String?): File? {
    if (path.isNullOrBlank()) return null

    return runCatching {
        val file = File(path)
        file.takeIf { it.isFile && it.length() > 0 }
    }.getOrNull()
}

fun playlistCoverUri(path: String?): Uri? =
    playlistCoverFile(path)?.let { Uri.fromFile(it) }

/**
 * Cache key that changes whenever the file is replaced, so Coil never shows a stale cover.
 */
fun playlistCoverCacheKey(path: String?): String? =
    playlistCoverFile(path)?.let { file -> "${file.absolutePath}#${file.lastModified()}" }
