package com.jedflix.tv.data.tmdb

import com.jedflix.tv.data.settings.QualityProfile

fun tmdbBrowsePosterSize(profile: QualityProfile): String = when (profile) {
    QualityProfile.Low -> "w185"
    QualityProfile.Medium -> "w342"
    QualityProfile.Max -> POSTER_SIZE
}

fun tmdbBrowseBackdropSize(profile: QualityProfile): String = when (profile) {
    QualityProfile.Low, QualityProfile.Medium -> "w780"
    QualityProfile.Max -> BACKDROP_SIZE
}

/**
 * Swap the TMDB size token in a stored image URL so browse can honor the quality profile
 * without refetching. Non-TMDB URLs are returned unchanged.
 */
fun tmdbImageUrlAtSize(url: String?, size: String): String? {
    if (url.isNullOrBlank()) return null
    val match = TMDB_SIZE.matchEntire(url) ?: return url
    return "${match.groupValues[1]}$size${match.groupValues[3]}"
}

private val TMDB_SIZE = Regex("""^(https://image\.tmdb\.org/t/p/)(w\d+|original)(/.*)$""")
