package com.jedflix.tv.data.comet

import java.util.Locale

/** One playable candidate from Comet, ready for the stream picker. */
data class StreamOption(
    val id: String,
    /** e.g. "1080P", "4K", or "Unknown". */
    val resolution: String,
    val filename: String,
    /** Extra info lines from Comet (codec, audio, seeders, size, tracker). */
    val details: List<String>,
    val sizeBytes: Long?,
    val cached: Boolean,
    /** Comet playback URL; resolving it yields the Real-Debrid download link. */
    val playbackUrl: String,
) {
    val sizeLabel: String? get() = sizeBytes?.let(::formatBytes)
}

private const val CACHED_MARK = "⚡"
private val NOTICE_MARKS = listOf("❌", "🔄", "⚠️")

/**
 * Comet notices (debrid errors, "scraping in progress") also point at `/playback/` (a status
 * video), but they are flagged in `name` and never carry `behaviorHints`.
 */
fun CometStreamDto.isNotice(): Boolean =
    NOTICE_MARKS.any { mark -> name?.contains(mark) == true } || behaviorHints == null

fun CometStreamDto.isPlayable(): Boolean {
    val target = url ?: return false
    return !isNotice() && target.startsWith("http") && target.contains("/playback/")
}

fun CometStreamDto.toStreamOption(): StreamOption? {
    if (!isPlayable()) return null
    val target = url ?: return null
    val rawName = name.orEmpty()
    val descriptionLines = description.orEmpty()
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    // Comet's description repeats the release name on its first line behind an emoji (e.g. "📄 Name.mkv").
    val filename = behaviorHints?.filename?.takeIf { it.isNotBlank() }
        ?: descriptionLines.firstOrNull()?.trimStart { !it.isLetterOrDigit() }?.takeIf { it.isNotBlank() }
        ?: rawName
    val details = descriptionLines.filterNot { it.contains(filename) }
    return StreamOption(
        id = target.substringAfter("/playback/").substringBefore('?'),
        resolution = parseResolution(rawName),
        filename = filename,
        details = details,
        sizeBytes = behaviorHints?.videoSize?.takeIf { it > 0 },
        cached = rawName.contains(CACHED_MARK),
        playbackUrl = target,
    )
}

/** Comet names look like `[RD⚡] Comet 1080P`; the resolution is the trailing token. */
private fun parseResolution(name: String): String {
    val token = name.substringAfterLast(' ', "").trim()
    return token.takeIf { it.isNotEmpty() && !it.endsWith("]") } ?: "Unknown"
}

fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1) return String.format(Locale.US, "%.1f GB", gb)
    val mb = bytes / 1_048_576.0
    return String.format(Locale.US, "%.0f MB", mb)
}
