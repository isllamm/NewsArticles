package com.news.articles.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.news.articles.data.local.entity.ArticleEntity
import com.news.articles.data.local.entity.TopHeadlineEntity

@Database(
    entities = [ArticleEntity::class, TopHeadlineEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NewsDatabase : RoomDatabase() {

    abstract fun articleDao(): ArticleDao

    companion object {
        const val NAME = "news.db"
    }
}
