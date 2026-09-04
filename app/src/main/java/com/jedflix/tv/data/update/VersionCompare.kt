package com.jedflix.tv.data.update

/**
 * Compares dotted version strings used as GitHub tags / [android.os.Build] versionName.
 * A leading `v` is ignored. Pre-release suffixes (`-beta`) are stripped so `1.2.0-beta`
 * compares as `1.2.0`. Non-numeric tags are treated as not newer.
 */
object VersionCompare {
    fun isNewer(latestTag: String, currentVersion: String): Boolean {
        val latest = parse(latestTag) ?: return false
        val current = parse(currentVersion) ?: return false
        val length = maxOf(latest.size, current.size)
        for (i in 0 until length) {
            val l = latest.getOrElse(i) { 0 }
            val c = current.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    fun displayVersion(tag: String): String =
        tag.trim().removePrefix("v").removePrefix("V")

    internal fun parse(raw: String): List<Int>? {
        val core = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .substringBefore("+")
        if (core.isEmpty() || !core.first().isDigit()) return null
        val parts = core.split('.')
        if (parts.isEmpty()) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        return numbers
    }
}
