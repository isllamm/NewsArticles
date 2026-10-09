package com.news.articles.presentation.common

import androidx.annotation.StringRes
import com.news.articles.R
import com.news.articles.domain.model.AppError

@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.NoInternet -> R.string.error_no_internet
    AppError.Timeout -> R.string.error_timeout
    AppError.Unauthorized -> R.string.error_unauthorized
    AppError.RateLimited -> R.string.error_rate_limited
    AppError.Server -> R.string.error_server
    AppError.Unknown -> R.string.error_unknown
}
