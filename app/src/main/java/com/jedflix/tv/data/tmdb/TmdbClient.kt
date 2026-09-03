package com.jedflix.tv.data.tmdb

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class MissingTmdbKeyException : IllegalStateException("TMDB_API_KEY is not configured")

class TmdbClient(private val apiKey: String, debug: Boolean) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor())
        .apply {
            if (debug) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    val api: TmdbApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(TmdbApi::class.java)

    private fun authInterceptor() = Interceptor { chain ->
        val original = chain.request()
        // Image CDN requests share this client; only API calls need the key.
        if (original.url.host != API_HOST) return@Interceptor chain.proceed(original)
        if (apiKey.isBlank()) throw MissingTmdbKeyException()
        val url = original.url.newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("language", "en-US")
            .build()
        chain.proceed(original.newBuilder().url(url).build())
    }

    companion object {
        private const val API_HOST = "api.themoviedb.org"
        private const val BASE_URL = "https://$API_HOST/3/"
    }
}
