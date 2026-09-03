package com.jedflix.tv.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.jedflix.tv.data.comet.CometClient
import com.jedflix.tv.data.playback.PlaybackSession
import com.jedflix.tv.data.settings.SettingsStore
import com.jedflix.tv.data.tmdb.CatalogSection
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.data.update.AppUpdateManager
import com.jedflix.tv.ui.detail.DetailScreen
import com.jedflix.tv.ui.home.CatalogScreen
import com.jedflix.tv.ui.player.PlayerScreen
import com.jedflix.tv.ui.search.SearchScreen
import com.jedflix.tv.ui.settings.SettingsScreen
import com.jedflix.tv.ui.settings.UpdatePromptOverlay
import com.jedflix.tv.ui.splash.SplashScreen
import com.jedflix.tv.ui.streams.StreamPickerScreen
import kotlinx.coroutines.launch

@Composable
fun JedflixNavHost(
    repository: TmdbRepository,
    settingsStore: SettingsStore,
    cometClient: CometClient,
    playbackSession: PlaybackSession,
    appUpdateManager: AppUpdateManager,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val updateState by appUpdateManager.state.collectAsStateWithLifecycle()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    LaunchedEffect(appUpdateManager) {
        launch {
            appUpdateManager.pendingConfirm.collect { intent ->
                runCatching { context.startActivity(intent) }
            }
        }
        launch {
            appUpdateManager.openUnknownSources.collect { intent ->
                runCatching { context.startActivity(intent) }
            }
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

    fun openSettings() {
        navController.navigate(Routes.SETTINGS) {
            popUpTo(CatalogSection.HOME.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    val hidePrompt = currentRoute == null ||
        currentRoute == Routes.SPLASH ||
        currentRoute == Routes.SETTINGS ||
        currentRoute == Routes.PLAYER ||
        currentRoute == Routes.STREAMS
    val showPrompt = updateState.showLaunchPrompt && updateState.available != null && !hidePrompt

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
                        onSectionSelected = { target ->
                            if (target != section) openSection(target)
                        },
                        onSearch = ::openSearch,
                        onSettings = ::openSettings,
                        onTitleClick = ::openTitle,
                    )
                }
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    repository = repository,
                    onSectionSelected = ::openSection,
                    onSettings = ::openSettings,
                    onTitleClick = ::openTitle,
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settingsStore = settingsStore,
                    appUpdateManager = appUpdateManager,
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
                    onTitleClick = ::openTitle,
                    onPlay = { openStreams(type, id) },
                    onPlayEpisode = { season, episode -> openStreams(type, id, season, episode) },
                )
            }

            composable(
                route = Routes.STREAMS,
                arguments = listOf(
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("id") { type = NavType.IntType },
                    navArgument("season") { type = NavType.IntType; defaultValue = Routes.NO_EPISODE },
                    navArgument("episode") { type = NavType.IntType; defaultValue = Routes.NO_EPISODE },
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
                    onPlay = { navController.navigate(Routes.PLAYER) },
                    onOpenSettings = ::openSettings,
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
                    onExit = { navController.popBackStack() },
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
    }
}
