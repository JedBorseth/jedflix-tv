package com.jedflix.tv.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseNotesTest {
    @Test
    fun prefersJedflixApk() {
        val picked = pickApkAsset(
            listOf(
                GithubAssetDto("notes.txt", "https://example/notes.txt", 12),
                GithubAssetDto("other.apk", "https://example/other.apk", 10),
                GithubAssetDto("jedflix-tv-0.2.0.apk", "https://example/jedflix.apk", 20),
            ),
        )
        assertEquals("jedflix-tv-0.2.0.apk", picked?.name)
    }

    @Test
    fun fallsBackToFirstApk() {
        val picked = pickApkAsset(
            listOf(GithubAssetDto("app.apk", "https://example/app.apk", 8)),
        )
        assertEquals("app.apk", picked?.name)
    }

    @Test
    fun returnsNullWithoutApk() {
        assertNull(pickApkAsset(listOf(GithubAssetDto("Source.zip", "https://example/s.zip", 1))))
    }

    @Test
    fun summarizesNotesAndFallsBackToName() {
        val body = (1..12).joinToString("\n") { "line $it" }
        val summary = summarizeReleaseNotes(body, "Fallback")
        assertEquals((1..8).joinToString("\n") { "line $it" }, summary)
        assertEquals("Release 0.2.0", summarizeReleaseNotes("   \n", "Release 0.2.0"))
    }
}
