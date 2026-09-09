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

    data class WatchProvider(
        override val id: String,
        override val title: String,
        val mediaType: MediaType,
        val providerId: Int,
    ) : ShelfSpec

    data class EditorialList(
        override val id: String,
        override val title: String,
        val listId: Int,
        val mediaType: MediaType,
    ) : ShelfSpec
}

object CatalogShelves {
    const val TRENDING_HOME = "trending-all"
    const val TRENDING_MOVIES = "trending-movies"
    const val TRENDING_SHOWS = "trending-tv"

    val jedsMovies = ShelfSpec.EditorialList(
        "jeds-movies",
        "Jed's Movies",
        JedsPicksLists.MOVIES,
        MediaType.MOVIE,
    )
    val jedsShows = ShelfSpec.EditorialList(
        "jeds-shows",
        "Jed's Shows",
        JedsPicksLists.SHOWS,
        MediaType.TV,
    )

    val craveMovies = provider("crave-movies", "Crave Movies", MediaType.MOVIE, TmdbWatchProviders.CRAVE)
    val craveShows = provider("crave-shows", "Crave Shows", MediaType.TV, TmdbWatchProviders.CRAVE)
    val appleTvMovies = provider("apple-tv-movies", "Apple TV Movies", MediaType.MOVIE, TmdbWatchProviders.APPLE_TV)
    val appleTvShows = provider("apple-tv-shows", "Apple TV Shows", MediaType.TV, TmdbWatchProviders.APPLE_TV)
    val paramountMovies = provider("paramount-movies", "Paramount+ Movies", MediaType.MOVIE, TmdbWatchProviders.PARAMOUNT_PLUS)
    val paramountShows = provider("paramount-shows", "Paramount+ Shows", MediaType.TV, TmdbWatchProviders.PARAMOUNT_PLUS)
    val disneyMovies = provider("disney-movies", "Disney+ Movies", MediaType.MOVIE, TmdbWatchProviders.DISNEY_PLUS)
    val disneyShows = provider("disney-shows", "Disney+ Shows", MediaType.TV, TmdbWatchProviders.DISNEY_PLUS)

    val homeEditorialAndProviders = listOf(
        jedsMovies,
        jedsShows,
        craveMovies,
        craveShows,
        appleTvMovies,
        appleTvShows,
        paramountMovies,
        paramountShows,
        disneyMovies,
        disneyShows,
    )

    val movieEditorialAndProviders = listOf(
        jedsMovies,
        craveMovies,
        appleTvMovies,
        paramountMovies,
        disneyMovies,
    )

    val showEditorialAndProviders = listOf(
        jedsShows,
        craveShows,
        appleTvShows,
        paramountShows,
        disneyShows,
    )

    fun billboardShelfId(section: CatalogSection): String = when (section) {
        CatalogSection.HOME -> TRENDING_HOME
        CatalogSection.MOVIES -> TRENDING_MOVIES
        CatalogSection.SHOWS -> TRENDING_SHOWS
    }

    fun forSection(section: CatalogSection): List<ShelfSpec> = when (section) {
        CatalogSection.HOME -> homeEditorialAndProviders + listOf(
            ShelfSpec.Trending(TRENDING_HOME, "Trending Now", "all"),
            ShelfSpec.MovieList("popular-movies", "Popular Movies", "popular"),
            ShelfSpec.TvList("popular-tv", "Popular TV", "popular"),
            ShelfSpec.MovieList("top-movies", "Top Rated Movies", "top_rated"),
            ShelfSpec.TvList("top-tv", "Top Rated TV", "top_rated"),
            ShelfSpec.Discover("action", "Action Movies", MediaType.MOVIE, TmdbGenres.MOVIE_ACTION),
            ShelfSpec.Discover("comedy", "Comedy Movies", MediaType.MOVIE, TmdbGenres.MOVIE_COMEDY),
            ShelfSpec.Discover("horror", "Horror Movies", MediaType.MOVIE, TmdbGenres.MOVIE_HORROR),
            ShelfSpec.Discover("scifi", "Sci-Fi Movies", MediaType.MOVIE, TmdbGenres.MOVIE_SCIFI),
        )

        CatalogSection.MOVIES -> movieEditorialAndProviders + listOf(
            ShelfSpec.Trending(TRENDING_MOVIES, "Trending Movies", "movie"),
            ShelfSpec.MovieList("now-playing", "Now Playing", "now_playing"),
            ShelfSpec.MovieList("popular-movies", "Popular Movies", "popular"),
            ShelfSpec.MovieList("top-movies", "Top Rated Movies", "top_rated"),
            ShelfSpec.MovieList("upcoming", "Coming Soon", "upcoming"),
            ShelfSpec.Discover("action", "Action", MediaType.MOVIE, TmdbGenres.MOVIE_ACTION),
            ShelfSpec.Discover("thriller", "Thrillers", MediaType.MOVIE, TmdbGenres.MOVIE_THRILLER),
            ShelfSpec.Discover("animation", "Animation", MediaType.MOVIE, TmdbGenres.MOVIE_ANIMATION),
            ShelfSpec.Discover("scifi", "Sci-Fi", MediaType.MOVIE, TmdbGenres.MOVIE_SCIFI),
        )

        CatalogSection.SHOWS -> showEditorialAndProviders + listOf(
            ShelfSpec.Trending(TRENDING_SHOWS, "Trending Shows", "tv"),
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

    private fun provider(
        id: String,
        title: String,
        mediaType: MediaType,
        providerId: Int,
    ) = ShelfSpec.WatchProvider(id, title, mediaType, providerId)
}
