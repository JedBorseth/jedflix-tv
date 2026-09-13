package com.jedflix.tv.ui.components

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jedflix.tv.R
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.tmdbBrowsePosterSize
import com.jedflix.tv.data.tmdb.tmdbImageUrlAtSize
import com.jedflix.tv.data.trailer.TRAILER_PREVIEW_MORPH_MS
import com.jedflix.tv.ui.images.LocalBrowseQuality
import com.jedflix.tv.ui.images.fitDp
import com.jedflix.tv.ui.theme.JedflixIcons
import com.jedflix.tv.ui.theme.JedflixRed
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc800
import kotlinx.coroutines.delay

val PosterWidth = 128.dp
val PosterHeight = 192.dp
val PosterPreviewWidth = PosterHeight * 16f / 9f
private val PosterShape = RoundedCornerShape(6.dp)
private val LogoOnVideoFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f,
        ),
    ),
)

@Composable
fun PosterCard(
    title: MediaTitle,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    previewPlayer: ExoPlayer? = null,
    expanded: Boolean = false,
    playing: Boolean = false,
    ended: Boolean = false,
    logoUrl: String? = null,
    onPreviewOpened: () -> Unit = {},
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    val placeholder = ColorPainter(Zinc800)
    val density = LocalDensity.current
    val posterUrl = tmdbImageUrlAtSize(
        title.posterUrl,
        tmdbBrowsePosterSize(LocalBrowseQuality.current),
    )
    val width by animateDpAsState(
        targetValue = if (expanded) PosterPreviewWidth else PosterWidth,
        animationSpec = tween(TRAILER_PREVIEW_MORPH_MS, easing = FastOutSlowInEasing),
        label = "poster-preview-width",
    )
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(TRAILER_PREVIEW_MORPH_MS.toLong())
            onPreviewOpened()
        }
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .height(PosterHeight)
            .testTag("poster-card")
            .onFocusChanged {
                if (it.isFocused) onFocused?.invoke()
            },
        shape = ClickableSurfaceDefaults.shape(shape = PosterShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Zinc800,
            focusedContainerColor = Zinc800,
            pressedContainerColor = Zinc800,
        ),
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = if (expanded) 1f else 1.08f,
            pressedScale = if (expanded) 1f else 1.04f,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, WarmWhite), shape = PosterShape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.White.copy(alpha = 0.35f), elevation = 14.dp),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(PosterShape)) {
            if (previewPlayer != null) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (playing) 1f else 0f }
                        .focusProperties { canFocus = false },
                    factory = { ctx ->
                        (LayoutInflater.from(ctx).inflate(R.layout.trailer_preview_player, null) as PlayerView).apply {
                            player = previewPlayer
                            isFocusable = false
                            isClickable = false
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        }
                    },
                    update = { view ->
                        if (view.player !== previewPlayer) view.player = previewPlayer
                    },
                    onRelease = { view -> view.player = null },
                )
            }
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(posterUrl)
                    .fitDp(density, PosterPreviewWidth, PosterHeight)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.cd_poster, title.title),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder,
                fallback = placeholder,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (playing) 0f else 1f },
            )
            if (playing) {
                PreviewWatermark(logoUrl = logoUrl)
            }
            if (ended) {
                EndedPlayHint()
            }
            if (progress != null && progress > 0f && !playing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0.04f, 1f))
                            .height(4.dp)
                            .background(JedflixRed),
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PreviewWatermark(logoUrl: String?) {
    val watermark = Modifier
        .align(Alignment.BottomStart)
        .padding(start = 10.dp, bottom = 8.dp)
    if (logoUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(logoUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = watermark
                .height(32.dp)
                .widthIn(max = 148.dp),
        )
    } else {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = LogoOnVideoFilter,
            modifier = watermark
                .height(28.dp)
                .width(28.dp),
        )
    }
}

@Composable
private fun BoxScope.EndedPlayHint() {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = JedflixIcons.Play,
            contentDescription = null,
            tint = WarmWhite,
            modifier = Modifier
                .size(28.dp)
                .padding(start = 3.dp),
        )
    }
}
