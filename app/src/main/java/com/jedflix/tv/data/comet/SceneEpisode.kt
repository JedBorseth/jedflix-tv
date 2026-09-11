package com.jedflix.tv.data.comet

import com.jedflix.tv.data.playback.EpisodeRef

/** One episode from Stremio Cinemeta, whose S/E numbers match torrent scene numbering. */
data class CinemetaEpisode(
    val season: Int,
    val episode: Int,
    val name: String,
)

/**
 * Maps a TMDB episode to the season/episode Comet should search.
 *
 * TMDB often merges two-part episodes (The Office S4 "Dinner Party" is E9). Scene releases
 * and Cinemeta split those parts (Dinner Party is S04E13), so searching TMDB numbers
 * returns the wrong file.
 */
object SceneEpisode {
    fun searchRef(
        tmdbSeason: Int,
        tmdbEpisode: Int,
        tmdbTitle: String?,
        videos: List<CinemetaEpisode>,
    ): EpisodeRef {
        val wanted = normalize(tmdbTitle)
        if (wanted.isEmpty()) return EpisodeRef(tmdbSeason, tmdbEpisode)
        val match = videos
            .filter { video ->
                video.season == tmdbSeason && video.episode > 0 && titlesMatch(wanted, video.name)
            }
            .minByOrNull { it.episode }
            ?: return EpisodeRef(tmdbSeason, tmdbEpisode)
        return EpisodeRef(match.season, match.episode)
    }

    /**
     * Drops files that name a different SxxExx than the one we searched, while keeping
     * season packs and unlabeled releases. If that would leave nothing, return [options].
     */
    fun filterStreams(options: List<StreamOption>, season: Int, episode: Int): List<StreamOption> {
        val kept = options.filter { filenameMatches(it.filename, season, episode) }
        return kept.ifEmpty { options }
    }

    internal fun filenameMatches(filename: String, season: Int, episode: Int): Boolean {
        val mentions = episodeMentions(filename)
        if (mentions.isEmpty()) return true
        return mentions.any { it.contains(season, episode) }
    }

    private fun titlesMatch(wanted: String, rawName: String): Boolean {
        val name = normalize(rawName)
        return name == wanted || stripPartSuffix(name) == wanted
    }

    internal fun normalize(title: String?): String {
        if (title.isNullOrBlank()) return ""
        val cleaned = title.lowercase()
            .replace("&", "and")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        return cleaned.removePrefix("the ").trim()
    }

    private fun stripPartSuffix(normalized: String): String =
        normalized.replace(PART_SUFFIX, "").trim()

    private fun episodeMentions(filename: String): List<EpisodeSpan> {
        val ranges = RANGE_TOKEN.findAll(filename).mapNotNull { match ->
            val season = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val start = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val end = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
            EpisodeSpan(season, minOf(start, end), maxOf(start, end))
        }
        val scenes = SCENE_TOKEN.findAll(filename).mapNotNull { match ->
            val season = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val start = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val end = match.groupValues[3].toIntOrNull() ?: start
            EpisodeSpan(season, minOf(start, end), maxOf(start, end))
        }
        val dotted = X_TOKEN.findAll(filename).mapNotNull { match ->
            val season = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val episode = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            EpisodeSpan(season, episode, episode)
        }
        return (ranges + scenes + dotted).toList()
    }

    private data class EpisodeSpan(val season: Int, val start: Int, val end: Int) {
        fun contains(season: Int, episode: Int): Boolean =
            this.season == season && episode in start..end
    }

    private val PART_SUFFIX = Regex("""\s+(part\s+)?\d+$""")
    private val SCENE_TOKEN = Regex(
        """(?:^|[^A-Za-z0-9])[sS](\d{1,2})[.\s_-]?[eE](\d{1,3})(?:[eE](\d{1,3}))?(?:[^A-Za-z0-9]|$)""",
    )
    private val RANGE_TOKEN = Regex(
        """(?:^|[^A-Za-z0-9])[sS](\d{1,2})[.\s_-]?[eE](\d{1,3})\s*[-–]\s*(?:[eE])?(\d{1,3})(?:[^A-Za-z0-9]|$)""",
    )
    private val X_TOKEN = Regex(
        """(?:^|[^A-Za-z0-9])(\d{1,2})x(\d{1,3})(?:[^A-Za-z0-9]|$)""",
    )
}
