package com.jedflix.tv.data.migration

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit live-release check; does not clear settings or remove installed apps. */
@RunWith(AndroidJUnit4::class)
class NewJedflixDownloadTest {
    @Test
    fun downloadsTheSignedPublishedBuildForThisDevice() = runBlocking {
        val action = InstrumentationRegistry.getArguments().getString("migrationDownload")
        assumeTrue("Requires an explicit live download request", action in setOf("verify", "install"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val migrator = NewJedflixMigrator(context)
        val artifact = migrator.fetchArtifact()
        assertTrue("Published build matches Android's ABI list", artifact.abi in Build.SUPPORTED_ABIS)
        val file = migrator.download(artifact) { _, _ -> }
        assertEquals("Verified complete release download", artifact.size, file.length())
        println("Verified Jedflix ${artifact.versionName} for ${artifact.abi}")
        if (action == "install") {
            assertTrue("Allow this legacy app to install apps in Android settings first", migrator.canInstall())
            migrator.install(file)
        }
    }
}
