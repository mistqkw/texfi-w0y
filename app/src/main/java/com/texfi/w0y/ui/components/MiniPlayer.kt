package com.texfi.w0y.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
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
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalW0yColors.current
    AnimatedVisibility(
        visible = state.song != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val song = state.song ?: return@AnimatedVisibility
        var position by remember { mutableLongStateOf(0L) }
        LaunchedEffect(state.isPlaying, song.id) {
            while (true) {
                position = positionProvider()
                delay(500)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceHigh),
        ) {
            val progress =
                if (state.durationMs > 0) {
                    (position.toFloat() / state.durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.border),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .background(colors.secondary),
                )
            }
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
                            .size(40.dp)
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
                        text = if (state.isBuffering) "Загружаю…" else song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        maxLines = 1,
                    )
                }
                PixelSprite(
                    rows = if (state.isPlaying) Sprites.pause else Sprites.play,
                    color = colors.text,
                    modifier =
                        Modifier
                            .size(22.dp)
                            .clickable(onClick = onToggle),
                )
                Spacer(Modifier.width(16.dp))
                PixelSprite(
                    rows = Sprites.next,
                    color = colors.textMuted,
                    modifier =
                        Modifier
                            .size(22.dp)
                            .clickable(onClick = onNext),
                )
            }
        }
    }
}
