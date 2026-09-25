package com.texfi.w0y.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.texfi.w0y.playback.PlayerUiState
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import kotlinx.coroutines.delay

/**
 * Полноэкранный плеер: обложка, перемотка, очередь.
 *
 * Полоса прогресса пиксельная и тянется пальцем; во время перетаскивания
 * показывается позиция пальца, а не то, что сейчас у плеера, — иначе
 * ползунок «дёргается» назад между кадрами.
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    positionProvider: () -> Long,
    onCollapse: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onPlayAt: (Int) -> Unit,
) {
    val colors = LocalW0yColors.current
    val song = state.song ?: return
    var position by remember { mutableLongStateOf(0L) }
    var dragPosition by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(song.id, state.isPlaying) {
        while (true) {
            position = positionProvider()
            delay(400)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelSprite(
                rows = Sprites.collapse,
                color = colors.textMuted,
                modifier =
                    Modifier
                        .size(22.dp)
                        .clickable(onClick = onCollapse),
            )
            Spacer(Modifier.width(14.dp))
            Text("❯ ИГРАЕТ", style = PixelSectionLabel, color = colors.accent)
        }

        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceHigh),
        )

        Spacer(Modifier.height(20.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.text,
            maxLines = 2,
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            maxLines = 1,
        )

        Spacer(Modifier.height(18.dp))
        Seekbar(
            positionMs = dragPosition ?: position,
            durationMs = state.durationMs,
            onDrag = { dragPosition = it },
            onDragEnd = {
                dragPosition?.let(onSeek)
                dragPosition = null
            },
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(dragPosition ?: position),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
            Text(
                text = formatTime(state.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelSprite(
                rows = Sprites.shuffle,
                color = if (state.shuffle) colors.accent else colors.textMuted,
                modifier = Modifier.size(22.dp).clickable(onClick = onShuffle),
            )
            PixelSprite(
                rows = Sprites.previous,
                color = colors.text,
                modifier = Modifier.size(28.dp).clickable(onClick = onPrevious),
            )
            PixelSprite(
                rows = if (state.isPlaying) Sprites.pause else Sprites.play,
                color = colors.accent,
                modifier = Modifier.size(40.dp).clickable(onClick = onToggle),
            )
            PixelSprite(
                rows = Sprites.next,
                color = colors.text,
                modifier = Modifier.size(28.dp).clickable(onClick = onNext),
            )
            PixelSprite(
                rows = Sprites.repeat,
                color = if (state.repeatMode == Player.REPEAT_MODE_OFF) colors.textMuted else colors.accent,
                modifier = Modifier.size(22.dp).clickable(onClick = onRepeat),
            )
        }
        if (state.repeatMode == Player.REPEAT_MODE_ONE) {
            Text(
                text = "повтор одного трека",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(Modifier.height(22.dp))
        Text("❯ ОЧЕРЕДЬ", style = PixelSectionLabel, color = colors.accent)
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(state.queue, key = { index, item -> "$index-${item.id}" }) { index, item ->
                val active = index == state.currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPlayAt(index) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) colors.accent else colors.textMuted,
                        modifier = Modifier.width(28.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (active) colors.accent else colors.text,
                            maxLines = 1,
                        )
                        Text(
                            text = item.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Seekbar(
    positionMs: Long,
    durationMs: Long,
    onDrag: (Long) -> Unit,
    onDragEnd: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val density = LocalDensity.current
    var widthPx by remember { mutableStateOf(1) }
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Box(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            // Ширина берётся из измерения, а не из жеста: иначе бегунок
            // стоит в левом краю, пока по нему не проведут пальцем.
            .onSizeChanged { widthPx = it.width }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                ) { change, _ ->
                    if (durationMs > 0) {
                        val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                        onDrag((ratio * durationMs).toLong())
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(colors.surfaceHigh),
        )
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(6.dp)
                .background(colors.secondary),
        )
        // Квадратный бегунок вместо круглой Material-ручки: круг здесь
        // читается как чужой элемент поверх пиксельной графики.
        Box(
            Modifier
                .padding(start = with(density) { (widthPx * progress).toDp() })
                .size(12.dp)
                .background(colors.text),
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
