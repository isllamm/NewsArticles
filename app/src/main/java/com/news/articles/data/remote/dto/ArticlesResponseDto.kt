package com.news.articles.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ArticlesResponseDto(
    val status: String? = null,
    val totalResults: Int? = null,
    val articles: List<ArticleDto> = emptyList(),
)

@Serializable
data class ArticleDto(
    val source: SourceDto? = null,
    val author: String? = null,
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val urlToImage: String? = null,
    val publishedAt: String? = null,
    val content: String? = null,
)

@Serializable
data class SourceDto(
    val id: String? = null,
    val name: String? = null,
)
