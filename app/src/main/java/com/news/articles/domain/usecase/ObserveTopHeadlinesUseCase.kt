package com.news.articles.domain.usecase

import com.news.articles.domain.model.Article
import com.news.articles.domain.repository.ArticleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTopHeadlinesUseCase @Inject constructor(
    private val repository: ArticleRepository,
) {
    operator fun invoke(): Flow<List<Article>> = repository.observeTopHeadlines()
}
