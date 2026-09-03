package com.jedflix.tv.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jedflix.tv.data.comet.CometClient
import com.jedflix.tv.data.library.UserLibraryRepository
import com.jedflix.tv.data.playback.PlaybackSession
import com.jedflix.tv.data.settings.SettingsStore
import com.jedflix.tv.data.tmdb.CatalogSection
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.ui.detail.DetailScreen
import com.jedflix.tv.ui.home.CatalogScreen
import com.jedflix.tv.ui.player.PlayerScreen
import com.jedflix.tv.ui.search.SearchScreen
import com.jedflix.tv.ui.settings.SettingsScreen
import com.jedflix.tv.ui.splash.SplashScreen
import com.jedflix.tv.ui.streams.StreamPickerScreen

@Composable
fun JedflixNavHost(
    repository: TmdbRepository,
    settingsStore: SettingsStore,
    cometClient: CometClient,
    playbackSession: PlaybackSession,
    library: UserLibraryRepository,
) {
    val navController = rememberNavController()

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
                    onSectionSelected = { target ->
                        if (target != section) openSection(target)
                    },
                    onSearch = ::openSearch,
                    onSettings = ::openSettings,
                    onTitleClick = ::openTitle,
                    onContinueWatching = { item ->
                        openStreams(item.title.mediaType, item.title.id, item.season, item.episode)
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

        composable(Routes.SETTINGS) {
            SettingsScreen(
                settingsStore = settingsStore,
                library = library,
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
                onPlay = { season, episode -> openStreams(type, id, season, episode) },
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
                library = library,
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
                library = library,
                onExit = { navController.popBackStack() },
            )
        }
    }
}
