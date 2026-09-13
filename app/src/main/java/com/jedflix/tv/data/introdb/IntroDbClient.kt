package com.jedflix.tv.data.introdb

import com.jedflix.tv.data.playback.SkipKind
import com.jedflix.tv.data.playback.SkipSegment
import com.jedflix.tv.jedflixUserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Public IntroDB timestamps for recap / intro / outro. Reads need no key. */
class IntroDbClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val http: OkHttpClient = defaultClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    suspend fun segments(imdbId: String, season: Int, episode: Int): List<SkipSegment> =
        withContext(Dispatchers.IO) {
            val id = imdbId.trim()
            if (!id.startsWith("tt") || season <= 0 || episode <= 0) return@withContext emptyList()
            val url = baseUrl.trimEnd('/').toHttpUrl()
                .newBuilder()
                .addPathSegment("segments")
                .addQueryParameter("imdb_id", id)
                .addQueryParameter("season", season.toString())
                .addQueryParameter("episode", episode.toString())
                .build()
            val request = Request.Builder().url(url).get().build()
            val body = try {
                http.newCall(request).execute().use { response ->
                    if (response.code == 404) return@withContext emptyList()
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body?.string().orEmpty()
                }
            } catch (_: IOException) {
                return@withContext emptyList()
            }
            IntroDbSegments.parse(json, body)
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.introdb.app"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", jedflixUserAgent())
                        .header("Accept", "application/json")
                        .build(),
                )
            }
            .build()
    }
}

internal object IntroDbSegments {
    fun parse(json: Json, body: String): List<SkipSegment> {
        if (body.isBlank()) return emptyList()
        val dto = runCatching { json.decodeFromString(IntroDbResponse.serializer(), body) }.getOrNull()
            ?: return emptyList()
        return listOfNotNull(
            dto.recap.toSegment(SkipKind.Recap),
            dto.intro.toSegment(SkipKind.Intro),
            dto.outro.toSegment(SkipKind.Outro),
        )
    }
}

@Serializable
internal data class IntroDbResponse(
    val intro: IntroDbSegmentDto? = null,
    val recap: IntroDbSegmentDto? = null,
    val outro: IntroDbSegmentDto? = null,
)

@Serializable
internal data class IntroDbSegmentDto(
    @SerialName("start_ms") val startMs: Long? = null,
    @SerialName("end_ms") val endMs: Long? = null,
    @SerialName("start_sec") val startSec: Double? = null,
    @SerialName("end_sec") val endSec: Double? = null,
)

private fun IntroDbSegmentDto?.toSegment(kind: SkipKind): SkipSegment? {
    val dto = this ?: return null
    val start = dto.startMs ?: dto.startSec?.times(1_000.0)?.toLong() ?: return null
    val end = dto.endMs ?: dto.endSec?.times(1_000.0)?.toLong() ?: return null
    if (start < 0L || end <= start) return null
    return SkipSegment(kind = kind, startMs = start, endMs = end)
}
