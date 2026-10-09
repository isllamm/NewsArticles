package com.news.articles.testutil

import com.news.articles.data.local.entity.ArticleEntity
import com.news.articles.data.remote.dto.ArticleDto
import com.news.articles.data.remote.dto.SourceDto
import com.news.articles.domain.model.Article
import java.time.Instant

const val PUBLISHED_AT = "2026-10-08T10:00:00Z"

fun articleUrl(id: Int): String = "https://news.example.com/$id"

fun article(
    id: Int,
    title: String = "Title $id",
    description: String? = "Description $id",
    author: String? = "Author $id",
    imageUrl: String? = "https://img.example.com/$id.jpg",
    sourceName: String? = "Source $id",
    publishedAt: Instant? = Instant.parse(PUBLISHED_AT),
    content: String? = "Content $id",
): Article = Article(
    url = articleUrl(id),
    title = title,
    description = description,
    author = author,
    imageUrl = imageUrl,
    sourceName = sourceName,
    publishedAt = publishedAt,
    content = content,
)

fun articleEntity(
    id: Int,
    title: String = "Title $id",
    description: String? = "Description $id",
    savedAt: Long = 0L,
): ArticleEntity = ArticleEntity(
    url = articleUrl(id),
    title = title,
    description = description,
    author = "Author $id",
    imageUrl = "https://img.example.com/$id.jpg",
    sourceName = "Source $id",
    publishedAt = Instant.parse(PUBLISHED_AT).toEpochMilli(),
    content = "Content $id",
    savedAt = savedAt,
)

fun articleDto(
    id: Int,
    title: String? = "Title $id",
    url: String? = articleUrl(id),
    author: String? = "Author $id",
    description: String? = "Description $id",
    urlToImage: String? = "https://img.example.com/$id.jpg",
    publishedAt: String? = PUBLISHED_AT,
    content: String? = "Content $id",
): ArticleDto = ArticleDto(
    source = SourceDto(id = null, name = "Source $id"),
    author = author,
    title = title,
    description = description,
    url = url,
    urlToImage = urlToImage,
    publishedAt = publishedAt,
    content = content,
)

fun articlesResponseJson(vararg ids: Int, extra: String = ""): String {
    val items = ids.joinToString(",") { id ->
        """
        {
          "source": {"id": null, "name": "Source $id"},
          "author": "Author $id",
          "title": "Title $id",
          "description": "Description $id",
          "url": "${articleUrl(id)}",
          "urlToImage": "https://img.example.com/$id.jpg",
          "publishedAt": "$PUBLISHED_AT",
          "content": "Content $id"
        }
        """.trimIndent()
    }
    val separator = if (extra.isEmpty() || items.isEmpty()) "" else ","
    return """{"status":"ok","totalResults":${ids.size},"articles":[$items$separator$extra]}"""
}
