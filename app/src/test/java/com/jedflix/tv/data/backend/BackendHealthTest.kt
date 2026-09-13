package com.jedflix.tv.data.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackendHealthTest {
    @Test
    fun healthUrlAppendsPath() {
        assertEquals("https://h/tv-api/health", BackendHealth.healthUrl("https://h/tv-api"))
    }

    @Test
    fun healthUrlToleratesTrailingSlashAndWhitespace() {
        assertEquals("https://h/tv-api/health", BackendHealth.healthUrl(" https://h/tv-api/ "))
    }

    @Test
    fun blankBaseHasNoUrl() {
        assertNull(BackendHealth.healthUrl("   "))
    }

    @Test
    fun okBodyIsOnlineWithVersion() {
        assertEquals(
            BackendHealthState.Online("abc123"),
            BackendHealth.parse(200, """{"status":"ok","version":"abc123"}"""),
        )
    }

    @Test
    fun missingVersionIsStillOnline() {
        assertEquals(BackendHealthState.Online("unknown"), BackendHealth.parse(200, """{"status":"ok"}"""))
    }

    @Test
    fun nonOkStatusTextIsOffline() {
        assertEquals(BackendHealthState.Offline, BackendHealth.parse(200, """{"status":"degraded"}"""))
    }

    @Test
    fun non2xxIsOffline() {
        assertEquals(BackendHealthState.Offline, BackendHealth.parse(502, """{"status":"ok"}"""))
    }

    @Test
    fun garbageBodyIsOffline() {
        assertEquals(BackendHealthState.Offline, BackendHealth.parse(200, "<html>Bad gateway</html>"))
        assertEquals(BackendHealthState.Offline, BackendHealth.parse(200, null))
    }
}
