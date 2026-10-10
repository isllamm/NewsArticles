package com.news.articles.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Reveal(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    delayMillis: Int = 0,
    offset: Dp = REVEAL_OFFSET,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    val offsetPx = with(LocalDensity.current) { offset.toPx() }

    LaunchedEffect(Unit) {
        if (enabled) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = REVEAL_DURATION_MS,
                    delayMillis = delayMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * offsetPx
        },
    ) {
        content()
    }
}

private val REVEAL_OFFSET = 24.dp
private const val REVEAL_DURATION_MS = 450
