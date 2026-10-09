package com.news.articles.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.news.articles.data.local.entity.ArticleEntity
import com.news.articles.data.local.entity.TopHeadlineEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ArticleDao {

    @Query(
        """
        SELECT articles.* FROM articles
        INNER JOIN top_headlines ON articles.url = top_headlines.url
        ORDER BY top_headlines.position ASC
        """,
    )
    abstract fun observeTopHeadlines(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE url = :url")
    abstract fun observeArticle(url: String): Flow<ArticleEntity?>

    @Query(
        """
        SELECT * FROM articles
        WHERE title LIKE '%' || :query || '%' ESCAPE '\'
        OR description LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY publishedAt DESC
        """,
    )
    abstract suspend fun searchArticles(query: String): List<ArticleEntity>

    @Upsert
    abstract suspend fun upsertArticles(articles: List<ArticleEntity>)

    @Query("DELETE FROM top_headlines")
    abstract suspend fun clearTopHeadlines()

    @Insert
    abstract suspend fun insertTopHeadlines(headlines: List<TopHeadlineEntity>)

    @Query("DELETE FROM articles WHERE savedAt < :threshold AND url NOT IN (SELECT url FROM top_headlines)")
    abstract suspend fun deleteStaleArticles(threshold: Long)

    @Transaction
    open suspend fun replaceTopHeadlines(articles: List<ArticleEntity>, staleThreshold: Long) {
        upsertArticles(articles)
        clearTopHeadlines()
        insertTopHeadlines(
            articles.mapIndexed { index, article -> TopHeadlineEntity(url = article.url, position = index) },
        )
        deleteStaleArticles(staleThreshold)
    }
}
