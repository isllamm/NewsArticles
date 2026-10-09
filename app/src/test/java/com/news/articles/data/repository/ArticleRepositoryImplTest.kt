package com.news.articles.data.repository

import com.news.articles.data.remote.ApiKeyInterceptor
import com.news.articles.data.remote.NewsApi
import com.news.articles.domain.model.AppError
import com.news.articles.domain.model.AppResult
import com.news.articles.testutil.FakeArticleDao
import com.news.articles.testutil.articleEntity
import com.news.articles.testutil.articleUrl
import com.news.articles.testutil.articlesResponseJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ArticleRepositoryImplTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }
    private val clock = Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC)
    private val dao = FakeArticleDao()
    private lateinit var server: MockWebServer
    private lateinit var repository: ArticleRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder().addInterceptor(ApiKeyInterceptor(API_KEY)).build()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NewsApi::class.java)
        repository = ArticleRepositoryImpl(api, dao, json, clock, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        runCatching { server.close() }
    }

    private fun respondWith(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handler(request)
        }
    }

    private fun RecordedRequest.page(): String? = url.queryParameter("page")

    private fun ok(body: String): MockResponse = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun failure(status: Int, apiCode: String? = null): MockResponse {
        val builder = MockResponse.Builder().code(status)
        if (apiCode != null) {
            builder
                .addHeader("Content-Type", "application/json")
                .body("""{"status":"error","code":"$apiCode","message":"message"}""")
        }
        return builder.build()
    }

    private fun urls(vararg ids: Int): List<String> = ids.map(::articleUrl)

    @Test
    fun `refresh asks for both pages with the api key and merges them in order`() = runTest {
        respondWith { request ->
            when (request.page()) {
                "1" -> ok(articlesResponseJson(1, 2))
                "2" -> ok(articlesResponseJson(3, 4))
                else -> failure(400, "parametersMissing")
            }
        }

        val result = repository.refreshTopHeadlines()

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(urls(1, 2, 3, 4), dao.headlineUrls)
        val requests = List(2) { server.takeRequest() }
        assertEquals(setOf("1", "2"), requests.map { it.page() }.toSet())
        assertTrue(requests.all { it.headers["x-api-key"] == API_KEY })
        assertTrue(requests.all { it.url.queryParameter("country") == "us" })
        assertTrue(requests.all { it.url.encodedPath == "/v2/top-headlines" })
    }

    @Test
    fun `refresh removes duplicates across pages`() = runTest {
        respondWith { request ->
            if (request.page() == "1") ok(articlesResponseJson(1, 2)) else ok(articlesResponseJson(2, 3))
        }

        repository.refreshTopHeadlines()

        assertEquals(urls(1, 2, 3), dao.headlineUrls)
    }

    @Test
    fun `refresh skips articles that were removed by the publisher`() = runTest {
        val removed = """
            {"source":{"id":null,"name":"x"},"author":null,"title":"[Removed]","description":"[Removed]",
             "url":"https://removed.com","urlToImage":null,"publishedAt":"2026-10-08T10:00:00Z","content":"[Removed]"}
        """.trimIndent()
        respondWith { request ->
            if (request.page() == "1") ok(articlesResponseJson(1, extra = removed)) else ok(articlesResponseJson())
        }

        repository.refreshTopHeadlines()

        assertEquals(urls(1), dao.headlineUrls)
    }

    @Test
    fun `refresh replaces the older headlines`() = runTest {
        dao.replaceTopHeadlines(listOf(articleEntity(9)), staleThreshold = 0L)
        respondWith { request ->
            if (request.page() == "1") ok(articlesResponseJson(1, 2)) else ok(articlesResponseJson(3))
        }

        repository.refreshTopHeadlines()

        assertEquals(urls(1, 2, 3), dao.headlineUrls)
    }

    @Test
    fun `refresh removes old saved articles but keeps recent ones`() = runTest {
        val now = clock.millis()
        dao.upsertArticles(
            listOf(
                articleEntity(8, savedAt = now - 2 * HOUR_MS),
                articleEntity(9, savedAt = now - 48 * HOUR_MS),
            ),
        )
        respondWith { request ->
            if (request.page() == "1") ok(articlesResponseJson(1)) else ok(articlesResponseJson())
        }

        repository.refreshTopHeadlines()

        assertEquals(setOf(articleUrl(1), articleUrl(8)), dao.savedArticles.map { it.url }.toSet())
    }

    @Test
    fun `refresh keeps the saved headlines when one page fails`() = runTest {
        dao.replaceTopHeadlines(listOf(articleEntity(9)), staleThreshold = 0L)
        respondWith { request ->
            if (request.page() == "1") ok(articlesResponseJson(1, 2)) else failure(500)
        }

        val result = repository.refreshTopHeadlines()

        assertEquals(AppResult.Failure(AppError.Server), result)
        assertEquals(urls(9), dao.headlineUrls)
    }

    @Test
    fun `refresh keeps the saved headlines when the server returns nothing usable`() = runTest {
        dao.replaceTopHeadlines(listOf(articleEntity(9)), staleThreshold = 0L)
        respondWith { ok(articlesResponseJson()) }

        val result = repository.refreshTopHeadlines()

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(urls(9), dao.headlineUrls)
    }

    @Test
    fun `refresh reports an invalid api key`() = runTest {
        respondWith { failure(401, "apiKeyInvalid") }

        assertEquals(AppResult.Failure(AppError.Unauthorized), repository.refreshTopHeadlines())
    }

    @Test
    fun `refresh reports rate limiting`() = runTest {
        respondWith { failure(429, "rateLimited") }

        assertEquals(AppResult.Failure(AppError.RateLimited), repository.refreshTopHeadlines())
    }

    @Test
    fun `refresh reports an unreadable response`() = runTest {
        respondWith { ok("not json") }

        assertEquals(AppResult.Failure(AppError.Unknown), repository.refreshTopHeadlines())
    }

    @Test
    fun `refresh reports no internet when the server cannot be reached`() = runTest {
        server.close()

        assertEquals(AppResult.Failure(AppError.NoInternet), repository.refreshTopHeadlines())
    }

    @Test
    fun `search returns live results and saves them without changing the headlines`() = runTest {
        respondWith { ok(articlesResponseJson(5, 6)) }

        val result = repository.searchArticles("apple pie") as AppResult.Success

        assertFalse(result.data.isFromCache)
        assertEquals(urls(5, 6), result.data.articles.map { it.url })
        assertEquals(setOf(articleUrl(5), articleUrl(6)), dao.savedArticles.map { it.url }.toSet())
        assertTrue(dao.headlineUrls.isEmpty())
        val request = server.takeRequest()
        assertEquals("apple pie", request.url.queryParameter("q"))
        assertEquals("us", request.url.queryParameter("country"))
        assertNull(request.page())
        assertEquals(API_KEY, request.headers["x-api-key"])
    }

    @Test
    fun `search with no matches succeeds with an empty list`() = runTest {
        respondWith { ok(articlesResponseJson()) }

        val result = repository.searchArticles("zzz") as AppResult.Success

        assertTrue(result.data.articles.isEmpty())
        assertFalse(result.data.isFromCache)
    }

    @Test
    fun `search falls back to saved articles when the request fails`() = runTest {
        dao.upsertArticles(
            listOf(
                articleEntity(1, title = "Apple unveils new phone"),
                articleEntity(2, title = "Weather today"),
            ),
        )
        respondWith { failure(500) }

        val result = repository.searchArticles("apple") as AppResult.Success

        assertTrue(result.data.isFromCache)
        assertEquals(urls(1), result.data.articles.map { it.url })
    }

    @Test
    fun `search reports the error when it fails and nothing saved matches`() = runTest {
        dao.upsertArticles(listOf(articleEntity(2, title = "Weather today")))
        respondWith { failure(429, "rateLimited") }

        assertEquals(AppResult.Failure(AppError.RateLimited), repository.searchArticles("apple"))
    }

    @Test
    fun `offline search treats percent and underscore as plain text`() = runTest {
        dao.upsertArticles(
            listOf(
                articleEntity(1, title = "Prices up 100% today"),
                articleEntity(2, title = "Other news"),
            ),
        )
        server.close()

        val result = repository.searchArticles("%") as AppResult.Success

        assertEquals(urls(1), result.data.articles.map { it.url })
    }

    @Test
    fun `observing an article returns the saved one and null for an unknown link`() = runTest {
        dao.upsertArticles(listOf(articleEntity(1)))

        assertEquals(articleUrl(1), repository.observeArticle(articleUrl(1)).first()?.url)
        assertNull(repository.observeArticle(articleUrl(2)).first())
    }

    @Test
    fun `observing the headlines follows the saved order`() = runTest {
        dao.replaceTopHeadlines(listOf(articleEntity(3), articleEntity(1), articleEntity(2)), staleThreshold = 0L)

        assertEquals(urls(3, 1, 2), repository.observeTopHeadlines().first().map { it.url })
    }

    private companion object {
        const val API_KEY = "test-key"
        const val HOUR_MS = 60L * 60 * 1000
    }
}
