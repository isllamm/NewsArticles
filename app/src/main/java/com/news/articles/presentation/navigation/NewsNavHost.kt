package com.news.articles.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.news.articles.presentation.articles.ArticlesRoute
import com.news.articles.presentation.details.ArticleDetailsRoute

@Composable
fun NewsNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = ArticlesDestination,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(SlideDirection.Start, tween(NAV_DURATION_MS)) +
                fadeIn(tween(NAV_DURATION_MS))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = SlideDirection.Start,
                animationSpec = tween(NAV_DURATION_MS),
                targetOffset = { it / PARALLAX_DIVISOR },
            ) + fadeOut(tween(NAV_DURATION_MS))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = SlideDirection.End,
                animationSpec = tween(NAV_DURATION_MS),
                initialOffset = { it / PARALLAX_DIVISOR },
            ) + fadeIn(tween(NAV_DURATION_MS))
        },
        popExitTransition = {
            slideOutOfContainer(SlideDirection.End, tween(NAV_DURATION_MS)) +
                fadeOut(tween(NAV_DURATION_MS))
        },
    ) {
        composable<ArticlesDestination> {
            ArticlesRoute(
                onOpenDetails = { url ->
                    navController.navigate(ArticleDetailsDestination(url)) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<ArticleDetailsDestination> {
            ArticleDetailsRoute(onNavigateBack = { navController.navigateUp() })
        }
    }
}

private const val NAV_DURATION_MS = 350
private const val PARALLAX_DIVISOR = 4
