package com.jedflix.tv.ui.components

import com.jedflix.tv.data.tmdb.MediaTitle

/** Ken Burns timing and the next Trending title after a billboard pan. */
object BillboardCycle {
    const val PAN_MS = 14_000
    const val FADE_MS = 900
    const val PAN_SCALE = 1.14f

    fun next(featured: List<MediaTitle>, currentKey: String): MediaTitle? {
        if (featured.size <= 1) return null
        val index = featured.indexOfFirst { it.key == currentKey }
        if (index < 0) return featured.first()
        return featured[(index + 1) % featured.size]
    }

    /** Horizontal shift for pan progress 0 (left) → 1 (right) after [PAN_SCALE]. */
    fun translationX(widthPx: Float, progress: Float, scale: Float = PAN_SCALE): Float {
        val extra = widthPx * (scale - 1f) / 2f
        return extra * (1f - 2f * progress)
    }
}
