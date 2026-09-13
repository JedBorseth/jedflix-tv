package com.jedflix.tv.ui.player

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DtsAudioTest {
    @Test
    fun marksLosslessDtsForSoftwareDecode() {
        assertTrue(isDtsAudioMime(MimeTypes.AUDIO_DTS))
        assertTrue(isDtsAudioMime(MimeTypes.AUDIO_DTS_HD))
        assertTrue(isDtsAudioMime(MimeTypes.AUDIO_DTS_EXPRESS))
        assertTrue(isDtsAudioMime(MimeTypes.AUDIO_DTS_UHD_P2))
    }

    @Test
    fun leavesNormalAudioToMediaCodec() {
        assertFalse(isDtsAudioMime(MimeTypes.AUDIO_AAC))
        assertFalse(isDtsAudioMime(MimeTypes.AUDIO_AC3))
        assertFalse(isDtsAudioMime(MimeTypes.AUDIO_E_AC3))
        assertFalse(isDtsAudioMime(null))
    }
}
