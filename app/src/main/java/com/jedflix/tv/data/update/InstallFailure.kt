package com.jedflix.tv.data.update

import java.security.MessageDigest

/**
 * PackageInstaller status ints (android.content.pm.PackageInstaller) so unit tests
 * can map failures without loading the Android framework.
 */
internal object PackageInstallStatus {
    const val PENDING_USER_ACTION = -1
    const val SUCCESS = 0
    const val FAILURE = 1
    const val FAILURE_ABORTED = 3
    const val FAILURE_CONFLICT = 5
    const val FAILURE_INCOMPATIBLE = 7
}

enum class InstallFailureReason {
    Generic,
    SignatureMismatch,
}

fun installFailureReason(status: Int, statusMessage: String?): InstallFailureReason {
    if (
        status == PackageInstallStatus.FAILURE_CONFLICT ||
        status == PackageInstallStatus.FAILURE_INCOMPATIBLE
    ) {
        return InstallFailureReason.SignatureMismatch
    }
    val message = statusMessage.orEmpty()
    if (
        message.contains("UPDATE_INCOMPATIBLE", ignoreCase = true) ||
        message.contains("signatures do not match", ignoreCase = true)
    ) {
        return InstallFailureReason.SignatureMismatch
    }
    return InstallFailureReason.Generic
}

fun installProgressForStatus(status: Int, statusMessage: String?): InstallProgress? {
    return when (status) {
        PackageInstallStatus.PENDING_USER_ACTION -> null
        PackageInstallStatus.SUCCESS,
        PackageInstallStatus.FAILURE_ABORTED,
        -> InstallProgress.Idle
        else -> InstallProgress.Failed(installFailureReason(status, statusMessage))
    }
}

fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.toHexLower()
}

internal fun ByteArray.toHexLower(): String {
    return joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}
