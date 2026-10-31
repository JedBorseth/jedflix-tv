package com.jedflix.tv.data.migration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run on a disposable emulator with identityFixture=stock, absent, foreign, or jedflix. */
@RunWith(AndroidJUnit4::class)
class NewJedflixIdentityTest {
    @Test
    fun onlyTheSignedJedflixInstallCanBeOpened() {
        val fixture = InstrumentationRegistry.getArguments().getString("identityFixture")
        assumeTrue("Requires an explicit installed-app fixture", fixture in setOf("stock", "absent", "foreign", "jedflix"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val present = context.packageManager.getInstalledPackages(0).any { it.packageName == NewJedflix.PACKAGE }
        assertEquals("Fixture package presence", fixture in setOf("foreign", "jedflix"), present)
        if (fixture == "stock") {
            assertNotNull(context.packageManager.getPackageInfo("com.stremio.one", 0))
        }
        val migrator = NewJedflixMigrator(context)
        val expectedState = when (fixture) {
            "absent", "stock" -> NewJedflixInstallState.ABSENT
            "jedflix" -> NewJedflixInstallState.JEDFLIX
            else -> NewJedflixInstallState.CONFLICT
        }
        assertEquals(expectedState, migrator.installedState())
        assertEquals("Only the pinned Jedflix signature is accepted", fixture == "jedflix", migrator.isInstalled())
        if (fixture == "jedflix") {
            assertNotNull(migrator.launchIntent())
        } else {
            assertNull("Stock Stremio must never be launched by migration", migrator.launchIntent())
        }
    }
}
