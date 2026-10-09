package com.news.articles.presentation.model

import androidx.compose.runtime.Immutable
import com.news.articles.domain.model.Article
import java.time.Instant

@Immutable
data class ArticleUi(
    val url: String,
    val title: String,
    val imageUrl: String?,
    val sourceName: String?,
)

@Immutable
data class ArticleDetailsUi(
    val url: String,
    val title: String,
    val imageUrl: String?,
    val description: String?,
    val author: String?,
    val sourceName: String?,
    val publishedAt: Instant?,
    val body: String?,
)

fun Article.toUi(): ArticleUi = ArticleUi(
    url = url,
    title = title,
    imageUrl = imageUrl,
    sourceName = sourceName,
)

fun Article.toDetailsUi(): ArticleDetailsUi = ArticleDetailsUi(
    url = url,
    title = title,
    imageUrl = imageUrl,
    description = description,
    author = author,
    sourceName = sourceName,
    publishedAt = publishedAt,
    body = content?.takeIf { it != description },
)
