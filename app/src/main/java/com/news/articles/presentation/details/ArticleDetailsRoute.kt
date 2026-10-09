package com.news.articles.presentation.details

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.news.articles.R
import com.news.articles.core.mvi.CollectEffects
import kotlinx.coroutines.launch

@Composable
fun ArticleDetailsRoute(
    onNavigateBack: () -> Unit,
    viewModel: ArticleDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val resources = LocalResources.current

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ArticleDetailsEffect.OpenInBrowser -> {
                runCatching { uriHandler.openUri(effect.url) }.onFailure {
                    scope.launch {
                        snackbarHostState.showSnackbar(resources.getString(R.string.error_open_link))
                    }
                }
            }
        }
    }

    ArticleDetailsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBackClick = onNavigateBack,
        onReadFullArticleClick = { viewModel.onIntent(ArticleDetailsIntent.ReadFullArticleClicked) },
    )
}
