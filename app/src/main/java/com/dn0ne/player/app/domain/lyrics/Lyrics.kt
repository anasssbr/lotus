package com.dn0ne.player.app.domain.lyrics

import kotlinx.serialization.Serializable

@Serializable
data class LyricsWord(
    val text: String,
    val startMs: Long
)

@Serializable
data class Lyrics(
    val uri: String,
    val areFromRemote: Boolean = true,
    val plain: List<String>? = null,
    val synced: List<Pair<Int, String>>? = null,
    val syncedWords: List<List<LyricsWord>?>? = null
)

data class ParsedSyncedLyrics(
    val lines: List<Pair<Int, String>>,
    val words: List<List<LyricsWord>?>?
)

private val LINE_TIMESTAMP_REGEX = Regex("""^\[(\d+):(\d+)\.(\d+)](.*)$""")
private val WORD_TIMESTAMP_REGEX = Regex("""<(\d+):(\d+)\.(\d+)>""")
private val WHITESPACE_REGEX = Regex("""\s+""")

fun String.toSyncedLyrics(): List<Pair<Int, String>> = toParsedSyncedLyrics().lines

fun String.toParsedSyncedLyrics(): ParsedSyncedLyrics {
    val parsed = split('\n').mapNotNull { rawLine ->
        val line = rawLine.removeSuffix("\r")
        val match = LINE_TIMESTAMP_REGEX.matchEntire(line) ?: return@mapNotNull null
        val timestamp = parseTimestamp(
            minutes = match.groupValues[1],
            seconds = match.groupValues[2],
            fraction = match.groupValues[3]
        ) ?: return@mapNotNull null

        val body = match.groupValues[4]
        parseBody(timestamp, body)
    }

    if (parsed.isEmpty()) {
        throw IllegalArgumentException("Synced lines not found.")
    }

    val hasWordTimestamps = parsed.any { it.second != null }

    return ParsedSyncedLyrics(
        lines = parsed.map { it.first },
        words = if (hasWordTimestamps) parsed.map { it.second } else null
    )
}

private fun parseBody(
    timestampMs: Int,
    body: String
): Pair<Pair<Int, String>, List<LyricsWord>?> {
    val tags = WORD_TIMESTAMP_REGEX.findAll(body).toList()
    if (tags.isEmpty()) {
        return (timestampMs to body.trim()) to null
    }

    val cleanedText = WORD_TIMESTAMP_REGEX.replace(body, "")
        .replace(WHITESPACE_REGEX, " ")
        .trim()

    val words = tags.mapIndexedNotNull { index, tag ->
        val wordStart = tag.range.last + 1
        val wordEnd = tags.getOrNull(index + 1)?.range?.first ?: body.length
        val wordText = body.substring(wordStart, wordEnd).trim()
        if (wordText.isEmpty()) return@mapIndexedNotNull null

        val startMs = parseTimestamp(
            minutes = tag.groupValues[1],
            seconds = tag.groupValues[2],
            fraction = tag.groupValues[3]
        )?.toLong() ?: return@mapIndexedNotNull null

        val normalized = wordText.trim { !it.isLetterOrDigit() }
        if (normalized.isEmpty()) null else LyricsWord(text = normalized, startMs = startMs)
    }

    return (timestampMs to cleanedText) to words.ifEmpty { null }
}

private fun parseTimestamp(
    minutes: String,
    seconds: String,
    fraction: String
): Int? {
    val minutesValue = minutes.toIntOrNull() ?: return null
    val secondsValue = seconds.toIntOrNull() ?: return null
    val centiseconds = fraction.padEnd(2, '0').take(2).toIntOrNull() ?: return null

    return minutesValue * 60 * 1000 + secondsValue * 1000 + centiseconds * 10
}

fun String.toLyricsTimestamp(): Int {
    val regex = Regex("""(\d+):(\d+)\.(\d+)""")
    regex.matchEntire(this)?.let { matchResult ->
        val minutes = matchResult.groupValues.getOrNull(1)?.toIntOrNull()
        val seconds = matchResult.groupValues.getOrNull(2)?.toIntOrNull()
        val centiseconds = matchResult.groupValues.getOrNull(3)?.toIntOrNull()

        if (minutes == null || seconds == null || centiseconds == null) {
            throw IllegalArgumentException("Failed to parse timestamp: $this")
        }

        return minutes * 60 * 1000 + seconds * 1000 + centiseconds * 10
    }
        ?: throw IllegalArgumentException("Failed to parse timestamp, does not match regex ${regex.pattern}")
}
