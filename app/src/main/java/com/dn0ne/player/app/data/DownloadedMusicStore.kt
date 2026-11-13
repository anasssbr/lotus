package com.dn0ne.player.app.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.dn0ne.player.app.domain.download.OnlineTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.StandardArtwork
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadedMusicStore(private val context: Context) {
    private val logTag = "DownloadedMusicStore"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun save(downloadedFile: File, track: OnlineTrack): Uri? =
        withContext(Dispatchers.IO) {
            writeTags(downloadedFile, track)

            val extension = downloadedFile.extension.lowercase()
            val displayName = buildDisplayName(track, extension)
            val mimeType = mimeTypeOf(extension)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveWithMediaStore(downloadedFile, displayName, mimeType)
                } else {
                    saveToPublicMusicFolder(downloadedFile, displayName, mimeType)
                }
            } finally {
                downloadedFile.delete()
            }
        }

    private fun saveWithMediaStore(file: File, displayName: String, mimeType: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values) ?: run {
            Log.w(logTag, "MediaStore refused to create an entry for $displayName")
            return null
        }

        val copied = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Could not open an output stream for $uri")
        }

        if (copied.isFailure) {
            Log.w(logTag, "Could not copy $displayName: ${copied.exceptionOrNull()?.message}")
            context.contentResolver.delete(uri, null, null)
            return null
        }

        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)

        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveToPublicMusicFolder(file: File, displayName: String, mimeType: String): Uri? {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (!musicDir.exists() && !musicDir.mkdirs()) {
            Log.w(logTag, "Could not create ${musicDir.absolutePath}")
            return null
        }

        val target = File(musicDir, displayName)
        val copied = runCatching {
            file.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }

        if (copied.isFailure) {
            Log.w(logTag, "Could not copy $displayName: ${copied.exceptionOrNull()?.message}")
            return null
        }

        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mimeType), null)

        return Uri.fromFile(target)
    }

    private fun writeTags(file: File, track: OnlineTrack) {
        val artwork = fetchCoverArt(track)
        val mimeType = when {
            artwork == null -> "image/jpeg"
            artwork.size > 4 && artwork[0] == 0x89.toByte() && artwork[1] == 0x50.toByte() -> "image/png"
            else -> "image/jpeg"
        }

        // Fragmented MP4 (what YouTube serves) cannot be written by jaudiotagger.
        val writtenByMp4Writer = Mp4MetadataWriter.write(
            file = file,
            title = track.title,
            artist = track.artist,
            album = track.title.ifBlank { track.artist },
            artwork = artwork,
            artworkMimeType = mimeType
        )
        if (writtenByMp4Writer) return

        try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagAndConvertOrCreateAndSetDefault ?: return

            tag.setField(FieldKey.TITLE, track.title)
            if (track.artist.isNotBlank()) {
                tag.setField(FieldKey.ARTIST, track.artist)
            }

            if (artwork != null && artwork.isNotEmpty()) {
                val artworkField = StandardArtwork().apply {
                    binaryData = artwork
                    this.mimeType = mimeType
                    description = "Cover"
                    pictureType = 3 // front cover
                }
                tag.setField(artworkField)
            }

            audioFile.commit()
        } catch (e: Throwable) {
            // Formats such as webm/opus cannot be tagged; the file is still usable.
            Log.w(logTag, "Could not write tags to ${file.name}: ${e.message}")
        }
    }

    /** Downloads the best available YouTube thumbnail so the file gets embedded artwork. */
    private fun fetchCoverArt(track: OnlineTrack): ByteArray? {
        val candidates = buildList {
            if (track.id.isNotBlank()) {
                add("https://i.ytimg.com/vi/${track.id}/maxresdefault.jpg")
                add("https://i.ytimg.com/vi/${track.id}/sddefault.jpg")
                add("https://i.ytimg.com/vi/${track.id}/hqdefault.jpg")
            }
            track.thumbnailUrl?.takeIf { it.isNotBlank() }?.let(::add)
        }.distinct()

        for (url in candidates) {
            try {
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) return bytes
                    }
                }
            } catch (e: Throwable) {
                Log.w(logTag, "Could not download cover art from $url: ${e.message}")
            }
        }

        return null
    }

    private fun buildDisplayName(track: OnlineTrack, extension: String): String {
        val artist = track.artist.ifBlank { "Unknown artist" }
        val name = if (track.artist.isBlank()) {
            track.title
        } else {
            "$artist - ${track.title}"
        }

        return "${sanitizeFileName(name)}.$extension"
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .replace(fileNameForbiddenChars, "_")
            .replace(whitespaceRegex, " ")
            .trim()
            .take(MAX_FILE_NAME_LENGTH)
    }

    private fun mimeTypeOf(extension: String): String {
        return when (extension) {
            "m4a", "mp4" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "opus" -> "audio/opus"
            "webm" -> "audio/webm"
            "ogg", "oga" -> "audio/ogg"
            "flac" -> "audio/flac"
            else -> "audio/*"
        }
    }
}

private val fileNameForbiddenChars = Regex("""[\\/:*?"<>|]""")
private val whitespaceRegex = Regex("""\s{2,}""")
private const val MAX_FILE_NAME_LENGTH = 120
