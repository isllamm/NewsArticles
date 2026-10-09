package com.news.articles.data.mapper

import com.news.articles.testutil.PUBLISHED_AT
import com.news.articles.testutil.articleDto
import com.news.articles.testutil.articleEntity
import com.news.articles.testutil.articleUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ArticleMappersTest {

    private val savedAt = 1_000L

    @Test
    fun `maps every field of a valid article`() {
        val entity = articleDto(1).toEntityOrNull(savedAt)

        assertEquals(
            articleEntity(1, savedAt = savedAt),
            entity,
        )
    }

    @Test
    fun `trims text fields`() {
        val entity = articleDto(1, title = "  Padded title  ", description = " text ", author = " Jane ")
            .toEntityOrNull(savedAt)

        assertEquals("Padded title", entity?.title)
        assertEquals("text", entity?.description)
        assertEquals("Jane", entity?.author)
    }

    @Test
    fun `turns blank optional fields into null`() {
        val entity = articleDto(1, description = "   ", author = "", urlToImage = " ", content = "")
            .toEntityOrNull(savedAt)

        assertNotNull(entity)
        assertNull(entity?.description)
        assertNull(entity?.author)
        assertNull(entity?.imageUrl)
        assertNull(entity?.content)
    }

    @Test
    fun `drops articles without a usable title`() {
        assertNull(articleDto(1, title = null).toEntityOrNull(savedAt))
        assertNull(articleDto(1, title = "   ").toEntityOrNull(savedAt))
        assertNull(articleDto(1, title = "[Removed]").toEntityOrNull(savedAt))
    }

    @Test
    fun `drops articles without a usable link`() {
        assertNull(articleDto(1, url = null).toEntityOrNull(savedAt))
        assertNull(articleDto(1, url = "").toEntityOrNull(savedAt))
        assertNull(articleDto(1, url = "[Removed]").toEntityOrNull(savedAt))
        assertNull(articleDto(1, url = "javascript:alert(1)").toEntityOrNull(savedAt))
        assertNull(articleDto(1, url = "intent://scan/#Intent;end").toEntityOrNull(savedAt))
    }

    @Test
    fun `ignores an author that is a web address`() {
        val entity = articleDto(1, author = "https://www.example.com/profile/jane").toEntityOrNull(savedAt)

        assertNull(entity?.author)
    }

    @Test
    fun `upgrades plain http images to https`() {
        val entity = articleDto(1, urlToImage = "http://img.example.com/a.jpg").toEntityOrNull(savedAt)

        assertEquals("https://img.example.com/a.jpg", entity?.imageUrl)
    }

    @Test
    fun `drops image values that are not web addresses`() {
        val entity = articleDto(1, urlToImage = "file:///sdcard/a.jpg").toEntityOrNull(savedAt)

        assertNull(entity?.imageUrl)
    }

    @Test
    fun `removes the truncation note from the content`() {
        val entity = articleDto(1, content = "First part of the story… [+2431 chars]").toEntityOrNull(savedAt)

        assertEquals("First part of the story…", entity?.content)
    }

    @Test
    fun `parses the publish date`() {
        val entity = articleDto(1, publishedAt = PUBLISHED_AT).toEntityOrNull(savedAt)

        assertEquals(Instant.parse(PUBLISHED_AT).toEpochMilli(), entity?.publishedAt)
    }

    @Test
    fun `keeps the article when the publish date is invalid`() {
        val entity = articleDto(1, publishedAt = "yesterday").toEntityOrNull(savedAt)

        assertNotNull(entity)
        assertNull(entity?.publishedAt)
    }

    @Test
    fun `removes invalid articles and duplicates from a list`() {
        val entities = listOf(
            articleDto(1),
            articleDto(2, title = "[Removed]"),
            articleDto(3),
            articleDto(1, title = "Same link again"),
            articleDto(4, url = null),
        ).toEntities(savedAt)

        assertEquals(listOf(articleUrl(1), articleUrl(3)), entities.map { it.url })
        assertEquals("Title 1", entities.first().title)
    }

    @Test
    fun `converts an entity to the domain model`() {
        val article = articleEntity(1).toDomain()

        assertEquals(articleUrl(1), article.url)
        assertEquals("Title 1", article.title)
        assertEquals(Instant.parse(PUBLISHED_AT), article.publishedAt)
    }
}
