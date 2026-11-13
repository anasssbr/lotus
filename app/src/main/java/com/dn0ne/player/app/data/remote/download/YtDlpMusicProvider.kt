package com.dn0ne.player.app.data.remote.download

import android.content.Context
import android.util.Log
import com.dn0ne.player.app.domain.download.OnlineTrack
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class YtDlpMusicProvider(
    private val context: Context,
    httpClient: HttpClient
) : OnlineMusicProvider {
    private val logTag = "YtDlpMusicProvider"
    private val appContext = context.applicationContext
    private val updater = YtDlpUpdater(appContext)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val initMutex = Mutex()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val streamCacheMutex = Mutex()
    private val streamCache = mutableMapOf<String, CachedStream>()

    @Volatile
    private var isInitialized = false

    init {
        runCatching { NewPipeRuntime.initialize(httpClient) }
            .onFailure { Log.w(logTag, "NewPipe init failed", it) }
    }

    override suspend fun warmUp() {
        runCatching { ensureInitialized() }
            .onFailure {
                Log.w(logTag, "yt-dlp init failed", it)
                return
            }

        updater.updateIfDue()
    }

    override suspend fun search(query: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val isDirectLink = trimmed.startsWith("http://") || trimmed.startsWith("https://")

        // NewPipe answers in about a second and needs no Python at all. yt-dlp stays as
        // the fallback for direct links and for the occasional NewPipe hiccup.
        if (!isDirectLink) {
            val fastResults = runCatching { searchWithNewPipe(trimmed) }
                .onFailure { Log.w(logTag, "NewPipe search failed: ${it.message}", it) }
                .getOrDefault(emptyList())

            if (fastResults.isNotEmpty()) return@withContext fastResults
        }

        searchWithYtDlp(trimmed, isDirectLink)
    }

    override suspend fun getStreamUrl(track: OnlineTrack): String? = withContext(Dispatchers.IO) {
        resolveAudio(track)?.url
    }

    override suspend fun download(
        track: OnlineTrack,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        // The app wipes cacheDir in onStop, so downloads live in filesDir until they are saved.
        val downloadDir = File(appContext.filesDir, DOWNLOAD_DIR_NAME).apply { mkdirs() }
        // Only one download runs at a time, so leftovers are always stale.
        downloadDir.listFiles()?.forEach { it.delete() }

        // Fast path: NewPipe resolves a direct audio URL, then plain HTTP streams the file.
        val resolved = runCatching { resolveAudio(track) }
            .onFailure { Log.w(logTag, "NewPipe resolve failed: ${it.message}", it) }
            .getOrNull()

        if (resolved != null) {
            val target = File(downloadDir, "${track.id}.${resolved.extension}")
            val downloaded = runCatching { downloadDirect(resolved.url, target, onProgress) }

            if (downloaded.isSuccess && target.length() > 0L) {
                return@withContext target
            }

            Log.w(logTag, "Direct download failed, falling back to yt-dlp", downloaded.exceptionOrNull())
            target.delete()
        }

        downloadWithYtDlp(track, downloadDir, onProgress)
    }

    private suspend fun resolveAudio(track: OnlineTrack): ResolvedAudio? {
        val now = System.currentTimeMillis()
        streamCacheMutex.withLock {
            streamCache[track.id]
                ?.takeIf { now - it.timestamp < STREAM_CACHE_TTL_MILLIS }
                ?.audio
        }?.let { return it }

        val resolved = resolveWithNewPipe(track) ?: resolveWithYtDlp(track) ?: return null

        streamCacheMutex.withLock {
            val cutoff = System.currentTimeMillis() - STREAM_CACHE_TTL_MILLIS
            streamCache.entries.removeAll { it.value.timestamp < cutoff }
            streamCache[track.id] = CachedStream(
                audio = resolved,
                timestamp = System.currentTimeMillis()
            )
        }

        return resolved
    }

    private fun resolveWithNewPipe(track: OnlineTrack): ResolvedAudio? {
        val info = StreamInfo.getInfo(ServiceList.YouTube, track.webpageUrl)

        val best = info.audioStreams
            .asSequence()
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .filter { !it.url.isNullOrBlank() }
            .sortedWith(
                compareByDescending<AudioStream> { it.format == MediaFormat.M4A }
                    .thenByDescending { it.averageBitrate }
            )
            .firstOrNull()
            ?: return null

        return ResolvedAudio(
            url = best.url ?: return null,
            extension = best.format?.suffix ?: "m4a",
            mimeType = best.format?.mimeType ?: "audio/mp4"
        )
    }

    private suspend fun resolveWithYtDlp(track: OnlineTrack): ResolvedAudio? {
        ensureInitialized()

        val request = YoutubeDLRequest(track.webpageUrl).apply {
            addOption("-f", AUDIO_FORMAT_SELECTOR)
            addOption("-g")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("-R", "1")
            addOption("--socket-timeout", "5")
        }

        val url = YoutubeDL.getInstance()
            .execute(request)
            .out
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("http") }
            ?: return null

        return ResolvedAudio(
            url = url,
            extension = if (url.contains("mime=audio%2Fwebm") || url.contains("mime=audio/webm")) "webm" else "m4a",
            mimeType = "audio/mp4"
        )
    }

    private fun downloadDirect(url: String, target: File, onProgress: (Float) -> Unit) {
        val startedAt = System.currentTimeMillis()

        // YouTube's CDN throttles a single connection, so large files are split into
        // parallel byte ranges. Small files are not worth the extra round trips.
        val totalBytes = runCatching { probeContentLength(url) }.getOrNull()

        if (totalBytes != null && totalBytes >= PARALLEL_DOWNLOAD_THRESHOLD_BYTES) {
            val parallel = runCatching {
                downloadInChunks(url, target, totalBytes, onProgress)
            }.onFailure {
                Log.w(logTag, "Parallel download failed, retrying single stream: ${it.message}", it)
            }.isSuccess

            if (parallel) {
                logSpeed(target, startedAt)
                onProgress(100f)
                return
            }

            target.delete()
        }

        downloadInOneStream(url, target, onProgress)
        logSpeed(target, startedAt)
    }

    /** Asks the server for a 1-byte range to learn the total size without downloading it. */
    private fun probeContentLength(url: String): Long? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MEDIA_USER_AGENT)
            .header("Accept-Encoding", "identity")
            .header("Range", "bytes=0-0")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body
            val contentRange = response.header("Content-Range")
            val total = contentRange?.substringAfterLast('/')?.toLongOrNull()
            body?.close()

            return when {
                response.code == 206 && total != null && total > 0 -> total
                response.isSuccessful && body != null && body.contentLength() > 0 -> body.contentLength()
                else -> null
            }
        }
    }

    private fun downloadInChunks(
        url: String,
        target: File,
        totalBytes: Long,
        onProgress: (Float) -> Unit
    ) {
        val chunkCount = minOf(MAX_PARALLEL_CONNECTIONS, (totalBytes / MIN_CHUNK_BYTES).toInt().coerceAtLeast(1))
        val chunkSize = (totalBytes + chunkCount - 1) / chunkCount
        val writtenBytes = AtomicLong(0L)
        val writeLock = Any()
        val executor = Executors.newFixedThreadPool(chunkCount)

        try {
            RandomAccessFile(target, "rw").use { file ->
                file.setLength(totalBytes)

                val futures = (0 until chunkCount).mapNotNull { index ->
                    val start = index * chunkSize
                    if (start >= totalBytes) return@mapNotNull null
                    val end = minOf(start + chunkSize - 1, totalBytes - 1)

                    executor.submit {
                        val request = Request.Builder()
                            .url(url)
                            .header("User-Agent", MEDIA_USER_AGENT)
                            .header("Accept-Encoding", "identity")
                            .header("Range", "bytes=$start-$end")
                            .build()

                        okHttpClient.newCall(request).execute().use { response ->
                            if (response.code != 206) {
                                error("HTTP ${response.code} for range $start-$end")
                            }

                            val body = response.body ?: error("Empty response body")
                            body.byteStream().use { input ->
                                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                                var position = start

                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break

                                    synchronized(writeLock) {
                                        file.seek(position)
                                        file.write(buffer, 0, read)
                                    }
                                    position += read

                                    val done = writtenBytes.addAndGet(read.toLong())
                                    onProgress((done * 100.0 / totalBytes).toFloat())
                                }
                            }
                        }
                    }
                }

                // get() rethrows the first failure from any worker.
                futures.forEach { it.get() }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun downloadInOneStream(url: String, target: File, onProgress: (Float) -> Unit) {
        var writtenBytes = 0L
        var totalBytes = -1L
        var attempt = 0

        while (true) {
            try {
                val builder = Request.Builder()
                    .url(url)
                    .header("User-Agent", MEDIA_USER_AGENT)
                    .header("Accept-Encoding", "identity")

                if (writtenBytes > 0L) {
                    builder.header("Range", "bytes=$writtenBytes-")
                }

                okHttpClient.newCall(builder.build()).execute().use { response ->
                    if (response.code != 200 && response.code != 206) {
                        error("HTTP ${response.code} while downloading audio")
                    }

                    // A 200 means the server ignored the range: start over instead of appending.
                    if (response.code == 200) writtenBytes = 0L

                    val body = response.body ?: error("Empty response body")
                    if (totalBytes <= 0L) {
                        totalBytes = if (response.code == 206) {
                            response.header("Content-Range")
                                ?.substringAfterLast('/')
                                ?.toLongOrNull()
                                ?: body.contentLength()
                        } else {
                            body.contentLength()
                        }
                    }

                    FileOutputStream(target, writtenBytes > 0L).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                writtenBytes += read

                                if (totalBytes > 0L) {
                                    onProgress((writtenBytes * 100.0 / totalBytes).toFloat())
                                }
                            }
                        }
                    }
                }

                onProgress(100f)
                return
            } catch (e: Throwable) {
                attempt++
                val isComplete = totalBytes > 0L && writtenBytes >= totalBytes
                if (isComplete || attempt >= MAX_DOWNLOAD_ATTEMPTS) throw e

                Log.w(logTag, "Download interrupted at $writtenBytes bytes, resuming (attempt $attempt)")
            }
        }
    }

    private fun logSpeed(file: File, startedAt: Long) {
        val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        val megabytesPerSecond = file.length() * 1000.0 / elapsed / (1024.0 * 1024.0)
        Log.i(
            logTag,
            "Downloaded ${file.length() / 1024} KB in $elapsed ms " +
                    "(~${String.format("%.2f", megabytesPerSecond)} MB/s)"
        )
    }

    private suspend fun downloadWithYtDlp(
        track: OnlineTrack,
        downloadDir: File,
        onProgress: (Float) -> Unit
    ): File {
        ensureInitialized()

        val request = YoutubeDLRequest(track.webpageUrl).apply {
            addOption("-f", AUDIO_FORMAT_SELECTOR)
            addOption("-P", downloadDir.absolutePath)
            addOption("-o", "%(id)s.%(ext)s")
            addOption("--no-playlist")
            addOption("--no-part")
            addOption("--no-mtime")
            addOption("--newline")
            addOption("--no-warnings")
            addOption("-R", "1")
            addOption("--socket-timeout", "5")
        }

        YoutubeDL.getInstance().execute(request) { progress, _, _ ->
            onProgress(progress.coerceIn(0f, 100f))
        }

        return downloadDir.listFiles()
            ?.firstOrNull { it.isFile && it.name.startsWith(track.id) }
            ?: downloadDir.listFiles()?.firstOrNull { it.isFile }
            ?: error("yt-dlp finished without producing a file")
    }

    private fun searchWithNewPipe(query: String): List<OnlineTrack> {
        val extractor = ServiceList.YouTube.getSearchExtractor(query, listOf("videos"), "")
        extractor.fetchPage()

        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item -> runCatching { item.toOnlineTrack() }.getOrNull() }
            .take(SEARCH_LIMIT)
    }

    private suspend fun searchWithYtDlp(query: String, isDirectLink: Boolean): List<OnlineTrack> {
        ensureInitialized()

        val target = if (isDirectLink) query else "ytsearch$SEARCH_LIMIT:$query"

        val request = YoutubeDLRequest(target).apply {
            addOption("--dump-json")
            addOption("--no-warnings")
            addOption("--ignore-errors")
            addOption("--no-playlist")
            if (!isDirectLink) addOption("--flat-playlist")
        }

        val response = YoutubeDL.getInstance().execute(request)

        return response.out
            .lineSequence()
            .filter { it.startsWith("{") }
            .mapNotNull { line ->
                runCatching { json.decodeFromString<YtDlpEntry>(line) }.getOrNull()
            }
            .mapNotNull { it.toOnlineTrack() }
            .distinctBy { it.id }
            .toList()
    }

    private suspend fun ensureInitialized() {
        if (isInitialized) return

        initMutex.withLock {
            if (isInitialized) return@withLock

            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().init(appContext)
                isInitialized = true
                Log.i(logTag, "yt-dlp initialized")
            }
        }
    }

    private fun YtDlpEntry.toOnlineTrack(): OnlineTrack? {
        val videoId = id?.takeIf { it.isNotBlank() }
            ?: webpageUrl?.let { extractVideoId(it) }
            ?: url?.takeIf { it.startsWith("http") }?.let { extractVideoId(it) }
            ?: return null

        val videoTitle = title?.takeIf { it.isNotBlank() } ?: return null

        val uploader = (uploader ?: channel ?: artist)
            ?.removeSuffix(" - Topic")
            ?.trim()
            .orEmpty()

        return OnlineTrack(
            id = videoId,
            title = videoTitle,
            artist = uploader,
            durationSeconds = duration?.toInt() ?: 0,
            thumbnailUrl = thumbnail
                ?: thumbnails?.lastOrNull()?.url
                ?: "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
            webpageUrl = webpageUrl ?: "https://www.youtube.com/watch?v=$videoId"
        )
    }

    private fun StreamInfoItem.toOnlineTrack(): OnlineTrack? {
        val itemUrl = url?.takeIf { it.isNotBlank() } ?: return null
        val videoId = extractVideoId(itemUrl) ?: return null

        return OnlineTrack(
            id = videoId,
            title = name?.takeIf { it.isNotBlank() } ?: videoId,
            artist = uploaderName?.removeSuffix(" - Topic")?.trim().orEmpty(),
            durationSeconds = duration
                .takeIf { it in 1..Int.MAX_VALUE.toLong() }
                ?.toInt()
                ?: 0,
            thumbnailUrl = thumbnails.lastOrNull()?.url
                ?.takeIf { it.isNotBlank() }
                ?: "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
            webpageUrl = "https://www.youtube.com/watch?v=$videoId"
        )
    }

    private fun extractVideoId(url: String): String? {
        return url.substringAfter("v=", "")
            .substringBefore("&")
            .takeIf { it.isNotBlank() }
            ?: url.substringAfter("youtu.be/", "")
                .substringBefore("?")
                .takeIf { it.isNotBlank() }
    }
}

@Serializable
private data class YtDlpEntry(
    val id: String? = null,
    val title: String? = null,
    val uploader: String? = null,
    val channel: String? = null,
    val artist: String? = null,
    val duration: Double? = null,
    val thumbnail: String? = null,
    val thumbnails: List<YtDlpThumbnail>? = null,
    @SerialName("webpage_url") val webpageUrl: String? = null,
    val url: String? = null
)

@Serializable
private data class YtDlpThumbnail(
    val url: String? = null
)

private data class ResolvedAudio(
    val url: String,
    val extension: String,
    val mimeType: String
)

private data class CachedStream(
    val audio: ResolvedAudio,
    val timestamp: Long
)

private const val SEARCH_LIMIT = 20
private const val DOWNLOAD_DIR_NAME = "downloads"
private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
private const val MAX_PARALLEL_CONNECTIONS = 4
private const val MIN_CHUNK_BYTES = 1024L * 1024L
private const val PARALLEL_DOWNLOAD_THRESHOLD_BYTES = 2L * 1024L * 1024L
private const val MAX_DOWNLOAD_ATTEMPTS = 3
private const val STREAM_CACHE_TTL_MILLIS = 10L * 60L * 1000L
private const val MEDIA_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

// Direct HTTP audio only: DASH/m3u8 streams cannot be handed to ExoPlayer as a plain URL.
private const val AUDIO_FORMAT_SELECTOR =
    "bestaudio[ext=m4a][protocol^=http][protocol!*=dash]/" +
            "bestaudio[protocol^=http][protocol!*=dash]/bestaudio"
