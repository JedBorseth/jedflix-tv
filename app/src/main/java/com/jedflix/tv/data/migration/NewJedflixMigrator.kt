package com.jedflix.tv.data.migration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class NewJedflixInstallState { ABSENT, JEDFLIX, CONFLICT }

/**
 * Moves this install to the new Jedflix app. The new app is a separate package, so it is
 * installed alongside this one; Android asks the user to confirm. Every file is checked
 * against the published manifest and the pinned signing certificate before it reaches
 * the installer.
 */
class NewJedflixMigrator(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val updates = File(context.cacheDir, "updates")

    fun installedState(): NewJedflixInstallState {
        val info = try {
            context.packageManager.getPackageInfo(NewJedflix.PACKAGE, signingFlags())
        } catch (_: PackageManager.NameNotFoundException) {
            return NewJedflixInstallState.ABSENT
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        return if (code >= NewJedflix.MIN_VERSION_CODE && hasJedflixSignature(info)) {
            NewJedflixInstallState.JEDFLIX
        } else {
            NewJedflixInstallState.CONFLICT
        }
    }

    fun isInstalled(): Boolean = installedState() == NewJedflixInstallState.JEDFLIX

    fun launchIntent(): Intent? = if (isInstalled()) {
        context.packageManager.getLaunchIntentForPackage(NewJedflix.PACKAGE)
    } else {
        null
    }

    fun conflictSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${NewJedflix.PACKAGE}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun fetchArtifact(): NewJedflixArtifact = withContext(Dispatchers.IO) {
        val (manifestUrl, tag) = NewJedflixRelease.manifestSource(get(NewJedflix.LATEST_RELEASE_API))
        NewJedflixManifest.parse(get(manifestUrl), tag, Build.SUPPORTED_ABIS, Build.VERSION.SDK_INT)
    }

    /** Downloads and verifies the APK. Returns a file that is safe to hand to the installer. */
    suspend fun download(artifact: NewJedflixArtifact, onProgress: (Long, Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            updates.mkdirs()
            updates.listFiles()?.forEach { it.delete() }
            val partial = File(updates, "${artifact.sha256}.part")
            val target = File(updates, "new-jedflix-${artifact.versionName}-${artifact.abi}.apk")
            val request = Request.Builder().url(artifact.url).header("User-Agent", userAgent()).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw MigrationException("Download failed (HTTP ${response.code}).")
                val body = response.body ?: throw MigrationException("Download failed.")
                partial.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(65536)
                        var read = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            read += count
                            if (read > artifact.size) throw MigrationException("Download is larger than expected.")
                            output.write(buffer, 0, count)
                            onProgress(read, artifact.size)
                        }
                    }
                }
            }
            if (partial.length() != artifact.size) {
                partial.delete()
                throw MigrationException("Download is incomplete. Try again.")
            }
            if (digest("SHA-256", partial) != artifact.sha256) {
                partial.delete()
                throw MigrationException("Download checksum failed. Try again.")
            }
            target.delete()
            if (!partial.renameTo(target)) throw MigrationException("Cannot save the downloaded update.")
            verifyPackage(target, artifact)
            target
        }

    /** Opens Android's installer for a file returned by [download]. */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        // Some TV installers need an explicit grant on the content URI.
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).forEach {
            context.grantUriPermission(it.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun verifyPackage(file: File, artifact: NewJedflixArtifact) {
        val info: PackageInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags())
            ?: throw MigrationException("The downloaded update is not a valid app.")
        if (info.packageName != NewJedflix.PACKAGE) throw MigrationException("The downloaded update is for a different app.")
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        if (code != artifact.versionCode || info.versionName != artifact.versionName) {
            throw MigrationException("The downloaded update does not match its release.")
        }
        if (!hasJedflixSignature(info)) {
            throw MigrationException("The new Jedflix is signed by an unexpected key.")
        }
        ZipFile(file).use { zip ->
            val native = zip.entries().asSequence().any {
                it.name.startsWith("lib/${artifact.abi}/") && it.name.endsWith(".so")
            }
            if (!native) throw MigrationException("The new Jedflix does not support this TV's processor.")
        }
    }

    private fun signingFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
    }

    private fun hasJedflixSignature(info: PackageInfo): Boolean {
        val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION") info.signatures.orEmpty()
        }
        return signers.isNotEmpty() && signers.all { sha256Hex(it.toByteArray()) == NewJedflix.SIGNING_CERT_SHA256 }
    }

    private fun get(url: String): String {
        if (!url.startsWith("https://")) throw MigrationException("Refusing an insecure update address.")
        val request = Request.Builder().url(url)
            .header("User-Agent", userAgent())
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) throw MigrationException("The new Jedflix has no published release yet.")
            if (response.code == 403 || response.code == 429) {
                throw MigrationException("GitHub is limiting requests. Try again later.")
            }
            if (!response.isSuccessful) throw MigrationException("Update check failed (HTTP ${response.code}).")
            val body = response.body ?: throw MigrationException("Update check failed.")
            val text = body.string()
            if (text.length > 262_144) throw MigrationException("Update metadata is too large.")
            return text
        }
    }

    private fun userAgent(): String = "Jedflix-Migration/${Build.VERSION.SDK_INT}"

    private fun sha256Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun digest(algorithm: String, file: File): String {
        val md = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                md.update(buffer, 0, count)
            }
        }
        return hex(md.digest())
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
