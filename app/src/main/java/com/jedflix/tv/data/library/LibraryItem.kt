package com.jedflix.tv.data.library

import com.jedflix.tv.data.tmdb.MediaTitle

/** A title in the local library, with optional in-progress playback. */
data class LibraryItem(
    val title: MediaTitle,
    val season: Int?,
    val episode: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
) {
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val isInProgress: Boolean
        get() = isPlaybackInProgress(positionMs, durationMs)
}

internal fun isPlaybackInProgress(positionMs: Long, durationMs: Long): Boolean {
    if (durationMs <= 0L) return false
    if (positionMs < (durationMs * CONTINUE_MIN_FRACTION).toLong()) return false
    val remaining = durationMs - positionMs
    val progress = positionMs.toDouble() / durationMs.toDouble()
    return progress <= CONTINUE_MAX_FRACTION || remaining > CONTINUE_MIN_REMAINING_MS
}

internal const val CONTINUE_MIN_FRACTION = 0.05
internal const val CONTINUE_MAX_FRACTION = 0.90
internal const val CONTINUE_MIN_REMAINING_MS = 120_000L
