package com.jedflix.tv.data.live

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Session-only titles that had no playable stream; the Guide and tuner skip them. */
object LiveUnplayable {
    private val _keys = MutableStateFlow<Set<String>>(emptySet())
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    fun mark(program: LiveProgram) {
        _keys.update { it + program.identity }
    }

    fun markCurrentNow(channelId: String, nowMs: Long = System.currentTimeMillis()) {
        val channel = LiveChannels.byId(channelId) ?: return
        val playable = filter(channel) ?: channel
        mark(LiveSchedule.tune(playable, nowMs).program)
    }

    fun contains(program: LiveProgram): Boolean = program.identity in _keys.value

    fun filter(channel: LiveChannel): LiveChannel? {
        val lineup = channel.lineup.filter { it.identity !in _keys.value }
        if (lineup.isEmpty()) return null
        if (lineup.size == channel.lineup.size) return channel
        return channel.copy(lineup = lineup)
    }

    fun filter(channels: List<LiveChannel>): List<LiveChannel> = channels.mapNotNull { filter(it) }

    fun clear() {
        _keys.value = emptySet()
    }
}
