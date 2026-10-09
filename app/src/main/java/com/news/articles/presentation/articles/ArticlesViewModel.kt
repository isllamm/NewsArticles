package com.news.articles.presentation.articles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.news.articles.core.mvi.MviViewModel
import com.news.articles.domain.model.AppError
import com.news.articles.domain.model.AppResult
import com.news.articles.domain.model.Article
import com.news.articles.domain.usecase.ObserveTopHeadlinesUseCase
import com.news.articles.domain.usecase.RefreshTopHeadlinesUseCase
import com.news.articles.domain.usecase.SearchArticlesUseCase
import com.news.articles.presentation.model.toUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArticlesViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val observeTopHeadlines: ObserveTopHeadlinesUseCase,
    private val refreshTopHeadlines: RefreshTopHeadlinesUseCase,
    private val searchArticles: SearchArticlesUseCase,
) : MviViewModel<ArticlesIntent, ArticlesState, ArticlesEffect>(
    ArticlesState(query = savedStateHandle.get<String>(KEY_QUERY).orEmpty().take(MAX_QUERY_LENGTH)),
) {

    private val query = MutableStateFlow(state.value.query)
    private val searchRetries = MutableStateFlow(0)
    private val refreshStatus = MutableStateFlow<RefreshStatus>(RefreshStatus.Refreshing)
    private var refreshJob: Job? = null

    init {
        observeContent()
        refreshHeadlines()
    }

    override fun onIntent(intent: ArticlesIntent) {
        when (intent) {
            is ArticlesIntent.QueryChanged -> updateQuery(intent.query)
            ArticlesIntent.ClearQuery -> updateQuery("")
            ArticlesIntent.Refresh -> refresh()
            is ArticlesIntent.ArticleClicked -> sendEffect(ArticlesEffect.OpenDetails(intent.url))
        }
    }

    private fun updateQuery(value: String) {
        val newQuery = value.take(MAX_QUERY_LENGTH)
        savedStateHandle[KEY_QUERY] = newQuery
        query.value = newQuery
        setState { copy(query = newQuery) }
    }

    private fun refresh() {
        if (query.value.isBlank()) {
            refreshHeadlines()
        } else {
            searchRetries.update { it + 1 }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeContent() {
        viewModelScope.launch {
            query
                .map { it.trim() }
                .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
                .distinctUntilChanged()
                .combine(searchRetries) { activeQuery, _ -> activeQuery }
                .collectLatest { activeQuery ->
                    if (activeQuery.isEmpty()) showHeadlines() else search(activeQuery)
                }
        }
    }

    private suspend fun showHeadlines() {
        combine(observeTopHeadlines(), refreshStatus) { articles, status -> articles to status }
            .collect { (articles, status) -> setState { withHeadlines(articles, status) } }
    }

    private suspend fun search(searchQuery: String) {
        setState {
            copy(
                content = if (content is ArticlesContent.Items) content else ArticlesContent.Loading,
                isRefreshing = false,
                isSearching = true,
            )
        }
        when (val result = searchArticles(searchQuery)) {
            is AppResult.Success -> setState {
                copy(
                    content = result.data.articles.toContent(searchQuery),
                    isSearching = false,
                    isShowingSavedArticles = result.data.isFromCache,
                )
            }
            is AppResult.Failure -> setState {
                copy(
                    content = ArticlesContent.Error(result.error),
                    isSearching = false,
                    isShowingSavedArticles = false,
                )
            }
        }
    }

    private fun refreshHeadlines() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshStatus.value = RefreshStatus.Refreshing
            when (val result = refreshTopHeadlines()) {
                is AppResult.Success -> refreshStatus.value = RefreshStatus.Succeeded
                is AppResult.Failure -> {
                    refreshStatus.value = RefreshStatus.Failed(result.error)
                    if (query.value.isBlank() && observeTopHeadlines().first().isNotEmpty()) {
                        sendEffect(ArticlesEffect.ShowError(result.error))
                    }
                }
            }
        }
    }

    private fun ArticlesState.withHeadlines(articles: List<Article>, status: RefreshStatus): ArticlesState {
        val hasArticles = articles.isNotEmpty()
        return copy(
            content = when {
                hasArticles -> articles.toContent(query = "")
                status is RefreshStatus.Failed -> ArticlesContent.Error(status.error)
                status is RefreshStatus.Refreshing -> ArticlesContent.Loading
                else -> ArticlesContent.Empty(query = "")
            },
            isRefreshing = hasArticles && status is RefreshStatus.Refreshing,
            isSearching = false,
            isShowingSavedArticles = hasArticles && status is RefreshStatus.Failed,
        )
    }

    private fun List<Article>.toContent(query: String): ArticlesContent =
        if (isEmpty()) {
            ArticlesContent.Empty(query)
        } else {
            ArticlesContent.Items(map(Article::toUi).toImmutableList())
        }

    private sealed interface RefreshStatus {
        data object Refreshing : RefreshStatus
        data object Succeeded : RefreshStatus
        data class Failed(val error: AppError) : RefreshStatus
    }

    internal companion object {
        const val KEY_QUERY = "query"
        const val SEARCH_DEBOUNCE_MS = 400L
        const val MAX_QUERY_LENGTH = 100
    }
}
