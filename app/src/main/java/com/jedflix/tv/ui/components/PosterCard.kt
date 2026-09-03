package com.jedflix.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jedflix.tv.R
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.ui.theme.JedflixRed
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc800

val PosterWidth = 128.dp
val PosterHeight = 192.dp
private val PosterShape = RoundedCornerShape(6.dp)

@Composable
fun PosterCard(
    title: MediaTitle,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    val placeholder = ColorPainter(Zinc800)
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(PosterWidth)
            .height(PosterHeight)
            .testTag("poster-card")
            .onFocusChanged { if (it.isFocused) onFocused?.invoke() },
        shape = ClickableSurfaceDefaults.shape(shape = PosterShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Zinc800,
            focusedContainerColor = Zinc800,
            pressedContainerColor = Zinc800,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f, pressedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, WarmWhite), shape = PosterShape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.White.copy(alpha = 0.35f), elevation = 14.dp),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(title.posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.cd_poster, title.title),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder,
                fallback = placeholder,
                modifier = Modifier.fillMaxSize(),
            )
            if (progress != null && progress > 0f) {
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
