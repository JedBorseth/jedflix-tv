package com.jedflix.tv.data.trailer

/**
 * Hosted preview clips are progressive MP4s named `{youtubeKey}.mp4`.
 *
 * `baseUrl` is the directory (no trailing slash required), e.g.
 * `https://api.example.com/clips` → `https://api.example.com/clips/dQw4w9WgXcQ.mp4`.
 *
 * A blank `baseUrl` (or a URL that already ends in `.mp4`) uses that file as-is so the
 * catalog preview can be exercised before the clip host exists.
 */
object TrailerClipUrls {

    const val STAND_IN_MP4 =
        "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"

    fun url(baseUrl: String, youtubeKey: String = ""): String? {
        val raw = baseUrl.trim()
        if (raw.endsWith(".mp4", ignoreCase = true)) return raw
        val base = raw.trimEnd('/')
        if (base.isEmpty()) return STAND_IN_MP4
        val key = youtubeKey.trim()
        if (!YOUTUBE_KEY.matches(key)) return null
        return "$base/$key.mp4"
    }

    fun usesStandIn(baseUrl: String): Boolean {
        val raw = baseUrl.trim()
        return raw.isEmpty() || raw.endsWith(".mp4", ignoreCase = true)
    }

    private val YOUTUBE_KEY = Regex("^[A-Za-z0-9_-]{6,20}$")
}
