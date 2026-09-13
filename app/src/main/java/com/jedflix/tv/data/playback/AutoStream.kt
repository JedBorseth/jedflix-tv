package com.jedflix.tv.data.playback

import com.jedflix.tv.data.comet.StreamOption
import com.jedflix.tv.data.settings.QualityProfile
import com.jedflix.tv.data.tmdb.MediaType

/** Picks a Comet stream from already-sorted results using the quality profile. Top of the list is better. */
object AutoStream {
    fun pick(
        options: List<StreamOption>,
        profile: QualityProfile,
        mediaType: MediaType,
    ): StreamOption? = ranked(options, profile, mediaType).firstOrNull()

    fun ranked(
        options: List<StreamOption>,
        profile: QualityProfile,
        mediaType: MediaType,
    ): List<StreamOption> {
        if (options.isEmpty()) return emptyList()
        val matching = options.filter { matches(it, profile, mediaType) }
        return matching.ifEmpty { options }
    }

    /** Streams after [failedId] in [ranked] order; the full list if that id is not present. */
    fun afterFailure(ranked: List<StreamOption>, failedId: String): List<StreamOption> {
        val index = ranked.indexOfFirst { it.id == failedId }
        return if (index < 0) ranked else ranked.drop(index + 1)
    }

    private fun matches(option: StreamOption, profile: QualityProfile, mediaType: MediaType): Boolean {
        val height = resolutionHeight(option.resolution) ?: return profile == QualityProfile.Max
        return when (profile) {
            QualityProfile.Max -> true
            QualityProfile.Medium -> {
                val size = option.sizeBytes ?: return false
                height >= 1080 && size < mediumCapBytes(mediaType)
            }
            QualityProfile.Low -> height == 720
        }
    }

    private fun mediumCapBytes(mediaType: MediaType): Long = when (mediaType) {
        MediaType.MOVIE -> 30L * GIB
        MediaType.TV -> 1L * GIB
    }

    private fun resolutionHeight(token: String): Int? {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return null
        val upper = trimmed.uppercase()
        if (upper == "4K" || upper == "UHD") return 2160
        val digits = upper.filter { it.isDigit() }
        return digits.toIntOrNull()?.takeIf { it in 144..4320 }
    }

    private const val GIB = 1_073_741_824L
}
