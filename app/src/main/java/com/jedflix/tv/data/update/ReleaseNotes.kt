package com.jedflix.tv.data.update

private const val MAX_NOTE_LINES = 8
private const val MAX_NOTE_CHARS = 400

fun summarizeReleaseNotes(body: String?, fallbackName: String?): String {
    val lines = body.orEmpty()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(MAX_NOTE_LINES)
        .toList()
    val joined = lines.joinToString("\n")
    val trimmed = if (joined.length <= MAX_NOTE_CHARS) {
        joined
    } else {
        joined.take(MAX_NOTE_CHARS).trimEnd() + "…"
    }
    return trimmed.ifBlank { fallbackName.orEmpty().trim() }
}

internal fun pickApkAsset(assets: List<GithubAssetDto>): GithubAssetDto? {
    val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    return apks.firstOrNull { it.name.contains("jedflix", ignoreCase = true) }
        ?: apks.firstOrNull()
}
