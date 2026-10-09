package com.news.articles.domain.model

data class SearchResult(
    val articles: List<Article>,
    val isFromCache: Boolean,
)
