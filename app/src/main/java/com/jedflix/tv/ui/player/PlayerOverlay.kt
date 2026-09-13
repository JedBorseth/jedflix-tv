package com.jedflix.tv.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jedflix.tv.R
import com.jedflix.tv.data.playback.PlaybackClock
import com.jedflix.tv.data.playback.SkipAction
import com.jedflix.tv.data.playback.SkipKind
import com.jedflix.tv.data.playback.TimelineScrub
import com.jedflix.tv.ui.theme.JedflixIcons
import com.jedflix.tv.ui.theme.JedflixRed
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc300
import com.jedflix.tv.ui.theme.Zinc400
import com.jedflix.tv.ui.theme.Zinc800
import com.jedflix.tv.ui.theme.Zinc900
import com.jedflix.tv.ui.theme.Zinc950

@Composable
internal fun PlayerChrome(
    state: PlayerUiState,
    seekHintSec: Int?,
    playFocus: FocusRequester,
    timelineFocus: FocusRequester,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onCaptions: () -> Unit,
    onAudio: () -> Unit,
    onSwitchStream: () -> Unit,
    onScrubBy: (Long) -> Unit,
    onSurfaceTap: () -> Unit,
) {
    val duration = state.durationMs.coerceAtLeast(0L)
    val position = state.positionMs.coerceIn(0L, duration.takeIf { it > 0L } ?: state.positionMs)
    val fraction = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val remaining = (duration - position).coerceAtLeast(0L)
    var timelineFocused by remember { mutableStateOf(false) }
    val toTimeline = Modifier.focusProperties { down = timelineFocus }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { onSurfaceTap() }
            },
    ) {
        TitleOverlay(title = state.item.title, subtitle = state.item.subtitle)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Zinc950.copy(alpha = 0.92f),
                    ),
                )
                .padding(start = CHROME_PAD_H, end = CHROME_PAD_H, top = 48.dp, bottom = CHROME_PAD_BOTTOM),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(
                    onClick = onPlayPause,
                    icon = if (state.isPlaying) JedflixIcons.Pause else JedflixIcons.Play,
                    label = stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play),
                    modifier = Modifier.focusRequester(playFocus).then(toTimeline).testTag("player-play"),
                    emphasized = true,
                )
                ControlButton(
                    onClick = onSeekBack,
                    icon = JedflixIcons.Replay10,
                    label = stringResource(R.string.player_rewind),
                    modifier = Modifier.then(toTimeline).testTag("player-rewind"),
                    showLabel = false,
                )
                ControlButton(
                    onClick = onSeekForward,
                    icon = JedflixIcons.Forward10,
                    label = stringResource(R.string.player_forward),
                    modifier = Modifier.then(toTimeline).testTag("player-forward"),
                    showLabel = false,
                )
                Spacer(Modifier.weight(1f))
                ControlButton(
                    onClick = onCaptions,
                    icon = JedflixIcons.ClosedCaption,
                    label = stringResource(R.string.player_captions),
                    modifier = Modifier.then(toTimeline).testTag("player-captions"),
                )
                ControlButton(
                    onClick = onAudio,
                    icon = JedflixIcons.Audiotrack,
                    label = stringResource(R.string.player_audio),
                    modifier = Modifier.then(toTimeline).testTag("player-audio"),
                )
                ControlButton(
                    onClick = onSwitchStream,
                    icon = JedflixIcons.SwapHoriz,
                    label = stringResource(R.string.player_switch_stream),
                    modifier = Modifier.then(toTimeline).testTag("player-switch-stream"),
                )
            }
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(22.dp)) {
                if (!timelineFocused) {
                    SeekHint(fraction = fraction, seekHintSec = seekHintSec)
                }
            }
            TimelineBar(
                fraction = fraction,
                focused = timelineFocused,
                timelineFocus = timelineFocus,
                playFocus = playFocus,
                onFocused = { timelineFocused = it },
                onScrubBy = onScrubBy,
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = PlaybackClock.formatMs(position),
                    style = MaterialTheme.typography.labelLarge,
                    color = Zinc300,
                )
                Text(
                    text = if (duration > 0L) "−${PlaybackClock.formatMs(remaining)}" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = Zinc300,
                )
            }
        }
        if (timelineFocused) {
            ScrubPreview(
                fraction = fraction,
                positionMs = position,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Bottom)
                    .padding(start = CHROME_PAD_H, end = CHROME_PAD_H, bottom = PREVIEW_ABOVE_BAR)
                    .zIndex(1f),
            )
        }
    }
}

@Composable
private fun SeekHint(
    fraction: Float,
    seekHintSec: Int?,
) {
    val seconds = seekHintSec ?: return
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val label = if (seconds >= 0) {
            stringResource(R.string.player_seek_forward, seconds)
        } else {
            stringResource(R.string.player_seek_back, -seconds)
        }
        val x = (maxWidth * fraction).coerceIn(8.dp, (maxWidth - 56.dp).coerceAtLeast(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = WarmWhite,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = x),
        )
    }
}

@Composable
private fun ScrubPreview(
    fraction: Float,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val previewWidth = PREVIEW_WIDTH
        val maxX = (maxWidth - previewWidth).coerceAtLeast(0.dp)
        val x = (maxWidth * fraction - previewWidth / 2).coerceIn(0.dp, maxX)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = x)
                .width(previewWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_HEIGHT)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .testTag("player-scrub-preview"),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = PlaybackClock.formatMs(positionMs),
                style = MaterialTheme.typography.labelLarge,
                color = WarmWhite,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TimelineBar(
    fraction: Float,
    focused: Boolean,
    timelineFocus: FocusRequester,
    playFocus: FocusRequester,
    onFocused: (Boolean) -> Unit,
    onScrubBy: (Long) -> Unit,
) {
    val label = stringResource(R.string.player_timeline)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .focusRequester(timelineFocus)
            .focusProperties { up = playFocus }
            .onFocusChanged { onFocused(it.isFocused) }
            .focusable()
            .onKeyEvent { event ->
                when (handleTimelineKey(event.key)) {
                    TimelineKeyAction.ScrubBack -> {
                        if (event.type == KeyEventType.KeyDown) onScrubBy(-TimelineScrub.STEP_MS)
                        true
                    }
                    TimelineKeyAction.ScrubForward -> {
                        if (event.type == KeyEventType.KeyDown) onScrubBy(TimelineScrub.STEP_MS)
                        true
                    }
                    TimelineKeyAction.Consume -> true
                    TimelineKeyAction.Ignore -> false
                }
            }
            .semantics { contentDescription = label }
            .testTag("player-progress"),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (focused) 8.dp else 6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(WarmWhite.copy(alpha = 0.22f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(JedflixRed),
            )
        }
        if (focused) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                val thumb = 14.dp
                val x = (maxWidth * fraction - thumb / 2).coerceIn(0.dp, (maxWidth - thumb).coerceAtLeast(0.dp))
                Box(
                    modifier = Modifier
                        .offset(x = x)
                        .size(thumb)
                        .clip(CircleShape)
                        .background(WarmWhite),
                )
            }
        }
    }
}

private val CHROME_PAD_H = 48.dp
private val CHROME_PAD_BOTTOM = 28.dp
private val TIME_LABEL_ROW = 22.dp
private val TIME_LABEL_GAP = 8.dp
private val TIMELINE_HEIGHT = 22.dp
private val PREVIEW_ABOVE_BAR = CHROME_PAD_BOTTOM + TIME_LABEL_ROW + TIME_LABEL_GAP + TIMELINE_HEIGHT
private val PREVIEW_WIDTH = 360.dp
private val PREVIEW_HEIGHT = PREVIEW_WIDTH * 9 / 16

@Composable
internal fun SkipOverlay(
    skip: SkipAction,
    skipFocus: FocusRequester,
    raised: Boolean,
    onSkip: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().testTag("player-skip-overlay")) {
        SkipControl(
            skip = skip,
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp, bottom = if (raised) 148.dp else 48.dp)
                .focusRequester(skipFocus)
                .testTag("player-skip"),
        )
    }
}

@Composable
private fun SkipControl(
    skip: SkipAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(
        when (skip.kind) {
            SkipKind.Intro -> R.string.player_skip_intro
            SkipKind.Recap -> R.string.player_skip_recap
            SkipKind.Outro -> R.string.player_skip_outro
        },
    )
    ControlButton(
        onClick = onClick,
        icon = JedflixIcons.SkipNext,
        label = label,
        modifier = modifier,
        emphasized = true,
    )
}

@Composable
internal fun TrackMenu(
    title: String,
    tracks: List<SelectableTrack>,
    selectedId: String?,
    includeOff: Boolean,
    emptyMessage: String,
    firstFocus: FocusRequester,
    onSelect: (String?) -> Unit,
) {
    val rows: List<Pair<String?, String>> = buildList {
        if (includeOff) add(null to stringResource(R.string.player_captions_off))
        addAll(tracks.map { it.id to it.label })
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .testTag("player-track-menu"),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(420.dp)
                .background(Zinc950.copy(alpha = 0.96f))
                .padding(horizontal = 28.dp, vertical = 36.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = WarmWhite,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))
            if (tracks.isEmpty() && !includeOff) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Zinc400,
                    modifier = Modifier.focusRequester(firstFocus).focusable(),
                )
            } else if (tracks.isEmpty() && includeOff) {
                TrackRow(
                    label = stringResource(R.string.player_captions_off),
                    selected = selectedId == null,
                    modifier = Modifier.focusRequester(firstFocus),
                    onClick = { onSelect(null) },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Zinc400,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(rows, key = { index, row -> row.first ?: "off-$index" }) { index, row ->
                        val selected = row.first == selectedId || (row.first == null && selectedId == null)
                        TrackRow(
                            label = row.second,
                            selected = selected,
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = { onSelect(row.first) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun UpNextOverlay(
    upNext: UpNextUi,
    playFocus: FocusRequester,
    onPlayNow: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Zinc950.copy(alpha = 0.88f),
                ),
            )
            .testTag("player-up-next"),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Row(
            modifier = Modifier
                .padding(48.dp)
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Zinc900.copy(alpha = 0.95f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(upNext.stillUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = upNext.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(180.dp)
                    .height(102.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Zinc800),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.player_up_next),
                    style = MaterialTheme.typography.labelLarge,
                    color = Zinc400,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.player_up_next_episode,
                        upNext.season,
                        upNext.episode,
                        upNext.title,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = WarmWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = upNext.secondsRemaining?.let { stringResource(R.string.player_playing_in, it) }
                        ?: stringResource(R.string.player_finding_next),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Zinc300,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onPlayNow,
                    modifier = Modifier.focusRequester(playFocus).testTag("player-play-now"),
                    colors = ButtonDefaults.colors(
                        containerColor = WarmWhite,
                        contentColor = Zinc950,
                        focusedContainerColor = WarmWhite,
                        focusedContentColor = Zinc950,
                    ),
                ) {
                    Text(stringResource(R.string.player_play_now), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun PlayerError(onBack: () -> Unit) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc950.copy(alpha = 0.9f))
            .testTag("player-error"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.player_error_title),
                style = MaterialTheme.typography.headlineMedium,
                color = WarmWhite,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.player_error),
                style = MaterialTheme.typography.bodyLarge,
                color = Zinc400,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.focusRequester(backFocus).testTag("player-back"),
                colors = ButtonDefaults.colors(
                    containerColor = WarmWhite,
                    contentColor = Zinc950,
                    focusedContainerColor = WarmWhite,
                    focusedContentColor = Zinc950,
                ),
            ) {
                Text(stringResource(R.string.action_back), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TitleOverlay(title: String, subtitle: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Zinc950.copy(alpha = 0.85f),
                    1f to Color.Transparent,
                ),
            )
            .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 56.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = WarmWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = Zinc300,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ControlButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    showLabel: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = if (emphasized) WarmWhite.copy(alpha = 0.2f) else WarmWhite.copy(alpha = 0.12f),
            contentColor = WarmWhite,
            focusedContainerColor = WarmWhite,
            focusedContentColor = Zinc950,
        ),
    ) {
        Icon(
            icon,
            contentDescription = if (showLabel) null else label,
            modifier = Modifier.size(22.dp),
        )
        if (showLabel) {
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TrackRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("player-track-row"),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Zinc800 else Zinc900,
            contentColor = WarmWhite,
            focusedContainerColor = WarmWhite,
            focusedContentColor = Zinc950,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, WarmWhite), shape = shape),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = JedflixIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
