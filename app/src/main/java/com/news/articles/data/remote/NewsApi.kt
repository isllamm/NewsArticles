package com.news.articles.data.remote

import com.news.articles.data.remote.dto.ArticlesResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface NewsApi {

    @GET("v2/top-headlines")
    suspend fun getTopHeadlines(
        @Query("country") country: String,
        @Query("page") page: Int,
    ): ArticlesResponseDto

    @GET("v2/top-headlines")
    suspend fun searchTopHeadlines(
        @Query("country") country: String,
        @Query("q") query: String,
    ): ArticlesResponseDto
}
