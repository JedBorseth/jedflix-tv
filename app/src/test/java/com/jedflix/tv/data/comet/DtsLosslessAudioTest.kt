package com.jedflix.tv.data.comet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DtsLosslessAudioTest {
    @Test
    fun cometConfigDisablesDtsLosslessFetch() {
        val payload = Json.encodeToString(CometConfigDto.serializer(), cometAddonConfig("key", 5))
        val audio = Json.parseToJsonElement(payload)
            .jsonObject
            .getValue("rtnSettings")
            .jsonObject
            .getValue("custom_ranks")
            .jsonObject
            .getValue("audio")
            .jsonObject
            .getValue("dts_lossless")
            .jsonObject
        assertEquals("false", audio.getValue("fetch").jsonPrimitive.content)
    }

    @Test
    fun dtsHdMaAndDtsXAreLossless() {
        assertTrue(stream("Movie.2024.1080p.BluRay.DTS-HD.MA.5.1.mkv").hasDtsLosslessAudio)
        assertTrue(stream("Movie.2024.2160p.UHD.BluRay.DTS-HDMA.7.1.mkv").hasDtsLosslessAudio)
        assertTrue(stream("Movie.2024.1080p.BluRay.DTSHDMA.5.1.mkv").hasDtsLosslessAudio)
        assertTrue(stream("Movie.2024.1080p.BluRay.DTS:X.7.1.mkv").hasDtsLosslessAudio)
        assertTrue(stream("Movie.2024.1080p.mkv", details = listOf("🔊 DTS Lossless 5.1")).hasDtsLosslessAudio)
    }

    @Test
    fun lossyDtsAndOtherCodecsAreKept() {
        assertFalse(stream("Movie.2024.1080p.BluRay.DTS.5.1.x264.mkv").hasDtsLosslessAudio)
        assertFalse(stream("Movie.2024.1080p.BluRay.DTS-HD.HRA.5.1.mkv").hasDtsLosslessAudio)
        assertFalse(stream("Movie.2024.1080p.WEB-DL.DDP5.1.Atmos.mkv").hasDtsLosslessAudio)
        assertFalse(stream("Movie.2024.1080p.BluRay.TrueHD.Atmos.mkv").hasDtsLosslessAudio)
        assertFalse(stream("Movie.2024.1080p.BluRay.DTS.x264.mkv").hasDtsLosslessAudio)
    }

    private fun stream(filename: String, details: List<String> = emptyList()) = StreamOption(
        id = filename,
        resolution = "1080P",
        filename = filename,
        details = details,
        sizeBytes = 1L,
        cached = true,
        playbackUrl = "https://comet.example/playback/$filename",
    )
}
