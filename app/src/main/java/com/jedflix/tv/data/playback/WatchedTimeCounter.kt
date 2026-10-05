package com.jedflix.tv.data.playback

/** Constant-space active-playing counter. Media position and seek jumps never enter it. */
class WatchedTimeCounter(
    private val nowMs: () -> Long,
    private var eligible: Boolean = true,
) {
    private var accumulatedMs = 0L
    private var startedAt: Long? = null

    fun setPlaying(playing: Boolean) {
        if (playing && eligible) {
            if (startedAt == null) startedAt = nowMs()
        } else {
            accumulatedMs = snapshotMs()
            startedAt = null
        }
    }

    fun snapshotMs(): Long = accumulatedMs +
        (startedAt?.let { (nowMs() - it).coerceAtLeast(0L) } ?: 0L)

    fun reset(eligible: Boolean) {
        this.eligible = eligible
        accumulatedMs = 0L
        startedAt = null
    }
}
