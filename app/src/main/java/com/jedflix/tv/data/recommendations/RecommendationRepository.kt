package com.jedflix.tv.data.recommendations

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class CachedRecommendations(
    val profileId: Long,
    val receivedAt: Long,
    val signalFingerprint: Int,
    val response: RecommendationResponse,
    val contextKey: String = "",
) {
    fun contextMatches(now: Long, timeZone: String): Boolean =
        contextKey == RecommendationContext.key(now, timeZone)

    fun isFresh(now: Long, timeZone: String): Boolean =
        !legacyModel && contextMatches(now, timeZone) && now - receivedAt in 0 until refreshTtl &&
            (response.validUntil <= 0 || now < response.validUntil)

    /** Preserve cached taste shelves offline, but never resurrect an expired calendar theme. */
    fun forDisplay(now: Long, timeZone: String): CachedRecommendations =
        if (isFresh(now, timeZone)) this else copy(response = response.copy(
            shelves = response.shelves.filterNot { it.id.startsWith("dynamic-") },
        ))

    /** Refresh at local eligibility boundaries; failures and warmup retry at a bounded pace. */
    fun nextRefreshDelay(now: Long, timeZone: String): Long {
        if (!response.catalogReady || !isFresh(now, timeZone)) return 60_000L
        val zone = runCatching { ZoneId.of(timeZone) }.getOrElse { ZoneId.of("UTC") }
        val nextHour = Instant.ofEpochMilli(now).atZone(zone).truncatedTo(ChronoUnit.HOURS)
            .plusHours(1).toInstant().toEpochMilli()
        val deadline = minOf(nextHour, receivedAt + refreshTtl,
            response.validUntil.takeIf { it > 0 } ?: Long.MAX_VALUE)
        return (deadline - now).coerceAtLeast(1000L)
    }

    private val refreshTtl: Long get() = if (response.catalogReady) 6 * 60 * 60 * 1000L else 60_000L
    private val legacyModel: Boolean get() = response.model.startsWith("BAAI/bge-", ignoreCase = true) ||
        response.model == "bge-small"
    fun verifiedKeys(now: Long): Set<String> =
        if (now - receivedAt in 0..EVIDENCE_TTL_MS) response.eligibleKeys.toSet() else emptySet()
    fun ineligibleKeys(now: Long): Set<String> =
        if (now - receivedAt in 0..EVIDENCE_TTL_MS) response.evaluatedKeys.toSet() - response.eligibleKeys.toSet()
        else emptySet()
    companion object { const val EVIDENCE_TTL_MS = 24 * 60 * 60 * 1000L }
}

/** Hourly buckets also invalidate legacy caches and cover local midnight/daypart transitions. */
internal object RecommendationContext {
    fun key(now: Long, timeZone: String): String {
        val zone = runCatching { ZoneId.of(timeZone) }.getOrElse { ZoneId.of("UTC") }
        val local = Instant.ofEpochMilli(now).atZone(zone)
        return "v2|${zone.id}|${local.toLocalDate()}|${local.hour}|${local.offset}"
    }
}

/** Disk/network work stays off the TV's main thread; failures retain each profile's cache. */
class RecommendationRepository(
    private val cacheDirectory: File,
    private val fetch: suspend (RecommendationRequest) -> RecommendationResponse,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val locks = ConcurrentHashMap<Long, Mutex>()
    @Volatile private var retainedProfileIds: Set<Long>? = null

    suspend fun retainProfiles(profileIds: Set<Long>) = withContext(Dispatchers.IO) {
        retainedProfileIds = profileIds
        cacheDirectory.listFiles().orEmpty().forEach { entry ->
            val id = entry.name.removePrefix("profile-").removeSuffix(".tmp")
                .removeSuffix(".json").toLongOrNull()
            if (id != null && id !in profileIds) entry.delete()
        }
    }

    suspend fun cached(profileId: Long, timeZone: String = ZoneId.systemDefault().id): CachedRecommendations? = withContext(Dispatchers.IO) {
        if (retainedProfileIds?.contains(profileId) == false) null else read(profileId)?.forDisplay(now(), timeZone)
    }

    suspend fun refresh(profileId: Long, request: RecommendationRequest): CachedRecommendations? =
        withContext(Dispatchers.IO) {
            locks.getOrPut(profileId) { Mutex() }.withLock {
                if (retainedProfileIds?.contains(profileId) == false) return@withLock null
                val previous = read(profileId)
                val fingerprint = listOf(request.history, request.myList, request.feedback).hashCode()
                if (previous != null && previous.signalFingerprint == fingerprint &&
                    previous.isFresh(now(), request.timeZone)) return@withLock previous
                try {
                    val response = fetch(request)
                    currentCoroutineContext().ensureActive()
                    if (retainedProfileIds?.contains(profileId) == false) return@withLock null
                    val receivedAt = now()
                    val updated = CachedRecommendations(profileId, receivedAt, fingerprint, response,
                        RecommendationContext.key(receivedAt, request.timeZone))
                    cacheDirectory.mkdirs()
                    val target = file(profileId)
                    val temporary = File(cacheDirectory, "${target.name}.tmp")
                    temporary.writeText(json.encodeToString(CachedRecommendations.serializer(), updated))
                    check(temporary.renameTo(target)) { "Could not persist recommendations" }
                    updated.forDisplay(receivedAt, request.timeZone)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    previous?.forDisplay(now(), request.timeZone)
                }
            }
        }

    private fun read(profileId: Long): CachedRecommendations? = runCatching {
        json.decodeFromString(CachedRecommendations.serializer(), file(profileId).readText())
            .takeIf { it.profileId == profileId }
    }.getOrNull()
    private fun file(profileId: Long) = File(cacheDirectory, "profile-$profileId.json")

    companion object {
        fun create(cacheDirectory: File, apiBaseUrl: String): RecommendationRepository {
            val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
            // Deliberately separate from TMDB/RD clients: no credentials leave the device.
            val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS).callTimeout(65, TimeUnit.SECONDS).build()
            val endpoint = "${apiBaseUrl.trimEnd('/')}/recommendations"
            return RecommendationRepository(cacheDirectory, fetch = { payload ->
                val body = json.encodeToString(RecommendationRequest.serializer(), payload)
                    .toRequestBody("application/json".toMediaType())
                suspendCancellableCoroutine { continuation ->
                    val call = client.newCall(Request.Builder().url(endpoint).post(body).build())
                    continuation.invokeOnCancellation { call.cancel() }
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, error: IOException) {
                            continuation.resumeWithException(error)
                        }
                        override fun onResponse(call: Call, response: Response) {
                            try {
                                val decoded = response.use { result ->
                                    check(result.isSuccessful) { "Recommendations unavailable: ${result.code}" }
                                    json.decodeFromString(RecommendationResponse.serializer(), checkNotNull(result.body).string())
                                }
                                continuation.resume(decoded)
                            } catch (error: Exception) {
                                continuation.resumeWithException(error)
                            }
                        }
                    })
                }
            })
        }
    }
}
