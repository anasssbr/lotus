package com.dn0ne.player.app.data.remote.lyrics

import android.content.Context
import android.util.Log
import androidx.compose.ui.util.fastForEach
import com.dn0ne.player.R
import com.dn0ne.player.app.domain.lyrics.Lyrics
import com.dn0ne.player.app.domain.lyrics.toSyncedLyrics
import com.dn0ne.player.app.domain.result.DataError
import com.dn0ne.player.app.domain.result.Result
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.presentation.components.snackbar.SnackbarController
import com.dn0ne.player.app.presentation.components.snackbar.SnackbarEvent
import com.dn0ne.player.core.util.getAppVersionName
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.net.SocketException
import java.nio.channels.UnresolvedAddressException
import java.security.MessageDigest
import kotlin.math.abs

class LrclibLyricsProvider(
    context: Context,
    private val client: HttpClient
) : LyricsProvider {
    private val lrclibEndpoint = "https://lrclib.net/api"
    private val logTag = "LrclibLyricsProvider"
    private val userAgent =
        "${context.resources.getString(R.string.app_name)}/${context.getAppVersionName()} ( dev.dn0ne@gmail.com )"

    override suspend fun getLyrics(track: Track): Result<Lyrics, DataError.Network> {
        if (track.title == null || track.artist == null) {
            return Result.Error(DataError.Network.BadRequest)
        }

        val durationSeconds = track.duration / 1000
        val trackUri = track.uri.toString()
        val guesses = buildTagGuesses(track.title, track.artist)
        val primaryGuess = guesses.first()

        // 1. Exact lookup with the original tags, including the album.
        val exactResult = requestLyrics(
            trackName = primaryGuess.title,
            artistName = primaryGuess.artist,
            albumName = track.album,
            durationSeconds = durationSeconds
        )
        when (exactResult) {
            is Result.Success -> if (exactResult.data.hasLyrics()) {
                Log.d(logTag, "Found lyrics with the exact lookup: $primaryGuess")
                return Result.Success(exactResult.data.toLyrics(trackUri))
            }

            is Result.Error -> if (exactResult.error != DataError.Network.NotFound) {
                return Result.Error(exactResult.error)
            }
        }

        // 2. Exact lookup again without the album, for every cleaned-up tag guess.
        //    A wrong album tag is a common reason for the lookup above to fail.
        for (guess in guesses) {
            val result = requestLyrics(
                trackName = guess.title,
                artistName = guess.artist,
                albumName = null,
                durationSeconds = durationSeconds
            )
            when (result) {
                is Result.Success -> if (result.data.hasLyrics()) {
                    Log.d(logTag, "Found lyrics with cleaned tags: $guess")
                    return Result.Success(result.data.toLyrics(trackUri))
                }

                is Result.Error -> if (result.error != DataError.Network.NotFound) {
                    return Result.Error(result.error)
                }
            }
        }

        // 3. Search by title and artist, then use the result closest in duration.
        for (guess in guesses) {
            when (val result = searchLyrics(guess.title, guess.artist)) {
                is Result.Error -> if (result.error != DataError.Network.NotFound) {
                    return Result.Error(result.error)
                }

                is Result.Success -> {
                    val match = result.data.bestMatch(durationSeconds)
                    if (match != null) {
                        Log.d(logTag, "Found lyrics with search: $guess")
                        return Result.Success(match.toLyrics(trackUri))
                    }
                }
            }
        }

        // 4. Last resort: search by title only, in case the artist tag is a channel name.
        val titleOnlyQuery = splitTitleArtist(cleanLyricsTitle(primaryGuess.title))?.second
            ?: cleanLyricsTitle(primaryGuess.title).ifBlank { primaryGuess.title }
        when (val result = searchLyrics(titleOnlyQuery, "")) {
            is Result.Error -> if (result.error != DataError.Network.NotFound) {
                return Result.Error(result.error)
            }

            is Result.Success -> {
                val match = result.data.bestMatch(durationSeconds)
                if (match != null) {
                    Log.d(logTag, "Found lyrics with title-only search: $titleOnlyQuery")
                    return Result.Success(match.toLyrics(trackUri))
                }
            }
        }

        Log.w(logTag, "No lyrics found for: ${track.title} - ${track.artist}")
        return Result.Error(DataError.Network.NotFound)
    }

    private suspend fun requestLyrics(
        trackName: String,
        artistName: String,
        albumName: String?,
        durationSeconds: Int
    ): Result<LyricsDto, DataError.Network> {
        return runRequest {
            val response = client.get(lrclibEndpoint) {
                url {
                    appendPathSegments("get")
                    parameters.append("track_name", trackName)
                    parameters.append("artist_name", artistName)
                    albumName?.takeIf { it.isNotBlank() }?.let {
                        parameters.append("album_name", it)
                    }
                    parameters.append("duration", durationSeconds.toString())
                }
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    append(HttpHeaders.UserAgent, userAgent)
                }
            }

            when (response.status) {
                HttpStatusCode.OK -> try {
                    Result.Success(response.body<LyricsDto>())
                } catch (e: JsonConvertException) {
                    Log.d(logTag, e.message, e)
                    Result.Error(DataError.Network.ParseError)
                }

                HttpStatusCode.NotFound -> Result.Error(DataError.Network.NotFound)

                HttpStatusCode.BadRequest -> Result.Error(DataError.Network.BadRequest)

                HttpStatusCode.TooManyRequests,
                HttpStatusCode.InternalServerError,
                HttpStatusCode.BadGateway,
                HttpStatusCode.ServiceUnavailable,
                HttpStatusCode.GatewayTimeout -> {
                    Log.w(logTag, "Lyrics lookup failed with HTTP ${response.status.value}")
                    Result.Error(DataError.Network.ServiceUnavailable)
                }

                else -> {
                    Log.w(logTag, "Lyrics lookup failed with HTTP ${response.status.value}")
                    Result.Error(DataError.Network.Unknown)
                }
            }
        }
    }

    private suspend fun searchLyrics(
        trackName: String,
        artistName: String
    ): Result<List<LyricsSearchResultDto>, DataError.Network> {
        return runRequest {
            val response = client.get(lrclibEndpoint) {
                url {
                    appendPathSegments("search")
                    parameters.append("track_name", trackName)
                    if (artistName.isNotBlank()) {
                        parameters.append("artist_name", artistName)
                    }
                }
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    append(HttpHeaders.UserAgent, userAgent)
                }
            }

            when (response.status) {
                HttpStatusCode.OK -> try {
                    Result.Success(response.body<List<LyricsSearchResultDto>>())
                } catch (e: JsonConvertException) {
                    Log.d(logTag, e.message, e)
                    Result.Error(DataError.Network.ParseError)
                }

                HttpStatusCode.NotFound -> Result.Error(DataError.Network.NotFound)

                HttpStatusCode.BadRequest -> Result.Error(DataError.Network.BadRequest)

                HttpStatusCode.TooManyRequests,
                HttpStatusCode.InternalServerError,
                HttpStatusCode.BadGateway,
                HttpStatusCode.ServiceUnavailable,
                HttpStatusCode.GatewayTimeout -> {
                    Log.w(logTag, "Lyrics search failed with HTTP ${response.status.value}")
                    Result.Error(DataError.Network.ServiceUnavailable)
                }

                else -> {
                    Log.w(logTag, "Lyrics search failed with HTTP ${response.status.value}")
                    Result.Error(DataError.Network.Unknown)
                }
            }
        }
    }

    private suspend fun <T> runRequest(
        block: suspend () -> Result<T, DataError.Network>
    ): Result<T, DataError.Network> {
        val firstAttempt = runRequestOnce(block)
        if (firstAttempt is Result.Error &&
            firstAttempt.error == DataError.Network.ServiceUnavailable
        ) {
            // The API occasionally rate limits or returns a 5xx; give it one more chance.
            delay(LYRICS_RETRY_DELAY_MILLIS)
            return runRequestOnce(block)
        }

        return firstAttempt
    }

    private suspend fun <T> runRequestOnce(
        block: suspend () -> Result<T, DataError.Network>
    ): Result<T, DataError.Network> {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnresolvedAddressException) {
            Log.i(logTag, e.message.toString())
            Result.Error(DataError.Network.NoInternet)
        } catch (e: HttpRequestTimeoutException) {
            Log.i(logTag, e.message.toString())
            Result.Error(DataError.Network.RequestTimeout)
        } catch (e: SocketException) {
            Log.i(logTag, e.message.toString())
            Result.Error(DataError.Network.Unknown)
        } catch (e: Exception) {
            Log.w(logTag, "Lyrics request failed: ${e.message}", e)
            Result.Error(DataError.Network.Unknown)
        }
    }

    private fun LyricsDto.hasLyrics(): Boolean {
        return !plainLyrics.isNullOrBlank() || !syncedLyrics.isNullOrBlank()
    }

    private fun LyricsDto.toLyrics(trackUri: String): Lyrics {
        val plainLyrics = this.plainLyrics?.split('\n')
        var syncedLyrics: List<Pair<Int, String>>? = null
        try {
            syncedLyrics = this.syncedLyrics?.toSyncedLyrics()
        } catch (e: IllegalArgumentException) {
            Log.i(logTag, e.message, e)
        }

        return Lyrics(
            uri = trackUri,
            plain = plainLyrics,
            synced = syncedLyrics
        )
    }

    private fun List<LyricsSearchResultDto>.bestMatch(durationSeconds: Int): LyricsDto? {
        val candidates = filter { it.hasLyrics() }
        if (candidates.isEmpty()) return null

        if (durationSeconds <= 0) {
            return candidates.minWithOrNull(preferSyncedComparator(durationSeconds))?.toLyricsDto()
        }

        val closeMatches = candidates.filter {
            it.durationIsWithin(durationSeconds, LYRICS_DURATION_TOLERANCE_SECONDS)
        }
        if (closeMatches.isNotEmpty()) {
            return closeMatches.minWithOrNull(preferSyncedComparator(durationSeconds))
                ?.toLyricsDto()
        }

        // Rips (e.g. from YouTube) often have intros or outros that shift the duration,
        // so look a bit wider. With a shifted duration the timings may be off, which is
        // why plain lyrics are preferred at this stage.
        val wideMatches = candidates.filter {
            it.durationIsWithin(durationSeconds, LYRICS_WIDE_DURATION_TOLERANCE_SECONDS)
        }
        val best = wideMatches.minWithOrNull(
            compareBy<LyricsSearchResultDto> { if (it.plainLyrics.isNullOrBlank()) 1 else 0 }
                .thenBy { abs((it.duration ?: Double.MAX_VALUE) - durationSeconds) }
        ) ?: return null

        return best.toLyricsDto()
    }

    private fun preferSyncedComparator(durationSeconds: Int): Comparator<LyricsSearchResultDto> {
        return compareBy<LyricsSearchResultDto> { if (it.syncedLyrics.isNullOrBlank()) 1 else 0 }
            .thenBy { abs((it.duration ?: Double.MAX_VALUE) - durationSeconds) }
    }

    private fun LyricsSearchResultDto.durationIsWithin(
        durationSeconds: Int,
        tolerance: Double
    ): Boolean {
        val resultDuration = duration ?: return false
        return abs(resultDuration - durationSeconds) <= tolerance
    }

    private fun LyricsSearchResultDto.toLyricsDto(): LyricsDto {
        return LyricsDto(
            plainLyrics = plainLyrics,
            syncedLyrics = syncedLyrics
        )
    }

    override suspend fun postLyrics(track: Track, lyrics: Lyrics): Result<Unit, DataError.Network> {
        if (
            track.title == null ||
            track.artist == null ||
            track.album == null ||
            lyrics.plain == null
        ) {
            return Result.Error(DataError.Network.BadRequest)
        }

        var response = try {
            client.post(lrclibEndpoint) {
                url {
                    appendPathSegments("request-challenge")
                }
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    append(HttpHeaders.UserAgent, userAgent)
                }
            }
        } catch (e: UnresolvedAddressException) {
            Log.i(logTag, e.message.toString())
            return Result.Error(DataError.Network.NoInternet)
        } catch (_: HttpRequestTimeoutException) {
            return Result.Error(DataError.Network.RequestTimeout)
        } catch (_: SocketException) {
            return Result.Error(DataError.Network.Unknown)
        }

        if (response.status != HttpStatusCode.OK) return Result.Error(DataError.Network.Unknown)

        val challenge = try {
            response.body<ChallengeDto>()
        } catch (_: Exception) {
            return Result.Error(DataError.Network.ParseError)
        }

        SnackbarController.sendEvent(
            SnackbarEvent(
                message = R.string.solving_challenge
            )
        )

        val nonce = solveChallenge(challenge.prefix, challenge.target)

        response = try {
            client.post(lrclibEndpoint) {
                url {
                    appendPathSegments("publish")
                }
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    append(HttpHeaders.UserAgent, userAgent)
                    append("X-Publish-Token", "${challenge.prefix}:$nonce")
                }
                contentType(ContentType.Application.Json)
                setBody(
                    PostLyricsDto(
                        trackName = track.title,
                        artistName = track.artist,
                        albumName = track.album,
                        duration = track.duration / 1000,
                        plainLyrics = lyrics.plain.joinToString("\n"),
                        syncedLyrics = lyrics.synced?.syncedLyricsToString() ?: ""
                    )
                )
            }
        } catch (e: UnresolvedAddressException) {
            Log.i(logTag, e.message.toString())
            return Result.Error(DataError.Network.NoInternet)
        } catch (_: HttpRequestTimeoutException) {
            return Result.Error(DataError.Network.RequestTimeout)
        } catch (_: SocketException) {
            return Result.Error(DataError.Network.Unknown)
        }

        return if (response.status == HttpStatusCode.Created) {
            Result.Success(Unit)
        } else {
            println("RESPONSE BODY: ${response.bodyAsText()}")
            Result.Error(DataError.Network.Unknown)
        }
    }

    fun verifyNonce(result: ByteArray, target: ByteArray): Boolean {
        if (result.size != target.size) {
            return false
        }

        for (i in result.indices) {
            if (result[i].toInt() and 0xFF > target[i].toInt() and 0xFF) {
                return false
            } else if (result[i].toInt() and 0xFF < target[i].toInt() and 0xFF) {
                break
            }
        }

        return true
    }

    fun solveChallenge(prefix: String, targetHex: String): String {
        val target = targetHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        var nonce = 0L

        while (true) {
            val input = "$prefix$nonce"
            val hashed = sha256(input.toByteArray())

            if (verifyNonce(hashed, target)) {
                break
            }

            nonce++
        }

        return nonce.toString()
    }

    fun sha256(input: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input)
    }
}

@Serializable
private data class LyricsDto(
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

@Serializable
private data class LyricsSearchResultDto(
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

private const val LYRICS_DURATION_TOLERANCE_SECONDS = 3.0
private const val LYRICS_WIDE_DURATION_TOLERANCE_SECONDS = 15.0
private const val LYRICS_RETRY_DELAY_MILLIS = 1500L

private fun LyricsSearchResultDto.hasLyrics(): Boolean {
    return !plainLyrics.isNullOrBlank() || !syncedLyrics.isNullOrBlank()
}

private fun cleanLyricsTitle(title: String): String {
    var cleaned = title

    cleaned = cleaned.replace(featSuffixRegex, "")
    cleaned = cleaned.replace(youtubeSuffixRegex, "")
    cleaned = cleaned.replace(bracketedMetadataRegex, "")
    cleaned = cleaned.replace(squareBracketRegex, "")
    cleaned = cleaned.replace(dashMetadataRegex, "")

    // Trailing brackets that mix several noise words, e.g.
    // "Simply Red - Sunrise (Official Remastered Video)". Stripped in a loop so that
    // stacked suffixes such as "(Official Video) [4K]" all disappear.
    while (true) {
        val stripped = cleaned.replace(trailingNoiseBracketRegex, "")
        if (stripped == cleaned) break
        cleaned = stripped
    }

    return cleaned
        .replace(whitespaceRegex, " ")
        .trim()
        .trim('-', '–', '—')
        .trim()
}

private fun mainLyricsArtist(artist: String): String {
    return artist
        .split(artistSeparatorRegex)
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
}

private data class LyricsTagGuess(
    val title: String,
    val artist: String
)

// Tag variants worth trying, most faithful first. Rips often have "Channel - Song (Lyrics)"
// titles and a channel name as the artist, so both the raw tags and the split ones are used.
private fun buildTagGuesses(title: String, artist: String): List<LyricsTagGuess> {
    val cleanedTitle = cleanLyricsTitle(title).ifBlank { title }
    val mainArtist = mainLyricsArtist(artist).ifBlank { artist }

    val guesses = mutableListOf(
        LyricsTagGuess(title = title, artist = artist),
        LyricsTagGuess(title = cleanedTitle, artist = mainArtist)
    )

    splitTitleArtist(cleanedTitle)?.let { (titleArtist, songTitle) ->
        guesses += LyricsTagGuess(title = songTitle, artist = titleArtist)
        guesses += LyricsTagGuess(title = songTitle, artist = mainArtist)
    }

    return guesses.distinct()
}

// "Artist - Song" -> artist to song. Returns null when the title has no such separator.
private fun splitTitleArtist(title: String): Pair<String, String>? {
    val match = titleArtistSeparatorRegex.matchEntire(title) ?: return null
    val artist = match.groupValues[1].trim()
    val songTitle = match.groupValues[2].trim()

    if (artist.isBlank() || songTitle.isBlank()) return null

    return artist to songTitle
}

// "(feat. X)", "[ft. X]", "(featuring X)", "(with X)".
private val featSuffixRegex = Regex(
    """\s*[(\[]\s*(?:feat\.?|ft\.?|featuring|with)\s[^)\]]*[)\]]""",
    RegexOption.IGNORE_CASE
)

// "(Lyrics)", "[Lyric Video]", "(Official Video)", "(Audio)", "(Visualizer)", "(HD)".
private val youtubeSuffixRegex = Regex(
    """\s*[(\[]\s*(?:official\s+)?(?:lyrics?|lyric\s+video|music\s+video|video|audio|visuali[sz]er|mv|hd|hq|4k)\s*[)\]]""",
    RegexOption.IGNORE_CASE
)

// "(Remastered)", "(Live at Wembley)", "(Radio Edit)", "[Bonus Track]", etc.
private val bracketedMetadataRegex = Regex(
    """\s*[(\[]\s*(?:\d{4}\s+)?(?:digitally\s+|digital\s+)?(?:remaster(?:ed)?|live|radio\s+edit|single\s+version|album\s+version|mono|stereo|bonus\s+track|explicit|clean)\b[^)\]]*[)\]]""",
    RegexOption.IGNORE_CASE
)

private val squareBracketRegex = Regex("""\s*\[[^\]]*]""")

// A trailing bracket made of uploader noise words, in any order or combination:
// "(Official Remastered Video)", "(Official Lyric Video)", "(4K Remaster)", "[Official Audio]".
private val trailingNoiseBracketRegex = Regex(
    """\s*[(\[]\s*[^)\]]*\b(?:official|lyrics?|video|audio|visuali[sz]er|remaster(?:ed)?|hd|hq|4k|mv)\b[^)\]]*[)\]]\s*$""",
    RegexOption.IGNORE_CASE
)

// " - Live", " - Remastered 2011", " - Radio Edit", " - Official Video", " - Lyrics".
private val dashMetadataRegex = Regex(
    """\s+[-–—]\s*(?:official|remaster(?:ed)?|live|radio\s+edit|single\s+version|album\s+version|mono|stereo|lyrics?|lyric\s+video|music\s+video|video|audio|visuali[sz]er|mv|hd|hq)\b.*$""",
    RegexOption.IGNORE_CASE
)

private val whitespaceRegex = Regex("""\s{2,}""")

// Multiple artists are usually comma separated, e.g. "Azumi Takahashi, Lotus Juice, ATLUS ...".
private val artistSeparatorRegex = Regex(
    """\s*(?:,|;|&|\bfeat\b\.?|\bft\b\.?|\bfeaturing\b)\s*""",
    RegexOption.IGNORE_CASE
)

// "Artist - Song" style titles, common in YouTube rips.
private val titleArtistSeparatorRegex = Regex("""^(.+?)\s+[-–—]\s+(.+)$""")

@Serializable
private data class ChallengeDto(
    val prefix: String,
    val target: String
)

@Serializable
private data class PostLyricsDto(
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val duration: Int,
    val plainLyrics: String,
    val syncedLyrics: String
)

private fun List<Pair<Int, String>>.syncedLyricsToString(): String {
    return buildString {
        fastForEach {
            append("${it.first.millisToTimestamp()} ${it.second}\n")
        }
    }
}

private fun Int.millisToTimestamp(): String {
    val minutes = (this / 60000).toString()
    val seconds = ((this % 60000) / 1000).toString()
    val centiseconds = ((this % 1000) / 10).toString()

    return "[${minutes.takeLast(2).padStart(2, '0')}:${
        seconds.takeLast(2).padStart(2, '0')
    }.${centiseconds.takeLast(2).padStart(2, '0')}]"
}