package com.jedflix.tv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.jedflix.tv.data.comet.CometClient
import com.jedflix.tv.data.library.RoomUserLibraryRepository
import com.jedflix.tv.data.library.UserLibraryRepository
import com.jedflix.tv.data.local.JedflixDatabase
import com.jedflix.tv.data.playback.PlaybackSession
import com.jedflix.tv.data.settings.SettingsStore
import com.jedflix.tv.data.tmdb.TmdbClient
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.data.update.ApkDownloader
import com.jedflix.tv.data.update.ApkInstaller
import com.jedflix.tv.data.update.AppUpdateManager
import com.jedflix.tv.data.update.GithubReleaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class JedflixTvApp : Application(), SingletonImageLoader.Factory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val tmdbClient: TmdbClient by lazy { TmdbClient(BuildConfig.TMDB_API_KEY, BuildConfig.DEBUG) }
    val tmdbRepository: TmdbRepository by lazy { TmdbRepository(tmdbClient.api) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val cometClient: CometClient by lazy { CometClient() }
    val playbackSession: PlaybackSession by lazy { PlaybackSession() }
    val database: JedflixDatabase by lazy { JedflixDatabase.create(this) }
    val userLibrary: UserLibraryRepository by lazy {
        RoomUserLibraryRepository(database, settingsStore)
    }
    val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManager(
            store = settingsStore,
            github = GithubReleaseClient(),
            downloader = ApkDownloader(this),
            installer = ApkInstaller(this),
            scope = applicationScope,
            currentVersion = BuildConfig.VERSION_NAME,
        )
    }

    override fun onCreate() {
        super.onCreate()
        appUpdateManager.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                // Share the TMDB OkHttp client so posters reuse its connection pool.
                add(OkHttpNetworkFetcherFactory(callFactory = { tmdbClient.okHttpClient }))
            }
            .crossfade(true)
            .build()
}
