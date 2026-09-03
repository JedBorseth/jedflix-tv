package com.jedflix.tv.data.playback

/** What the player needs; kept in memory because resolved URLs are too long for nav arguments. */
data class PlaybackItem(
    val streamUrl: String,
    val title: String,
    val subtitle: String?,
)

/** Single-slot hand-off between the stream picker and the player. */
class PlaybackSession {
    @Volatile
    var current: PlaybackItem? = null
        private set

    fun start(item: PlaybackItem) {
        current = item
    }

    fun clear() {
        current = null
    }
}
