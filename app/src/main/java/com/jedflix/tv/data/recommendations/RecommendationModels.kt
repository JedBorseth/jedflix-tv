package com.jedflix.tv.data.recommendations

import com.jedflix.tv.data.library.RecommendationSignals
import com.jedflix.tv.data.tmdb.CatalogRow
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import kotlinx.serialization.Serializable
import java.time.ZoneId

@Serializable
data class RecommendationCandidate(
    val id: Int,
    val mediaType: String,
    val title: String,
    val overview: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val genres: List<String> = emptyList(),
    val releaseDate: String? = null,
) {
    fun toTitle(): MediaTitle? {
        val type = MediaType.fromApi(mediaType) ?: return null
        if (id <= 0 || title.isBlank() || posterUrl.isNullOrBlank()) return null
        return MediaTitle(id, type, title, overview, posterUrl, backdropUrl, year?.toString(), rating, genres, releaseDate)
    }
    companion object {
        fun from(title: MediaTitle) = RecommendationCandidate(
            title.id, title.mediaType.apiValue, title.title, title.overview, title.posterUrl,
            title.backdropUrl, title.year?.toIntOrNull(), title.rating, title.genres, title.releaseDate,
        )
    }
}

@Serializable
data class WatchSignal(val tmdbId: Int, val mediaType: String, val watchedMs: Long, val positionMs: Long,
    val durationMs: Long, val lastWatchedAt: Long, val season: Int = 0, val episode: Int = 0,
    val latestWatchedMs: Long = 0)
@Serializable
data class ListSignal(val tmdbId: Int, val mediaType: String)
@Serializable
data class FeedbackSignal(val tmdbId: Int, val mediaType: String, val value: String, val updatedAt: Long)
@Serializable
data class RecommendationRequest(
    val history: List<WatchSignal>,
    val myList: List<ListSignal>,
    val feedback: List<FeedbackSignal>,
    val candidates: List<RecommendationCandidate>,
    val timeZone: String = ZoneId.systemDefault().id,
) {
    companion object {
        fun from(signals: RecommendationSignals, candidates: List<MediaTitle>) = RecommendationRequest(
            history = signals.history.map { WatchSignal(it.tmdbId, it.mediaType, it.watchedMs, it.positionMs, it.durationMs, it.lastWatchedAt, it.season, it.episode, it.latestWatchedMs) },
            myList = signals.myList.map { ListSignal(it.tmdbId, it.mediaType) },
            feedback = signals.feedback.map { FeedbackSignal(it.tmdbId, it.mediaType, it.value, it.updatedAt) },
            candidates = candidates.distinctBy { it.key }.take(300).map(RecommendationCandidate::from),
        )
    }
}
@Serializable
data class RecommendationShelf(val id: String, val title: String, val items: List<RecommendationCandidate>) {
    fun toRow(): CatalogRow? {
        if (id != "for-you" && !id.startsWith("because-") && !DynamicShelves.isKnown(id)) return null
        val titles = items.mapNotNull { it.toTitle() }.distinctBy { it.key }.take(40)
        return CatalogRow(id, title.take(180), titles).takeIf { titles.isNotEmpty() }
    }
}
@Serializable
data class RecommendationResponse(
    val model: String = "",
    val shelves: List<RecommendationShelf> = emptyList(),
    val eligibleKeys: List<String> = emptyList(),
    val evaluatedKeys: List<String> = emptyList(),
    val refreshedAt: Long = 0,
    val validUntil: Long = 0,
    val catalogReady: Boolean = true,
    val modelVersion: String = "",
) {
    fun toRows(): List<CatalogRow> {
        var dynamicCount = 0
        var becauseCount = 0
        return shelves.mapNotNull { it.toRow() }.distinctBy { it.id }.filter { row ->
            when {
                row.id.startsWith("dynamic-") -> ++dynamicCount <= 5
                row.id.startsWith("because-") -> ++becauseCount <= 2
                else -> true
            }
        }.take(8)
    }
}
