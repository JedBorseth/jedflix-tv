package com.jedflix.tv.ui.navigation

import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType

object Routes {
    const val SPLASH = "splash"
    const val SEARCH = "search"
    const val DETAIL = "detail/{mediaType}/{id}"

    fun detail(title: MediaTitle): String = detail(title.mediaType, title.id)

    fun detail(type: MediaType, id: Int): String = "detail/${type.apiValue}/$id"
}
