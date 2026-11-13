package com.dn0ne.player.app.presentation.home

import android.content.Context
import com.dn0ne.player.app.data.repository.ListeningHistoryRepository
import com.dn0ne.player.app.domain.listening.DailyListening
import com.dn0ne.player.app.domain.track.Track
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

data class HomeTrack(
    val track: Track,
    val playCount: Int? = null
)

data class HomeState(
    val recentlyPlayed: List<Track> = emptyList(),
    val recentlyPlayedIsFallback: Boolean = false,
    val mix: List<Track> = emptyList(),
    val week: List<DailyListening> = emptyList(),
    val todaySeconds: Long = 0L,
    val mostPlayed: List<HomeTrack> = emptyList(),
    val hasHistory: Boolean = false,
    val hasTracks: Boolean = true,
    val isLoading: Boolean = true
)

/**
 * Builds everything the Home screen shows from the local listening history.
 * No network calls and no generated content: My Mix is assembled from the
 * user's own tracks and is stable for the whole local calendar day.
 */
class HomeViewModel(
    private val listeningHistoryRepository: ListeningHistoryRepository,
    private val dailyMixStore: DailyMixStore
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    private var refreshJob: Job? = null

    fun refresh(tracks: List<Track>) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val newState = withContext(Dispatchers.IO) {
                buildState(tracks)
            }
            _state.value = newState
        }
    }

    private suspend fun buildState(tracks: List<Track>): HomeState {
        if (tracks.isEmpty()) {
            return HomeState(
                hasTracks = false,
                isLoading = false,
                week = listeningHistoryRepository.getListeningSecondsPerDay(7)
            )
        }

        val trackByUri = tracks.associateBy { it.uri.toString() }

        val recentRaw = listeningHistoryRepository.getRecentlyPlayed(limit = 12)
        val recentlyPlayed = recentRaw
            .mapNotNull { trackByUri[it.trackUri] }
            .distinctBy { it.uri }
            .take(10)

        val recentlyAdded = tracks
            .sortedByDescending { it.dateModified }
            .take(10)

        val mostPlayedRaw = listeningHistoryRepository.getMostPlayedLastNDays(days = 30, limit = 10)
        val mostPlayed = mostPlayedRaw
            .mapNotNull { entry ->
                trackByUri[entry.trackUri]?.let { HomeTrack(track = it, playCount = entry.playCount) }
            }
            .take(8)

        val week = listeningHistoryRepository.getListeningSecondsPerDay(7)

        val mix = buildMix(
            tracks = tracks,
            trackByUri = trackByUri,
            mostPlayedRaw = mostPlayedRaw
        )

        return HomeState(
            recentlyPlayed = if (recentlyPlayed.isEmpty()) recentlyAdded else recentlyPlayed,
            recentlyPlayedIsFallback = recentlyPlayed.isEmpty(),
            mix = mix,
            week = week,
            todaySeconds = week.lastOrNull()?.seconds ?: 0L,
            mostPlayed = mostPlayed,
            hasHistory = recentRaw.isNotEmpty(),
            hasTracks = true,
            isLoading = false
        )
    }

    private suspend fun buildMix(
        tracks: List<Track>,
        trackByUri: Map<String, Track>,
        mostPlayedRaw: List<com.dn0ne.player.app.domain.listening.MostPlayedTrack>
    ): List<Track> {
        val today = LocalDate.now().toEpochDay()

        val stored = dailyMixStore.get(today)
        if (stored != null) {
            val resolved = stored
                .mapNotNull { trackByUri[it] }
                .distinctBy { it.uri }
                .take(MIX_SIZE)

            if (resolved.size >= minOf(MIN_STORED_MIX_SIZE, tracks.size)) {
                return resolved
            }
        }

        val mix = LinkedHashSet<Track>()

        mostPlayedRaw
            .mapNotNull { trackByUri[it.trackUri] }
            .take(10)
            .forEach { mix += it }

        val cutoff = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L
        listeningHistoryRepository
            .getTracksNotPlayedSince(
                cutoffMillis = cutoff,
                candidateTrackUris = tracks.map { it.uri.toString() }
            )
            .shuffled()
            .mapNotNull { trackByUri[it] }
            .take(8)
            .forEach { mix += it }

        if (mix.size < MIX_SIZE) {
            tracks.shuffled().forEach { track ->
                if (mix.size < MIX_SIZE) mix += track
            }
        }

        val result = mix.shuffled().take(MIX_SIZE)

        if (result.isNotEmpty()) {
            dailyMixStore.save(today, result.map { it.uri.toString() })
        }

        return result
    }

    private companion object {
        const val MIX_SIZE = 25
        const val MIN_STORED_MIX_SIZE = 5
    }
}

/** Remembers the generated mix for one local calendar day. */
class DailyMixStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("home_daily_mix", Context.MODE_PRIVATE)

    fun get(epochDay: Long): List<String>? {
        if (preferences.getLong(KEY_DAY, -1L) != epochDay) return null
        val raw = preferences.getString(KEY_URIS, null) ?: return null

        return try {
            Json.decodeFromString<List<String>>(raw)
        } catch (e: Exception) {
            null
        }
    }

    fun save(epochDay: Long, uris: List<String>) {
        preferences.edit()
            .putLong(KEY_DAY, epochDay)
            .putString(KEY_URIS, Json.encodeToString(uris))
            .apply()
    }

    private companion object {
        const val KEY_DAY = "day"
        const val KEY_URIS = "uris"
    }
}
