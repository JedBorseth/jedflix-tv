package com.jedflix.tv.data.playback

/** Season/episode Play and Browse Episodes land on for a Show. */
object ShowPlay {
    const val BROWSE_SEASON = 1
    const val BROWSE_EPISODE = 2

    /**
     * Play uses in-progress watch history when present; otherwise season 1 episode 1.
     */
    fun play(resumeSeason: Int?, resumeEpisode: Int?): Pair<Int, Int> {
        if (resumeSeason != null && resumeEpisode != null) return resumeSeason to resumeEpisode
        return 1 to 1
    }

    /** Browse Episodes always prefers season 1, skipping specials (season 0). */
    fun browseSeason(available: List<Int>): Int =
        available.firstOrNull { it == BROWSE_SEASON }
            ?: available.filter { it > 0 }.minOrNull()
            ?: BROWSE_SEASON
}
