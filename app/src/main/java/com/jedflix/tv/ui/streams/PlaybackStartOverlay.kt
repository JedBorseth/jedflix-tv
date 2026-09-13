package com.jedflix.tv.ui.streams

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jedflix.tv.R
import com.jedflix.tv.ui.components.ContentStartPadding
import com.jedflix.tv.ui.components.SkeletonBlock
import com.jedflix.tv.ui.components.rememberShimmerBrush
import com.jedflix.tv.ui.theme.JedflixIcons
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc400
import com.jedflix.tv.ui.theme.Zinc500
import com.jedflix.tv.ui.theme.Zinc950

@Composable
fun PlaybackStartingOverlay(onCancel: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc950.copy(alpha = 0.82f))
            .focusable()
            .testTag("playback-starting"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val brush = rememberShimmerBrush()
            SkeletonBlock(brush, width = 260.dp, height = 6.dp, radius = 3.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.streams_resolving),
                style = MaterialTheme.typography.titleLarge,
                color = WarmWhite,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onCancel,
                modifier = Modifier.focusRequester(cancelFocus),
                colors = ButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.22f),
                    contentColor = WarmWhite,
                    focusedContainerColor = WarmWhite,
                    focusedContentColor = Zinc950,
                ),
            ) {
                Text(text = stringResource(R.string.action_cancel), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun PlaybackStartErrorOverlay(
    kind: StreamErrorKind,
    detail: String?,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(kind) { runCatching { primaryFocus.requestFocus() } }
    val (titleRes, bodyRes) = when (kind) {
        StreamErrorKind.MISSING_KEY -> R.string.streams_error_missing_key_title to R.string.streams_error_missing_key
        StreamErrorKind.NO_IMDB -> R.string.streams_error_no_imdb_title to R.string.streams_error_no_imdb
        StreamErrorKind.NO_STREAMS -> R.string.streams_error_empty_title to R.string.streams_error_empty
        StreamErrorKind.DEBRID -> R.string.streams_error_debrid_title to R.string.streams_error_debrid
        StreamErrorKind.NETWORK -> R.string.streams_error_network_title to R.string.error_network
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc950.copy(alpha = 0.92f))
            .testTag("playback-start-error"),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = ContentStartPadding, end = 48.dp)
                .widthIn(max = 620.dp),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineMedium,
                color = WarmWhite,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = Zinc400,
            )
            if (kind == StreamErrorKind.DEBRID && !detail.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Zinc500,
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (kind) {
                    StreamErrorKind.MISSING_KEY -> {
                        Button(
                            onClick = onOpenSettings,
                            modifier = Modifier.focusRequester(primaryFocus).testTag("streams-open-settings"),
                            colors = filledButtonColors(),
                        ) {
                            Icon(JedflixIcons.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_open_settings), fontWeight = FontWeight.SemiBold)
                        }
                        Button(onClick = onDismiss, colors = secondaryButtonColors()) {
                            Text(stringResource(R.string.action_back), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    StreamErrorKind.NO_IMDB -> {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.focusRequester(primaryFocus),
                            colors = filledButtonColors(),
                        ) {
                            Text(stringResource(R.string.action_back), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    else -> {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.focusRequester(primaryFocus).testTag("retry"),
                            colors = filledButtonColors(),
                        ) {
                            Icon(JedflixIcons.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_retry), fontWeight = FontWeight.SemiBold)
                        }
                        Button(onClick = onDismiss, colors = secondaryButtonColors()) {
                            Text(stringResource(R.string.action_back), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun filledButtonColors() = ButtonDefaults.colors(
    containerColor = WarmWhite,
    contentColor = Zinc950,
    focusedContainerColor = WarmWhite,
    focusedContentColor = Zinc950,
)

@Composable
private fun secondaryButtonColors() = ButtonDefaults.colors(
    containerColor = Color.White.copy(alpha = 0.22f),
    contentColor = WarmWhite,
    focusedContainerColor = WarmWhite,
    focusedContentColor = Zinc950,
)
