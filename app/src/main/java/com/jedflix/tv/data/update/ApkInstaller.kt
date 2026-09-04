package com.jedflix.tv.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

sealed interface InstallStart {
    data object Started : InstallStart
    data object StartedLegacy : InstallStart
    data object NeedsUnknownSources : InstallStart
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

    fun start(apk: File): InstallStart {
        if (!apk.exists() || apk.length() == 0L) {
            throw IOException("Downloaded APK is missing")
        }
        if (!canRequestInstalls()) return InstallStart.NeedsUnknownSources
        return try {
            startSession(apk)
            InstallStart.Started
        } catch (_: Exception) {
            startLegacy(apk)
            InstallStart.StartedLegacy
        }
    }

    private fun startSession(apk: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(app.packageName)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            session.openWrite("package", 0, apk.length()).use { out ->
                apk.inputStream().buffered().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            val pending = PendingIntent.getBroadcast(
                app,
                sessionId,
                Intent(app, InstallStatusReceiver::class.java).setAction(ACTION_INSTALL_STATUS),
                pendingFlags(),
            )
            session.commit(pending.intentSender)
        } catch (error: Exception) {
            try {
                session.abandon()
            } catch (_: Exception) {
                // Session may already be closed.
            }
            throw error
        }
    }

    private fun startLegacy(apk: File) {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(intent)
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.jedflix.tv.UPDATE_INSTALL_STATUS"

        private fun pendingFlags(): Int {
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            return PendingIntent.FLAG_UPDATE_CURRENT or mutable
        }
    }
}
