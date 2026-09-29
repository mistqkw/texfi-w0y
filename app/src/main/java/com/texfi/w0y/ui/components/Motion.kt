package com.texfi.w0y.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Появление «лесенкой»: элемент выезжает снизу с лёгким перелётом.
 *
 * Пружина читается только в слое отрисовки, поэтому содержимое при
 * анимации не пересобирается. Индекс задаёт задержку — элементы приходят
 * по очереди, а не все разом.
 */
@Composable
fun Stagger(
    index: Int,
    modifier: Modifier = Modifier,
    stepMs: Long = 22,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * stepMs)
        progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 900f))
    }
    Box(
        modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * 26.dp.toPx()
        },
    ) { content() }
}
