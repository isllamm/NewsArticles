package com.news.articles.presentation.articles

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.news.articles.domain.model.AppError
import com.news.articles.domain.model.AppResult
import com.news.articles.domain.model.Article
import com.news.articles.domain.model.SearchResult
import com.news.articles.domain.usecase.ObserveTopHeadlinesUseCase
import com.news.articles.domain.usecase.RefreshTopHeadlinesUseCase
import com.news.articles.domain.usecase.SearchArticlesUseCase
import com.news.articles.presentation.articles.ArticlesViewModel.Companion.KEY_QUERY
import com.news.articles.presentation.articles.ArticlesViewModel.Companion.MAX_QUERY_LENGTH
import com.news.articles.presentation.articles.ArticlesViewModel.Companion.SEARCH_DEBOUNCE_MS
import com.news.articles.testutil.FakeArticleRepository
import com.news.articles.testutil.MainDispatcherRule
import com.news.articles.testutil.article
import com.news.articles.testutil.articleUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArticlesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeArticleRepository()

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) = ArticlesViewModel(
        savedStateHandle = savedStateHandle,
        observeTopHeadlines = ObserveTopHeadlinesUseCase(repository),
        refreshTopHeadlines = RefreshTopHeadlinesUseCase(repository),
        searchArticles = SearchArticlesUseCase(repository),
    )

    private fun ArticlesState.itemUrls(): List<String> =
        (content as ArticlesContent.Items).articles.map { it.url }

    private fun found(vararg articles: Article, fromCache: Boolean = false) =
        AppResult.Success(SearchResult(articles.toList(), fromCache))

    @Test
    fun `shows loading until the first refresh finishes`() = runTest {
        repository.refreshDelayMs = 1_000
        val viewModel = createViewModel()
        runCurrent()

        assertEquals(ArticlesContent.Loading, viewModel.state.value.content)

        repository.headlines.value = listOf(article(1), article(2))
        advanceUntilIdle()

        assertEquals(listOf(articleUrl(1), articleUrl(2)), viewModel.state.value.itemUrls())
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun `shows saved headlines straight away while refreshing in the background`() = runTest {
        repository.headlines.value = listOf(article(1))
        repository.refreshDelayMs = 1_000
        val viewModel = createViewModel()
        runCurrent()

        assertEquals(listOf(articleUrl(1)), viewModel.state.value.itemUrls())
        assertTrue(viewModel.state.value.isRefreshing)

        advanceUntilIdle()

        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun `shows an error when the first refresh fails and nothing is saved`() = runTest {
        repository.refreshResult = AppResult.Failure(AppError.NoInternet)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ArticlesContent.Error(AppError.NoInternet), viewModel.state.value.content)
        assertFalse(viewModel.state.value.isShowingSavedArticles)
        viewModel.effects.test { expectNoEvents() }
    }

    @Test
    fun `keeps saved headlines and reports the error when a refresh fails`() = runTest {
        repository.headlines.value = listOf(article(1))
        repository.refreshResult = AppResult.Failure(AppError.Timeout)
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf(articleUrl(1)), state.itemUrls())
        assertTrue(state.isShowingSavedArticles)
        assertFalse(state.isRefreshing)
        viewModel.effects.test {
            assertEquals(ArticlesEffect.ShowError(AppError.Timeout), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows the empty state when there are no headlines`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ArticlesContent.Empty(query = ""), viewModel.state.value.content)
    }

    @Test
    fun `updates the query straight away but searches only after the debounce`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ArticlesIntent.QueryChanged("apple"))
        advanceTimeBy(SEARCH_DEBOUNCE_MS - 1)
        runCurrent()

        assertEquals("apple", viewModel.state.value.query)
        assertTrue(repository.searchQueries.isEmpty())

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("apple"), repository.searchQueries)
    }

    @Test
    fun `searches only for the final query when typing quickly`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ArticlesIntent.QueryChanged("a"))
        advanceTimeBy(100)
        viewModel.onIntent(ArticlesIntent.QueryChanged("ap"))
        advanceTimeBy(100)
        viewModel.onIntent(ArticlesIntent.QueryChanged("app"))
        advanceUntilIdle()

        assertEquals(listOf("app"), repository.searchQueries)
    }

    @Test
    fun `trims the query and does not repeat an identical search`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ArticlesIntent.QueryChanged("apple"))
        advanceUntilIdle()
        viewModel.onIntent(ArticlesIntent.QueryChanged("  apple "))
        advanceUntilIdle()

        assertEquals(listOf("apple"), repository.searchQueries)
    }

    @Test
    fun `shows only the latest search when an earlier one is still running`() = runTest {
        repository.searchHandler = { query ->
            if (query == "slow") {
                delay(5_000)
                found(article(1))
            } else {
                found(article(2))
            }
        }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ArticlesIntent.QueryChanged("slow"))
        advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
        viewModel.onIntent(ArticlesIntent.QueryChanged("fast"))
        advanceUntilIdle()

        assertEquals(listOf(articleUrl(2)), viewModel.state.value.itemUrls())
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun `shows the headlines and skips the search when the query is blank`() = runTest {
        repository.headlines.value = listOf(article(1))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ArticlesIntent.QueryChanged("   "))
        advanceUntilIdle()

        assertTrue(repository.searchQueries.isEmpty())
        assertEquals(listOf(articleUrl(1)), viewModel.state.value.itemUrls())
    }

    @Test
    fun `shows search results and keeps the saved flag off for live results`() = runTest {
        repository.searchHandler = { found(article(5), article(6)) }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ArticlesIntent.QueryChanged("news"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf(articleUrl(5), articleUrl(6)), state.itemUrls())
        assertFalse(state.isSearching)
        assertFalse(state.isShowingSavedArticles)
    }

    @Test
    fun `keeps the previous results visible while a new search is running`() = runTest {
        repository.searchHandler = { query ->
            if (query == "first") {
                found(article(1))
            } else {
                delay(2_000)
                found(article(2))
            }
        }
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(ArticlesIntent.QueryChanged("first"))
        advanceUntilIdle()

        viewModel.onIntent(ArticlesIntent.QueryChanged("second"))
        advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
        runCurrent()

        assertEquals(listOf(articleUrl(1)), viewModel.state.value.itemUrls())
        assertTrue(viewModel.state.value.isSearching)

        advanceUntilIdle()

        assertEquals(listOf(articleUrl(2)), viewModel.state.value.itemUrls())
    }

    @Test
    fun `shows the empty state with the query when a search finds nothing`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ArticlesIntent.QueryChanged("zzz"))
        advanceUntilIdle()

        assertEquals(ArticlesContent.Empty(query = "zzz"), viewModel.state.value.content)
    }

    @Test
    fun `shows an error when a search fails`() = runTest {
        repository.searchHandler = { AppResult.Failure(AppError.RateLimited) }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ArticlesIntent.QueryChanged("apple"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(ArticlesContent.Error(AppError.RateLimited), state.content)
        assertFalse(state.isSearching)
    }

    @Test
    fun `flags saved articles when search results come from the cache`() = runTest {
        repository.searchHandler = { found(article(3), fromCache = true) }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ArticlesIntent.QueryChanged("apple"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isShowingSavedArticles)
    }

    @Test
    fun `clearing the query goes back to the headlines`() = runTest {
        repository.headlines.value = listOf(article(1))
        repository.searchHandler = { found(article(9)) }
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(ArticlesIntent.QueryChanged("news"))
        advanceUntilIdle()

        viewModel.onIntent(ArticlesIntent.ClearQuery)
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.query)
        assertEquals(listOf(articleUrl(1)), viewModel.state.value.itemUrls())
    }

    @Test
    fun `refresh repeats the current search`() = runTest {
        repository.searchHandler = { AppResult.Failure(AppError.NoInternet) }
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(ArticlesIntent.QueryChanged("apple"))
        advanceUntilIdle()

        repository.searchHandler = { found(article(4)) }
        viewModel.onIntent(ArticlesIntent.Refresh)
        advanceUntilIdle()

        assertEquals(listOf("apple", "apple"), repository.searchQueries)
        assertEquals(listOf(articleUrl(4)), viewModel.state.value.itemUrls())
    }

    @Test
    fun `refresh reloads the headlines when the query is blank`() = runTest {
        repository.refreshResult = AppResult.Failure(AppError.Server)
        val viewModel = createViewModel()
        advanceUntilIdle()

        repository.refreshResult = AppResult.Success(Unit)
        repository.headlines.value = listOf(article(1))
        viewModel.onIntent(ArticlesIntent.Refresh)
        advanceUntilIdle()

        assertEquals(2, repository.refreshCount)
        assertEquals(listOf(articleUrl(1)), viewModel.state.value.itemUrls())
        assertFalse(viewModel.state.value.isShowingSavedArticles)
    }

    @Test
    fun `ignores a refresh while one is already running`() = runTest {
        repository.refreshDelayMs = 1_000
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onIntent(ArticlesIntent.Refresh)
        viewModel.onIntent(ArticlesIntent.Refresh)
        advanceUntilIdle()

        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun `clicking an article sends an effect to open it`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ArticlesIntent.ArticleClicked(articleUrl(7)))

        viewModel.effects.test {
            assertEquals(ArticlesEffect.OpenDetails(articleUrl(7)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `restores the query from saved state and searches for it`() = runTest {
        val viewModel = createViewModel(SavedStateHandle(mapOf(KEY_QUERY to "apple")))
        advanceUntilIdle()

        assertEquals("apple", viewModel.state.value.query)
        assertEquals(listOf("apple"), repository.searchQueries)
    }

    @Test
    fun `stores the query in saved state`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle)
        advanceUntilIdle()

        viewModel.onIntent(ArticlesIntent.QueryChanged("abc"))

        assertEquals("abc", savedStateHandle.get<String>(KEY_QUERY))
    }

    @Test
    fun `limits the query length`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ArticlesIntent.QueryChanged("x".repeat(MAX_QUERY_LENGTH + 50)))

        assertEquals(MAX_QUERY_LENGTH, viewModel.state.value.query.length)
    }
}
