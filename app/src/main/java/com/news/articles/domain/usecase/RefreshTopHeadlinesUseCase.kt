package com.news.articles.domain.usecase

import com.news.articles.domain.model.AppResult
import com.news.articles.domain.repository.ArticleRepository
import javax.inject.Inject

class RefreshTopHeadlinesUseCase @Inject constructor(
    private val repository: ArticleRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> = repository.refreshTopHeadlines()
}
