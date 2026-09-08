package com.jedflix.tv.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

sealed interface InstallStart {
    data object StartedLegacy : InstallStart
    data object NeedsUnknownSources : InstallStart
    data object SignatureMismatch : InstallStart
}

class ApkInstaller(context: Context) {
    private val app = context.applicationContext

    fun canRequestInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun unknownSourcesIntent(): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${app.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun canReplaceInstalledPackage(): Boolean {
        return try {
            val installed = installedSigningCertSha256() ?: return true
            installed.equals(UPLOAD_CERT_SHA256, ignoreCase = true)
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Drop leftover PackageInstaller sessions. A committed self-update session can leave
     * [com.jedflix.tv] disabled or make the TV resume a trampoline activity for every
     * install of this package name, including older APKs.
     */
    fun abandonStaleSessions() {
        val installer = app.packageManager.packageInstaller
        for (session in installer.mySessions) {
            runCatching { installer.abandonSession(session.sessionId) }
        }
    }

    fun start(apk: File): InstallStart {
        if (!apk.exists() || apk.length() == 0L) {
            throw IOException("Downloaded APK is missing")
        }
        if (!canReplaceInstalledPackage()) return InstallStart.SignatureMismatch
        if (!canRequestInstalls()) return InstallStart.NeedsUnknownSources
        abandonStaleSessions()
        startLegacy(apk)
        return InstallStart.StartedLegacy
    }

    private fun startLegacy(apk: File) {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolveFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager.MATCH_DEFAULT_ONLY
        } else {
            0
        }
        val targets = app.packageManager.queryIntentActivities(intent, resolveFlags)
        for (resolve in targets) {
            app.grantUriPermission(
                resolve.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        app.startActivity(intent)
    }

    private fun installedSigningCertSha256(): String? {
        val packageManager = app.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = packageManager.getPackageInfo(
                app.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            val info = packageManager.getPackageInfo(
                app.packageName,
                PackageManager.GET_SIGNATURES,
            )
            info.signatures
        }
        val cert = signatures?.firstOrNull()?.toByteArray() ?: return null
        return sha256Hex(cert)
    }

    companion object {
        /** SHA-256 of the committed upload cert (matches GitHub v0.3.1+). */
        const val UPLOAD_CERT_SHA256 = "6ebef2bcf3a0ce87323895a9720e3f0dc84e5369868abf0405a0d4f09e24486b"
    }
}
