package com.news.articles.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NewsApiKey

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NewsBaseUrl
