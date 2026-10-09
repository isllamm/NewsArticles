package com.news.articles.domain.repository

import com.news.articles.domain.model.AppResult
import com.news.articles.domain.model.Article
import com.news.articles.domain.model.SearchResult
import kotlinx.coroutines.flow.Flow

interface ArticleRepository {

    fun observeTopHeadlines(): Flow<List<Article>>

    suspend fun refreshTopHeadlines(): AppResult<Unit>

    suspend fun searchArticles(query: String): AppResult<SearchResult>

    fun observeArticle(url: String): Flow<Article?>
}
