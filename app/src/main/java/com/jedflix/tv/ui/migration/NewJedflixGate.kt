package com.jedflix.tv.ui.migration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jedflix.tv.R
import com.jedflix.tv.data.migration.MigrationException
import com.jedflix.tv.data.migration.NewJedflixInstallState
import com.jedflix.tv.data.migration.NewJedflixMigrator
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc400
import com.jedflix.tv.ui.theme.Zinc950
import java.io.File
import kotlinx.coroutines.launch

/**
 * Shown in place of the old app until the new Jedflix is installed. Installation always
 * needs Android's confirmation; the gate resumes after the installer or permission screen.
 */
@Composable
fun NewJedflixGate(
    migrator: NewJedflixMigrator,
    onInstalled: () -> Unit,
    onClose: () -> Unit,
    onContinueOld: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf<File?>(null) }
    var waitingPermission by remember { mutableStateOf(false) }
    var installerOpened by remember { mutableStateOf(false) }
    var installedState by remember { mutableStateOf(migrator.installedState()) }
    val conflict = installedState == NewJedflixInstallState.CONFLICT
    val primaryFocus = remember { FocusRequester() }

    fun startInstall(file: File) {
        migrator.install(file)
        installerOpened = true
        status = "Confirm the install in Android's installer."
    }

    fun move() {
        if (busy) return
        failed = false
        installedState = migrator.installedState()
        if (installedState == NewJedflixInstallState.CONFLICT) {
            context.startActivity(migrator.conflictSettingsIntent())
            return
        }
        if (installedState == NewJedflixInstallState.JEDFLIX) {
            onInstalled()
            return
        }
        if (!migrator.canInstall()) {
            waitingPermission = true
            status = "Allow Jedflix to install apps, then return here."
            context.startActivity(migrator.permissionIntent())
            return
        }
        scope.launch {
            busy = true
            try {
                val file = verified ?: run {
                    status = "Finding the new Jedflix…"
                    val artifact = migrator.fetchArtifact()
                    status = context.getString(R.string.migration_downloading)
                    migrator.download(artifact) { read, total ->
                        progress = (read * 100 / total).toInt()
                    }.also { verified = it }
                }
                progress = null
                startInstall(file)
            } catch (error: MigrationException) {
                failed = true
                status = error.message.orEmpty()
            } catch (_: Exception) {
                failed = true
                status = "Could not reach the new Jedflix. Check your connection and try again."
            } finally {
                busy = false
                progress = null
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val previousState = installedState
        installedState = migrator.installedState()
        if (installedState == NewJedflixInstallState.JEDFLIX) {
            onInstalled()
            return@LifecycleEventEffect
        }
        if (previousState != installedState) {
            status = ""
            failed = false
        }
        if (installedState == NewJedflixInstallState.CONFLICT) {
            waitingPermission = false
            installerOpened = false
            return@LifecycleEventEffect
        }
        if (waitingPermission && migrator.canInstall()) {
            waitingPermission = false
            move()
        } else if (installerOpened) {
            installerOpened = false
            status = "The install was not completed. Try again when you are ready."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc950)
            .padding(64.dp)
            .testTag("new-jedflix-gate"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(if (conflict) R.string.migration_conflict_title else R.string.migration_title),
                style = MaterialTheme.typography.headlineMedium,
                color = WarmWhite,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(if (conflict) R.string.migration_conflict_body else R.string.migration_body),
                style = MaterialTheme.typography.bodyLarge,
                color = Zinc400,
            )
            if (status.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(text = status, style = MaterialTheme.typography.bodyLarge, color = WarmWhite)
            }
            progress?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "${stringResource(R.string.migration_downloading)} · $it%",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Zinc400,
                )
            }
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { move() },
                    enabled = !busy,
                    modifier = Modifier.focusRequester(primaryFocus).testTag("migration-move"),
                ) {
                    Text(stringResource(when {
                        conflict -> R.string.migration_conflict_settings
                        failed -> R.string.migration_retry
                        else -> R.string.migration_move
                    }))
                }
                if (failed || conflict) {
                    Button(onClick = onContinueOld, modifier = Modifier.testTag("migration-continue-old")) {
                        Text(stringResource(R.string.migration_continue_old))
                    }
                }
                Button(onClick = onClose, modifier = Modifier.testTag("migration-close")) {
                    Text(stringResource(R.string.migration_close))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { primaryFocus.requestFocus() }
    }
}
