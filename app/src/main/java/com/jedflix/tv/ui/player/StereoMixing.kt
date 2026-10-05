package com.jedflix.tv.ui.player

import androidx.media3.common.audio.ChannelMixingMatrix

/** Mixing layouts accepted by the TV's stereo audio sink. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun stereoMixingMatrices(): List<ChannelMixingMatrix> = buildList {
    add(ChannelMixingMatrix.createForConstantPower(1, 1))
    add(ChannelMixingMatrix.createForConstantPower(2, 2))
    for (channels in 3..6) {
        add(ChannelMixingMatrix.createForConstantPower(channels, 2))
    }
    // Media3's default matrices stop at 5.1. PCM uses Android/FFmpeg channel order;
    // keep dialogue and LFE in both speakers, and surround channels on their own side.
    add(ChannelMixingMatrix(7, 2, floatArrayOf(
        1f, 0f,           // front left
        0f, 1f,           // front right
        0.7071f, 0.7071f, // center
        0.5f, 0.5f,       // LFE
        0.5f, 0.5f,       // back center
        0.7071f, 0f,      // side left
        0f, 0.7071f,      // side right
    )))
    add(ChannelMixingMatrix(8, 2, floatArrayOf(
        1f, 0f,           // front left
        0f, 1f,           // front right
        0.7071f, 0.7071f, // center
        0.5f, 0.5f,       // LFE
        0.7071f, 0f,      // back left
        0f, 0.7071f,      // back right
        0.7071f, 0f,      // side left
        0f, 0.7071f,      // side right
    )))
}
