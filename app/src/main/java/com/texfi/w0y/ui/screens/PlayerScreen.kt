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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.ui.components.AddToPlaylistPanel
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import kotlinx.coroutines.delay

/**
 * Полноэкранный плеер: обложка, перемотка, лирика, очередь.
 *
 * Полоса прогресса пиксельная и тянется пальцем; во время перетаскивания
 * показывается позиция пальца, а не то, что сейчас у плеера, — иначе
 * ползунок «дёргается» назад между кадрами.
 */
@Composable
fun PlayerScreen(
    onCollapse: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val colors = LocalW0yColors.current
    val state by viewModel.player.state.collectAsStateWithLifecycle()
    val liked by viewModel.isLiked.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val sleepLeft by viewModel.player.sleepRemainingMs.collectAsStateWithLifecycle()
    val song = state.song ?: return

    var position by remember { mutableLongStateOf(0L) }
    var dragPosition by remember { mutableStateOf<Long?>(null) }
    var showPlaylists by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(song.id) { viewModel.ensureLyrics(song) }
    LaunchedEffect(song.id, state.isPlaying) {
        while (true) {
            position = viewModel.player.positionMs()
            delay(300)
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpriteButton(Sprites.collapse, onClick = onCollapse)
                    Spacer(Modifier.width(14.dp))
                    Text("❯ ИГРАЕТ", style = PixelSectionLabel, color = colors.accent, modifier = Modifier.weight(1f))
                    SpriteButton(
                        rows = Sprites.timer,
                        onClick = { if (sleepLeft == null) viewModel.startSleepTimer() else viewModel.cancelSleepTimer() },
                        active = sleepLeft != null,
                    )
                }
                sleepLeft?.let {
                    Text(
                        text = "Таймер сна: ${it / 60_000} мин ${(it / 1000) % 60} с",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondary,
                    )
                }
            }

            item {
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
                Spacer(Modifier.height(18.dp))
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
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
                    }
                    SpriteButton(Sprites.heart, onClick = { viewModel.toggleLike(song) }, active = liked)
                    Spacer(Modifier.width(14.dp))
                    SpriteButton(Sprites.download, onClick = { viewModel.download(song) })
                    Spacer(Modifier.width(14.dp))
                    SpriteButton(Sprites.plus, onClick = { showPlaylists = true })
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                Seekbar(
                    positionMs = dragPosition ?: position,
                    durationMs = state.durationMs,
                    onDrag = { dragPosition = it },
                    onDragEnd = {
                        dragPosition?.let(viewModel.player::seekTo)
                        dragPosition = null
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                Spacer(Modifier.height(18.dp))
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpriteButton(Sprites.shuffle, onClick = viewModel.player::toggleShuffle, active = state.shuffle)
                    PixelSprite(
                        rows = Sprites.previous,
                        color = colors.text,
                        modifier = Modifier.size(28.dp).clickable(onClick = viewModel.player::skipPrevious),
                    )
                    PixelSprite(
                        rows = if (state.isPlaying) Sprites.pause else Sprites.play,
                        color = colors.accent,
                        modifier = Modifier.size(40.dp).clickable(onClick = viewModel.player::togglePlayPause),
                    )
                    PixelSprite(
                        rows = Sprites.next,
                        color = colors.text,
                        modifier = Modifier.size(28.dp).clickable { viewModel.player.skipNext() },
                    )
                    SpriteButton(
                        rows = Sprites.repeat,
                        onClick = viewModel.player::cycleRepeat,
                        active = state.repeatMode != Player.REPEAT_MODE_OFF,
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
                Spacer(Modifier.height(20.dp))
            }

            lyrics?.let { text ->
                item { Text("❯ ЛИРИКА", style = PixelSectionLabel, color = colors.accent) }
                if (text.synced.isNotEmpty()) {
                    val current = dragPosition ?: position
                    val activeIndex = text.synced.indexOfLast { it.timeMs <= current }
                    itemsIndexed(text.synced, key = { index, line -> "$index-${line.timeMs}" }) { index, line ->
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (index == activeIndex) colors.accent else colors.textMuted,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.player.seekTo(line.timeMs) }
                                    .padding(vertical = 3.dp),
                        )
                    }
                } else {
                    item {
                        Text(
                            text = text.plain.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                        )
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }

            item { Text("❯ ОЧЕРЕДЬ", style = PixelSectionLabel, color = colors.accent) }
            itemsIndexed(state.queue, key = { index, item -> "$index-${item.id}" }) { index, item ->
                val active = index == state.currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.player.playAt(index) }
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
            item { Spacer(Modifier.height(30.dp)) }
        }

        if (showPlaylists) {
            AddToPlaylistPanel(
                song = song,
                playlists = playlists.map { it.id to it.name },
                newName = newPlaylistName,
                onNewNameChange = { newPlaylistName = it },
                onPick = { id ->
                    viewModel.addToPlaylist(id, song)
                    showPlaylists = false
                },
                onCreate = { name ->
                    viewModel.createPlaylistWith(name, song)
                    newPlaylistName = null
                    showPlaylists = false
                },
                onDismiss = {
                    showPlaylists = false
                    newPlaylistName = null
                },
            )
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
