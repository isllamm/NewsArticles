package com.news.articles.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val url: String,
    val title: String,
    val description: String?,
    val author: String?,
    val imageUrl: String?,
    val sourceName: String?,
    val publishedAt: Long?,
    val content: String?,
    val savedAt: Long,
)
