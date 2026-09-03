package com.jedflix.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jedflix.tv.R
import com.jedflix.tv.data.update.AppUpdateState
import com.jedflix.tv.data.update.InstallProgress
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc400
import com.jedflix.tv.ui.theme.Zinc950

@Composable
fun AboutUpdateSection(
    state: AppUpdateState,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
    onAllowInstalls: () -> Unit,
    onCancelInstall: () -> Unit,
) {
    val available = state.available

    Column(modifier = Modifier.testTag("settings-about")) {
        Text(
            text = stringResource(R.string.settings_about_heading),
            style = MaterialTheme.typography.headlineMedium,
            color = WarmWhite,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = aboutStatus(state),
            style = MaterialTheme.typography.bodyLarge,
            color = Zinc400,
            modifier = Modifier.testTag("settings-update-status"),
        )
        val notes = available?.notes
        if (!notes.isNullOrBlank() && state.install is InstallProgress.Idle) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                color = Zinc400,
            )
        }
        val downloading = state.install as? InstallProgress.Downloading
        if (downloading != null) {
            Spacer(Modifier.height(16.dp))
            DownloadProgressBar(bytesRead = downloading.bytesRead, totalBytes = downloading.totalBytes)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val busy = state.checking ||
                state.install is InstallProgress.Downloading ||
                state.install is InstallProgress.Installing
            when (state.install) {
                InstallProgress.NeedsUnknownSources -> {
                    Button(
                        onClick = onAllowInstalls,
                        modifier = Modifier.testTag("settings-allow-installs"),
                    ) {
                        Text(stringResource(R.string.settings_update_allow_installs))
                    }
                }
                is InstallProgress.Downloading -> {
                    SettingsSecondaryButton(
                        label = stringResource(R.string.action_cancel),
                        modifier = Modifier.testTag("settings-update-cancel"),
                        onClick = onCancelInstall,
                    )
                }
                InstallProgress.Installing -> Unit
                InstallProgress.Failed, InstallProgress.Idle -> {
                    if (available != null && available.apkUrl.isNotBlank()) {
                        Button(
                            onClick = onInstall,
                            modifier = Modifier.testTag("settings-update-install"),
                        ) {
                            Text(
                                stringResource(
                                    if (state.install is InstallProgress.Failed) {
                                        R.string.settings_update_retry
                                    } else {
                                        R.string.settings_update_install
                                    },
                                ),
                            )
                        }
                    }
                }
            }
            if (state.install !is InstallProgress.Downloading && state.install !is InstallProgress.Installing) {
                SettingsSecondaryButton(
                    label = stringResource(R.string.settings_update_check),
                    modifier = Modifier.testTag("settings-check-update"),
                    onClick = onCheck,
                    enabled = !busy,
                )
            }
        }
    }
}

@Composable
private fun aboutStatus(state: AppUpdateState): String {
    val available = state.available
    return when {
        state.install is InstallProgress.Downloading ||
            state.install is InstallProgress.Installing ||
            state.install is InstallProgress.NeedsUnknownSources ||
            state.install is InstallProgress.Failed -> promptBody(state, available?.versionLabel.orEmpty())
        state.checking -> stringResource(R.string.settings_update_checking)
        available != null && available.apkUrl.isBlank() -> stringResource(R.string.settings_update_no_apk)
        available != null -> stringResource(R.string.settings_update_available, available.versionLabel)
        state.checkFailed -> stringResource(R.string.settings_update_check_failed)
        else -> stringResource(R.string.settings_update_current)
    }
}

@Composable
private fun SettingsSecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.22f),
            contentColor = WarmWhite,
            focusedContainerColor = WarmWhite,
            focusedContentColor = Zinc950,
        ),
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold)
    }
}
