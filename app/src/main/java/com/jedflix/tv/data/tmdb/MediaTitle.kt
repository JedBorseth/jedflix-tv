package com.jedflix.tv.data.tmdb

enum class MediaType(val apiValue: String) {
    MOVIE("movie"),
    TV("tv");

    companion object {
        fun fromApi(value: String?): MediaType? = entries.firstOrNull { it.apiValue == value }
    }
}

/** UI-ready title normalised from TMDB list responses. */
data class MediaTitle(
    val id: Int,
    val mediaType: MediaType,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: String?,
    val rating: Double?,
    val genres: List<String>,
) {
    val key: String get() = "${mediaType.apiValue}-$id"
}

data class CatalogRow(
    val id: String,
    val title: String,
    val items: List<MediaTitle>,
)

data class Catalog(
    /** Titles eligible for the billboard; the first row mirrors these so focus drives the hero. */
    val featured: List<MediaTitle>,
    val rows: List<CatalogRow>,
)

data class CastMember(
    val id: Int,
    val name: String,
    val character: String,
    val profileUrl: String?,
)

data class TvSeason(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
)

data class TvEpisode(
    val episodeNumber: Int,
    val title: String,
    val overview: String,
    val stillUrl: String?,
    val runtimeMinutes: Int?,
)

data class TitleDetails(
    val title: MediaTitle,
    val runtimeMinutes: Int?,
    val cast: List<CastMember>,
    val similar: List<MediaTitle>,
    val seasons: List<TvSeason>,
)

private const val IMAGE_BASE = "https://image.tmdb.org/t/p"
const val POSTER_SIZE = "w500"
const val BACKDROP_SIZE = "w1280"
const val PROFILE_SIZE = "w185"
const val STILL_SIZE = "w300"

fun tmdbImageUrl(path: String?, size: String): String? =
    path?.takeIf { it.isNotBlank() }?.let { "$IMAGE_BASE/$size$it" }

fun TmdbMediaDto.toMediaTitle(fallbackType: MediaType?): MediaTitle? {
    if (adult) return null
    val type = MediaType.fromApi(mediaType) ?: fallbackType ?: return null
    val displayTitle = (title ?: name)?.takeIf { it.isNotBlank() } ?: return null
    val poster = posterPath ?: return null
    val date = releaseDate ?: firstAirDate
    return MediaTitle(
        id = id,
        mediaType = type,
        title = displayTitle,
        overview = overview.orEmpty(),
        posterUrl = tmdbImageUrl(poster, POSTER_SIZE),
        backdropUrl = tmdbImageUrl(backdropPath, BACKDROP_SIZE),
        year = date?.take(4)?.takeIf { it.length == 4 },
        rating = voteAverage?.takeIf { it > 0.0 },
        genres = genreIds.mapNotNull { TmdbGenres.name(type, it) }.take(3),
    )
}

fun TmdbDetailsDto.toTitleDetails(type: MediaType): TitleDetails? {
    if (adult) return null
    val displayTitle = (title ?: name)?.takeIf { it.isNotBlank() } ?: return null
    val date = releaseDate ?: firstAirDate
    val media = MediaTitle(
        id = id,
        mediaType = type,
        title = displayTitle,
        overview = overview.orEmpty(),
        posterUrl = tmdbImageUrl(posterPath, POSTER_SIZE),
        backdropUrl = tmdbImageUrl(backdropPath, BACKDROP_SIZE),
        year = date?.take(4)?.takeIf { it.length == 4 },
        rating = voteAverage?.takeIf { it > 0.0 },
        genres = genres.map { it.name }.filter { it.isNotBlank() }.take(4),
    )
    val credits = (if (type == MediaType.MOVIE) credits else aggregateCredits)?.cast.orEmpty()
    val runtime = if (type == MediaType.MOVIE) {
        runtime?.takeIf { it > 0 }
    } else {
        episodeRunTime.firstOrNull()?.takeIf { it > 0 }
    }
    return TitleDetails(
        title = media,
        runtimeMinutes = runtime,
        cast = credits
            .sortedBy { it.order }
            .mapNotNull { it.toCastMember() }
            .distinctBy { it.id }
            .take(CAST_LIMIT),
        similar = recommendations?.results.orEmpty()
            .mapNotNull { it.toMediaTitle(type) }
            .filter { it.id != id }
            .distinctBy { it.key }
            .take(SIMILAR_LIMIT),
        seasons = if (type == MediaType.TV) {
            seasons
                .filter { it.seasonNumber > 0 }
                .map {
                    TvSeason(
                        seasonNumber = it.seasonNumber,
                        name = it.name.ifBlank { "Season ${it.seasonNumber}" },
                        episodeCount = it.episodeCount,
                    )
                }
        } else {
            emptyList()
        },
    )
}

fun TmdbCastDto.toCastMember(): CastMember? {
    val displayName = name.takeIf { it.isNotBlank() } ?: return null
    val role = character?.takeIf { it.isNotBlank() }
        ?: roles.firstOrNull()?.character?.takeIf { it.isNotBlank() }
        ?: "Unknown role"
    return CastMember(
        id = id,
        name = displayName,
        character = role,
        profileUrl = tmdbImageUrl(profilePath, PROFILE_SIZE),
    )
}

fun TmdbEpisodeDto.toTvEpisode(): TvEpisode =
    TvEpisode(
        episodeNumber = episodeNumber,
        title = name.ifBlank { "Episode $episodeNumber" },
        overview = overview.orEmpty(),
        stillUrl = tmdbImageUrl(stillPath, STILL_SIZE),
        runtimeMinutes = runtime?.takeIf { it > 0 },
    )

private const val CAST_LIMIT = 20
private const val SIMILAR_LIMIT = 20
