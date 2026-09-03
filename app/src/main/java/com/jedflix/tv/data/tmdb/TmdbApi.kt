package com.jedflix.tv.data.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** TMDB v3 endpoints used by the catalog. `api_key` and `language` are added by an interceptor. */
interface TmdbApi {

    @GET("trending/{mediaType}/week")
    suspend fun trending(@Path("mediaType") mediaType: String): TmdbPagedResponse

    @GET("movie/{list}")
    suspend fun movieList(@Path("list") list: String, @Query("page") page: Int = 1): TmdbPagedResponse

    @GET("tv/{list}")
    suspend fun tvList(@Path("list") list: String, @Query("page") page: Int = 1): TmdbPagedResponse

    @GET("discover/{mediaType}")
    suspend fun discover(
        @Path("mediaType") mediaType: String,
        @Query("with_genres") genreId: Int,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("vote_count.gte") minVotes: Int = 100,
        @Query("page") page: Int = 1,
    ): TmdbPagedResponse

    @GET("{mediaType}/{id}")
    suspend fun details(
        @Path("mediaType") mediaType: String,
        @Path("id") id: Int,
        @Query("append_to_response") append: String,
    ): TmdbDetailsDto

    @GET("tv/{id}/season/{season}")
    suspend fun seasonEpisodes(
        @Path("id") id: Int,
        @Path("season") season: Int,
    ): TmdbSeasonDto

    @GET("search/multi")
    suspend fun search(
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1,
    ): TmdbPagedResponse
}
