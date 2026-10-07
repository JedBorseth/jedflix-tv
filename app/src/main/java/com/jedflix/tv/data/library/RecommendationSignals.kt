package com.jedflix.tv.data.library

/** Bounded, profile-pinned snapshot read only when recommendations refresh. */
data class RecommendationSignals(
    val profileId: Long,
    val history: List<RecommendationWatch>,
    val myList: List<RecommendationTitle>,
    val feedback: List<RecommendationFeedback>,
)

data class RecommendationWatch(
    val tmdbId: Int,
    val mediaType: String,
    val watchedMs: Long,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
    val season: Int = 0,
    val episode: Int = 0,
    val latestWatchedMs: Long = 0,
)

data class RecommendationTitle(val tmdbId: Int, val mediaType: String)
data class RecommendationFeedback(
    val tmdbId: Int,
    val mediaType: String,
    val value: String,
    val updatedAt: Long,
)

enum class TitleFeedback(val apiValue: String) {
    LIKE("like"),
    DISLIKE("dislike");

    companion object {
        fun fromApi(value: String?): TitleFeedback? = entries.firstOrNull { it.apiValue == value }
    }
}
