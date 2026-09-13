package com.jedflix.tv.data.playback

enum class SkipKind {
    Recap,
    Intro,
    Outro,
}

data class SkipSegment(
    val kind: SkipKind,
    val startMs: Long,
    val endMs: Long,
)

data class SkipAction(
    val kind: SkipKind,
    val endMs: Long,
)

/** Picks the skippable recap/intro the playhead is currently inside. Outro opens Up Next instead. */
object SkipWindows {
    fun active(segments: List<SkipSegment>, positionMs: Long): SkipAction? {
        val position = positionMs.coerceAtLeast(0L)
        val hit = segments
            .filter { segment ->
                segment.kind != SkipKind.Outro &&
                    inWindow(segment, position) &&
                    segment.endMs > segment.startMs
            }
            .minWithOrNull(compareBy<SkipSegment> { kindRank(it.kind) }.thenBy { it.endMs })
            ?: return null
        return SkipAction(kind = hit.kind, endMs = hit.endMs)
    }

    fun inOutro(segments: List<SkipSegment>, positionMs: Long): Boolean {
        val position = positionMs.coerceAtLeast(0L)
        return segments.any { segment ->
            segment.kind == SkipKind.Outro &&
                segment.endMs > segment.startMs &&
                position >= segment.startMs &&
                position < segment.endMs
        }
    }

    private fun inWindow(segment: SkipSegment, position: Long): Boolean {
        val hideAt = (segment.endMs - HIDE_BEFORE_END_MS).coerceAtLeast(segment.startMs + 1)
        return position >= segment.startMs && position < hideAt
    }

    private fun kindRank(kind: SkipKind): Int = when (kind) {
        SkipKind.Recap -> 0
        SkipKind.Intro -> 1
        SkipKind.Outro -> 2
    }

    private const val HIDE_BEFORE_END_MS = 800L
}
