package com.news.articles.data.repository

import com.news.articles.data.local.ArticleDao
import com.news.articles.data.local.entity.ArticleEntity
import com.news.articles.data.mapper.toDomain
import com.news.articles.data.mapper.toEntities
import com.news.articles.data.remote.NewsApi
import com.news.articles.data.remote.safeApiCall
import com.news.articles.di.IoDispatcher
import com.news.articles.domain.model.AppError
import com.news.articles.domain.model.AppResult
import com.news.articles.domain.model.Article
import com.news.articles.domain.model.SearchResult
import com.news.articles.domain.repository.ArticleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject

class ArticleRepositoryImpl @Inject constructor(
    private val api: NewsApi,
    private val dao: ArticleDao,
    private val json: Json,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ArticleRepository {

    override fun observeTopHeadlines(): Flow<List<Article>> =
        dao.observeTopHeadlines()
            .map { articles -> articles.map(ArticleEntity::toDomain) }
            .flowOn(ioDispatcher)

    override fun observeArticle(url: String): Flow<Article?> =
        dao.observeArticle(url)
            .map { article -> article?.toDomain() }
            .flowOn(ioDispatcher)

    override suspend fun refreshTopHeadlines(): AppResult<Unit> = withContext(ioDispatcher) {
        safeApiCall(json) {
            val responses = coroutineScope {
                TOP_HEADLINES_PAGES
                    .map { page -> async { api.getTopHeadlines(COUNTRY, page) } }
                    .awaitAll()
            }
            val now = clock.millis()
            val articles = responses.flatMap { it.articles }.toEntities(savedAt = now)
            if (articles.isNotEmpty()) {
                dao.replaceTopHeadlines(articles, staleThreshold = now - STALE_ARTICLE_AGE_MS)
            }
        }
    }

    override suspend fun searchArticles(query: String): AppResult<SearchResult> = withContext(ioDispatcher) {
        when (val remote = safeApiCall(json) { fetchAndSaveSearchResults(query) }) {
            is AppResult.Success -> AppResult.Success(SearchResult(remote.data, isFromCache = false))
            is AppResult.Failure -> searchSavedArticles(query, remote.error)
        }
    }

    private suspend fun fetchAndSaveSearchResults(query: String): List<Article> {
        val articles = api.searchTopHeadlines(COUNTRY, query).articles.toEntities(savedAt = clock.millis())
        dao.upsertArticles(articles)
        return articles.map(ArticleEntity::toDomain)
    }

    private suspend fun searchSavedArticles(query: String, error: AppError): AppResult<SearchResult> {
        val saved = dao.searchArticles(query.escapeLikePattern()).map(ArticleEntity::toDomain)
        return if (saved.isEmpty()) {
            AppResult.Failure(error)
        } else {
            AppResult.Success(SearchResult(saved, isFromCache = true))
        }
    }

    private fun String.escapeLikePattern(): String =
        replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private companion object {
        const val COUNTRY = "us"
        const val STALE_ARTICLE_AGE_MS = 24L * 60 * 60 * 1000
        val TOP_HEADLINES_PAGES = listOf(1, 2)
    }
}
