package com.dn0ne.player.app.data.repository

import com.dn0ne.player.app.domain.listening.DailyListening
import com.dn0ne.player.app.domain.listening.MostPlayedTrack
import com.dn0ne.player.app.domain.listening.RecentlyPlayedTrack
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.time.LocalDate

/**
 * Stores listening history in the same Realm instance that already holds playlists
 * and lyrics. Every query returns plain domain objects, so no Realm object ever
 * escapes the repository and a track that was deleted from the device is only ever
 * a stale URI string - never a lookup that could throw.
 */
class RealmListeningHistoryRepository(
    private val realm: Realm
) : ListeningHistoryRepository {

    override suspend fun recordPlay(
        eventId: String,
        trackUri: String,
        title: String?,
        artist: String?,
        playedAt: Long,
        secondsPlayed: Long
    ) {
        realm.write {
            copyToRealm(
                instance = ListeningEventJson().apply {
                    this.eventId = eventId
                    this.trackUri = trackUri
                    this.title = title
                    this.artist = artist
                    this.playedAt = playedAt
                    this.secondsPlayed = secondsPlayed
                    this.counted = true
                },
                updatePolicy = UpdatePolicy.ALL
            )
        }
    }

    override suspend fun addListeningSeconds(epochDay: Long, seconds: Long) {
        if (seconds <= 0L) return

        realm.write {
            val existing = query<DailyListeningJson>(query = "epochDay = $0", epochDay)
                .find()
                .firstOrNull()

            if (existing == null) {
                copyToRealm(DailyListeningJson().apply {
                    this.epochDay = epochDay
                    this.seconds = seconds
                })
            } else {
                findLatest(existing)?.let { it.seconds += seconds }
            }
        }
    }

    override suspend fun getRecentlyPlayed(limit: Int): List<RecentlyPlayedTrack> {
        val events = countedEvents()

        return events
            .groupBy { it.trackUri }
            .mapNotNull { (trackUri, trackEvents) ->
                val latest = trackEvents.maxByOrNull { it.playedAt } ?: return@mapNotNull null

                RecentlyPlayedTrack(
                    trackUri = trackUri,
                    title = latest.title,
                    artist = latest.artist,
                    lastPlayedAt = latest.playedAt,
                    secondsPlayed = latest.secondsPlayed
                )
            }
            .sortedByDescending { it.lastPlayedAt }
            .take(limit)
    }

    override suspend fun getMostPlayedLastNDays(days: Int, limit: Int): List<MostPlayedTrack> {
        if (days <= 0) return emptyList()

        val cutoff = System.currentTimeMillis() - days * MILLIS_PER_DAY

        return countedEvents()
            .filter { it.playedAt >= cutoff }
            .groupBy { it.trackUri }
            .mapNotNull { (trackUri, trackEvents) ->
                val latest = trackEvents.maxByOrNull { it.playedAt } ?: return@mapNotNull null

                MostPlayedTrack(
                    trackUri = trackUri,
                    title = latest.title,
                    artist = latest.artist,
                    playCount = trackEvents.size,
                    lastPlayedAt = latest.playedAt
                )
            }
            .sortedWith(
                compareByDescending<MostPlayedTrack> { it.playCount }
                    .thenByDescending { it.lastPlayedAt }
            )
            .take(limit)
    }

    override suspend fun getListeningSecondsPerDay(days: Int): List<DailyListening> {
        if (days <= 0) return emptyList()

        val byDay = realm.query<DailyListeningJson>()
            .find()
            .associate { it.epochDay to it.seconds }

        val today = LocalDate.now().toEpochDay()

        return (days - 1 downTo 0).map { offset ->
            val epochDay = today - offset
            DailyListening(
                epochDay = epochDay,
                seconds = byDay[epochDay] ?: 0L
            )
        }
    }

    override suspend fun getLastPlayedTimestamps(): Map<String, Long> {
        return countedEvents()
            .groupBy { it.trackUri }
            .mapNotNull { (trackUri, trackEvents) ->
                val lastPlayedAt = trackEvents.maxOfOrNull { it.playedAt } ?: return@mapNotNull null
                trackUri to lastPlayedAt
            }
            .toMap()
    }

    override suspend fun getTracksNotPlayedSince(
        cutoffMillis: Long,
        candidateTrackUris: List<String>
    ): List<String> {
        if (candidateTrackUris.isEmpty()) return emptyList()

        val lastPlayed = getLastPlayedTimestamps()

        return candidateTrackUris.filter { trackUri ->
            val lastPlayedAt = lastPlayed[trackUri]
            lastPlayedAt == null || lastPlayedAt < cutoffMillis
        }
    }

    private fun countedEvents(): List<ListeningEventJson> {
        return realm.query<ListeningEventJson>()
            .find()
            .filter { it.counted }
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}

class ListeningEventJson() : RealmObject {
    @PrimaryKey
    var eventId: String = ""
    var trackUri: String = ""
    var title: String? = null
    var artist: String? = null
    var playedAt: Long = 0L
    var secondsPlayed: Long = 0L
    var counted: Boolean = false
}

class DailyListeningJson() : RealmObject {
    @PrimaryKey
    var epochDay: Long = 0L
    var seconds: Long = 0L
}
