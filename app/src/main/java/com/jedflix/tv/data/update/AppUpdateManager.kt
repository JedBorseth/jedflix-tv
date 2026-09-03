package com.jedflix.tv.data.update

import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.jedflix.tv.data.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

class AppUpdateManager(
    private val store: SettingsStore,
    private val github: GithubReleaseClient,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val scope: CoroutineScope,
    private val currentVersion: String,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val _state = MutableStateFlow(AppUpdateState(currentVersion = currentVersion))
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val _pendingConfirm = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val pendingConfirm: SharedFlow<Intent> = _pendingConfirm.asSharedFlow()

    private val _openUnknownSources = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val openUnknownSources: SharedFlow<Intent> = _openUnknownSources.asSharedFlow()

    private var checkJob: Job? = null
    private var installJob: Job? = null

    fun start() {
        scope.launch {
            hydrateFromCache()
            check(force = false)
        }
    }

    fun check(force: Boolean) {
        if (checkJob?.isActive == true) return
        checkJob = scope.launch { runCheck(force) }
    }

    fun downloadAndInstall() {
        val update = _state.value.available ?: return
        if (update.apkUrl.isBlank()) {
            _state.update { it.copy(install = InstallProgress.Failed) }
            return
        }
        if (installJob?.isActive == true) return
        val progress = _state.value.install
        if (progress is InstallProgress.Downloading || progress is InstallProgress.Installing) return
        installJob = scope.launch { runDownloadAndInstall(update) }
    }

    fun cancelInstall() {
        installJob?.cancel()
        installJob = null
        downloader.clear()
        _state.update { it.copy(install = InstallProgress.Idle) }
    }

    fun dismissPrompt() {
        val tag = _state.value.available?.tag ?: return
        scope.launch { store.setDismissedUpdateTag(tag) }
        _state.update { it.copy(showLaunchPrompt = false) }
    }

    fun requestUnknownSourcesPermission() {
        _openUnknownSources.tryEmit(installer.unknownSourcesIntent())
    }

    fun retryInstallAfterPermission() {
        if (_state.value.install !is InstallProgress.NeedsUnknownSources) return
        if (!installer.canRequestInstalls()) return
        downloadAndInstall()
    }

    fun onInstallStatus(intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmIntent(intent) ?: return
                _pendingConfirm.tryEmit(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                downloader.clear()
                _state.update { it.copy(install = InstallProgress.Idle, showLaunchPrompt = false) }
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                _state.update { it.copy(install = InstallProgress.Idle) }
            }
            else -> {
                _state.update { it.copy(install = InstallProgress.Failed) }
            }
        }
    }

    private suspend fun hydrateFromCache() {
        val cache = store.loadCachedRelease()
        applyRelease(
            tag = cache.tag,
            notes = cache.notes,
            apkUrl = cache.apkUrl,
            apkName = cache.apkName,
            apkSize = cache.apkSize,
            dismissedTag = cache.dismissedTag,
            checkFailed = false,
        )
    }

    private suspend fun runCheck(force: Boolean) {
        val cache = store.loadCachedRelease()
        val now = clock()
        if (!force && cache.lastCheckAtMs > 0L && now - cache.lastCheckAtMs < CHECK_INTERVAL_MS) {
            return
        }
        _state.update { it.copy(checking = true, checkFailed = false) }
        try {
            val release = github.fetchLatest()
            store.setLastUpdateCheckAt(now)
            if (release == null) {
                store.clearCachedRelease()
                _state.update {
                    it.copy(
                        checking = false,
                        checkFailed = false,
                        available = null,
                        showLaunchPrompt = false,
                    )
                }
                return
            }
            val notes = summarizeReleaseNotes(release.body, release.name)
            val apk = release.apk
            store.setCachedRelease(
                tag = release.tagName,
                notes = notes,
                apkUrl = apk?.url.orEmpty(),
                apkName = apk?.name.orEmpty(),
                apkSize = apk?.size ?: 0L,
            )
            val dismissed = store.loadCachedRelease().dismissedTag
            applyRelease(
                tag = release.tagName,
                notes = notes,
                apkUrl = apk?.url.orEmpty(),
                apkName = apk?.name.orEmpty(),
                apkSize = apk?.size ?: 0L,
                dismissedTag = dismissed,
                checkFailed = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(checking = false, checkFailed = true) }
        }
    }

    private fun applyRelease(
        tag: String,
        notes: String,
        apkUrl: String,
        apkName: String,
        apkSize: Long,
        dismissedTag: String,
        checkFailed: Boolean,
    ) {
        if (tag.isBlank() || !VersionCompare.isNewer(tag, currentVersion)) {
            _state.update {
                it.copy(
                    checking = false,
                    checkFailed = checkFailed,
                    available = null,
                    showLaunchPrompt = false,
                )
            }
            return
        }
        val available = AvailableUpdate(
            tag = tag,
            versionLabel = VersionCompare.displayVersion(tag),
            notes = notes,
            apkUrl = apkUrl,
            apkName = apkName,
            apkSize = apkSize,
        )
        _state.update {
            it.copy(
                checking = false,
                checkFailed = checkFailed,
                available = available,
                showLaunchPrompt = dismissedTag != tag && it.install is InstallProgress.Idle,
            )
        }
    }

    private suspend fun runDownloadAndInstall(update: AvailableUpdate) {
        if (!installer.canRequestInstalls()) {
            _state.update { it.copy(install = InstallProgress.NeedsUnknownSources) }
            return
        }
        _state.update {
            it.copy(
                install = InstallProgress.Downloading(0L, update.apkSize),
                showLaunchPrompt = true,
            )
        }
        try {
            val existing = downloader.apkFile()
            val apk = if (
                existing.exists() &&
                existing.length() > 0L &&
                (update.apkSize <= 0L || existing.length() == update.apkSize)
            ) {
                existing
            } else {
                downloader.download(update.apkUrl) { read, total ->
                    _state.update { it.copy(install = InstallProgress.Downloading(read, total)) }
                }
            }
            _state.update { it.copy(install = InstallProgress.Installing) }
            when (installer.start(apk)) {
                InstallStart.NeedsUnknownSources -> {
                    _state.update { it.copy(install = InstallProgress.NeedsUnknownSources) }
                }
                InstallStart.Started -> Unit
                InstallStart.StartedLegacy -> {
                    _state.update { it.copy(install = InstallProgress.Idle) }
                }
            }
        } catch (e: CancellationException) {
            downloader.clear()
            _state.update { it.copy(install = InstallProgress.Idle) }
            throw e
        } catch (_: IOException) {
            downloader.clear()
            _state.update { it.copy(install = InstallProgress.Failed) }
        } catch (_: Exception) {
            downloader.clear()
            _state.update { it.copy(install = InstallProgress.Failed) }
        }
    }

    private fun confirmIntent(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }

    companion object {
        const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    }
}
