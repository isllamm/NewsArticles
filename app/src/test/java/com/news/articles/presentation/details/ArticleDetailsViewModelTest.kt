package com.news.articles.presentation.details

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.news.articles.domain.usecase.ObserveArticleUseCase
import com.news.articles.presentation.model.toDetailsUi
import com.news.articles.presentation.navigation.ArticleDetailsDestination
import com.news.articles.testutil.FakeArticleRepository
import com.news.articles.testutil.MainDispatcherRule
import com.news.articles.testutil.article
import com.news.articles.testutil.articleUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeArticleRepository()

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(
            mapOf(ArticleDetailsDestination::url.name to articleUrl(1)),
        ),
    ) = ArticleDetailsViewModel(
        savedStateHandle = savedStateHandle,
        observeArticle = ObserveArticleUseCase(repository),
    )

    private fun handleFor(url: String) = SavedStateHandle(mapOf(ArticleDetailsDestination::url.name to url))

    @Test
    fun `starts in the loading state`() = runTest {
        val viewModel = createViewModel()

        assertEquals(ArticleDetailsState.Loading, viewModel.state.value)
    }

    @Test
    fun `shows the article when it exists`() = runTest {
        val saved = article(1)
        repository.articles.value = mapOf(saved.url to saved)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ArticleDetailsState.Content(saved.toDetailsUi()), viewModel.state.value)
    }

    @Test
    fun `shows only the article that matches the link`() = runTest {
        val other = article(2)
        repository.articles.value = mapOf(other.url to other)
        val viewModel = createViewModel(handleFor(articleUrl(1)))
        advanceUntilIdle()

        assertEquals(ArticleDetailsState.NotFound, viewModel.state.value)
    }

    @Test
    fun `shows not found when the article is missing`() = runTest {
        val viewModel = createViewModel(handleFor(articleUrl(99)))
        advanceUntilIdle()

        assertEquals(ArticleDetailsState.NotFound, viewModel.state.value)
    }

    @Test
    fun `shows not found when the link argument is missing`() = runTest {
        val viewModel = createViewModel(SavedStateHandle())
        advanceUntilIdle()

        assertEquals(ArticleDetailsState.NotFound, viewModel.state.value)
    }

    @Test
    fun `follows changes to the saved article`() = runTest {
        val viewModel = createViewModel()
        runCurrent()
        assertEquals(ArticleDetailsState.NotFound, viewModel.state.value)

        val saved = article(1, title = "Updated")
        repository.articles.value = mapOf(saved.url to saved)
        advanceUntilIdle()

        assertEquals(ArticleDetailsState.Content(saved.toDetailsUi()), viewModel.state.value)
    }

    @Test
    fun `read full article opens the article link`() = runTest {
        val viewModel = createViewModel(handleFor(articleUrl(1)))

        viewModel.onIntent(ArticleDetailsIntent.ReadFullArticleClicked)

        viewModel.effects.test {
            assertEquals(ArticleDetailsEffect.OpenInBrowser(articleUrl(1)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
