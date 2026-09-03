package com.jedflix.tv.data.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbPagedResponse(
    val page: Int = 1,
    val results: List<TmdbMediaDto> = emptyList(),
)

@Serializable
data class TmdbMediaDto(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    val adult: Boolean = false,
    val popularity: Double? = null,
)

@Serializable
data class TmdbGenreDto(
    val id: Int,
    val name: String = "",
)

@Serializable
data class TmdbCastDto(
    val id: Int,
    val name: String = "",
    val character: String? = null,
    val roles: List<TmdbCastRoleDto> = emptyList(),
    @SerialName("profile_path") val profilePath: String? = null,
    val order: Int = Int.MAX_VALUE,
)

@Serializable
data class TmdbCastRoleDto(
    val character: String? = null,
)

@Serializable
data class TmdbCreditsDto(
    val cast: List<TmdbCastDto> = emptyList(),
)

@Serializable
data class TmdbSeasonSummaryDto(
    val name: String = "",
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("episode_count") val episodeCount: Int = 0,
)

@Serializable
data class TmdbExternalIdsDto(
    @SerialName("imdb_id") val imdbId: String? = null,
)

@Serializable
data class TmdbDetailsDto(
    val id: Int,
    @SerialName("external_ids") val externalIds: TmdbExternalIdsDto? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val adult: Boolean = false,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val genres: List<TmdbGenreDto> = emptyList(),
    val seasons: List<TmdbSeasonSummaryDto> = emptyList(),
    val credits: TmdbCreditsDto? = null,
    @SerialName("aggregate_credits") val aggregateCredits: TmdbCreditsDto? = null,
    val recommendations: TmdbPagedResponse? = null,
)

@Serializable
data class TmdbEpisodeDto(
    val name: String = "",
    val overview: String? = null,
    @SerialName("episode_number") val episodeNumber: Int = 0,
    @SerialName("still_path") val stillPath: String? = null,
    val runtime: Int? = null,
)

@Serializable
data class TmdbSeasonDto(
    val episodes: List<TmdbEpisodeDto> = emptyList(),
)
