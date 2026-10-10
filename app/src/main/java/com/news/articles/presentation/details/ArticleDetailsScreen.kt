package com.news.articles.presentation.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.news.articles.R
import com.news.articles.presentation.common.ArticleImage
import com.news.articles.presentation.common.LoadingView
import com.news.articles.presentation.common.MessageView
import com.news.articles.presentation.common.Reveal
import com.news.articles.presentation.model.ArticleDetailsUi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailsScreen(
    state: ArticleDetailsState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onReadFullArticleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (state) {
            ArticleDetailsState.Loading -> LoadingView(Modifier.padding(padding))
            ArticleDetailsState.NotFound -> MessageView(
                message = stringResource(R.string.details_not_found),
                modifier = Modifier.padding(padding),
            )
            is ArticleDetailsState.Content -> ArticleDetailsContent(
                article = state.article,
                onReadFullArticleClick = onReadFullArticleClick,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ArticleDetailsContent(
    article: ArticleDetailsUi,
    onReadFullArticleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ArticleImage(
            imageUrl = article.imageUrl,
            contentDescription = stringResource(R.string.article_image),
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Reveal(delayMillis = 0) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Reveal(delayMillis = REVEAL_STEP_MS) {
                Text(
                    text = article.author?.let { stringResource(R.string.by_author, it) }
                        ?: stringResource(R.string.unknown_author),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            SourceAndDate(
                sourceName = article.sourceName,
                publishedAt = article.publishedAt,
                revealDelayMillis = REVEAL_STEP_MS * 2,
            )
            article.description?.let { description ->
                Reveal(delayMillis = REVEAL_STEP_MS * 3) {
                    Text(text = description, style = MaterialTheme.typography.bodyLarge)
                }
            }
            article.body?.let { body ->
                Reveal(delayMillis = REVEAL_STEP_MS * 4) {
                    Text(text = body, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Reveal(
                modifier = Modifier.fillMaxWidth(),
                delayMillis = REVEAL_STEP_MS * 5,
            ) {
                Button(
                    onClick = onReadFullArticleClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.read_full_article))
                }
            }
        }
    }
}

@Composable
private fun SourceAndDate(
    sourceName: String?,
    publishedAt: Instant?,
    revealDelayMillis: Int,
    modifier: Modifier = Modifier,
) {
    val formatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    }
    val text = listOfNotNull(sourceName, publishedAt?.let(formatter::format)).joinToString(SEPARATOR)
    if (text.isNotEmpty()) {
        Reveal(modifier = modifier, delayMillis = revealDelayMillis) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val SEPARATOR = " · "
private const val REVEAL_STEP_MS = 70
