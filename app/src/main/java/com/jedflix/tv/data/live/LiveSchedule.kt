package com.jedflix.tv.data.live

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object LiveSchedule {
    const val SLOT_MS = 30L * 60_000L
    const val SEEK_CLAMP_MS = 2_000L
    const val WINDOW_MS = 2L * 60L * 60_000L

    private val slotTime: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm")

    fun snappedDurationMs(realDurationMs: Long): Long {
        require(realDurationMs > 0L)
        val slots = (realDurationMs + SLOT_MS - 1L) / SLOT_MS
        return slots.coerceAtLeast(1L) * SLOT_MS
    }

    fun loopMs(channel: LiveChannel): Long = channel.lineup.sumOf { snappedDurationMs(it.durationMs) }

    fun joinOffsetMs(program: LiveProgram, cellStartEpochMs: Long, nowMs: Long): Long {
        val intoCell = (nowMs - cellStartEpochMs).coerceAtLeast(0L)
        val maxSeek = (program.durationMs - SEEK_CLAMP_MS).coerceAtLeast(0L)
        return intoCell.coerceAtMost(maxSeek)
    }

    fun tune(channel: LiveChannel, nowMs: Long): LiveTune {
        val loop = loopMs(channel)
        val t = nowMs.floorMod(loop)
        var acc = 0L
        channel.lineup.forEachIndexed { index, program ->
            val snap = snappedDurationMs(program.durationMs)
            if (t < acc + snap) {
                val intoCell = t - acc
                return LiveTune(
                    channel = channel,
                    program = program,
                    index = index,
                    offsetMs = joinOffsetMs(program, nowMs - intoCell, nowMs),
                    next = channel.lineup[(index + 1) % channel.lineup.size],
                )
            }
            acc += snap
        }
        val last = channel.lineup.last()
        return LiveTune(
            channel = channel,
            program = last,
            index = channel.lineup.lastIndex,
            offsetMs = (last.durationMs - SEEK_CLAMP_MS).coerceAtLeast(0L),
            next = channel.lineup.first(),
        )
    }

    fun nextProgram(channel: LiveChannel, current: LiveProgram): LiveProgram {
        val index = channel.lineup.indexOfFirst { it.sameAs(current) }
        if (index < 0) return channel.lineup.first()
        return channel.lineup[(index + 1) % channel.lineup.size]
    }

    fun programsFrom(channel: LiveChannel, start: LiveProgram): List<LiveProgram> {
        val index = channel.lineup.indexOfFirst { it.sameAs(start) }.coerceAtLeast(0)
        return channel.lineup.indices.map { step ->
            channel.lineup[(index + step) % channel.lineup.size]
        }
    }

    fun windowStartMs(nowMs: Long): Long = nowMs - nowMs.floorMod(SLOT_MS)

    fun epgWindow(
        channels: List<LiveChannel>,
        nowMs: Long,
        windowMs: Long = WINDOW_MS,
    ): List<LiveChannelGuide> {
        val start = windowStartMs(nowMs)
        val end = start + windowMs
        return channels.map { channel ->
            LiveChannelGuide(channel = channel, cells = cellsOverlapping(channel, start, end))
        }
    }

    fun formatTimeslot(startEpochMs: Long, endEpochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val start = Instant.ofEpochMilli(startEpochMs).atZone(zone).toLocalTime()
        val end = Instant.ofEpochMilli(endEpochMs).atZone(zone).toLocalTime()
        return "${slotTime.format(start)} – ${slotTime.format(end)}"
    }

    private fun cellsOverlapping(channel: LiveChannel, windowStart: Long, windowEnd: Long): List<LiveEpgCell> {
        val loop = loopMs(channel)
        val t = windowStart.floorMod(loop)
        var acc = 0L
        var index = 0
        for ((i, program) in channel.lineup.withIndex()) {
            val snap = snappedDurationMs(program.durationMs)
            if (t < acc + snap) {
                index = i
                break
            }
            acc += snap
        }
        var absStart = windowStart - t + acc
        var cursor = index
        val cells = ArrayList<LiveEpgCell>()
        while (absStart < windowEnd) {
            val program = channel.lineup[cursor]
            val snap = snappedDurationMs(program.durationMs)
            val absEnd = absStart + snap
            if (absEnd > windowStart) {
                cells += LiveEpgCell(
                    channelId = channel.id,
                    program = program,
                    startEpochMs = absStart,
                    endEpochMs = absEnd,
                )
            }
            absStart = absEnd
            cursor = (cursor + 1) % channel.lineup.size
        }
        return cells
    }

    private fun Long.floorMod(mod: Long): Long {
        val r = this % mod
        return if (r < 0L) r + mod else r
    }
}
