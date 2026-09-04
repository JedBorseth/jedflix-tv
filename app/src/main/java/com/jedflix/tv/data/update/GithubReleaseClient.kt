package com.jedflix.tv.data.update

import com.jedflix.tv.jedflixUserAgent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GithubRelease(
    val tagName: String,
    val name: String?,
    val body: String?,
    val apk: GithubApkAsset?,
)

data class GithubApkAsset(
    val name: String,
    val url: String,
    val size: Long,
)

class GithubReleaseClient(
    private val owner: String = DEFAULT_OWNER,
    private val repo: String = DEFAULT_REPO,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", jedflixUserAgent())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .build()
            chain.proceed(request)
        }
        .build()

    /**
     * Latest stable GitHub release, or null when the repo has no non-prerelease.
     */
    suspend fun fetchLatest(): GithubRelease? {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest".toHttpUrl()
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).await().use { response ->
            when (response.code) {
                404 -> null
                in 200..299 -> {
                    val body = response.body?.string().orEmpty()
                    val dto = try {
                        json.decodeFromString(GithubReleaseDto.serializer(), body)
                    } catch (_: Exception) {
                        throw GithubReleaseException.InvalidResponse
                    }
                    dto.toRelease()
                }
                else -> throw GithubReleaseException.UnexpectedStatus
            }
        }
    }

    private fun GithubReleaseDto.toRelease(): GithubRelease {
        val apk = pickApkAsset(assets)
        return GithubRelease(
            tagName = tagName,
            name = name,
            body = body,
            apk = apk?.let {
                GithubApkAsset(
                    name = it.name,
                    url = it.browserDownloadUrl,
                    size = it.size,
                )
            },
        )
    }

    private companion object {
        const val DEFAULT_OWNER = "JedBorseth"
        const val DEFAULT_REPO = "jedflix-tv"
        const val GITHUB_API_VERSION = "2022-11-28"
    }
}

sealed class GithubReleaseException : Exception() {
    data object InvalidResponse : GithubReleaseException()
    data object UnexpectedStatus : GithubReleaseException()
}

@Serializable
internal data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GithubAssetDto> = emptyList(),
)

@Serializable
internal data class GithubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0,
)

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
