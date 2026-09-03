package com.jedflix.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.jedflix.tv.ui.theme.Zinc800
import com.jedflix.tv.ui.theme.Zinc950

@Composable
fun UpdatePromptOverlay(
    state: AppUpdateState,
    onInstall: () -> Unit,
    onAllowInstalls: () -> Unit,
    onCancel: () -> Unit,
    onLater: () -> Unit,
) {
    val available = state.available ?: return
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(state.install) {
        runCatching { actionFocus.requestFocus() }
    }

    val busy = state.install is InstallProgress.Downloading ||
        state.install is InstallProgress.Installing
    BackHandler(enabled = true) {
        if (busy) onCancel() else onLater()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc950.copy(alpha = 0.82f))
            .testTag("update-prompt"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.settings_update_prompt_title),
                style = MaterialTheme.typography.headlineMedium,
                color = WarmWhite,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = promptBody(state, available.versionLabel),
                style = MaterialTheme.typography.bodyLarge,
                color = Zinc400,
            )
            val downloading = state.install as? InstallProgress.Downloading
            if (downloading != null) {
                Spacer(Modifier.height(20.dp))
                DownloadProgressBar(bytesRead = downloading.bytesRead, totalBytes = downloading.totalBytes)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state.install) {
                    InstallProgress.Idle -> {
                        if (available.apkUrl.isNotBlank()) {
                            Button(
                                onClick = onInstall,
                                modifier = Modifier
                                    .focusRequester(actionFocus)
                                    .testTag("update-prompt-install"),
                            ) {
                                Text(stringResource(R.string.settings_update_install))
                            }
                        }
                        PromptSecondary(
                            label = stringResource(R.string.settings_update_later),
                            modifier = if (available.apkUrl.isBlank()) Modifier.focusRequester(actionFocus) else Modifier,
                            onClick = onLater,
                        )
                    }
                    InstallProgress.NeedsUnknownSources -> {
                        Button(
                            onClick = onAllowInstalls,
                            modifier = Modifier
                                .focusRequester(actionFocus)
                                .testTag("update-prompt-allow"),
                        ) {
                            Text(stringResource(R.string.settings_update_allow_installs))
                        }
                        PromptSecondary(
                            label = stringResource(R.string.settings_update_later),
                            onClick = onLater,
                        )
                    }
                    is InstallProgress.Downloading -> {
                        PromptSecondary(
                            label = stringResource(R.string.action_cancel),
                            modifier = Modifier.focusRequester(actionFocus),
                            onClick = onCancel,
                        )
                    }
                    InstallProgress.Installing -> Unit
                    InstallProgress.Failed -> {
                        Button(
                            onClick = onInstall,
                            modifier = Modifier
                                .focusRequester(actionFocus)
                                .testTag("update-prompt-retry"),
                        ) {
                            Text(stringResource(R.string.settings_update_retry))
                        }
                        PromptSecondary(
                            label = stringResource(R.string.settings_update_later),
                            onClick = onLater,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DownloadProgressBar(bytesRead: Long, totalBytes: Long) {
    val fraction = if (totalBytes > 0L) {
        (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Zinc800, RoundedCornerShape(4.dp))
            .testTag("update-progress"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (totalBytes > 0L) fraction else 0.35f)
                .height(8.dp)
                .background(WarmWhite, RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
internal fun promptBody(state: AppUpdateState, versionLabel: String): String {
    return when (val install = state.install) {
        is InstallProgress.Downloading -> {
            if (install.totalBytes > 0L) {
                val percent = ((install.bytesRead * 100L) / install.totalBytes).toInt().coerceIn(0, 100)
                stringResource(R.string.settings_update_downloading, percent)
            } else {
                stringResource(R.string.settings_update_downloading_unknown)
            }
        }
        InstallProgress.Installing -> stringResource(R.string.settings_update_installing)
        InstallProgress.NeedsUnknownSources -> stringResource(R.string.settings_update_needs_permission)
        InstallProgress.Failed -> stringResource(R.string.settings_update_failed)
        InstallProgress.Idle -> stringResource(R.string.settings_update_prompt_body, versionLabel)
    }
}

@Composable
private fun PromptSecondary(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.testTag("update-prompt-later"),
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
