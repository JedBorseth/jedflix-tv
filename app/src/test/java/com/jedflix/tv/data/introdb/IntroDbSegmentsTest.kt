package com.jedflix.tv.data.introdb

import com.jedflix.tv.data.playback.SkipKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroDbSegmentsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun parsesIntroRecapAndOutro() {
        val body = """
            {
              "imdb_id":"tt0903747",
              "season":1,
              "episode":2,
              "intro":{"start_sec":314.5,"end_sec":331,"start_ms":314500,"end_ms":331000},
              "recap":{"start_ms":0,"end_ms":28000},
              "outro":{"start_sec":2843,"end_sec":2901,"start_ms":2843000,"end_ms":2901000}
            }
        """.trimIndent()
        val segments = IntroDbSegments.parse(json, body)
        assertEquals(listOf(SkipKind.Recap, SkipKind.Intro, SkipKind.Outro), segments.map { it.kind })
        assertEquals(0L, segments[0].startMs)
        assertEquals(28_000L, segments[0].endMs)
        assertEquals(314_500L, segments[1].startMs)
        assertEquals(331_000L, segments[1].endMs)
        assertEquals(2_843_000L, segments[2].startMs)
    }

    @Test
    fun fallsBackToSecondsWhenMsMissing() {
        val body = """{"intro":{"start_sec":10.5,"end_sec":42},"recap":null,"outro":null}"""
        val segments = IntroDbSegments.parse(json, body)
        assertEquals(1, segments.size)
        assertEquals(SkipKind.Intro, segments[0].kind)
        assertEquals(10_500L, segments[0].startMs)
        assertEquals(42_000L, segments[0].endMs)
    }

    @Test
    fun blankOrInvalidIsEmpty() {
        assertTrue(IntroDbSegments.parse(json, "").isEmpty())
        assertTrue(IntroDbSegments.parse(json, "<html>nope</html>").isEmpty())
        assertTrue(IntroDbSegments.parse(json, """{"intro":{"start_ms":90,"end_ms":10}}""").isEmpty())
    }
}
