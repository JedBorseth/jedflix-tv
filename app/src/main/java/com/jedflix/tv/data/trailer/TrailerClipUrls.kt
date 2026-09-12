package com.jedflix.tv.data.trailer

/**
 * Hosted preview clips are progressive MP4s named `{youtubeKey}.mp4`.
 *
 * `baseUrl` is the directory (no trailing slash required), e.g.
 * `https://api.example.com/clips` → `https://api.example.com/clips/dQw4w9WgXcQ.mp4`.
 */
object TrailerClipUrls {

    fun url(baseUrl: String, youtubeKey: String): String? {
        val base = baseUrl.trim().trimEnd('/')
        val key = youtubeKey.trim()
        if (base.isEmpty() || !YOUTUBE_KEY.matches(key)) return null
        return "$base/$key.mp4"
    }

    private val YOUTUBE_KEY = Regex("^[A-Za-z0-9_-]{6,20}$")
}
