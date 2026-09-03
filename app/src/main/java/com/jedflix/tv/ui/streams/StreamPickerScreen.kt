package com.jedflix.tv.ui.streams

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jedflix.tv.R
import com.jedflix.tv.data.comet.CometClient
import com.jedflix.tv.data.comet.StreamOption
import com.jedflix.tv.data.playback.PlaybackSession
import com.jedflix.tv.data.settings.SettingsStore
import com.jedflix.tv.data.tmdb.MediaType
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.ui.components.BillboardBackdrop
import com.jedflix.tv.ui.components.ContentStartPadding
import com.jedflix.tv.ui.components.SkeletonBlock
import com.jedflix.tv.ui.components.rememberShimmerBrush
import com.jedflix.tv.ui.theme.JedflixIcons
import com.jedflix.tv.ui.theme.JedflixRed
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc300
import com.jedflix.tv.ui.theme.Zinc400
import com.jedflix.tv.ui.theme.Zinc500
import com.jedflix.tv.ui.theme.Zinc800
import com.jedflix.tv.ui.theme.Zinc900
import com.jedflix.tv.ui.theme.Zinc950

@Composable
fun StreamPickerScreen(
    mediaType: MediaType,
    mediaId: Int,
    season: Int?,
    episode: Int?,
    repository: TmdbRepository,
    cometClient: CometClient,
    settingsStore: SettingsStore,
    playbackSession: PlaybackSession,
    onPlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: StreamPickerViewModel = viewModel(
        key = "streams-${mediaType.apiValue}-$mediaId-$season-$episode",
        factory = StreamPickerViewModel.Factory(
            mediaType, mediaId, season, episode, repository, cometClient, settingsStore, playbackSession,
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.play.collect { onPlay() }
    }

    val resolving = (state as? StreamPickerUiState.Ready)?.resolving != null
    // Back while resolving cancels the resolve rather than leaving the screen.
    BackHandler(enabled = resolving) { viewModel.cancelResolve() }

    Box(modifier = Modifier.fillMaxSize().background(Zinc950).testTag("streams")) {
        BillboardBackdrop(title = state.target()?.title)
        Box(Modifier.fillMaxSize().background(Zinc950.copy(alpha = 0.55f)))

        Crossfade(targetState = state, animationSpec = tween(300), label = "streams-state") { current ->
            when (current) {
                is StreamPickerUiState.Loading -> PickerLoading(current.target)
                is StreamPickerUiState.Error -> PickerError(
                    state = current,
                    onRetry = viewModel::retry,
                    onOpenSettings = onOpenSettings,
                    onBack = onBack,
                )
                is StreamPickerUiState.Ready -> PickerReady(state = current, onSelect = viewModel::select)
            }
        }

        if (resolving) {
            ResolvingOverlay(onCancel = viewModel::cancelResolve)
        }
    }
}

private fun StreamPickerUiState.target(): StreamTarget? = when (this) {
    is StreamPickerUiState.Loading -> target
    is StreamPickerUiState.Error -> target
    is StreamPickerUiState.Ready -> target
}

@Composable
private fun PickerHeader(target: StreamTarget?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.streams_heading).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = JedflixRed,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = target?.title?.title ?: "",
            style = MaterialTheme.typography.displayMedium,
            color = WarmWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        target?.subtitle?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = Zinc300,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.streams_source),
            style = MaterialTheme.typography.bodyMedium,
            color = Zinc500,
        )
    }
}

@Composable
private fun PickerReady(state: StreamPickerUiState.Ready, onSelect: (StreamOption) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(state.target) { runCatching { firstFocus.requestFocus() } }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = ContentStartPadding, end = 48.dp, top = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        Column(modifier = Modifier.width(380.dp).fillMaxHeight()) {
            PickerHeader(target = state.target)
            state.resolveError?.let { message ->
                Spacer(Modifier.height(20.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JedflixRed,
                    modifier = Modifier.testTag("streams-resolve-error"),
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight().testTag("streams-list"),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(state.options, key = { _, option -> option.id }) { index, option ->
                StreamRow(
                    option = option,
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun StreamRow(option: StreamOption, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("stream-row"),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Zinc900.copy(alpha = 0.85f),
            contentColor = WarmWhite,
            focusedContainerColor = Zinc800,
            focusedContentColor = WarmWhite,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, WarmWhite), shape = shape),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(84.dp)
                    .background(WarmWhite.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.resolution,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WarmWhite,
                    maxLines = 1,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.filename,
                    style = MaterialTheme.typography.bodyLarge,
                    color = WarmWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val info = option.details.joinToString("   ")
                if (info.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = info,
                        style = MaterialTheme.typography.bodySmall,
                        color = Zinc400,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                option.sizeLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Zinc300,
                        maxLines = 1,
                    )
                }
                if (option.cached) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.streams_cached),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4ADE80),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerLoading(target: StreamTarget?) {
    val brush = rememberShimmerBrush()
    val sink = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { sink.requestFocus() } }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .testTag("streams-loading")
            .focusRequester(sink)
            .focusable()
            .padding(start = ContentStartPadding, end = 48.dp, top = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        Column(modifier = Modifier.width(380.dp)) {
            if (target != null) {
                PickerHeader(target = target)
            } else {
                SkeletonBlock(brush, width = 140.dp, height = 14.dp)
                Spacer(Modifier.height(14.dp))
                SkeletonBlock(brush, width = 320.dp, height = 40.dp)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.streams_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = Zinc400,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(6) {
                SkeletonBlock(brush, width = 640.dp, height = 68.dp, radius = 8.dp)
            }
        }
    }
}

@Composable
private fun PickerError(
    state: StreamPickerUiState.Error,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(state.kind) { runCatching { primaryFocus.requestFocus() } }

    val (titleRes, bodyRes) = when (state.kind) {
        StreamErrorKind.MISSING_KEY -> R.string.streams_error_missing_key_title to R.string.streams_error_missing_key
        StreamErrorKind.NO_IMDB -> R.string.streams_error_no_imdb_title to R.string.streams_error_no_imdb
        StreamErrorKind.NO_STREAMS -> R.string.streams_error_empty_title to R.string.streams_error_empty
        StreamErrorKind.DEBRID -> R.string.streams_error_debrid_title to R.string.streams_error_debrid
        StreamErrorKind.NETWORK -> R.string.streams_error_network_title to R.string.error_network
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("streams-error")
            .padding(start = ContentStartPadding, end = 48.dp, top = 40.dp),
    ) {
        PickerHeader(target = state.target, modifier = Modifier.width(380.dp))
        Spacer(Modifier.height(40.dp))
        Column(modifier = Modifier.widthIn(max = 620.dp)) {
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
            if (state.kind == StreamErrorKind.DEBRID && !state.detail.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Zinc500,
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state.kind) {
                    StreamErrorKind.MISSING_KEY -> {
                        PrimaryButton(
                            label = stringResource(R.string.action_open_settings),
                            icon = JedflixIcons.Settings,
                            modifier = Modifier.focusRequester(primaryFocus).testTag("streams-open-settings"),
                            onClick = onOpenSettings,
                        )
                        SecondaryButton(label = stringResource(R.string.action_back), onClick = onBack)
                    }
                    StreamErrorKind.NO_IMDB -> {
                        PrimaryButton(
                            label = stringResource(R.string.action_back),
                            icon = null,
                            modifier = Modifier.focusRequester(primaryFocus),
                            onClick = onBack,
                        )
                    }
                    else -> {
                        PrimaryButton(
                            label = stringResource(R.string.action_retry),
                            icon = JedflixIcons.Refresh,
                            modifier = Modifier.focusRequester(primaryFocus).testTag("retry"),
                            onClick = onRetry,
                        )
                        SecondaryButton(label = stringResource(R.string.action_back), onClick = onBack)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResolvingOverlay(onCancel: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc950.copy(alpha = 0.82f))
            .testTag("streams-resolving"),
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
            SecondaryButton(
                label = stringResource(R.string.action_cancel),
                modifier = Modifier.focusRequester(cancelFocus),
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = WarmWhite,
            contentColor = Zinc950,
            focusedContainerColor = WarmWhite,
            focusedContentColor = Zinc950,
        ),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
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
