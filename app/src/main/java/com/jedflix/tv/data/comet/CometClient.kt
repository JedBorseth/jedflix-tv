package com.jedflix.tv.data.comet

import android.util.Base64
import com.jedflix.tv.data.tmdb.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the hosted Comet addon. The Real-Debrid key is embedded in the request path (Stremio
 * addon convention) so Comet can both check cache status and generate the download link for us.
 *
 * No logging interceptor is attached: every URL carries the encoded key.
 */
class CometClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val streamsClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    // Redirects are followed by hand so we can stop at the Real-Debrid hop without fetching video bytes.
    private val resolveClient: OkHttpClient = streamsClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun fetchStreams(
        apiKey: String,
        mediaType: MediaType,
        imdbId: String,
        season: Int? = null,
        episode: Int? = null,
    ): List<StreamOption> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw StreamException.MissingKey()
        val config = encodeConfig(apiKey)
        val mediaId = buildString {
            append(imdbId)
            if (mediaType == MediaType.TV && season != null && episode != null) {
                append(':').append(season).append(':').append(episode)
            }
        }
        val type = if (mediaType == MediaType.MOVIE) "movie" else "series"
        val url = "$baseUrl$config/stream/$type/$mediaId.json"

        var response = requestStreams(url)
        if (response.streams.any { it.isScrapingNotice() } && response.streams.none { it.isPlayable() }) {
            delay(SCRAPE_RETRY_DELAY_MS)
            response = requestStreams(url)
        }

        val options = response.streams.mapNotNull { it.toStreamOption() }
        if (options.isEmpty()) {
            val notice = response.streams.firstOrNull { it.isDebridErrorNotice() }
            if (notice != null) {
                throw StreamException.DebridError(notice.description?.replace('\n', ' ')?.trim().orEmpty())
            }
            throw StreamException.NoStreams()
        }
        options
    }

    /**
     * Follows Comet's playback redirect chain and returns the Real-Debrid download URL. Comet answers
     * failures with a small status MP4 instead of an HTTP error, so anything that does not land on a
     * Real-Debrid host is treated as a failure.
     */
    suspend fun resolvePlaybackUrl(playbackUrl: String): String = withContext(Dispatchers.IO) {
        var current = playbackUrl.toHttpUrlOrNull()
            ?: throw StreamException.ResolveFailed("Invalid playback URL")
        repeat(MAX_REDIRECTS) {
            if (current.isRealDebridHost()) return@withContext current.toString()
            val request = Request.Builder().url(current).get().build()
            val response = try {
                resolveClient.newCall(request).execute()
            } catch (e: IOException) {
                throw StreamException.Network(e)
            }
            response.use { res ->
                val location = res.header("Location")
                when {
                    res.isRedirect && location != null -> {
                        current = current.resolve(location)
                            ?: throw StreamException.ResolveFailed("Bad redirect from Comet")
                    }
                    res.isSuccessful -> {
                        // 200 from Comet itself is the status video; there is no RD link to play.
                        throw StreamException.ResolveFailed(
                            "Comet couldn't generate a Real-Debrid link for this stream",
                        )
                    }
                    else -> throw StreamException.ResolveFailed("Comet returned HTTP ${res.code}")
                }
            }
        }
        throw StreamException.ResolveFailed("Too many redirects")
    }

    private fun requestStreams(url: String): CometStreamsResponse {
        val request = Request.Builder().url(url).get().build()
        val body = try {
            streamsClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw StreamException.Network(IOException("HTTP ${response.code}"))
                response.body?.string() ?: ""
            }
        } catch (e: IOException) {
            throw StreamException.Network(e)
        }
        return try {
            json.decodeFromString(CometStreamsResponse.serializer(), body)
        } catch (e: Exception) {
            throw StreamException.Network(e)
        }
    }

    private fun encodeConfig(apiKey: String): String {
        val config = CometConfigDto(
            debridServices = listOf(CometDebridServiceDto(service = "realdebrid", apiKey = apiKey)),
            cachedOnly = true,
            enableTorrent = false,
            removeTrash = true,
            deduplicateStreams = true,
            maxResultsPerResolution = MAX_RESULTS_PER_RESOLUTION,
        )
        val payload = json.encodeToString(CometConfigDto.serializer(), config)
        return Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun CometStreamDto.isScrapingNotice(): Boolean =
        isNotice() &&
            (name?.contains("🔄") == true || description?.contains("Scraping in progress", ignoreCase = true) == true)

    private fun CometStreamDto.isDebridErrorNotice(): Boolean =
        isNotice() && !isScrapingNotice() && name?.contains("❌") == true

    private fun HttpUrl.isRealDebridHost(): Boolean =
        REAL_DEBRID_HOST_SUFFIXES.any { suffix -> host == suffix || host.endsWith(".$suffix") }

    companion object {
        const val DEFAULT_BASE_URL = "https://comet.elfhosted.com/"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) JedFlix/0.1"
        private const val MAX_RESULTS_PER_RESOLUTION = 5
        private const val MAX_REDIRECTS = 6
        private const val SCRAPE_RETRY_DELAY_MS = 4_000L
        private val REAL_DEBRID_HOST_SUFFIXES = listOf("real-debrid.com", "rdeb.io", "real-debrid.cloud")
    }
}
