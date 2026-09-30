package com.texfi.w0y.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.playback.PlayerUiState
import com.texfi.w0y.ui.theme.LocalW0yColors
import kotlinx.coroutines.delay

/**
 * Мини-плеер над навигацией. Полоса прогресса — янтарная: это
 * единственное место, где живёт собственный акцент w0y.
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    positionProvider: () -> Long,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalW0yColors.current
    AnimatedVisibility(
        visible = state.song != null,
        enter = expandVertically(androidx.compose.animation.core.tween(W0yMotion.FAST_MS, easing = W0yMotion.Step)),
        exit = shrinkVertically(androidx.compose.animation.core.tween(W0yMotion.FAST_MS, easing = W0yMotion.Step)),
        modifier = modifier,
    ) {
        val song = state.song ?: return@AnimatedVisibility
        var position by remember { mutableLongStateOf(0L) }
        // На паузе позиция стоит на месте: опрашиваем один раз, а не
        // каждые полсекунды вхолостую.
        LaunchedEffect(state.isPlaying, song.id) {
            position = positionProvider()
            while (state.isPlaying) {
                delay(500)
                position = positionProvider()
            }
        }
        val bump = remember { androidx.compose.animation.core.Animatable(1f) }
        LaunchedEffect(song.id) {
            bump.snapTo(0.78f)
            bump.animateTo(
                1f,
                androidx.compose.animation.core.tween(W0yMotion.MID_MS, easing = W0yMotion.StepBack),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceHigh),
        ) {
            val duration = state.durationMs
            // Позиция читается только при рисовании: полоса двигается,
            // а строка мини-плеера не пересобирается.
            SegmentedBar(
                progress = { if (duration > 0) position.toFloat() / duration else 0f },
                lit = colors.secondary,
                dim = colors.border,
                modifier = Modifier.fillMaxWidth(),
                height = 4.dp,
                cell = 10.dp,
            )
            // Смахивание мини-плеера: вправо — предыдущий трек, влево — следующий.
            SwipeRow(
                onSwipeRight = onPrevious,
                rightLabel = "<<",
                onSwipeLeft = onNext,
                leftLabel = ">>",
                backdrop = colors.surfaceHigh,
            ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(
                    url = song.thumbnailUrl,
                    px = Thumbnails.ROW,
                    modifier =
                        Modifier
                            .size(42.dp)
                            .graphicsLayer {
                                scaleX = bump.value
                                scaleY = bump.value
                            }
                            .border(2.dp, colors.border, RoundedCornerShape(4.dp))
                            .clickable(onClick = onExpand),
                )
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onExpand),
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                        maxLines = 1,
                    )
                    Text(
                        text = if (state.isBuffering) stringResource(R.string.common_loading) else song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        maxLines = 1,
                    )
                }
                // Кнопки с запасом вокруг: на 22dp в мини-плеере промахнуться
                // мимо «паузы» проще, чем попасть.
                PlayPauseButton(isPlaying = state.isPlaying, size = 22, onClick = onToggle, touchPadding = 8)
                TransportButton(Sprites.next, size = 22, onClick = onNext, touchPadding = 8)
            }
            }
        }
    }
}
