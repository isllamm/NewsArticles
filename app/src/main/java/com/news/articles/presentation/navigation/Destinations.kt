package com.news.articles.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
data object ArticlesDestination

@Serializable
data class ArticleDetailsDestination(val url: String)
