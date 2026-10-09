package com.news.articles.presentation.articles

import com.news.articles.domain.model.AppError
import com.news.articles.presentation.model.ArticleUi
import kotlinx.collections.immutable.ImmutableList

data class ArticlesState(
    val query: String = "",
    val content: ArticlesContent = ArticlesContent.Loading,
    val isRefreshing: Boolean = false,
    val isSearching: Boolean = false,
    val isShowingSavedArticles: Boolean = false,
)

sealed interface ArticlesContent {
    data object Loading : ArticlesContent
    data class Items(val articles: ImmutableList<ArticleUi>) : ArticlesContent
    data class Empty(val query: String) : ArticlesContent
    data class Error(val error: AppError) : ArticlesContent
}

sealed interface ArticlesIntent {
    data class QueryChanged(val query: String) : ArticlesIntent
    data object ClearQuery : ArticlesIntent
    data object Refresh : ArticlesIntent
    data class ArticleClicked(val url: String) : ArticlesIntent
}

sealed interface ArticlesEffect {
    data class OpenDetails(val url: String) : ArticlesEffect
    data class ShowError(val error: AppError) : ArticlesEffect
}
