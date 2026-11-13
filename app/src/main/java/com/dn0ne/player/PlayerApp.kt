package com.dn0ne.player

import android.app.Application
import android.util.Log
import com.dn0ne.player.app.data.remote.download.OnlineMusicProvider
import com.dn0ne.player.app.di.playerModule
import com.dn0ne.player.core.di.appModule
import com.dn0ne.player.setup.di.setupModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PlayerApp: Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@PlayerApp)
            modules(appModule, setupModule, playerModule)
        }

        // Unpacking the bundled Python/yt-dlp takes a few seconds; doing it here keeps
        // that cost off the first online search.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { get<OnlineMusicProvider>().warmUp() }
                .onFailure { Log.w("PlayerApp", "yt-dlp warm-up failed", it) }
        }
    }
}
