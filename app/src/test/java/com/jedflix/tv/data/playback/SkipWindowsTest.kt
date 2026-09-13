package com.jedflix.tv.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkipWindowsTest {
    @Test
    fun showsIntroWhilePlayheadIsInside() {
        val skip = SkipWindows.active(listOf(intro(10_000, 70_000)), positionMs = 12_000)
        assertEquals(SkipKind.Intro, skip?.kind)
        assertEquals(70_000L, skip?.endMs)
    }

    @Test
    fun hidesJustBeforeIntroEnds() {
        assertNull(SkipWindows.active(listOf(intro(10_000, 70_000)), positionMs = 69_500))
    }

    @Test
    fun prefersRecapWhenBothOverlap() {
        val skip = SkipWindows.active(
            listOf(intro(20_000, 80_000), recap(0, 40_000)),
            positionMs = 25_000,
        )
        assertEquals(SkipKind.Recap, skip?.kind)
    }

    @Test
    fun showsOutroNearTheEnd() {
        val skip = SkipWindows.active(listOf(outro(3_000_000, 3_100_000)), positionMs = 3_020_000)
        assertEquals(SkipKind.Outro, skip?.kind)
        assertEquals(3_100_000L, skip?.endMs)
    }

    @Test
    fun ignoresEmptyOrInvertedWindows() {
        assertNull(SkipWindows.active(listOf(intro(50_000, 50_000)), positionMs = 50_000))
        assertNull(SkipWindows.active(listOf(intro(80_000, 10_000)), positionMs = 40_000))
    }

    private fun intro(start: Long, end: Long) = SkipSegment(SkipKind.Intro, start, end)
    private fun recap(start: Long, end: Long) = SkipSegment(SkipKind.Recap, start, end)
    private fun outro(start: Long, end: Long) = SkipSegment(SkipKind.Outro, start, end)
}
