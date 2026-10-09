package com.news.articles.data.mapper

import com.news.articles.data.local.entity.ArticleEntity
import com.news.articles.data.remote.dto.ArticleDto
import com.news.articles.domain.model.Article
import java.time.Instant

private const val REMOVED_PLACEHOLDER = "[Removed]"
private const val HTTP_SCHEME = "http://"
private const val HTTPS_SCHEME = "https://"
private val TRUNCATION_SUFFIX = Regex("""\s*\[\+\d+ chars]\s*$""")

fun List<ArticleDto>.toEntities(savedAt: Long): List<ArticleEntity> =
    mapNotNull { it.toEntityOrNull(savedAt) }.distinctBy { it.url }

fun ArticleDto.toEntityOrNull(savedAt: Long): ArticleEntity? {
    val articleUrl = url.clean()?.takeIf { it.isWebUrl() } ?: return null
    val articleTitle = title.clean() ?: return null
    return ArticleEntity(
        url = articleUrl,
        title = articleTitle,
        description = description.clean(),
        author = author.clean()?.takeUnless { it.isWebUrl() },
        imageUrl = urlToImage.clean()?.takeIf { it.isWebUrl() }?.toHttps(),
        sourceName = source?.name.clean(),
        publishedAt = publishedAt.toEpochMillisOrNull(),
        content = content?.replace(TRUNCATION_SUFFIX, "").clean(),
        savedAt = savedAt,
    )
}

fun ArticleEntity.toDomain(): Article = Article(
    url = url,
    title = title,
    description = description,
    author = author,
    imageUrl = imageUrl,
    sourceName = sourceName,
    publishedAt = publishedAt?.let(Instant::ofEpochMilli),
    content = content,
)

private fun String?.clean(): String? =
    this?.trim()?.takeUnless { it.isEmpty() || it == REMOVED_PLACEHOLDER }

private fun String.isWebUrl(): Boolean =
    startsWith(HTTP_SCHEME, ignoreCase = true) || startsWith(HTTPS_SCHEME, ignoreCase = true)

private fun String.toHttps(): String =
    if (startsWith(HTTP_SCHEME, ignoreCase = true)) HTTPS_SCHEME + substring(HTTP_SCHEME.length) else this

private fun String?.toEpochMillisOrNull(): Long? =
    this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
