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
) {
    fun verifiedKeys(now: Long): Set<String> =
        if (now - receivedAt in 0..EVIDENCE_TTL_MS) response.eligibleKeys.toSet() else emptySet()
    fun ineligibleKeys(now: Long): Set<String> =
        if (now - receivedAt in 0..EVIDENCE_TTL_MS) response.evaluatedKeys.toSet() - response.eligibleKeys.toSet()
        else emptySet()
    companion object { const val EVIDENCE_TTL_MS = 24 * 60 * 60 * 1000L }
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

    suspend fun cached(profileId: Long): CachedRecommendations? = withContext(Dispatchers.IO) {
        if (retainedProfileIds?.contains(profileId) == false) null else read(profileId)
    }

    suspend fun refresh(profileId: Long, request: RecommendationRequest): CachedRecommendations? =
        withContext(Dispatchers.IO) {
            locks.getOrPut(profileId) { Mutex() }.withLock {
                if (retainedProfileIds?.contains(profileId) == false) return@withLock null
                val previous = read(profileId)
                val fingerprint = listOf(request.history, request.myList, request.feedback).hashCode()
                if (previous != null && previous.signalFingerprint == fingerprint &&
                    now() - previous.receivedAt in 0 until REFRESH_TTL_MS) return@withLock previous
                try {
                    val response = fetch(request)
                    currentCoroutineContext().ensureActive()
                    if (retainedProfileIds?.contains(profileId) == false) return@withLock null
                    val updated = CachedRecommendations(profileId, now(), fingerprint, response)
                    cacheDirectory.mkdirs()
                    val target = file(profileId)
                    val temporary = File(cacheDirectory, "${target.name}.tmp")
                    temporary.writeText(json.encodeToString(CachedRecommendations.serializer(), updated))
                    check(temporary.renameTo(target)) { "Could not persist recommendations" }
                    updated
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    previous
                }
            }
        }

    private fun read(profileId: Long): CachedRecommendations? = runCatching {
        json.decodeFromString(CachedRecommendations.serializer(), file(profileId).readText())
            .takeIf { it.profileId == profileId }
    }.getOrNull()
    private fun file(profileId: Long) = File(cacheDirectory, "profile-$profileId.json")

    companion object {
        private const val REFRESH_TTL_MS = 6 * 60 * 60 * 1000L
        fun create(cacheDirectory: File, apiBaseUrl: String): RecommendationRepository {
            val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
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
