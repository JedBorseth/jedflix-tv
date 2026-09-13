package com.jedflix.tv.data.tmdb

/** Picks a transparent raster title logo from TMDB `images.logos`. */
object TitleLogoPicker {

    fun path(logos: List<TmdbImageDto>): String? {
        val usable = logos.filter { it.filePath.isNotBlank() && it.isRasterLogo() }
        if (usable.isEmpty()) return null
        return usable.best { it.isEnglish() }
            ?: usable.best { it.iso6391.isNullOrBlank() }
            ?: usable.best { true }
    }

    private fun List<TmdbImageDto>.best(predicate: (TmdbImageDto) -> Boolean): String? =
        filter(predicate).maxWithOrNull(compareBy<TmdbImageDto> { it.voteAverage }.thenBy { it.filePath })
            ?.filePath

    private fun TmdbImageDto.isEnglish() = iso6391.equals(ENGLISH, ignoreCase = true)

    private fun TmdbImageDto.isRasterLogo(): Boolean {
        val path = filePath.lowercase()
        return path.endsWith(".png") || path.endsWith(".webp")
    }

    private const val ENGLISH = "en"
}
