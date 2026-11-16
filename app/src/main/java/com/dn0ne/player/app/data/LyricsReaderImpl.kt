package com.dn0ne.player.app.data

import android.content.Context
import android.util.Log
import com.dn0ne.player.app.domain.lyrics.Lyrics
import com.dn0ne.player.app.domain.lyrics.toParsedSyncedLyrics
import com.dn0ne.player.app.domain.result.DataError
import com.dn0ne.player.app.domain.result.Result
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.domain.track.format
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.FileOutputStream

class LyricsReaderImpl(private val context: Context) : LyricsReader {
    private val logTag = "LyricsReader"

    override fun readFromTag(track: Track): Result<Lyrics?, DataError.Local> {
        // A .lrc file next to the audio file is the most precise source (it may
        // contain enhanced word timestamps), so it wins over the embedded tag.
        val sidecarText = readSidecarLyrics(track)
        sidecarText?.let { text ->
            lyricsFromText(track, text)?.let { lyrics ->
                if (lyrics.synced != null) return Result.Success(lyrics)
            }
        }

        var lyrics: Lyrics? = null

        var file: File? = null
        try {
            context.contentResolver.openInputStream(track.uri)?.use { input ->
                val temp = File.createTempFile("temp_audio", ".${track.format}", context.cacheDir)
                FileOutputStream(temp).use { output ->
                    input.copyTo(output)
                }
                file = temp
            }

            if (file == null) return Result.Error(DataError.Local.NoReadPermission)

            val audioFile =
                AudioFileIO.read(file) ?: return Result.Error(DataError.Local.FailedToRead)
            val tag = audioFile.tagAndConvertOrCreateAndSetDefault
                ?: return Result.Error(DataError.Local.FailedToRead)

            val lyricsText = tag.getFirst(FieldKey.LYRICS)

            if (lyricsText?.isNotBlank() == true) {
                lyrics = lyricsFromText(track, lyricsText)
            }
        } catch (e: Exception) {
            // Jaudiotagger has no reader for some formats (e.g. .opus), so treat the
            // failure as "this track simply has no embedded lyrics".
            Log.i(logTag, "Could not read lyrics from tags: ${e.message}")
        } finally {
            file?.delete()
        }

        if (lyrics == null && sidecarText?.isNotBlank() == true) {
            lyrics = lyricsFromText(track, sidecarText)
        }

        return Result.Success(lyrics)
    }

    private fun lyricsFromText(track: Track, text: String): Lyrics? {
        if (text.isBlank()) return null

        return try {
            val parsed = text.toParsedSyncedLyrics()
            Lyrics(
                uri = track.uri.toString(),
                plain = parsed.lines.map { it.second },
                synced = parsed.lines,
                syncedWords = parsed.words,
                areFromRemote = false
            )
        } catch (_: IllegalArgumentException) {
            Lyrics(
                uri = track.uri.toString(),
                plain = text.split('\n'),
                synced = null,
                syncedWords = null,
                areFromRemote = false
            )
        }
    }

    private fun readSidecarLyrics(track: Track): String? {
        return try {
            val path = track.data
            if (path.isBlank()) return null

            val audioFile = File(path)
            val parent = audioFile.parentFile ?: return null
            val baseName = audioFile.nameWithoutExtension

            val candidates = listOf("$baseName.lrc", "$baseName.LRC", "$baseName.Lrc")
            val lrcFile = candidates
                .map { File(parent, it) }
                .firstOrNull { it.isFile && it.canRead() }
                ?: return null

            lrcFile.readText().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.i(logTag, "Could not read sidecar .lrc: ${e.message}")
            null
        }
    }
}
