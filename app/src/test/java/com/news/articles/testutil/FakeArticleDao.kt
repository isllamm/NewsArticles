package com.news.articles.testutil

import com.news.articles.data.local.ArticleDao
import com.news.articles.data.local.entity.ArticleEntity
import com.news.articles.data.local.entity.TopHeadlineEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeArticleDao : ArticleDao() {

    private val articles = MutableStateFlow<Map<String, ArticleEntity>>(emptyMap())
    private val headlines = MutableStateFlow<List<TopHeadlineEntity>>(emptyList())

    val savedArticles: Collection<ArticleEntity> get() = articles.value.values
    val headlineUrls: List<String> get() = headlines.value.sortedBy { it.position }.map { it.url }

    override fun observeTopHeadlines(): Flow<List<ArticleEntity>> =
        combine(articles, headlines) { saved, top ->
            top.sortedBy { it.position }.mapNotNull { saved[it.url] }
        }

    override fun observeArticle(url: String): Flow<ArticleEntity?> = articles.map { it[url] }

    override suspend fun searchArticles(query: String): List<ArticleEntity> {
        val pattern = query.toLikeRegex()
        return articles.value.values
            .filter { pattern.containsMatchIn(it.title) || pattern.containsMatchIn(it.description.orEmpty()) }
            .sortedByDescending { it.publishedAt }
    }

    override suspend fun upsertArticles(articles: List<ArticleEntity>) {
        this.articles.update { current -> current + articles.associateBy { it.url } }
    }

    override suspend fun clearTopHeadlines() {
        headlines.value = emptyList()
    }

    override suspend fun insertTopHeadlines(headlines: List<TopHeadlineEntity>) {
        this.headlines.update { it + headlines }
    }

    override suspend fun deleteStaleArticles(threshold: Long) {
        val pinned = headlines.value.map { it.url }.toSet()
        articles.update { current ->
            current.filterValues { it.savedAt >= threshold || it.url in pinned }
        }
    }

    private fun String.toLikeRegex(): Regex {
        val body = StringBuilder()
        var index = 0
        while (index < length) {
            when (val char = this[index]) {
                '\\' -> {
                    index++
                    if (index < length) body.append(Regex.escape(this[index].toString()))
                }
                '%' -> body.append(".*")
                '_' -> body.append(".")
                else -> body.append(Regex.escape(char.toString()))
            }
            index++
        }
        return Regex(body.toString(), RegexOption.IGNORE_CASE)
    }
}
