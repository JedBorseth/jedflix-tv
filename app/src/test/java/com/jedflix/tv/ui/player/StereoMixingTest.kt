package com.jedflix.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class StereoMixingTest {
    @Test
    fun supportsEveryLayoutThroughSevenPointOne() {
        val matrices = stereoMixingMatrices().associateBy { it.inputChannelCount }
        for (channels in 1..8) {
            val matrix = matrices.getValue(channels)
            assertEquals(if (channels == 1) 1 else 2, matrix.outputChannelCount)
            if (channels >= 7) for (input in 0 until channels) {
                assertTrue((0 until matrix.outputChannelCount).any { matrix.getMixingCoefficient(input, it) > 0 })
            }
        }
    }

    @Test
    fun sevenPointOneKeepsLeftRightSurroundAndDialogue() {
        val matrix = stereoMixingMatrices().single { it.inputChannelCount == 8 }
        // PCM order: front L/R, center, LFE, back L/R, side L/R.
        for (left in listOf(0, 4, 6)) {
            assertTrue(matrix.getMixingCoefficient(left, 0) > 0)
            assertEquals(0f, matrix.getMixingCoefficient(left, 1), 0f)
        }
        for (right in listOf(1, 5, 7)) {
            assertEquals(0f, matrix.getMixingCoefficient(right, 0), 0f)
            assertTrue(matrix.getMixingCoefficient(right, 1) > 0)
        }
        for (center in listOf(2, 3)) {
            assertTrue(matrix.getMixingCoefficient(center, 0) > 0)
            assertEquals(matrix.getMixingCoefficient(center, 0), matrix.getMixingCoefficient(center, 1), 0f)
        }
    }
}
