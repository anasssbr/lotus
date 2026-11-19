package com.dn0ne.player.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.max

/**
 * Stores custom playlist covers inside the app's private files directory.
 *
 * Files are named after the playlist plus a timestamp, so every save produces a new
 * path. That makes image loader caches miss naturally and keeps the old picture from
 * sticking around after a change.
 */
class PlaylistCoverStore(
    private val context: Context
) {
    private val directory: File
        get() = File(context.filesDir, COVER_DIRECTORY)

    suspend fun saveCover(playlistName: String, source: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decodeSampled(source, MAX_DIMENSION) ?: return@runCatching null
                val scaled = scaleDown(bitmap, MAX_DIMENSION)

                directory.mkdirs()
                val file = File(
                    directory,
                    "${stableKey(playlistName)}_${System.currentTimeMillis()}.jpg"
                )

                FileOutputStream(file).use { output ->
                    if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                        return@runCatching null
                    }
                }

                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()

                file.absolutePath
            }.getOrNull()
        }

    fun deleteCover(path: String?) {
        if (path.isNullOrBlank()) return

        runCatching {
            val file = File(path)
            val coverDirectory = directory.canonicalFile
            val coverFile = file.canonicalFile
            if (coverFile.parentFile == coverDirectory) {
                coverFile.delete()
            }
        }
    }

    private fun decodeSampled(uri: Uri, maxDimension: Int): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }

        return resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longestSide = max(bitmap.width, bitmap.height)
        if (longestSide <= maxDimension) return bitmap

        val ratio = maxDimension.toFloat() / longestSide
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun stableKey(playlistName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(playlistName.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val COVER_DIRECTORY = "playlist_covers"
        const val MAX_DIMENSION = 1024
        const val JPEG_QUALITY = 90
    }
}
