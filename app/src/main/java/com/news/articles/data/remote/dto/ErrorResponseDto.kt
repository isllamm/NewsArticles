package com.news.articles.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponseDto(
    val status: String? = null,
    val code: String? = null,
    val message: String? = null,
)
