package com.jedflix.tv.data.recommendations

/** Remote themes cannot claim pinned Home or configurable catalog Shelf identities. */
internal object DynamicShelves {
    private val themes = setOf(
        "friday-movie-night", "saturday-double-feature", "sunday-comfort", "after-long-day",
        "done-before-bed", "late-night-thrillers", "weekend-binge", "halloween", "spooky-not-scary",
        "christmas", "cozy-winter", "new-year", "valentines", "summer-adventure", "thanksgiving",
        "start-new-series", "next-obsession", "one-season-done", "hidden-gems", "change-pace",
        "back-90s", "twists-turns", "worlds", "make-laugh", "true-story",
    )
    private val personTheme = Regex("^(more-director|starring-actor)-[1-9][0-9]*$")

    fun isKnown(id: String): Boolean {
        if (!id.startsWith("dynamic-")) return false
        val theme = id.removePrefix("dynamic-")
        return theme in themes || personTheme.matches(theme)
    }
}
