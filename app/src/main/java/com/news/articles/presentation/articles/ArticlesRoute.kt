package com.news.articles.presentation.articles

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.news.articles.core.mvi.CollectEffects
import com.news.articles.presentation.common.messageRes
import kotlinx.coroutines.launch

@Composable
fun ArticlesRoute(
    onOpenDetails: (String) -> Unit,
    viewModel: ArticlesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ArticlesEffect.OpenDetails -> onOpenDetails(effect.url)
            is ArticlesEffect.ShowError -> scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(resources.getString(effect.error.messageRes()))
            }
        }
    }

    ArticlesScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::onIntent,
    )
}
