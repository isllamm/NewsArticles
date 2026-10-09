package com.news.articles.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "top_headlines",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["url"],
            childColumns = ["url"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TopHeadlineEntity(
    @PrimaryKey val url: String,
    val position: Int,
)
