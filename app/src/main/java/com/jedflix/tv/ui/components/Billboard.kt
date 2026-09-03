package com.jedflix.tv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.jedflix.tv.ui.theme.JedflixIcons
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc300
import com.jedflix.tv.ui.theme.Zinc950
import java.util.Locale

val BillboardInfoHeight = 280.dp

/** Full-bleed backdrop pinned behind the catalog, faded into the background on the left/bottom. */
@Composable
fun BillboardBackdrop(title: MediaTitle?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.68f),
    ) {
        Crossfade(
            targetState = title?.backdropUrl,
            animationSpec = tween(600),
            label = "backdrop",
        ) { url ->
            if (url != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(false)
                        .build(),
                    contentDescription = title?.let { stringResource(R.string.cd_backdrop, it.title) },
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(Zinc950))
            }
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

/** Title, metadata, synopsis and the Play / My List buttons for the current hero. */
@Composable
fun BillboardInfo(
    title: MediaTitle,
    modifier: Modifier = Modifier,
    playFocusRequester: FocusRequester? = null,
    onPlay: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .height(BillboardInfoHeight)
            .padding(start = ContentStartPadding, top = 32.dp)
            .fillMaxWidth(0.48f)
            .testTag("billboard"),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = title.title,
            style = MaterialTheme.typography.displayMedium,
            color = WarmWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = title.metaLine(),
            style = MaterialTheme.typography.titleMedium,
            color = Zinc300,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = title.overview,
            style = MaterialTheme.typography.bodyMedium,
            color = Zinc300,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BillboardButton(
                label = stringResource(R.string.action_play),
                icon = JedflixIcons.Play,
                containerColor = WarmWhite,
                contentColor = Zinc950,
                testTag = "billboard-play",
                modifier = playFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
                onClick = onPlay,
            )
            BillboardButton(
                label = stringResource(R.string.action_my_list),
                icon = JedflixIcons.Add,
                containerColor = Color.White.copy(alpha = 0.22f),
                contentColor = WarmWhite,
                testTag = "billboard-my-list",
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
