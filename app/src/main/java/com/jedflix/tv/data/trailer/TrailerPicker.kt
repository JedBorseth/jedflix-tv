package com.jedflix.tv.data.trailer

import com.jedflix.tv.data.tmdb.TmdbVideoDto

/** Picks the YouTube clip the hosted 30s preview is keyed by. */
object TrailerPicker {

    fun youtubeKey(videos: List<TmdbVideoDto>): String? {
        val youtube = videos.filter { it.site.equals(SITE_YOUTUBE, ignoreCase = true) && it.key.isNotBlank() }
        if (youtube.isEmpty()) return null
        return youtube.firstOrNull { it.isTrailer() && it.official && it.isEnglish() }?.key
            ?: youtube.firstOrNull { it.isTrailer() && it.isEnglish() }?.key
            ?: youtube.firstOrNull { it.isTrailer() }?.key
            ?: youtube.firstOrNull { it.isTeaser() && it.official && it.isEnglish() }?.key
            ?: youtube.firstOrNull { it.isTeaser() && it.isEnglish() }?.key
            ?: youtube.firstOrNull { it.isTeaser() }?.key
    }

    private fun TmdbVideoDto.isTrailer() = type.equals(TYPE_TRAILER, ignoreCase = true)
    private fun TmdbVideoDto.isTeaser() = type.equals(TYPE_TEASER, ignoreCase = true)
    private fun TmdbVideoDto.isEnglish() = iso6391.equals(ENGLISH, ignoreCase = true)

    private const val SITE_YOUTUBE = "YouTube"
    private const val TYPE_TRAILER = "Trailer"
    private const val TYPE_TEASER = "Teaser"
    private const val ENGLISH = "en"
}
