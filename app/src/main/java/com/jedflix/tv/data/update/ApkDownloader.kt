package com.jedflix.tv.data.update

import android.content.Context
import com.jedflix.tv.jedflixUserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class ApkDownloader(context: Context) {
    private val cacheDir = File(context.applicationContext.cacheDir, "updates")

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(0)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", jedflixUserAgent())
                    .build(),
            )
        }
        .build()

    fun apkFile(): File = File(cacheDir, APK_NAME)

    suspend fun download(
        url: String,
        onProgress: suspend (bytesRead: Long, totalBytes: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        cacheDir.mkdirs()
        val dest = apkFile()
        val partial = File(cacheDir, "$APK_NAME.part")
        if (partial.exists()) partial.delete()
        if (dest.exists()) dest.delete()

        val request = Request.Builder().url(url).get().build()
        val call = client.newCall(request)
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed (${response.code})")
                }
                val body = response.body ?: throw IOException("Empty download")
                val total = body.contentLength()
                partial.outputStream().buffered().use { out ->
                    body.byteStream().buffered().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            read += n
                            onProgress(read, total)
                        }
                        out.flush()
                    }
                }
            }
        } finally {
            cancelHandle?.dispose()
        }
        if (!partial.renameTo(dest)) {
            partial.copyTo(dest, overwrite = true)
            partial.delete()
        }
        dest
    }

    fun clear() {
        apkFile().delete()
        File(cacheDir, "$APK_NAME.part").delete()
    }

    private companion object {
        const val APK_NAME = "jedflix-update.apk"
    }
}
