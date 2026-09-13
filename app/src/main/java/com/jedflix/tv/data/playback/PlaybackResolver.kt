package com.jedflix.tv.data.playback

import com.jedflix.tv.data.comet.CometClient
import com.jedflix.tv.data.comet.StreamException
import com.jedflix.tv.data.comet.StreamOption
import com.jedflix.tv.data.library.UserLibraryRepository
import com.jedflix.tv.data.settings.SettingsStore
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import com.jedflix.tv.data.tmdb.TmdbRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Resolves a Comet stream into [PlaybackSession] without showing the picker. */
class PlaybackResolver(
    private val tmdb: TmdbRepository,
    private val comet: CometClient,
    private val settingsStore: SettingsStore,
    private val library: UserLibraryRepository,
    private val session: PlaybackSession,
) {
    suspend fun autoStart(
        mediaType: MediaType,
        mediaId: Int,
        season: Int?,
        episode: Int?,
    ) {
        val details = tmdb.loadDetails(mediaType, mediaId)
        val episodeTitle = episodeTitle(mediaId, season, episode)
        val apiKey = settingsStore.realDebridApiKey.first()
        if (apiKey.isBlank()) throw StreamException.MissingKey()
        val imdbId = details.imdbId ?: throw StreamException.NoImdbId()
        val options = comet.fetchStreams(
            apiKey,
            mediaType,
            imdbId,
            season,
            episode,
            episodeTitle,
        )
        val profile = settingsStore.qualityProfile.first()
        val ranked = AutoStream.ranked(options, profile, mediaType)
        if (ranked.isEmpty()) throw StreamException.NoStreams()
        var lastError: StreamException = StreamException.NoStreams()
        for ((index, option) in ranked.withIndex()) {
            try {
                start(
                    title = details.title,
                    imdbId = imdbId,
                    season = season,
                    episode = episode,
                    episodeTitle = episodeTitle,
                    option = option,
                    fallbacks = ranked.drop(index + 1),
                )
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: StreamException) {
                lastError = e
            } catch (e: Exception) {
                lastError = StreamException.Network(e)
            }
        }
        throw lastError
    }

    suspend fun start(
        title: MediaTitle,
        imdbId: String?,
        season: Int?,
        episode: Int?,
        episodeTitle: String?,
        option: StreamOption,
        fallbacks: List<StreamOption>,
    ) {
        val url = comet.resolvePlaybackUrl(option.playbackUrl)
        val startPositionMs = library.playbackPosition(title.mediaType, title.id, season, episode)
        session.start(
            PlaybackItem(
                streamUrl = url,
                title = title.title,
                subtitle = subtitle(title, season, episode, episodeTitle),
                mediaType = title.mediaType,
                tmdbId = title.id,
                season = season,
                episode = episode,
                overview = title.overview,
                posterUrl = title.posterUrl,
                backdropUrl = title.backdropUrl,
                year = title.year,
                rating = title.rating,
                genres = title.genres,
                startPositionMs = startPositionMs,
                imdbId = imdbId,
                resolution = option.resolution,
                fallbacks = fallbacks,
            ),
        )
    }

    private suspend fun episodeTitle(mediaId: Int, season: Int?, episode: Int?): String? {
        if (season == null || episode == null) return null
        return runCatching { tmdb.loadSeasonEpisodes(mediaId, season) }
            .getOrNull()
            ?.firstOrNull { it.episodeNumber == episode }
            ?.title
    }

    private fun subtitle(
        title: MediaTitle,
        season: Int?,
        episode: Int?,
        episodeTitle: String?,
    ): String? = when {
        season != null && episode != null -> NextEpisode.episodeSubtitle(season, episode, episodeTitle)
        else -> title.year
    }
}
