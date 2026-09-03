package com.jedflix.tv.data.tmdb

/** Static TMDB genre ids so list responses can show genre names without an extra request. */
object TmdbGenres {
    const val MOVIE_ACTION = 28
    const val MOVIE_COMEDY = 35
    const val MOVIE_HORROR = 27
    const val MOVIE_SCIFI = 878
    const val MOVIE_ANIMATION = 16
    const val MOVIE_THRILLER = 53

    const val TV_ACTION_ADVENTURE = 10759
    const val TV_COMEDY = 35
    const val TV_DRAMA = 18
    const val TV_SCIFI_FANTASY = 10765
    const val TV_CRIME = 80
    const val TV_ANIMATION = 16

    private val movie = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime",
        99 to "Documentary", 18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History",
        27 to "Horror", 10402 to "Music", 9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi",
        10770 to "TV Movie", 53 to "Thriller", 10752 to "War", 37 to "Western",
    )

    private val tv = mapOf(
        10759 to "Action & Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime",
        99 to "Documentary", 18 to "Drama", 10751 to "Family", 10762 to "Kids", 9648 to "Mystery",
        10763 to "News", 10764 to "Reality", 10765 to "Sci-Fi & Fantasy", 10766 to "Soap",
        10767 to "Talk", 10768 to "War & Politics", 37 to "Western",
    )

    fun name(type: MediaType, id: Int): String? = when (type) {
        MediaType.MOVIE -> movie[id]
        MediaType.TV -> tv[id]
    }
}
