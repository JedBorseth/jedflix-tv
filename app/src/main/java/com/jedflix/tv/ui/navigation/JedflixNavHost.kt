package com.jedflix.tv.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jedflix.tv.data.backend.BackendHealthMonitor
import com.jedflix.tv.data.comet.CometClient
import com.jedflix.tv.data.comet.StreamException
import com.jedflix.tv.data.library.UserLibraryRepository
import com.jedflix.tv.data.playback.PlaybackResolver
import com.jedflix.tv.data.playback.PlaybackSession
import com.jedflix.tv.data.settings.QualityProfile
import com.jedflix.tv.data.settings.SettingsStore
import com.jedflix.tv.data.tmdb.CatalogSection
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.data.update.AppUpdateManager
import com.jedflix.tv.ui.detail.DetailScreen
import com.jedflix.tv.ui.home.CatalogScreen
import com.jedflix.tv.ui.images.LocalBrowseQuality
import com.jedflix.tv.ui.player.PlayerScreen
import com.jedflix.tv.ui.search.SearchScreen
import com.jedflix.tv.ui.settings.SettingsScreen
import com.jedflix.tv.ui.settings.UpdatePromptOverlay
import com.jedflix.tv.ui.splash.SplashScreen
import com.jedflix.tv.ui.splash.SplashStingEffect
import com.jedflix.tv.ui.streams.PlaybackStartErrorOverlay
import com.jedflix.tv.ui.streams.PlaybackStartingOverlay
import com.jedflix.tv.ui.streams.StreamPickerScreen
import com.jedflix.tv.ui.streams.toKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun JedflixNavHost(
    repository: TmdbRepository,
    settingsStore: SettingsStore,
    cometClient: CometClient,
    playbackSession: PlaybackSession,
    library: UserLibraryRepository,
    appUpdateManager: AppUpdateManager,
    backendHealth: BackendHealthMonitor,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val playbackResolver = remember(repository, cometClient, settingsStore, library, playbackSession) {
        PlaybackResolver(repository, cometClient, settingsStore, library, playbackSession)
    }
    var startingPlayback by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<StreamException?>(null) }
    var pendingStart by remember { mutableStateOf<PlaybackRequest?>(null) }
    var startJob by remember { mutableStateOf<Job?>(null) }
    val updateState by appUpdateManager.state.collectAsStateWithLifecycle()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    LaunchedEffect(appUpdateManager) {
        appUpdateManager.openUnknownSources.collect { intent ->
            runCatching { context.startActivity(intent) }
        }
    }

    DisposableEffect(lifecycleOwner, appUpdateManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appUpdateManager.retryInstallAfterPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openTitle(title: MediaTitle) {
        navController.navigate(Routes.detail(title))
    }

    fun cancelStart() {
        startJob?.cancel()
        startJob = null
        startingPlayback = false
        startError = null
        pendingStart = null
    }

    fun startPlayback(type: MediaType, id: Int, season: Int? = null, episode: Int? = null) {
        if (startingPlayback) return
        val request = PlaybackRequest(type, id, season, episode)
        pendingStart = request
        startError = null
        startingPlayback = true
        startJob = scope.launch {
            try {
                playbackResolver.autoStart(type, id, season, episode)
                startingPlayback = false
                navController.navigate(Routes.PLAYER)
            } catch (e: CancellationException) {
                throw e
            } catch (e: StreamException) {
                startError = e
                startingPlayback = false
            } catch (e: Exception) {
                startError = StreamException.Network(e)
                startingPlayback = false
            }
        }
    }

    fun openStreams(type: MediaType, id: Int, season: Int? = null, episode: Int? = null) {
        navController.navigate(Routes.streams(type, id, season, episode))
    }

    fun openSection(target: CatalogSection) {
        navController.navigate(target.route) {
            popUpTo(CatalogSection.HOME.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun openSearch() {
        navController.navigate(Routes.SEARCH) {
            popUpTo(CatalogSection.HOME.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun openSettings(focusKey: Boolean = false) {
        navController.navigate(Routes.settings(focusKey)) {
            popUpTo(CatalogSection.HOME.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    val browseQuality by settingsStore.qualityProfile.collectAsStateWithLifecycle(QualityProfile.Max)
    val hidePrompt = currentRoute == null ||
        currentRoute == Routes.SPLASH ||
        currentRoute == Routes.SETTINGS ||
        currentRoute == Routes.PLAYER ||
        currentRoute == Routes.STREAMS
    val showPrompt = updateState.showLaunchPrompt && updateState.available != null && !hidePrompt

    CompositionLocalProvider(LocalBrowseQuality provides browseQuality) {
        SplashStingEffect()
        Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            enterTransition = { fadeIn(tween(450)) },
            exitTransition = { fadeOut(tween(300)) },
            popEnterTransition = { fadeIn(tween(450)) },
            popExitTransition = { fadeOut(tween(300)) },
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    onFinished = {
                        navController.navigate(CatalogSection.HOME.route) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            CatalogSection.entries.forEach { section ->
                composable(section.route) {
                    CatalogScreen(
                        section = section,
                        repository = repository,
                        library = library,
                        settingsStore = settingsStore,
                        onSectionSelected = { target ->
                            if (target != section) openSection(target)
                        },
                        onSearch = ::openSearch,
                        onSettings = ::openSettings,
                        onTitleClick = ::openTitle,
                        onContinueWatching = { item ->
                            startPlayback(item.title.mediaType, item.title.id, item.season, item.episode)
                        },
                    )
                }
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    repository = repository,
                    library = library,
                    onSectionSelected = ::openSection,
                    onSettings = ::openSettings,
                    onTitleClick = ::openTitle,
                )
            }

            composable(
                route = Routes.SETTINGS,
                arguments = listOf(
                    navArgument("focusKey") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                SettingsScreen(
                    settingsStore = settingsStore,
                    library = library,
                    appUpdateManager = appUpdateManager,
                    backendHealth = backendHealth,
                    focusApiKey = entry.arguments?.getBoolean("focusKey") == true,
                    onSectionSelected = ::openSection,
                    onSearch = ::openSearch,
                )
            }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("id") { type = NavType.IntType },
                ),
            ) { entry ->
                val type = MediaType.fromApi(entry.arguments?.getString("mediaType")) ?: MediaType.MOVIE
                val id = entry.arguments?.getInt("id") ?: return@composable
                DetailScreen(
                    mediaType = type,
                    mediaId = id,
                    repository = repository,
                    library = library,
                    onTitleClick = ::openTitle,
                    onPlay = { season, episode -> startPlayback(type, id, season, episode) },
                    onPlayEpisode = { season, episode -> startPlayback(type, id, season, episode) },
                    startingPlayback = startingPlayback,
                )
            }

            composable(
                route = Routes.STREAMS,
                arguments = listOf(
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("id") { type = NavType.IntType },
                    navArgument("season") { type = NavType.IntType; defaultValue = Routes.NO_EPISODE },
                    navArgument("episode") { type = NavType.IntType; defaultValue = Routes.NO_EPISODE },
                    navArgument("auto") { type = NavType.IntType; defaultValue = Routes.MANUAL_PICK },
                ),
            ) { entry ->
                val type = MediaType.fromApi(entry.arguments?.getString("mediaType")) ?: MediaType.MOVIE
                val id = entry.arguments?.getInt("id") ?: return@composable
                val season = entry.arguments?.getInt("season")?.takeIf { it != Routes.NO_EPISODE }
                val episode = entry.arguments?.getInt("episode")?.takeIf { it != Routes.NO_EPISODE }
                StreamPickerScreen(
                    mediaType = type,
                    mediaId = id,
                    season = season,
                    episode = episode,
                    repository = repository,
                    cometClient = cometClient,
                    settingsStore = settingsStore,
                    playbackSession = playbackSession,
                    library = library,
                    onPlay = {
                        navController.navigate(Routes.PLAYER) {
                            popUpTo(Routes.STREAMS) { inclusive = true }
                        }
                    },
                    onOpenSettings = { openSettings(focusKey = true) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.PLAYER,
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(200)) },
            ) {
                PlayerScreen(
                    playbackSession = playbackSession,
                    library = library,
                    settingsStore = settingsStore,
                    tmdb = repository,
                    comet = cometClient,
                    onExit = { navController.popBackStack() },
                    onSeriesComplete = { navController.popBackStack() },
                    onNeedPicker = { season, episode ->
                        val playing = playbackSession.current
                        if (playing == null) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(
                                Routes.streams(
                                    playing.mediaType,
                                    playing.tmdbId,
                                    season,
                                    episode,
                                    auto = false,
                                ),
                            ) {
                                popUpTo(Routes.PLAYER) { inclusive = true }
                            }
                        }
                    },
                )
            }
        }

        if (showPrompt) {
            UpdatePromptOverlay(
                state = updateState,
                onInstall = appUpdateManager::downloadAndInstall,
                onAllowInstalls = appUpdateManager::requestUnknownSourcesPermission,
                onCancel = appUpdateManager::cancelInstall,
                onLater = appUpdateManager::dismissPrompt,
            )
        }

        val startErrorNow = startError
        BackHandler(enabled = startingPlayback || startErrorNow != null) { cancelStart() }
        if (startingPlayback && currentRoute != Routes.DETAIL) {
            PlaybackStartingOverlay(onCancel = ::cancelStart)
        }
        if (startErrorNow != null) {
            PlaybackStartErrorOverlay(
                kind = startErrorNow.toKind(),
                detail = startErrorNow.message,
                onRetry = {
                    val request = pendingStart
                    cancelStart()
                    if (request != null) {
                        startPlayback(request.type, request.id, request.season, request.episode)
                    }
                },
                onOpenSettings = {
                    cancelStart()
                    openSettings(focusKey = true)
                },
                onDismiss = ::cancelStart,
            )
        }
        }
    }
}

private data class PlaybackRequest(
    val type: MediaType,
    val id: Int,
    val season: Int?,
    val episode: Int?,
)
