package com.jedflix.tv.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    @Test
    fun patchReleaseIsNewer() {
        assertTrue(VersionCompare.isNewer("0.1.1", "0.1.0"))
    }

    @Test
    fun tagPrefixIsIgnored() {
        assertTrue(VersionCompare.isNewer("v0.2.0", "0.1.9"))
    }

    @Test
    fun equalVersionsAreNotNewer() {
        assertFalse(VersionCompare.isNewer("v0.1.0", "0.1.0"))
        assertFalse(VersionCompare.isNewer("0.1.0", "0.1.0"))
    }

    @Test
    fun olderReleaseIsNotNewer() {
        assertFalse(VersionCompare.isNewer("0.1.0", "0.2.0"))
    }

    @Test
    fun prereleaseSuffixIsStripped() {
        assertTrue(VersionCompare.isNewer("0.2.0-beta", "0.1.9"))
        assertFalse(VersionCompare.isNewer("0.1.0-beta", "0.1.0"))
    }

    @Test
    fun missingSegmentsCompareAsZero() {
        assertTrue(VersionCompare.isNewer("1.2", "1.1.9"))
        assertFalse(VersionCompare.isNewer("1.2", "1.2.0"))
    }

    @Test
    fun nonNumericTagsAreNotNewer() {
        assertFalse(VersionCompare.isNewer("latest", "0.1.0"))
        assertFalse(VersionCompare.isNewer("", "0.1.0"))
    }

    @Test
    fun displayVersionStripsPrefix() {
        assertEquals("0.2.0", VersionCompare.displayVersion("v0.2.0"))
    }

    @Test
    fun parseRejectsGarbage() {
        assertNull(VersionCompare.parse("beta"))
        assertEquals(listOf(1, 2, 3), VersionCompare.parse("v1.2.3-rc.1"))
    }
}
