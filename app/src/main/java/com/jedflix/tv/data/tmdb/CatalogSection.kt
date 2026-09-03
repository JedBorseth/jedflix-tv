package com.jedflix.tv.data.tmdb

/** Top-level nav rail destinations. Each one has its own set of TMDB shelves. */
enum class CatalogSection(val route: String) {
    HOME("catalog/home"),
    MOVIES("catalog/movies"),
    SHOWS("catalog/shows");

    companion object {
        fun fromRoute(route: String?): CatalogSection? = entries.firstOrNull { it.route == route }
    }
}

/** Declarative description of one shelf; the repository turns it into a [CatalogRow]. */
sealed interface ShelfSpec {
    val id: String
    val title: String

    data class Trending(override val id: String, override val title: String, val mediaType: String) : ShelfSpec
    data class MovieList(override val id: String, override val title: String, val list: String) : ShelfSpec
    data class TvList(override val id: String, override val title: String, val list: String) : ShelfSpec
    data class Discover(
        override val id: String,
        override val title: String,
        val mediaType: MediaType,
        val genreId: Int,
    ) : ShelfSpec
}

object CatalogShelves {
    fun forSection(section: CatalogSection): List<ShelfSpec> = when (section) {
        CatalogSection.HOME -> listOf(
            ShelfSpec.Trending("trending-all", "Trending Now", "all"),
            ShelfSpec.MovieList("popular-movies", "Popular Movies", "popular"),
            ShelfSpec.TvList("popular-tv", "Popular TV", "popular"),
            ShelfSpec.MovieList("top-movies", "Top Rated Movies", "top_rated"),
            ShelfSpec.TvList("top-tv", "Top Rated TV", "top_rated"),
            ShelfSpec.Discover("action", "Action Movies", MediaType.MOVIE, TmdbGenres.MOVIE_ACTION),
            ShelfSpec.Discover("comedy", "Comedy Movies", MediaType.MOVIE, TmdbGenres.MOVIE_COMEDY),
            ShelfSpec.Discover("horror", "Horror Movies", MediaType.MOVIE, TmdbGenres.MOVIE_HORROR),
            ShelfSpec.Discover("scifi", "Sci-Fi Movies", MediaType.MOVIE, TmdbGenres.MOVIE_SCIFI),
        )

        CatalogSection.MOVIES -> listOf(
            ShelfSpec.Trending("trending-movies", "Trending Movies", "movie"),
            ShelfSpec.MovieList("now-playing", "Now Playing", "now_playing"),
            ShelfSpec.MovieList("popular-movies", "Popular Movies", "popular"),
            ShelfSpec.MovieList("top-movies", "Top Rated Movies", "top_rated"),
            ShelfSpec.MovieList("upcoming", "Coming Soon", "upcoming"),
            ShelfSpec.Discover("action", "Action", MediaType.MOVIE, TmdbGenres.MOVIE_ACTION),
            ShelfSpec.Discover("thriller", "Thrillers", MediaType.MOVIE, TmdbGenres.MOVIE_THRILLER),
            ShelfSpec.Discover("animation", "Animation", MediaType.MOVIE, TmdbGenres.MOVIE_ANIMATION),
            ShelfSpec.Discover("scifi", "Sci-Fi", MediaType.MOVIE, TmdbGenres.MOVIE_SCIFI),
        )

        CatalogSection.SHOWS -> listOf(
            ShelfSpec.Trending("trending-tv", "Trending Shows", "tv"),
            ShelfSpec.TvList("on-the-air", "New Episodes", "on_the_air"),
            ShelfSpec.TvList("popular-tv", "Popular Shows", "popular"),
            ShelfSpec.TvList("top-tv", "Top Rated Shows", "top_rated"),
            ShelfSpec.Discover("drama", "Drama", MediaType.TV, TmdbGenres.TV_DRAMA),
            ShelfSpec.Discover("crime", "Crime", MediaType.TV, TmdbGenres.TV_CRIME),
            ShelfSpec.Discover("scifi", "Sci-Fi & Fantasy", MediaType.TV, TmdbGenres.TV_SCIFI_FANTASY),
            ShelfSpec.Discover("comedy", "Comedy", MediaType.TV, TmdbGenres.TV_COMEDY),
            ShelfSpec.Discover("animation", "Animation", MediaType.TV, TmdbGenres.TV_ANIMATION),
        )
    }
}
