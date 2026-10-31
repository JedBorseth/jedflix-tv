package com.jedflix.tv.data.migration

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
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
            val previousUpdate = runCatching {
                context.packageManager.getPackageInfo(NewJedflix.PACKAGE, 0).lastUpdateTime
            }.getOrDefault(0L)
            migrator.install(file)
            // Keep the source process alive while Android reads its private URI,
            // and prove the installer completed instead of merely opening it.
            withTimeout(120_000) {
                while (runCatching {
                    context.packageManager.getPackageInfo(NewJedflix.PACKAGE, 0).lastUpdateTime
                }.getOrDefault(0L) <= previousUpdate) delay(250)
            }
            assertEquals(NewJedflixInstallState.JEDFLIX, migrator.installedState())
        }
    }
}
