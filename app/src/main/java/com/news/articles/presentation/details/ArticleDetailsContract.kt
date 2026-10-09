package com.news.articles.presentation.details

import com.news.articles.presentation.model.ArticleDetailsUi

sealed interface ArticleDetailsState {
    data object Loading : ArticleDetailsState
    data class Content(val article: ArticleDetailsUi) : ArticleDetailsState
    data object NotFound : ArticleDetailsState
}

sealed interface ArticleDetailsIntent {
    data object ReadFullArticleClicked : ArticleDetailsIntent
}

sealed interface ArticleDetailsEffect {
    data class OpenInBrowser(val url: String) : ArticleDetailsEffect
}
