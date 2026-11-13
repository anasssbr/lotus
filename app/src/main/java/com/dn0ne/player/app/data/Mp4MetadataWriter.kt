package com.dn0ne.player.app.data

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Writes title/artist/cover into the `moov/udta/meta/ilst` atom of an MP4 file.
 *
 * YouTube serves fragmented MP4 (`sidx` + `moof`/`mdat`), which jaudiotagger refuses to
 * rewrite ("incorrect offsets written") because sample offsets live in the movie
 * fragments instead of `stco`. Inserting a box into `moov` only shifts the boxes that
 * follow it, and in an fMP4 every offset is relative to its own box (`default-base-is-moof`),
 * so the file stays valid.
 */
object Mp4MetadataWriter {
    private const val TAG = "Mp4MetadataWriter"
    private const val ITEM_TITLE = "\u00A9nam"
    private const val ITEM_ARTIST = "\u00A9ART"
    private const val ITEM_ALBUM = "\u00A9alb"
    private const val ITEM_COVER = "covr"

    fun write(
        file: File,
        title: String?,
        artist: String?,
        album: String?,
        artwork: ByteArray?,
        artworkMimeType: String = "image/jpeg"
    ): Boolean {
        if (title.isNullOrBlank() && artist.isNullOrBlank() &&
            album.isNullOrBlank() && artwork == null
        ) {
            return false
        }

        return try {
            val bytes = file.readBytes()
            val topLevel = parseBoxes(bytes, 0, bytes.size)
            val moov = topLevel.firstOrNull { it.type == "moov" } ?: return false

            // Non-fragmented MP4 uses absolute chunk offsets, so inserting bytes into moov
            // would invalidate them. jaudiotagger handles that case instead.
            val isFragmented = topLevel.any { it.type == "moof" }
            if (!isFragmented) return false

            val newMoov = rebuildMoov(bytes, moov, title, artist, album, artwork, artworkMimeType)
                ?: return false

            val temp = File(file.parentFile, file.name + ".tagging")
            FileOutputStream(temp).use { out ->
                out.write(bytes, 0, moov.start)
                out.write(newMoov)
                out.write(bytes, moov.end, bytes.size - moov.end)
            }

            if (!temp.renameTo(file)) {
                temp.delete()
                Log.w(TAG, "Could not replace ${file.name} with the tagged copy")
                return false
            }

            Log.i(TAG, "Wrote MP4 metadata to ${file.name} (artwork=${artwork?.size ?: 0} bytes)")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Could not write MP4 metadata to ${file.name}: ${e.message}", e)
            false
        }
    }

    private fun rebuildMoov(
        bytes: ByteArray,
        moov: Box,
        title: String?,
        artist: String?,
        album: String?,
        artwork: ByteArray?,
        artworkMimeType: String
    ): ByteArray? {
        val moovChildren = parseBoxes(bytes, moov.contentStart, moov.end)
        val udta = moovChildren.firstOrNull { it.type == "udta" }

        val newUdta = if (udta != null) {
            val udtaChildren = parseBoxes(bytes, udta.contentStart, udta.end)
            val meta = udtaChildren.firstOrNull { it.type == "meta" }

            val newMeta = if (meta != null) {
                val metaChildren = parseBoxes(bytes, meta.contentStart + FULL_BOX_HEADER, meta.end)
                val hdlr = metaChildren.firstOrNull { it.type == "hdlr" }
                val ilst = metaChildren.firstOrNull { it.type == "ilst" }

                val newIlst = rebuildIlst(bytes, ilst, title, artist, album, artwork, artworkMimeType)
                    ?: return null

                val metaPayload = ByteArrayOutputStream().apply {
                    write(ByteArray(FULL_BOX_HEADER))
                    if (hdlr != null) write(bytes, hdlr.start, hdlr.size) else write(buildHdlr())
                    write(newIlst)
                }.toByteArray()

                buildBox("meta", metaPayload)
            } else {
                val newIlst = rebuildIlst(bytes, null, title, artist, album, artwork, artworkMimeType)
                    ?: return null

                buildBox("meta", ByteArrayOutputStream().apply {
                    write(ByteArray(FULL_BOX_HEADER))
                    write(buildHdlr())
                    write(newIlst)
                }.toByteArray())
            }

            buildBox("udta", ByteArrayOutputStream().apply {
                udtaChildren.filter { it.type != "meta" }
                    .forEach { write(bytes, it.start, it.size) }
                write(newMeta)
            }.toByteArray())
        } else {
            val ilst = rebuildIlst(bytes, null, title, artist, album, artwork, artworkMimeType)
                ?: return null
            val metaPayload = ByteArrayOutputStream().apply {
                write(ByteArray(FULL_BOX_HEADER))
                write(buildHdlr())
                write(ilst)
            }.toByteArray()

            buildBox("udta", buildBox("meta", metaPayload))
        }

        val moovPayload = ByteArrayOutputStream().apply {
            moovChildren.filter { it.type != "udta" }.forEach { write(bytes, it.start, it.size) }
            write(newUdta)
        }.toByteArray()

        return buildBox("moov", moovPayload)
    }

    private fun rebuildIlst(
        bytes: ByteArray,
        ilst: Box?,
        title: String?,
        artist: String?,
        album: String?,
        artwork: ByteArray?,
        artworkMimeType: String
    ): ByteArray? {
        val items = if (ilst != null) parseBoxes(bytes, ilst.contentStart, ilst.end) else emptyList()

        val payload = ByteArrayOutputStream().apply {
            items.filter { it.type !in REPLACED_ITEMS }
                .forEach { write(bytes, it.start, it.size) }

            if (!title.isNullOrBlank()) {
                write(buildTextItem(ITEM_TITLE, title))
            }
            if (!artist.isNullOrBlank()) {
                write(buildTextItem(ITEM_ARTIST, artist))
            }
            // MediaStore only indexes album art per album, so give every download its own album.
            if (!album.isNullOrBlank()) {
                write(buildTextItem(ITEM_ALBUM, album))
            }
            if (artwork != null && artwork.isNotEmpty()) {
                write(buildCoverItem(artwork, artworkMimeType))
            }
        }.toByteArray()

        if (payload.isEmpty()) return null
        return buildBox("ilst", payload)
    }

    private fun buildTextItem(type: String, value: String): ByteArray {
        val data = ByteArrayOutputStream().apply {
            writeIntValue(DATA_TYPE_UTF8)
            writeIntValue(0) // locale
            write(value.toByteArray(Charsets.UTF_8))
        }.toByteArray()

        return buildBox(type, buildBox("data", data))
    }

    private fun buildCoverItem(artwork: ByteArray, mimeType: String): ByteArray {
        val dataType = if (mimeType.contains("png", ignoreCase = true)) DATA_TYPE_PNG else DATA_TYPE_JPEG
        val data = ByteArrayOutputStream().apply {
            writeIntValue(dataType)
            writeIntValue(0)
            write(artwork)
        }.toByteArray()

        return buildBox(ITEM_COVER, buildBox("data", data))
    }

    /** OutputStream.write(Int) writes a single byte, which is not what MP4 boxes need. */
    private fun ByteArrayOutputStream.writeIntValue(value: Int) {
        write(
            byteArrayOf(
                (value ushr 24).toByte(),
                (value ushr 16).toByte(),
                (value ushr 8).toByte(),
                value.toByte()
            )
        )
    }

    /** Minimal `mdir` handler, required by parsers when the file had no `udta` at all. */
    private fun buildHdlr(): ByteArray {
        val payload = ByteArray(25)
        "mdir".toByteArray(Charsets.ISO_8859_1).copyInto(payload, 8)
        return buildBox("hdlr", payload)
    }

    private fun parseBoxes(bytes: ByteArray, from: Int, to: Int): List<Box> {
        val boxes = mutableListOf<Box>()
        var pos = from

        while (pos + BOX_HEADER <= to) {
            var size = readInt(bytes, pos)
            val type = String(bytes, pos + 4, 4, Charsets.ISO_8859_1)
            var header = BOX_HEADER

            if (size == 1) {
                if (pos + LARGE_BOX_HEADER > to) break
                size = readLong(bytes, pos + 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                header = LARGE_BOX_HEADER
            } else if (size == 0) {
                size = to - pos
            }

            if (size < header || pos + size > to) break

            boxes += Box(type, pos, size, header)
            pos += size
        }

        return boxes
    }

    private fun buildBox(type: String, payload: ByteArray): ByteArray {
        val out = ByteArray(BOX_HEADER + payload.size)
        writeInt(out, 0, out.size)
        type.toByteArray(Charsets.ISO_8859_1).copyInto(out, 4)
        payload.copyInto(out, BOX_HEADER)
        return out
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)

    private fun readLong(bytes: ByteArray, offset: Int): Long =
        (readInt(bytes, offset).toLong() shl 32) or (readInt(bytes, offset + 4).toLong() and 0xFFFFFFFFL)

    private data class Box(val type: String, val start: Int, val size: Int, val header: Int) {
        val end: Int get() = start + size
        val contentStart: Int get() = start + header
    }

    private const val BOX_HEADER = 8
    private const val LARGE_BOX_HEADER = 16
    private const val FULL_BOX_HEADER = 4
    private const val DATA_TYPE_UTF8 = 1
    private const val DATA_TYPE_JPEG = 13
    private const val DATA_TYPE_PNG = 14

    private val REPLACED_ITEMS = setOf(ITEM_TITLE, ITEM_ARTIST, ITEM_ALBUM, ITEM_COVER)
}
