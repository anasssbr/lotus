package com.dn0ne.player.app.data.remote.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps the bundled yt-dlp up to date.
 *
 * YouTube changes its player frequently; an extractor that is a few months old starts
 * answering 403 to audio requests. The bundled binary cannot be updated without an app
 * release, so it is refreshed from GitHub in the background.
 */
class YtDlpUpdater(private val context: Context) {
    private val logTag = "YtDlpUpdater"
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun updateIfDue(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
        val isDue = lastUpdate == 0L || now - lastUpdate >= UPDATE_INTERVAL_MILLIS

        if (!isDue || isActiveNetworkMetered()) return@withContext false

        try {
            val status = YoutubeDL.getInstance()
                .updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)

            val version = runCatching {
                YoutubeDL.getInstance().versionName(context)
            }.getOrNull()

            prefs.edit()
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .putString(KEY_VERSION, version)
                .apply()

            Log.i(logTag, "yt-dlp update status=$status version=$version")
            true
        } catch (e: Throwable) {
            Log.w(logTag, "yt-dlp update failed: ${e.message}", e)
            false
        }
    }

    private fun isActiveNetworkMetered(): Boolean {
        return try {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } catch (e: Throwable) {
            false
        }
    }

    private companion object {
        const val PREFS_NAME = "yt_dlp_updater"
        const val KEY_LAST_UPDATE = "last_update"
        const val KEY_VERSION = "version"
        const val UPDATE_INTERVAL_MILLIS = 3L * 24L * 60L * 60L * 1000L
    }
}
