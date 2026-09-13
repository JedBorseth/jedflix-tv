package com.jedflix.tv.ui.images

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import coil3.request.ImageRequest
import coil3.size.Precision
import com.jedflix.tv.data.settings.QualityProfile

val LocalBrowseQuality = staticCompositionLocalOf { QualityProfile.Max }

fun ImageRequest.Builder.fitPixels(widthPx: Int, heightPx: Int): ImageRequest.Builder =
    size(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1))
        .precision(Precision.INEXACT)

fun ImageRequest.Builder.fitDp(density: Density, width: Dp, height: Dp): ImageRequest.Builder {
    val w = with(density) { width.roundToPx() }
    val h = with(density) { height.roundToPx() }
    return fitPixels(w, h)
}
