package com.jedflix.tv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jedflix.tv.R
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.tmdbBrowseBackdropSize
import com.jedflix.tv.data.tmdb.tmdbImageUrlAtSize
import com.jedflix.tv.ui.focus.optionalFocusRequester
import com.jedflix.tv.ui.focus.railItemFocus
import com.jedflix.tv.ui.images.LocalBrowseQuality
import com.jedflix.tv.ui.images.fitPixels
import com.jedflix.tv.ui.theme.JedflixIcons
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc300
import com.jedflix.tv.ui.theme.Zinc950
import java.util.Locale

val BillboardInfoHeight = 280.dp

/** Full-bleed backdrop pinned behind the catalog, faded into the background on the left/bottom. */
@Composable
fun BillboardBackdrop(
    title: MediaTitle?,
    modifier: Modifier = Modifier,
    pan: Boolean = false,
    panActive: Boolean = true,
    panGeneration: Int = 0,
    onPanFinished: (() -> Unit)? = null,
    onBoundsInWindow: ((Rect) -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.68f)
            .clipToBounds()
            .then(
                if (onBoundsInWindow == null) {
                    Modifier
                } else {
                    Modifier.onGloballyPositioned { coords ->
                        val topLeft = coords.positionInWindow()
                        onBoundsInWindow(
                            Rect(
                                topLeft.x,
                                topLeft.y,
                                topLeft.x + coords.size.width,
                                topLeft.y + coords.size.height,
                            ),
                        )
                    }
                },
            ),
    ) {
        if (pan) {
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    fadeIn(tween(BillboardCycle.FADE_MS)) togetherWith
                        fadeOut(tween(BillboardCycle.FADE_MS))
                },
                contentKey = { it?.key },
                label = "billboard-backdrop",
                modifier = Modifier.fillMaxSize(),
            ) { current ->
                BillboardStill(
                    title = current,
                    pan = true,
                    panActive = panActive,
                    panGeneration = panGeneration,
                    onPanFinished = onPanFinished,
                )
            }
        } else {
            BillboardStill(title = title, pan = false)
        }
        // Left fade keeps the title readable; bottom fade lets rows scroll over the image.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Zinc950.copy(alpha = 0.72f),
                        0.28f to Zinc950.copy(alpha = 0.35f),
                        0.55f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Zinc950.copy(alpha = 0.2f),
                        1f to Zinc950,
                    ),
                ),
        )
    }
}

@Composable
private fun BillboardStill(
    title: MediaTitle?,
    pan: Boolean,
    panActive: Boolean = true,
    panGeneration: Int = 0,
    onPanFinished: (() -> Unit)? = null,
) {
    var layoutPx by remember { mutableStateOf(IntSize.Zero) }
    val panOffset = remember { Animatable(0f) }
    val onFinished by rememberUpdatedState(onPanFinished)
    var lastKey by remember { mutableStateOf(title?.key) }
    LaunchedEffect(title?.key, panGeneration, pan, panActive) {
        if (title?.key != lastKey) {
            panOffset.snapTo(0f)
            lastKey = title?.key
        }
        if (!pan || !panActive || title == null) return@LaunchedEffect
        val target = if (panOffset.value < 0.5f) 1f else 0f
        val distance = kotlin.math.abs(target - panOffset.value)
        val duration = (BillboardCycle.PAN_MS * distance).toInt()
        if (duration > 0) {
            panOffset.animateTo(target, tween(duration, easing = LinearEasing))
        }
        onFinished?.invoke()
    }
    val backdropUrl = tmdbImageUrlAtSize(
        title?.backdropUrl,
        tmdbBrowseBackdropSize(LocalBrowseQuality.current),
    )
    if (backdropUrl != null) {
        val scale = if (pan) BillboardCycle.PAN_SCALE else 1f
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(backdropUrl)
                .fitPixels(
                    ((if (layoutPx.width > 0) layoutPx.width else 1920) * scale).toInt(),
                    ((if (layoutPx.height > 0) layoutPx.height else 800) * scale).toInt(),
                )
                .crossfade(true)
                .build(),
            contentDescription = title?.let { stringResource(R.string.cd_backdrop, it.title) },
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { layoutPx = it }
                .then(
                    if (pan) {
                        Modifier.graphicsLayer {
                            scaleX = BillboardCycle.PAN_SCALE
                            scaleY = BillboardCycle.PAN_SCALE
                            translationX = BillboardCycle.translationX(size.width, panOffset.value)
                        }
                    } else {
                        Modifier
                    },
                ),
        )
    } else {
        Box(Modifier.fillMaxSize().background(Zinc950))
    }
}

/** Title, metadata, synopsis and the Play / My List buttons for the current hero. */
@Composable
fun BillboardInfo(
    title: MediaTitle,
    modifier: Modifier = Modifier,
    inMyList: Boolean = false,
    playFocusRequester: FocusRequester? = null,
    contentReturnFocus: FocusRequester? = null,
    returnToPlay: Boolean = false,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onPlay: () -> Unit = {},
    onMyList: () -> Unit = {},
    onPlayFocused: () -> Unit = {},
) {
    val playEnter = playFocusRequester
    Column(
        modifier = modifier
            .height(BillboardInfoHeight)
            .padding(start = ContentStartPadding, top = 32.dp)
            .fillMaxWidth(0.48f)
            .testTag("billboard")
            .then(
                if (playEnter != null) {
                    Modifier
                        .focusGroup()
                        .focusProperties { onEnter = { playEnter.requestFocus() } }
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.Bottom,
    ) {
        AnimatedContent(
            targetState = title,
            transitionSpec = {
                fadeIn(tween(BillboardCycle.FADE_MS)) togetherWith
                    fadeOut(tween(BillboardCycle.FADE_MS))
            },
            contentKey = { it.key },
            label = "billboard-info",
            contentAlignment = Alignment.BottomStart,
        ) { current ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.displayMedium,
                    color = WarmWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = current.metaLine(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Zinc300,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = current.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Zinc300,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BillboardButton(
                label = stringResource(R.string.action_play),
                icon = JedflixIcons.Play,
                containerColor = WarmWhite,
                contentColor = Zinc950,
                testTag = "billboard-play",
                modifier = Modifier
                    .optionalFocusRequester(playFocusRequester)
                    .optionalFocusRequester(if (returnToPlay) contentReturnFocus else null)
                    .onFocusChanged { if (it.isFocused) onPlayFocused() }
                    .railItemFocus(up = upFocusRequester, down = downFocusRequester),
                onClick = onPlay,
            )
            BillboardButton(
                label = stringResource(
                    if (inMyList) R.string.action_my_list_remove else R.string.action_my_list,
                ),
                icon = if (inMyList) JedflixIcons.Check else JedflixIcons.Add,
                containerColor = Color.White.copy(alpha = 0.22f),
                contentColor = WarmWhite,
                testTag = "billboard-my-list",
                modifier = Modifier.railItemFocus(
                    up = upFocusRequester,
                    down = downFocusRequester,
                ),
                onClick = onMyList,
            )
        }
    }
}

@Composable
private fun BillboardButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Button(
        onClick = onClick,
        modifier = modifier.testTag(testTag),
        colors = ButtonDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = WarmWhite,
            focusedContentColor = Zinc950,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = label, fontWeight = FontWeight.SemiBold)
    }
}

private fun MediaTitle.metaLine(): String {
    val parts = buildList {
        year?.let { add(it) }
        addAll(genres)
        rating?.let { add("★ " + String.format(Locale.US, "%.1f", it)) }
    }
    return parts.joinToString("  •  ")
}
