package com.jedflix.tv.ui.player

import androidx.media3.common.MimeTypes

/** DTS bitstreams that Android will "support" as HDMI passthrough and then play as silence without an AVR. */
internal fun isDtsAudioMime(mimeType: String?): Boolean = when (mimeType) {
    MimeTypes.AUDIO_DTS,
    MimeTypes.AUDIO_DTS_HD,
    MimeTypes.AUDIO_DTS_EXPRESS,
    MimeTypes.AUDIO_DTS_UHD_P2,
    -> true
    else -> false
}
