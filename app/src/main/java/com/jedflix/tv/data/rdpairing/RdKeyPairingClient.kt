package com.jedflix.tv.data.rdpairing

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Opens a one-time Real-Debrid key slot and long-polls until the phone submits the key.
 *
 * No logging interceptor: URLs carry the pairing code and 200 bodies contain the API key.
 */
class RdKeyPairingClient(
    private val apiBase: String = API_BASE,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    suspend fun openSlot(code: String) {
        val request = Request.Builder()
            .url(urlFor(code))
            .put(ByteArray(0).toRequestBody(null))
            .build()
        client.newCall(request).await().use { response ->
            if (response.code != 201) throw RdKeyPairingException.OpenFailed
        }
    }

    suspend fun poll(code: String): RdKeyPollResult {
        val request = Request.Builder()
            .url(urlFor(code))
            .get()
            .build()
        return client.newCall(request).await().use { response ->
            when (response.code) {
                200 -> {
                    val body = response.body?.string().orEmpty()
                    val key = try {
                        json.decodeFromString(RdKeyPollDto.serializer(), body).apiKey.trim()
                    } catch (_: Exception) {
                        throw RdKeyPairingException.InvalidResponse
                    }
                    if (key.isEmpty()) throw RdKeyPairingException.InvalidResponse
                    RdKeyPollResult.Ready(key)
                }
                204 -> RdKeyPollResult.Waiting
                404 -> RdKeyPollResult.Expired
                else -> throw RdKeyPairingException.UnexpectedStatus
            }
        }
    }

    private fun urlFor(code: String) = "${apiBase.trimEnd('/')}/$code".toHttpUrl()

    private companion object {
        const val USER_AGENT = "JedFlix-TV/0.1"
    }
}

sealed interface RdKeyPollResult {
    data object Waiting : RdKeyPollResult
    data object Expired : RdKeyPollResult
    class Ready(val apiKey: String) : RdKeyPollResult {
        override fun toString(): String = "Ready"
    }
}

sealed class RdKeyPairingException : Exception() {
    data object OpenFailed : RdKeyPairingException()
    data object InvalidResponse : RdKeyPairingException()
    data object UnexpectedStatus : RdKeyPairingException()
}

@Serializable
private data class RdKeyPollDto(val apiKey: String)

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isCancelled) return
            cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { response.close() }
        }
    })
    cont.invokeOnCancellation { cancel() }
}
