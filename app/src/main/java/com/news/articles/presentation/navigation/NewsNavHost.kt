package com.news.articles.presentation.navigation

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
