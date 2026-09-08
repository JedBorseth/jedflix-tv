package com.jedflix.tv.data.playback

/**
 * One in-band audio stream, as exposed by the player. [supported] is renderer support, not
 * whether the track matches the preferred language.
 */
data class AudioTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,
    val supported: Boolean,
    val selected: Boolean,
    val channelCount: Int,
    val isDefault: Boolean,
)

/**
 * Picks a track that can actually play. 0.3.0 started passing a preferred audio language into
 * Media3; rips often tag the main mix as `und` (or leave it blank) and may also carry an English
 * track the TV cannot decode. Media3 can then select nothing playable. Prefer the requested
 * language when it is supported, otherwise the untagged/default mix, then any other playable track.
 */
object PlayerAudioSelection {
    fun pickPlayable(
        options: List<AudioTrackOption>,
        preferredLanguage: String,
    ): AudioTrackOption? {
        val playable = options.filter { it.supported }
        if (playable.isEmpty()) return null
        val best = playable.maxWithOrNull(
            compareBy<AudioTrackOption> { languageRank(it.language, preferredLanguage) }
                .thenBy { it.isDefault }
                .thenBy { it.channelCount },
        ) ?: return null
        val current = playable.firstOrNull { it.selected }
        if (current != null &&
            languageRank(current.language, preferredLanguage) >= languageRank(best.language, preferredLanguage)
        ) {
            return current
        }
        return best
    }

    internal fun languageRank(language: String?, preferredLanguage: String): Int {
        if (PlayerLanguages.matches(language, preferredLanguage)) return RANK_PREFERRED
        val normalized = PlayerLanguages.normalize(language)
        if (normalized == null || normalized == PlayerLanguages.UNKNOWN) return RANK_UNDETERMINED
        return RANK_OTHER
    }

    private const val RANK_PREFERRED = 3
    private const val RANK_UNDETERMINED = 2
    private const val RANK_OTHER = 1
}
