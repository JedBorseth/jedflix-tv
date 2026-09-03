package com.jedflix.tv.data.update

data class AvailableUpdate(
    val tag: String,
    val versionLabel: String,
    val notes: String,
    val htmlUrl: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
)

sealed interface InstallProgress {
    data object Idle : InstallProgress
    data object NeedsUnknownSources : InstallProgress
    data class Downloading(val bytesRead: Long, val totalBytes: Long) : InstallProgress
    data object Installing : InstallProgress
    data object Failed : InstallProgress
}

data class AppUpdateState(
    val currentVersion: String,
    val checking: Boolean = false,
    val checkFailed: Boolean = false,
    val available: AvailableUpdate? = null,
    val install: InstallProgress = InstallProgress.Idle,
    val showLaunchPrompt: Boolean = false,
)
