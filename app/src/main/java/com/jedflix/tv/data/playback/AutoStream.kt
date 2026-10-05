package com.jedflix.tv.data.playback

import com.jedflix.tv.data.comet.StreamOption

/** Keeps Comet's best-first order for Play and every fallback, regardless of browse quality. */
object AutoStream {
    fun pick(options: List<StreamOption>): StreamOption? = ranked(options).firstOrNull()

    fun ranked(options: List<StreamOption>): List<StreamOption> = options

    /** Streams after [failedId] in [ranked] order; the full list if that id is not present. */
    fun afterFailure(ranked: List<StreamOption>, failedId: String): List<StreamOption> {
        val index = ranked.indexOfFirst { it.id == failedId }
        return if (index < 0) ranked else ranked.drop(index + 1)
    }
}
