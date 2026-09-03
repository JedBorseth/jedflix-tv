package com.jedflix.tv.ui.streams

import com.jedflix.tv.data.comet.StreamOption
import com.jedflix.tv.data.tmdb.MediaTitle

/** Which title/episode the picker is resolving for. */
data class StreamTarget(
    val title: MediaTitle,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
) {
    val isEpisode: Boolean get() = season != null && episode != null

    /** "S1 E4 · Episode name" for shows, the release year for movies. */
    val subtitle: String?
        get() = when {
            isEpisode -> buildString {
                append("S").append(season).append(" E").append(episode)
                episodeTitle?.takeIf { it.isNotBlank() }?.let { append("  •  ").append(it) }
            }
            else -> title.year
        }
}

enum class StreamErrorKind {
    /** No Real-Debrid key saved; offer a Settings shortcut. */
    MISSING_KEY,
    /** TMDB has no IMDb mapping for this title. */
    NO_IMDB,
    /** Comet found nothing cached on Real-Debrid. */
    NO_STREAMS,
    /** Real-Debrid rejected the key or account (message from Comet). */
    DEBRID,
    /** Couldn't reach Comet. */
    NETWORK,
}

sealed interface StreamPickerUiState {
    data class Loading(val target: StreamTarget?) : StreamPickerUiState

    data class Error(
        val target: StreamTarget?,
        val kind: StreamErrorKind,
        val detail: String? = null,
    ) : StreamPickerUiState

    data class Ready(
        val target: StreamTarget,
        val options: List<StreamOption>,
        /** Stream currently being turned into a Real-Debrid link, if any. */
        val resolving: StreamOption? = null,
        /** Last resolve failure, shown inline until the user picks again. */
        val resolveError: String? = null,
    ) : StreamPickerUiState
}
