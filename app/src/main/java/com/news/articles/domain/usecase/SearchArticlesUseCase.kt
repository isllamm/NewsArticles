package com.news.articles.domain.usecase

import com.news.articles.domain.model.AppResult
import com.news.articles.domain.model.SearchResult
import com.news.articles.domain.repository.ArticleRepository
import javax.inject.Inject

class SearchArticlesUseCase @Inject constructor(
    private val repository: ArticleRepository,
) {
    suspend operator fun invoke(query: String): AppResult<SearchResult> =
        repository.searchArticles(query.trim())
}
