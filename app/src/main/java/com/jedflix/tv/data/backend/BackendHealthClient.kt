package com.jedflix.tv.data.backend

import com.jedflix.tv.jedflixUserAgent
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Talks to the JedFlix TV API. One call, one result; never throws to callers —
 * any transport failure is reported as [BackendHealthState.Offline].
 */
class BackendHealthClient(private val baseUrl: String) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", jedflixUserAgent())
                    .header("Accept", "application/json")
                    .build(),
            )
        }
        .build()

    suspend fun check(): BackendHealthState {
        val url = BackendHealth.healthUrl(baseUrl)?.toHttpUrlOrNull()
            ?: return BackendHealthState.Offline
        val request = Request.Builder().url(url).get().build()
        return try {
            client.newCall(request).await().use { response ->
                BackendHealth.parse(response.code, response.body?.string())
            }
        } catch (_: IOException) {
            BackendHealthState.Offline
        }
    }
}

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
