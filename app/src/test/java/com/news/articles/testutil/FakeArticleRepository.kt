package com.news.articles.testutil

import com.news.articles.domain.model.AppResult
import com.news.articles.domain.model.Article
import com.news.articles.domain.model.SearchResult
import com.news.articles.domain.repository.ArticleRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeArticleRepository : ArticleRepository {

    val headlines = MutableStateFlow<List<Article>>(emptyList())
    val articles = MutableStateFlow<Map<String, Article>>(emptyMap())
    val searchQueries = mutableListOf<String>()

    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshDelayMs: Long = 0L
    var searchHandler: suspend (String) -> AppResult<SearchResult> = {
        AppResult.Success(SearchResult(emptyList(), isFromCache = false))
    }

    var refreshCount: Int = 0
        private set

    override fun observeTopHeadlines(): Flow<List<Article>> = headlines

    override suspend fun refreshTopHeadlines(): AppResult<Unit> {
        refreshCount++
        delay(refreshDelayMs)
        return refreshResult
    }

    override suspend fun searchArticles(query: String): AppResult<SearchResult> {
        searchQueries += query
        return searchHandler(query)
    }

    override fun observeArticle(url: String): Flow<Article?> = articles.map { it[url] }
}
