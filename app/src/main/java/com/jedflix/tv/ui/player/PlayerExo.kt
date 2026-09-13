@file:OptIn(UnstableApi::class)

package com.jedflix.tv.ui.player

import android.content.Context
import android.os.Handler
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.jedflix.tv.jedflixUserAgent
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer

internal data class ConfiguredPlayer(
    val player: ExoPlayer,
    val trackSelector: DefaultTrackSelector,
)

/**
 * Media3 defaults skip some MPEG-TS audio (HDMV DTS) and will not select a surround mix when
 * channel-count constraints fail. Torrent/RD files hit both cases. Android has no DTS software
 * decoder; FFmpeg (same path Stremio's MPV / Just Player use) decodes DTS and DTS-HD MA to PCM
 * when the TV cannot passthrough the bitstream.
 */
internal fun createConfiguredPlayer(context: Context): ConfiguredPlayer {
    val appContext = context.applicationContext
    val trackSelector = DefaultTrackSelector(appContext)
    trackSelector.parameters = trackSelector.buildUponParameters()
        .setExceedRendererCapabilitiesIfNecessary(false)
        .setConstrainAudioChannelCountToDeviceCapabilities(false)
        .build()
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
        .setRenderersFactory(DtsAudioRenderersFactory(appContext))
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

/**
 * MediaCodec will claim DTS as HDMI passthrough (`FORMAT_HANDLED`) on Google TV even when there is
 * no AVR, which plays as silence. FFmpeg decodes those tracks to PCM; MediaCodec keeps AAC/AC3.
 */
private class DtsAudioRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    init {
        setEnableDecoderFallback(true)
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_OFF)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink {
        val mixer = ChannelMixingAudioProcessor()
        mixer.putChannelMixingMatrix(ChannelMixingMatrix.createForConstantPower(1, 1))
        mixer.putChannelMixingMatrix(ChannelMixingMatrix.createForConstantPower(2, 2))
        for (channels in 3..6) {
            mixer.putChannelMixingMatrix(ChannelMixingMatrix.createForConstantPower(channels, 2))
        }
        val sink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(mixer))
            .build()
        // Stereo TV speakers / the emulator cannot play HDMI DTS or 5.1 PCM; mix to 2ch.
        return SurroundToStereoAudioSink(sink)
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        out.add(
            object : MediaCodecAudioRenderer(
                context,
                getCodecAdapterFactory(),
                mediaCodecSelector,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                audioSink,
            ) {
                override fun supportsFormat(
                    mediaCodecSelector: MediaCodecSelector,
                    format: Format,
                ): Int {
                    if (isDtsAudioMime(format.sampleMimeType)) {
                        return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
                    }
                    return super.supportsFormat(mediaCodecSelector, format)
                }
            },
        )
        out.add(FfmpegAudioRenderer(eventHandler, eventListener, audioSink))
    }
}

/**
 * DefaultAudioSink.supportsFormat checks the AudioTrack config before processors run, so FFmpeg
 * would still refuse 5.1 PCM on a stereo HAL. Report those layouts as transcodable to stereo.
 */
private class SurroundToStereoAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    override fun getFormatSupport(format: Format): Int {
        val direct = super.getFormatSupport(format)
        if (direct != AudioSink.SINK_FORMAT_UNSUPPORTED) return direct
        if (format.sampleMimeType != MimeTypes.AUDIO_RAW || format.channelCount <= 2) return direct
        val stereo = format.buildUpon().setChannelCount(2).setChannelMask(Format.NO_VALUE).build()
        val stereoSupport = super.getFormatSupport(stereo)
        return if (stereoSupport == AudioSink.SINK_FORMAT_UNSUPPORTED) {
            AudioSink.SINK_FORMAT_UNSUPPORTED
        } else {
            AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
        }
    }
}

internal const val SEEK_INCREMENT_MS = 10_000L
private const val TS_TIMESTAMP_SEARCH_BYTES = 1_500_000
