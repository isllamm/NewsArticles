package com.news.articles.presentation.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.news.articles.core.mvi.MviViewModel
import com.news.articles.domain.usecase.ObserveArticleUseCase
import com.news.articles.presentation.model.toDetailsUi
import com.news.articles.presentation.navigation.ArticleDetailsDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class ArticleDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeArticle: ObserveArticleUseCase,
) : MviViewModel<ArticleDetailsIntent, ArticleDetailsState, ArticleDetailsEffect>(ArticleDetailsState.Loading) {

    private val url = savedStateHandle.get<String>(ArticleDetailsDestination::url.name).orEmpty()

    init {
        observeArticle(url)
            .onEach { article ->
                setState {
                    article?.let { ArticleDetailsState.Content(it.toDetailsUi()) } ?: ArticleDetailsState.NotFound
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: ArticleDetailsIntent) {
        when (intent) {
            ArticleDetailsIntent.ReadFullArticleClicked -> sendEffect(ArticleDetailsEffect.OpenInBrowser(url))
        }
    }
}
