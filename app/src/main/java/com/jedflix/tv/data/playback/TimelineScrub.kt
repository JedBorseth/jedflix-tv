package com.jedflix.tv.data.playback

/** 10s timeline steps, matching the skip-back / skip-forward buttons. */
object TimelineScrub {
    const val STEP_MS = 10_000L

    fun step(positionMs: Long, durationMs: Long, deltaMs: Long): Long {
        val duration = durationMs.coerceAtLeast(0L)
        if (duration <= 0L) return 0L
        return (positionMs + deltaMs).coerceIn(0L, duration)
    }
}
