package com.news.articles.domain.model

sealed interface AppError {
    data object NoInternet : AppError
    data object Timeout : AppError
    data object Unauthorized : AppError
    data object RateLimited : AppError
    data object Server : AppError
    data object Unknown : AppError
}
