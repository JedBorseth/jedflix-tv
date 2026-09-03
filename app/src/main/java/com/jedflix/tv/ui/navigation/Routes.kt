package com.jedflix.tv.ui.navigation

import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType

object Routes {
    const val SPLASH = "splash"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{mediaType}/{id}"
    const val STREAMS = "streams/{mediaType}/{id}?season={season}&episode={episode}"
    const val PLAYER = "player"

    /** Sentinel for "no season/episode"; nav args can't carry nullable ints. */
    const val NO_EPISODE = -1

    fun detail(title: MediaTitle): String = detail(title.mediaType, title.id)

    fun detail(type: MediaType, id: Int): String = "detail/${type.apiValue}/$id"

    fun streams(type: MediaType, id: Int, season: Int? = null, episode: Int? = null): String =
        "streams/${type.apiValue}/$id?season=${season ?: NO_EPISODE}&episode=${episode ?: NO_EPISODE}"
}
