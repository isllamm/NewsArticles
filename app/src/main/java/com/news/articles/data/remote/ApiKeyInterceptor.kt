package com.news.articles.data.remote

import com.news.articles.di.NewsApiKey
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class ApiKeyInterceptor @Inject constructor(
    @param:NewsApiKey private val apiKey: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request()
                .newBuilder()
                .header(HEADER_API_KEY, apiKey)
                .build(),
        )

    companion object {
        const val HEADER_API_KEY = "x-api-key"
    }
}
