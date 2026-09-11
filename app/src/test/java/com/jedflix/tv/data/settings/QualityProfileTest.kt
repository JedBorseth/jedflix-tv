package com.jedflix.tv.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class QualityProfileTest {
    @Test
    fun fromStoredMapsKnownValues() {
        assertEquals(QualityProfile.Max, QualityProfile.fromStored("max"))
        assertEquals(QualityProfile.Medium, QualityProfile.fromStored("medium"))
        assertEquals(QualityProfile.Low, QualityProfile.fromStored("low"))
    }

    @Test
    fun fromStoredIsCaseInsensitive() {
        assertEquals(QualityProfile.Max, QualityProfile.fromStored("MAX"))
        assertEquals(QualityProfile.Medium, QualityProfile.fromStored("Medium"))
    }

    @Test
    fun fromStoredFallsBackToMax() {
        assertEquals(QualityProfile.Max, QualityProfile.fromStored(null))
        assertEquals(QualityProfile.Max, QualityProfile.fromStored(""))
        assertEquals(QualityProfile.Max, QualityProfile.fromStored("ultra"))
    }
}
