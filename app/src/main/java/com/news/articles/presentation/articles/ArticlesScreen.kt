package com.news.articles.presentation.articles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.news.articles.R
import com.news.articles.presentation.articles.components.ArticleList
import com.news.articles.presentation.articles.components.SavedArticlesBanner
import com.news.articles.presentation.articles.components.SearchField
import com.news.articles.presentation.common.ErrorView
import com.news.articles.presentation.common.LoadingView
import com.news.articles.presentation.common.MessageView

@Composable
fun ArticlesScreen(
    state: ArticlesState,
    snackbarHostState: SnackbarHostState,
    onIntent: (ArticlesIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SearchField(
                query = state.query,
                onQueryChange = { onIntent(ArticlesIntent.QueryChanged(it)) },
                onClear = { onIntent(ArticlesIntent.ClearQuery) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                if (state.isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            AnimatedVisibility(visible = state.isShowingSavedArticles) {
                SavedArticlesBanner()
            }
            ArticlesBody(
                state = state,
                onIntent = onIntent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ArticlesBody(
    state: ArticlesState,
    onIntent: (ArticlesIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val content = state.content) {
        ArticlesContent.Loading -> LoadingView(modifier)
        is ArticlesContent.Items -> ArticleList(
            articles = content.articles,
            query = state.query,
            isRefreshing = state.isRefreshing,
            onRefresh = { onIntent(ArticlesIntent.Refresh) },
            onArticleClick = { onIntent(ArticlesIntent.ArticleClicked(it)) },
            modifier = modifier,
        )
        is ArticlesContent.Empty -> MessageView(
            message = if (content.query.isEmpty()) {
                stringResource(R.string.empty_headlines)
            } else {
                stringResource(R.string.empty_search, content.query)
            },
            modifier = modifier,
        )
        is ArticlesContent.Error -> ErrorView(
            error = content.error,
            onRetry = { onIntent(ArticlesIntent.Refresh) },
            modifier = modifier,
        )
    }
}
