package com.jedflix.tv.data.playback

import com.jedflix.tv.data.comet.StreamOption

object NextStream {
    /**
     * First cached stream at the same resolution as the file that just played.
     * Anything else (wrong resolution, uncached-only) is a picker fallback.
     */
    fun pickCachedAtResolution(options: List<StreamOption>, resolution: String): StreamOption? {
        val wanted = resolution.trim()
        if (wanted.isEmpty()) return null
        return options.firstOrNull { option ->
            option.cached && option.resolution.equals(wanted, ignoreCase = true)
        }
    }
}
