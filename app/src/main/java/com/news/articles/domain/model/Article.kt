package com.news.articles.domain.model

import java.time.Instant

data class Article(
    val url: String,
    val title: String,
    val description: String?,
    val author: String?,
    val imageUrl: String?,
    val sourceName: String?,
    val publishedAt: Instant?,
    val content: String?,
)
