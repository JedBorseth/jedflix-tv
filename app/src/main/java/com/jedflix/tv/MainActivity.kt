package com.jedflix.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.jedflix.tv.data.migration.NewJedflixMigrator
import com.jedflix.tv.ui.migration.NewJedflixGate
import com.jedflix.tv.ui.navigation.JedflixNavHost
import com.jedflix.tv.ui.theme.JedflixTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val migrator = NewJedflixMigrator(this)
        // Once the new app is installed, this launcher entry only forwards to it.
        if (migrator.isInstalled()) {
            openNewJedflix(migrator)
            return
        }
        val app = application as JedflixTvApp
        setContent {
            JedflixTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        // Exposes Compose testTags as resource-ids so Maestro can target them.
                        .semantics { testTagsAsResourceId = true },
                    colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                ) {
                    var keepOld by rememberSaveable { mutableStateOf(false) }
                    if (keepOld) {
                        JedflixNavHost(
                            repository = app.tmdbRepository,
                            settingsStore = app.settingsStore,
                            cometClient = app.cometClient,
                            playbackSession = app.playbackSession,
                            library = app.userLibrary,
                            appUpdateManager = app.appUpdateManager,
                            backendHealth = app.backendHealth,
                        )
                    } else {
                        NewJedflixGate(
                            migrator = migrator,
                            onInstalled = { openNewJedflix(migrator) },
                            onClose = { finish() },
                            onContinueOld = { keepOld = true },
                        )
                    }
                }
            }
        }
    }

    private fun openNewJedflix(migrator: NewJedflixMigrator) {
        migrator.launchIntent()?.let { startActivity(it) }
        finish()
    }
}
