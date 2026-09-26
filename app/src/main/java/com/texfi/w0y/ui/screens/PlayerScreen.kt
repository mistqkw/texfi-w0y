package com.texfi.w0y.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.texfi.w0y.R
import com.texfi.w0y.data.QueueMode
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.SoundPreset
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.components.AddToPlaylistPanel
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.ExplicitBadge
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.PixelSegmented
import com.texfi.w0y.ui.components.SectionHeader
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.PlayPauseButton
import com.texfi.w0y.ui.components.TransportButton
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.nav.BrowseRoute
import com.texfi.w0y.ui.nav.LocalBrowseNavigator
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
    val navigator = LocalBrowseNavigator.current
    val state by viewModel.player.state.collectAsStateWithLifecycle()
    val liked by viewModel.isLiked.collectAsStateWithLifecycle()
    val pinned by viewModel.isPinned.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val queueMode by viewModel.queueMode.collectAsStateWithLifecycle()
    val radioLoading by viewModel.loadingRadio.collectAsStateWithLifecycle()
    val findingClean by viewModel.findingClean.collectAsStateWithLifecycle()
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
                    Text(stringResource(R.string.player_now_playing), style = PixelSectionLabel, color = colors.accent, modifier = Modifier.weight(1f))
                    SpriteButton(
                        rows = Sprites.timer,
                        onClick = { if (sleepLeft == null) viewModel.startSleepTimer() else viewModel.cancelSleepTimer() },
                        active = sleepLeft != null,
                    )
                }
                if (findingClean) {
                    Text(
                        text = stringResource(R.string.player_finding_clean),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accent,
                    )
                }
                sleepLeft?.let {
                    Text(
                        text = stringResource(R.string.player_sleep_timer, it / 60_000, (it / 1000) % 60),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondary,
                    )
                }
            }

            item {
                // Обложка на весь экран — единственное место, где нужен
                // самый крупный вариант картинки.
                Box(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .offset(5.dp, 5.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.shadow),
                    )
                    CoverImage(
                        url = song.thumbnailUrl,
                        px = Thumbnails.HERO,
                        corner = 10,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .border(2.dp, colors.border, RoundedCornerShape(10.dp)),
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (song.explicit) {
                            ExplicitBadge()
                            Spacer(Modifier.height(6.dp))
                        }
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
                    SpriteButton(Sprites.pin, onClick = { viewModel.togglePin(song) }, active = pinned)
                    Spacer(Modifier.width(14.dp))
                    SpriteButton(Sprites.download, onClick = { viewModel.download(song) })
                    Spacer(Modifier.width(14.dp))
                    SpriteButton(Sprites.plus, onClick = { showPlaylists = true })
                }
                // Переход к артисту и альбому прямо из плеера: из него чаще
                // всего и хочется уйти «послушать, что ещё у них есть».
                if (song.artistId != null || song.albumId != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        song.artistId?.let { id ->
                            PixelButton(
                                text = stringResource(R.string.player_to_artist),
                                fill = colors.surfaceHigh,
                                onClick = {
                                    onCollapse()
                                    navigator.open(BrowseRoute.Artist(id, song.artist, song.thumbnailUrl))
                                },
                            )
                        }
                        song.albumId?.let { id ->
                            PixelButton(
                                text = stringResource(R.string.player_to_album),
                                fill = colors.surfaceHigh,
                                onClick = {
                                    onCollapse()
                                    navigator.open(
                                        BrowseRoute.Album(id, song.album ?: song.title, song.thumbnailUrl),
                                    )
                                },
                            )
                        }
                    }
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
                    TransportButton(Sprites.previous, size = 28, onClick = viewModel.player::skipPrevious)
                    PlayPauseButton(
                        isPlaying = state.isPlaying,
                        size = 40,
                        onClick = viewModel.player::togglePlayPause,
                    )
                    TransportButton(Sprites.next, size = 28, onClick = viewModel.player::skipNext)
                    SpriteButton(
                        rows = Sprites.repeat,
                        onClick = viewModel.player::cycleRepeat,
                        active = state.repeatMode != Player.REPEAT_MODE_OFF,
                    )
                }
                if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                    Text(
                        text = stringResource(R.string.player_repeat_one),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            item {
                SectionHeader(stringResource(R.string.player_sound))
                Spacer(Modifier.height(10.dp))
                PixelSegmented(
                    options = SoundPreset.entries.map { stringResource(it.label) },
                    // −1 значит «ни один»: когда значения подкручены руками
                    // в настройках, подсвечивать готовый пресет было бы враньём.
                    selectedIndex = SoundPreset.entries.indexOfFirst { it.matches(settings) },
                    onSelect = { viewModel.setSoundPreset(SoundPreset.entries[it]) },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text =
                        buildString {
                            append(stringResource(R.string.player_sound_speed, settings.speed.toString()))
                            if (settings.pitch != 1f) {
                                append(" ")
                                append(stringResource(R.string.player_sound_pitch, settings.pitch.toString()))
                            }
                            if (settings.reverb != Reverb.OFF) {
                                append(" ")
                                append(
                                    stringResource(
                                        R.string.player_sound_reverb,
                                        stringResource(settings.reverb.label).lowercase(),
                                    ),
                                )
                            }
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(20.dp))
            }

            item {
                SectionHeader(stringResource(R.string.player_next_up))
                Spacer(Modifier.height(10.dp))
                PixelSegmented(
                    options = QueueMode.entries.map { stringResource(it.label) },
                    selectedIndex = QueueMode.entries.indexOf(queueMode),
                    onSelect = { viewModel.setQueueMode(QueueMode.entries[it]) },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (radioLoading) stringResource(R.string.player_finding_similar) else stringResource(queueMode.hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (radioLoading) colors.accent else colors.textMuted,
                )
                Spacer(Modifier.height(20.dp))
            }

            lyrics?.let { text ->
                item {
                    SectionHeader(stringResource(R.string.player_lyrics))
                    Spacer(Modifier.height(8.dp))
                }
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

            item {
                SectionHeader(stringResource(R.string.player_queue))
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(state.queue, key = { index, item -> "$index-${item.id}" }) { index, item ->
                val active = index == state.currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
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
    var dragging by remember { mutableStateOf(false) }
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // Своя анимация на перемотку: пока тянут, бегунок и полоса вырастают.
    // Палец закрывает бегунок собой, и без этого непонятно, ведёшь ты
    // перемотку или просто скроллишь экран.
    val thumb by animateDpAsState(
        targetValue = if (dragging) 18.dp else 12.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "seekThumb",
    )
    val track by animateDpAsState(
        targetValue = if (dragging) 8.dp else 6.dp,
        animationSpec = tween(140),
        label = "seekTrack",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            // Ширина берётся из измерения, а не из жеста: иначе бегунок
            // стоит в левом краю, пока по нему не проведут пальцем.
            .onSizeChanged { widthPx = it.width }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        onDragEnd()
                    },
                    onDragCancel = {
                        dragging = false
                        onDragEnd()
                    },
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
                .height(track)
                .background(colors.surfaceHigh),
        )
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(track)
                .background(colors.secondary),
        )
        // Квадратный бегунок вместо круглой Material-ручки: круг здесь
        // читается как чужой элемент поверх пиксельной графики.
        Box(
            Modifier
                .padding(start = with(density) { (widthPx * progress).toDp() })
                .size(thumb)
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
