package com.jedflix.tv.data.playback

/** Season/episode to play after the current one, if binge can continue. */
data class EpisodeRef(val season: Int, val episode: Int)

object NextEpisode {
    /**
     * Next episode in the current season, or null if this is the last numbered episode
     * we know about in that season.
     */
    fun nextInSeason(episodeNumbers: List<Int>, currentEpisode: Int): Int? =
        episodeNumbers.filter { it > currentEpisode }.minOrNull()

    /** Lowest season number greater than [currentSeason], or null at series end. */
    fun nextSeasonNumber(seasonNumbers: List<Int>, currentSeason: Int): Int? =
        seasonNumbers.filter { it > currentSeason }.minOrNull()

    fun firstEpisode(episodeNumbers: List<Int>): Int? = episodeNumbers.filter { it > 0 }.minOrNull()

    fun episodeSubtitle(season: Int, episode: Int, episodeTitle: String?): String = buildString {
        append('S').append(season).append(" E").append(episode)
        episodeTitle?.takeIf { it.isNotBlank() }?.let { append("  •  ").append(it) }
    }
}
