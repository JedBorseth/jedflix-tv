package com.jedflix.tv.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jedflix.tv.R
import com.jedflix.tv.data.trailer.TrailerPreviewPhase
import kotlin.math.roundToInt

@Composable
fun TrailerPreviewOverlay(
    ui: TrailerPreviewUi,
    player: ExoPlayer,
    originInWindow: Rect?,
    destinationInWindow: Rect?,
    onMorphFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    val fallbackDest = Rect(
        0f,
        0f,
        overlaySize.width.toFloat(),
        overlaySize.height * BILLBOARD_HEIGHT_FRACTION,
    )
    val dest = destinationInWindow?.translate(-overlayOrigin) ?: fallbackDest
    val liveOrigin = originInWindow?.translate(-overlayOrigin)
    var morphFrom by remember { mutableStateOf<Rect?>(null) }
    val progress = remember { Animatable(0f) }
    val onOpened = rememberUpdatedState(onMorphFinished)

    LaunchedEffect(ui.phase) {
        when (ui.phase) {
            TrailerPreviewPhase.Opening -> {
                morphFrom = liveOrigin ?: dest
                progress.snapTo(0f)
                progress.animateTo(
                    1f,
                    tween(TrailerPreviewViewModel.MORPH_MS, easing = FastOutSlowInEasing),
                )
                onOpened.value()
            }
            TrailerPreviewPhase.Playing -> progress.snapTo(1f)
            TrailerPreviewPhase.Hidden, TrailerPreviewPhase.Preparing -> {
                morphFrom = null
                progress.snapTo(0f)
            }
        }
    }

    val title = ui.title
    val morphing = ui.phase == TrailerPreviewPhase.Opening || ui.phase == TrailerPreviewPhase.Playing
    val showPlayer = title != null &&
        ui.phase != TrailerPreviewPhase.Hidden &&
        dest.width > 0f &&
        dest.height > 0f
    val stillRect = lerpRect(morphFrom ?: dest, dest, progress.value)
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusProperties { canFocus = false }
            .onGloballyPositioned { coords ->
                overlayOrigin = coords.positionInWindow()
                overlaySize = coords.size
            }
            .testTag("trailer-preview"),
    ) {
        if (showPlayer) {
            PreviewPlayerLayer(
                player = player,
                rect = dest,
                visible = ui.phase == TrailerPreviewPhase.Playing,
            )
        }
        if (morphing && title != null) {
            val still = title.posterUrl ?: title.backdropUrl
            Box(
                modifier = Modifier
                    .offset { IntOffset(stillRect.left.roundToInt(), stillRect.top.roundToInt()) }
                    .size(
                        width = with(density) { stillRect.width.toDp() },
                        height = with(density) { stillRect.height.toDp() },
                    )
                    .clipToBounds()
                    .graphicsLayer {
                        alpha = if (ui.phase == TrailerPreviewPhase.Playing) 0f else 1f
                    },
            ) {
                if (still != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(still)
                            .crossfade(false)
                            .build(),
                        contentDescription = stringResource(R.string.cd_poster, title.title),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewPlayerLayer(
    player: ExoPlayer,
    rect: Rect,
    visible: Boolean,
) {
    val density = LocalDensity.current
    AndroidView(
        modifier = Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(
                width = with(density) { rect.width.toDp() },
                height = with(density) { rect.height.toDp() },
            )
            .graphicsLayer { alpha = if (visible) 1f else 0f }
            .focusProperties { canFocus = false },
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.trailer_preview_player, null) as PlayerView).apply {
                this.player = player
                isFocusable = false
                isClickable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
        },
        onRelease = { view -> view.player = null },
    )
}

private fun lerpRect(from: Rect, to: Rect, t: Float): Rect = Rect(
    left = from.left + (to.left - from.left) * t,
    top = from.top + (to.top - from.top) * t,
    right = from.right + (to.right - from.right) * t,
    bottom = from.bottom + (to.bottom - from.bottom) * t,
)

private const val BILLBOARD_HEIGHT_FRACTION = 0.68f
