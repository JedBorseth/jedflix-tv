package com.jedflix.tv.data.comet

/** Failure categories surfaced by the stream picker; each maps to distinct UI copy. */
sealed class StreamException(message: String) : Exception(message) {
    /** No Real-Debrid key saved in Settings. */
    class MissingKey : StreamException("Real-Debrid API key is not configured")

    /** Title has no IMDb id, so Comet cannot look it up. */
    class NoImdbId : StreamException("Title has no IMDb id")

    /** Comet returned no cached streams for this title. */
    class NoStreams : StreamException("No streams found")

    /** Comet relayed a debrid-side error (bad key, account expired, ...). */
    class DebridError(detail: String) : StreamException(detail)

    /** Comet playback URL did not redirect to a Real-Debrid download. */
    class ResolveFailed(detail: String) : StreamException(detail)

    /** Transport failure talking to Comet. */
    class Network(cause: Throwable?) : StreamException(cause?.message ?: "Network error")
}
