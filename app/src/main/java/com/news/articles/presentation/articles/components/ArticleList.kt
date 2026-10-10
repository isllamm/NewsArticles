package com.news.articles.presentation.articles.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.news.articles.presentation.model.ArticleUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleList(
    articles: ImmutableList<ArticleUi>,
    query: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onArticleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var previousQuery by rememberSaveable { mutableStateOf(query) }
    var hasPlayedEntrance by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(ENTRANCE_WINDOW_MS)
        hasPlayedEntrance = true
    }

    LaunchedEffect(query) {
        if (query != previousQuery) {
            previousQuery = query
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { it }
            .collect { focusManager.clearFocus() }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(items = articles, key = { _, article -> article.url }) { index, article ->
                ArticleCard(
                    article = article,
                    onClick = { onArticleClick(article.url) },
                    modifier = Modifier.animateItem(),
                    animateIn = !hasPlayedEntrance && index < ENTRANCE_ITEM_COUNT,
                    entranceDelayMillis = index * ENTRANCE_STAGGER_MS,
                )
            }
        }
    }
}

private const val ENTRANCE_ITEM_COUNT = 6
private const val ENTRANCE_STAGGER_MS = 70
private const val ENTRANCE_WINDOW_MS = 500L
