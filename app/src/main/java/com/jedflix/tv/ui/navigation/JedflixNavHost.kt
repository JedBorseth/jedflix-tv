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
import com.jedflix.tv.data.tmdb.CatalogSection
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.ui.detail.DetailScreen
import com.jedflix.tv.ui.home.CatalogScreen
import com.jedflix.tv.ui.search.SearchScreen
import com.jedflix.tv.ui.splash.SplashScreen

@Composable
fun JedflixNavHost(repository: TmdbRepository) {
    val navController = rememberNavController()

    fun openTitle(title: MediaTitle) {
        navController.navigate(Routes.detail(title))
    }

    fun openSection(target: CatalogSection) {
        navController.navigate(target.route) {
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
                    onSectionSelected = { target ->
                        if (target != section) openSection(target)
                    },
                    onSearch = {
                        navController.navigate(Routes.SEARCH) {
                            popUpTo(CatalogSection.HOME.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onTitleClick = ::openTitle,
                )
            }
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                repository = repository,
                onSectionSelected = ::openSection,
                onTitleClick = ::openTitle,
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
            )
        }
    }
}
