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

class JedflixTvApp : Application(), SingletonImageLoader.Factory {

    val tmdbClient: TmdbClient by lazy { TmdbClient(BuildConfig.TMDB_API_KEY, BuildConfig.DEBUG) }
    val tmdbRepository: TmdbRepository by lazy { TmdbRepository(tmdbClient.api) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val cometClient: CometClient by lazy { CometClient() }
    val playbackSession: PlaybackSession by lazy { PlaybackSession() }
    val database: JedflixDatabase by lazy { JedflixDatabase.create(this) }
    val userLibrary: UserLibraryRepository by lazy {
        RoomUserLibraryRepository(database, settingsStore)
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
