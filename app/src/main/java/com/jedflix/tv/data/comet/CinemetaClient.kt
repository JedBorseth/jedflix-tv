package com.jedflix.tv.data.comet

import com.jedflix.tv.jedflixUserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Stremio Cinemeta catalog: episode numbers here match the SxxExx Comet scrapes for. */
class CinemetaClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", jedflixUserAgent()).build())
        }
        .build()

    private val cache = ConcurrentHashMap<String, List<CinemetaEpisode>>()

    suspend fun episodes(imdbId: String): List<CinemetaEpisode> = withContext(Dispatchers.IO) {
        cache[imdbId]?.let { return@withContext it }
        val request = Request.Builder().url("$baseUrl$imdbId.json").get().build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        val parsed = json.decodeFromString(CinemetaMetaResponse.serializer(), body)
            .meta.videos.mapNotNull { it.toEpisode() }
        cache[imdbId] = parsed
        parsed
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://v3-cinemeta.strem.io/meta/series/"
    }
}

@Serializable
internal data class CinemetaMetaResponse(
    val meta: CinemetaMetaDto = CinemetaMetaDto(),
)

@Serializable
internal data class CinemetaMetaDto(
    val videos: List<CinemetaVideoDto> = emptyList(),
)

@Serializable
internal data class CinemetaVideoDto(
    val season: Int = 0,
    val episode: Int = 0,
    val name: String = "",
)

private fun CinemetaVideoDto.toEpisode(): CinemetaEpisode? {
    if (season <= 0 || episode <= 0 || name.isBlank()) return null
    return CinemetaEpisode(season, episode, name)
}
