package com.jedflix.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jedflix.tv.R
import com.jedflix.tv.data.live.LiveChannelGuide
import com.jedflix.tv.data.live.LiveChannels
import com.jedflix.tv.data.live.LiveEpgCell
import com.jedflix.tv.data.live.LiveProgram
import com.jedflix.tv.data.live.LiveSchedule
import com.jedflix.tv.data.live.LiveUnplayable
import com.jedflix.tv.data.playback.PlaybackItem
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.ui.theme.JedflixRed
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc400
import com.jedflix.tv.ui.theme.Zinc800
import com.jedflix.tv.ui.theme.Zinc900
import com.jedflix.tv.ui.theme.Zinc950
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

@Composable
fun LiveGuideOverlay(
    playing: PlaybackItem,
    tmdb: TmdbRepository,
    onSelectNow: (cell: LiveEpgCell) -> Unit,
    onClose: () -> Unit,
) {
    val viewModel: LiveGuideViewModel = viewModel(factory = LiveGuideViewModel.Factory(tmdb))
    val overviews by viewModel.overviews.collectAsStateWithLifecycle()
    val skipped by LiveUnplayable.keys.collectAsStateWithLifecycle()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var focused by remember { mutableStateOf<LiveEpgCell?>(null) }
    val windowStart = LiveSchedule.windowStartMs(nowMs)
    val windowEnd = windowStart + LiveSchedule.WINDOW_MS
    val guides = remember(nowMs, skipped) {
        LiveSchedule.epgWindow(LiveUnplayable.filter(LiveChannels.all), nowMs)
    }
    val listState = rememberLazyListState()
    val initialFocus = remember { FocusRequester() }
    var didFocus by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            nowMs = System.currentTimeMillis()
        }
    }
    LaunchedEffect(guides) {
        viewModel.ensure(guides.flatMap { guide -> guide.cells.map { it.program } })
    }

    val playingCell = remember(guides, playing) {
        guides.flatMap { it.cells }.firstOrNull { it.matches(playing) }
    }
    val focusCell = playingCell
        ?: guides.firstOrNull { it.channel.id == playing.liveChannelId }?.cells?.firstOrNull { it.contains(nowMs) }
        ?: guides.firstOrNull()?.cells?.firstOrNull()
    val playingIndex = guides.indexOfFirst { it.channel.id == playing.liveChannelId }.coerceAtLeast(0)

    LaunchedEffect(focusCell, playingIndex) {
        if (focusCell == null || didFocus) return@LaunchedEffect
        listState.scrollToItem(playingIndex)
        focused = focusCell
        didFocus = true
        runCatching { initialFocus.requestFocus() }
    }

    val ribbonCell = focused ?: focusCell
    val ribbonTitle = ribbonCell?.program?.displayTitle.orEmpty()
    val ribbonOverview = ribbonCell?.let { overviews[it.program.key]?.second }.orEmpty()
    val ribbonSlot = ribbonCell?.let { LiveSchedule.formatTimeslot(it.startEpochMs, it.endEpochMs) }.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("live-guide"),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.68f)
                .background(Zinc950.copy(alpha = 0.94f))
                .padding(start = 36.dp, end = 36.dp, top = 16.dp, bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.live_guide_title),
                style = MaterialTheme.typography.labelLarge,
                color = JedflixRed,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = listOf(ribbonTitle, ribbonSlot).filter { it.isNotBlank() }.joinToString("  •  "),
                style = MaterialTheme.typography.titleLarge,
                color = WarmWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (ribbonOverview.isNotBlank()) {
                Text(
                    text = ribbonOverview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Zinc400,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            TimeHeader(windowStart = windowStart)
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(guides, key = { it.channel.id }) { guide ->
                    ChannelRow(
                        guide = guide,
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        nowMs = nowMs,
                        playing = playing,
                        initialCell = focusCell,
                        initialFocus = initialFocus,
                        onFocused = { focused = it },
                        onClick = { cell ->
                            when (liveGuideNowAction(cell, playing, nowMs)) {
                                LiveGuideNowAction.Ignore -> Unit
                                LiveGuideNowAction.Close -> onClose()
                                LiveGuideNowAction.Retune -> onSelectNow(cell)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeHeader(windowStart: Long) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(CHANNEL_COL))
        val slots = (LiveSchedule.WINDOW_MS / LiveSchedule.SLOT_MS).toInt()
        repeat(slots) { index ->
            val start = windowStart + index * LiveSchedule.SLOT_MS
            Text(
                text = LiveSchedule.formatTimeslot(start, start + LiveSchedule.SLOT_MS).substringBefore(" –"),
                style = MaterialTheme.typography.labelLarge,
                color = Zinc400,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChannelRow(
    guide: LiveChannelGuide,
    windowStart: Long,
    windowEnd: Long,
    nowMs: Long,
    playing: PlaybackItem,
    initialCell: LiveEpgCell?,
    initialFocus: FocusRequester,
    onFocused: (LiveEpgCell) -> Unit,
    onClick: (LiveEpgCell) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = guide.channel.name,
            color = WarmWhite,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(CHANNEL_COL).padding(end = 12.dp),
        )
        Row(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            guide.cells.forEach { cell ->
                val visible = visibleMs(cell, windowStart, windowEnd)
                if (visible <= 0L) return@forEach
                val weight = (visible.toFloat() / LiveSchedule.SLOT_MS).coerceAtLeast(0.15f)
                val isPlaying = cell.matches(playing)
                val isNow = cell.contains(nowMs)
                Surface(
                    onClick = { onClick(cell) },
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .then(
                            if (cell === initialCell || (initialCell != null && cell.sameSlot(initialCell))) {
                                Modifier.focusRequester(initialFocus)
                            } else {
                                Modifier
                            },
                        )
                        .onFocusChanged { if (it.isFocused) onFocused(cell) }
                        .testTag("live-guide-cell"),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = when {
                            isPlaying -> JedflixRed.copy(alpha = 0.55f)
                            isNow -> Zinc800
                            else -> Zinc900
                        },
                        contentColor = WarmWhite,
                        focusedContainerColor = WarmWhite,
                        focusedContentColor = Zinc950,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp)),
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = cell.program.displayTitle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

private fun visibleMs(cell: LiveEpgCell, windowStart: Long, windowEnd: Long): Long {
    val start = max(cell.startEpochMs, windowStart)
    val end = min(cell.endEpochMs, windowEnd)
    return (end - start).coerceAtLeast(0L)
}

private fun LiveEpgCell.matches(item: PlaybackItem): Boolean =
    program.tmdbId == item.tmdbId &&
        program.mediaType == item.mediaType &&
        program.season == item.season &&
        program.episode == item.episode

private fun LiveEpgCell.sameSlot(other: LiveEpgCell): Boolean =
    channelId == other.channelId && startEpochMs == other.startEpochMs && program.sameAs(other.program)

internal enum class LiveGuideNowAction { Ignore, Close, Retune }

/** Now on the playing title closes the guide; Now on a different title retunes, even on the same Channel. */
internal fun liveGuideNowAction(cell: LiveEpgCell, playing: PlaybackItem, nowMs: Long): LiveGuideNowAction = when {
    !cell.contains(nowMs) -> LiveGuideNowAction.Ignore
    cell.matches(playing) -> LiveGuideNowAction.Close
    else -> LiveGuideNowAction.Retune
}

private val CHANNEL_COL = 196.dp

class LiveGuideViewModel(
    private val tmdb: TmdbRepository,
) : ViewModel() {
    private val _overviews = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())
    val overviews: StateFlow<Map<String, Pair<String, String>>> = _overviews.asStateFlow()

    fun ensure(programs: List<LiveProgram>) {
        val missing = programs.distinctBy { it.key }.filter { it.key !in _overviews.value }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val next = _overviews.value.toMutableMap()
            for (program in missing) {
                val details = runCatching { tmdb.loadDetails(program.mediaType, program.tmdbId) }.getOrNull()
                if (details != null) {
                    next[program.key] = details.title.title to details.title.overview
                }
            }
            _overviews.value = next
        }
    }

    class Factory(private val tmdb: TmdbRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LiveGuideViewModel(tmdb) as T
    }
}
