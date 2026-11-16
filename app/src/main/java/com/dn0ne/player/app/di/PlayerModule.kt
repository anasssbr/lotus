package com.dn0ne.player.app.di

import com.dn0ne.player.EqualizerController
import com.dn0ne.player.app.data.DownloadedMusicStore
import com.dn0ne.player.app.data.LyricsReader
import com.dn0ne.player.app.data.LyricsReaderImpl
import com.dn0ne.player.app.data.MetadataWriter
import com.dn0ne.player.app.data.MetadataWriterImpl
import com.dn0ne.player.app.data.SavedPlayerState
import com.dn0ne.player.app.data.notification.DownloadNotifications
import com.dn0ne.player.app.data.remote.download.OnlineMusicProvider
import com.dn0ne.player.app.data.remote.download.YtDlpMusicProvider
import com.dn0ne.player.app.data.remote.lyrics.LrclibLyricsProvider
import com.dn0ne.player.app.data.remote.lyrics.LyricsProvider
import com.dn0ne.player.app.data.remote.metadata.MetadataProvider
import com.dn0ne.player.app.data.remote.metadata.MusicBrainzMetadataProvider
import com.dn0ne.player.app.data.repository.LyricsJson
import com.dn0ne.player.app.data.repository.LyricsRepository
import com.dn0ne.player.app.data.repository.DailyListeningJson
import com.dn0ne.player.app.data.repository.ListeningEventJson
import com.dn0ne.player.app.data.repository.ListeningHistoryRepository
import com.dn0ne.player.app.data.repository.PlaylistJson
import com.dn0ne.player.app.data.repository.PlaylistRepository
import com.dn0ne.player.app.data.repository.RealmListeningHistoryRepository
import com.dn0ne.player.app.data.repository.RealmLyricsRepository
import com.dn0ne.player.app.data.repository.RealmPlaylistRepository
import com.dn0ne.player.app.data.repository.TrackRepository
import com.dn0ne.player.app.data.repository.TrackRepositoryImpl
import com.dn0ne.player.app.presentation.DownloadViewModel
import com.dn0ne.player.app.presentation.PlayerViewModel
import com.dn0ne.player.app.presentation.home.DailyMixStore
import com.dn0ne.player.app.presentation.home.HomeViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val playerModule = module {

    single<TrackRepository> {
        TrackRepositoryImpl(
            context = androidContext(),
            settings = get()
        )
    }

    single<SavedPlayerState> {
        SavedPlayerState(
            context = androidContext()
        )
    }

    single<HttpClient> {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    }
                )
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 180000
            }
        }
    }

    single<MetadataProvider> {
        MusicBrainzMetadataProvider(
            context = androidContext(),
            client = get()
        )
    }

    single<MetadataWriter> {
        MetadataWriterImpl(context = androidContext())
    }

    single<LyricsProvider> {
        LrclibLyricsProvider(
            context = androidContext(),
            client = get()
        )
    }

    single<Realm> {
        val configuration = RealmConfiguration.create(
            schema = setOf(
                LyricsJson::class,
                PlaylistJson::class,
                ListeningEventJson::class,
                DailyListeningJson::class
            )
        )

        Realm.open(configuration)
    }

    single<LyricsRepository> {
        RealmLyricsRepository(
            realm = get()
        )
    }

    single<PlaylistRepository> {
        RealmPlaylistRepository(
            realm = get()
        )
    }

    single<ListeningHistoryRepository> {
        RealmListeningHistoryRepository(
            realm = get()
        )
    }

    single<EqualizerController> {
        EqualizerController(
            context = androidContext()
        )
    }

    single<LyricsReader> {
        LyricsReaderImpl(
            context = androidContext()
        )
    }

    single<OnlineMusicProvider> {
        YtDlpMusicProvider(
            context = androidContext(),
            httpClient = get()
        )
    }

    single<DownloadedMusicStore> {
        DownloadedMusicStore(
            context = androidContext()
        )
    }

    single {
        DownloadNotifications(
            context = androidContext()
        )
    }

    viewModel<DownloadViewModel> {
        DownloadViewModel(
            context = androidContext(),
            provider = get(),
            store = get(),
            notifications = get()
        )
    }

    single {
        DailyMixStore(
            context = androidContext()
        )
    }

    viewModel<HomeViewModel> {
        HomeViewModel(
            listeningHistoryRepository = get(),
            dailyMixStore = get()
        )
    }

    viewModel<PlayerViewModel> {
        PlayerViewModel(
            savedPlayerState = get(),
            trackRepository = get(),
            metadataProvider = get(),
            lyricsProvider = get(),
            lyricsRepository = get(),
            lyricsReader = get(),
            playlistRepository = get(),
            listeningHistoryRepository = get(),
            unsupportedArtworkEditFormats = get<MetadataWriter>().unsupportedArtworkEditFormats,
            settings = get(),
            musicScanner = get(),
            equalizerController = get()
        )
    }
}