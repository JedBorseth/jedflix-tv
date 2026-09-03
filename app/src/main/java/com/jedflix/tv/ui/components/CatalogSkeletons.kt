package com.jedflix.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jedflix.tv.ui.theme.Zinc800
import com.jedflix.tv.ui.theme.Zinc900

/** Shimmering stand-in for the billboard plus the first few poster rows. */
@Composable
fun CatalogSkeletons(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    // Hold focus while loading so it doesn't fall into the nav rail and pop the drawer open.
    val focusSink = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusSink.requestFocus() } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("catalog-skeleton")
            .focusRequester(focusSink)
            .focusable()
            .padding(start = ContentStartPadding),
    ) {
        Column(
            modifier = Modifier.height(BillboardInfoHeight).padding(top = 32.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            SkeletonBlock(brush, width = 380.dp, height = 44.dp)
            Spacer(Modifier.height(14.dp))
            SkeletonBlock(brush, width = 260.dp, height = 16.dp)
            Spacer(Modifier.height(14.dp))
            SkeletonBlock(brush, width = 440.dp, height = 14.dp)
            Spacer(Modifier.height(8.dp))
            SkeletonBlock(brush, width = 400.dp, height = 14.dp)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(brush, width = 110.dp, height = 40.dp, radius = 20.dp)
                SkeletonBlock(brush, width = 130.dp, height = 40.dp, radius = 20.dp)
            }
        }
        Spacer(Modifier.height(16.dp))
        repeat(3) {
            SkeletonRow(brush)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun SkeletonRow(brush: Brush) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonBlock(brush, width = 170.dp, height = 18.dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            repeat(8) {
                SkeletonBlock(brush, width = PosterWidth, height = PosterHeight, radius = 6.dp)
            }
        }
    }
}

@Composable
fun SkeletonBlock(brush: Brush, width: Dp, height: Dp, radius: Dp = 4.dp) {
    Box(
        Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(radius))
            .background(brush),
    )
}

@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-offset",
    )
    val travel = 2400f
    val x = offset * travel - 600f
    return Brush.linearGradient(
        colors = listOf(Zinc900, Zinc800, Zinc900),
        start = Offset(x, 0f),
        end = Offset(x + 600f, 300f),
    )
}
