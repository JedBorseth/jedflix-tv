package com.jedflix.tv.data.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NewJedflixManifestTest {
    private val sha = "ab8dd6ca4a5e10d317c3a112d87d5b64fd7b5c40bd1be28de22651e66d94816b"
    private val base = "https://github.com/JedBorseth/jedflix-tv-stremio/releases/download/v1.0.1"

    private fun manifest(
        packageName: String = "com.stremio.one",
        versionName: String = "1.0.1",
        versionCode: Long = 34194310,
        minSdk: Int = 24,
        abi: String = "arm64-v8a",
        url: String = "$base/jedflix-1.0.1-arm64-v8a.apk",
        hash: String = sha,
    ) = """{
      "schemaVersion": 1,
      "packageName": "$packageName",
      "versionCode": $versionCode,
      "versionName": "$versionName",
      "minSdk": $minSdk,
      "notes": "",
      "artifacts": { "$abi": { "url": "$url", "sha256": "$hash", "size": 96424500 } }
    }"""

    @Test
    fun picksTheArchitectureTheDeviceSupports() {
        val artifact = NewJedflixManifest.parse(manifest(), "v1.0.1", arrayOf("arm64-v8a", "armeabi-v7a"), 36)
        assertEquals("arm64-v8a", artifact.abi)
        assertEquals(34194310L, artifact.versionCode)
        assertEquals(sha, artifact.sha256)
    }

    @Test
    fun emulatorArchitectureIsAccepted() {
        val text = manifest(abi = "x86_64", url = "$base/jedflix-1.0.1-x86_64.apk")
        assertEquals("x86_64", NewJedflixManifest.parse(text, "v1.0.1", arrayOf("x86_64"), 36).abi)
    }

    @Test
    fun rejectsAnotherApp() {
        assertThrows(MigrationException::class.java) {
            NewJedflixManifest.parse(manifest(packageName = "com.jedflix.tv"), "v1.0.1", arrayOf("arm64-v8a"), 36)
        }
    }

    @Test
    fun rejectsTagThatDoesNotMatchVersion() {
        assertThrows(MigrationException::class.java) {
            NewJedflixManifest.parse(manifest(), "v1.0.2", arrayOf("arm64-v8a"), 36)
        }
    }

    @Test
    fun rejectsUpstreamOrOlderVersionCodes() {
        assertThrows(MigrationException::class.java) {
            NewJedflixManifest.parse(manifest(versionCode = 34194308), "v1.0.1", arrayOf("arm64-v8a"), 36)
        }
    }

    @Test
    fun rejectsAndroidVersionBelowMinimum() {
        assertThrows(MigrationException::class.java) {
            NewJedflixManifest.parse(manifest(minSdk = 37), "v1.0.1", arrayOf("arm64-v8a"), 36)
        }
    }

    @Test
    fun rejectsProcessorWithoutBuild() {
        assertThrows(MigrationException::class.java) {
            NewJedflixManifest.parse(manifest(), "v1.0.1", arrayOf("armeabi-v7a"), 36)
        }
    }

    @Test
    fun rejectsDownloadOutsideTheRelease() {
        assertThrows(MigrationException::class.java) {
            NewJedflixManifest.parse(
                manifest(url = "https://example.com/jedflix-1.0.1-arm64-v8a.apk"), "v1.0.1", arrayOf("arm64-v8a"), 36,
            )
        }
    }

    @Test
    fun rejectsMalformedChecksum() {
        assertThrows(MigrationException::class.java) {
            NewJedflixManifest.parse(manifest(hash = "not-a-hash"), "v1.0.1", arrayOf("arm64-v8a"), 36)
        }
    }

    @Test
    fun rejectsNonJson() {
        assertThrows(MigrationException::class.java) {
            NewJedflixManifest.parse("<html>", "v1.0.1", arrayOf("arm64-v8a"), 36)
        }
    }

    @Test
    fun latestReleaseYieldsManifestAddress() {
        val release = """{
          "tag_name": "v1.0.1", "draft": false, "prerelease": false,
          "assets": [
            {"name": "jedflix-1.0.1-x86_64.apk", "browser_download_url": "$base/jedflix-1.0.1-x86_64.apk"},
            {"name": "jedflix-update.json", "browser_download_url": "$base/jedflix-update.json"}
          ]
        }"""
        val (url, tag) = NewJedflixRelease.manifestSource(release)
        assertEquals("$base/jedflix-update.json", url)
        assertEquals("v1.0.1", tag)
    }

    @Test
    fun draftAndPrereleaseAreNotMigrationTargets() {
        val draft = """{"tag_name":"v1.0.1","draft":true,"prerelease":false,"assets":[]}"""
        val prerelease = """{"tag_name":"v1.0.1","draft":false,"prerelease":true,"assets":[]}"""
        assertThrows(MigrationException::class.java) { NewJedflixRelease.manifestSource(draft) }
        assertThrows(MigrationException::class.java) { NewJedflixRelease.manifestSource(prerelease) }
    }

    @Test
    fun releaseWithoutManifestIsRejected() {
        val release = """{"tag_name":"v1.0.1","draft":false,"prerelease":false,"assets":[]}"""
        assertThrows(MigrationException::class.java) { NewJedflixRelease.manifestSource(release) }
    }
}
