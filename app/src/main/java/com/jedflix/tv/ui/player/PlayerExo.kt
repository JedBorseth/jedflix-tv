@file:OptIn(UnstableApi::class)

package com.jedflix.tv.ui.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.jedflix.tv.jedflixUserAgent

internal data class ConfiguredPlayer(
    val player: ExoPlayer,
    val trackSelector: DefaultTrackSelector,
)

/**
 * Media3 defaults skip some MPEG-TS audio (HDMV DTS) and will not select a surround mix when
 * channel-count constraints fail. Torrent/RD files hit both cases.
 */
internal fun createConfiguredPlayer(context: Context): ConfiguredPlayer {
    val appContext = context.applicationContext
    val trackSelector = DefaultTrackSelector(appContext)
    val extractors = DefaultExtractorsFactory()
        .setConstantBitrateSeekingEnabled(true)
        .setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES,
        )
        .setTsExtractorTimestampSearchBytes(TS_TIMESTAMP_SEARCH_BYTES)
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(jedflixUserAgent())
        .setAllowCrossProtocolRedirects(true)
    val player = ExoPlayer.Builder(appContext)
        .setTrackSelector(trackSelector)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(DefaultDataSource.Factory(appContext, http), extractors),
        )
        .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
        .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        .build()
    return ConfiguredPlayer(player, trackSelector)
}

internal const val SEEK_INCREMENT_MS = 10_000L
private const val TS_TIMESTAMP_SEARCH_BYTES = 1_500_000
