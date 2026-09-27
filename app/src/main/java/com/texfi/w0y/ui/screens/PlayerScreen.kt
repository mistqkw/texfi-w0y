package com.texfi.w0y.ui.screens

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.texfi.w0y.R
import com.texfi.w0y.data.QueueMode
import com.texfi.w0y.data.EditKind
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.SoundPreset
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.playback.OutputKind
import com.texfi.w0y.ui.components.DownloadButton
import com.texfi.w0y.ui.components.AddToPlaylistPanel
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.ExplicitBadge
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.PixelSegmented
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.PlayPauseButton
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.components.TransportButton
import com.texfi.w0y.ui.components.VersionBadge
import com.texfi.w0y.ui.components.asGlow
import com.texfi.w0y.ui.components.rememberCoverTint
import com.texfi.w0y.ui.nav.BrowseRoute
import com.texfi.w0y.ui.nav.LocalBrowseNavigator
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import kotlinx.coroutines.delay

/** Что показано под управлением. Три вкладки вместо одной длинной простыни. */
private enum class PlayerTab(@StringRes val label: Int) {
    QUEUE(R.string.player_queue),
    LYRICS(R.string.player_lyrics),
    SOUND(R.string.player_sound),
}

/**
 * Полноэкранный плеер.
 *
 * Раньше это была одна колонка на пять экранов прокрутки: очередь лежала
 * за звучанием и лирикой, и добраться до неё во время прослушивания было
 * невозможно. Теперь под управлением три вкладки, а их полоса липнет к
 * верху, пока список едет под ней. Обложку можно смахнуть в сторону —
 * это то движение, которым трек переключают, не глядя на экран.
 *
 * Полоса прогресса пиксельная и тянется пальцем; во время перетаскивания
 * показывается позиция пальца, а не то, что сейчас у плеера, — иначе
 * ползунок «дёргается» назад между кадрами.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    val output by viewModel.activeOutput.collectAsStateWithLifecycle()
    val trackSound by viewModel.trackSound.collectAsStateWithLifecycle()
    val effectiveSound by viewModel.effectiveSound.collectAsStateWithLifecycle()
    val sleepAfterTrack by viewModel.player.sleepAfterTrack.collectAsStateWithLifecycle()
    val shareFile by viewModel.shareFile.collectAsStateWithLifecycle()
    val sharing by viewModel.sharing.collectAsStateWithLifecycle()
    val edit by viewModel.edit.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val song = state.song ?: return

    // Карточка уходит через системное окно выбора: куда именно её отправить,
    // решает человек, а не приложение.
    LaunchedEffect(shareFile) {
        val file = shareFile ?: return@LaunchedEffect
        runCatching {
            val uri =
                FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "${song.title} — ${song.artist}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(Intent.createChooser(send, null))
        }
        viewModel.consumeShare()
    }

    var position by remember { mutableLongStateOf(0L) }
    var dragPosition by remember { mutableStateOf<Long?>(null) }
    var showPlaylists by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(PlayerTab.QUEUE) }

    LaunchedEffect(song.id) { viewModel.ensureLyrics(song) }
    LaunchedEffect(song.id, state.isPlaying) {
        // На паузе позиция не меняется, и опрос четыре раза в секунду был
        // просто работой вхолостую: экран перерисовывался, ничего не
        // показывая нового.
        position = viewModel.player.positionMs()
        while (state.isPlaying) {
            delay(POSITION_POLL_MS)
            position = viewModel.player.positionMs()
        }
    }

    val tint by rememberCoverTint(song.thumbnailUrl, colors.accent)
    val glow by animateColorAsState(
        targetValue = if (settings.playerCoverGlow) tint else Color.Transparent,
        animationSpec = tween(420),
        label = "coverGlow",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            // Свечение цвета обложки: один мягкий градиент сверху, без
            // блюра. Размытие поверх пиксельной графики читается как
            // чужой эффект, а плоский градиент — как её же фон.
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to glow.asGlow(GLOW_ALPHA),
                        GLOW_STOP to Color.Transparent,
                    ),
                )
            },
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
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
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.player_now_playing),
                            style = PixelSectionLabel,
                            color = colors.accent,
                        )
                        if (state.queue.size > 1) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.player_position,
                                        state.currentIndex + 1,
                                        state.queue.size,
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
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
                Box(
                    Modifier
                        .fillMaxWidth()
                        // Смахивание по обложке переключает трек: на ходу
                        // и в кармане так удобнее, чем попадать в кнопку.
                        .pointerInput(song.id) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    when {
                                        total <= -SWIPE_THRESHOLD_PX -> viewModel.player.skipNext()
                                        total >= SWIPE_THRESHOLD_PX -> viewModel.player.skipPrevious()
                                    }
                                },
                            ) { _, dragAmount -> total += dragAmount }
                        },
                ) {
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
                            style =
                                MaterialTheme.typography.bodyLarge
                                    .copy(fontWeight = FontWeight.SemiBold),
                            color = colors.text,
                            maxLines = 1,
                            // Длинное название едет само, а не обрывается
                            // многоточием: у трека с длинным именем важна
                            // как раз вторая половина.
                            modifier = Modifier.basicMarquee(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            trackSound?.takeIf { !it.isPlain }?.let { own ->
                                Spacer(Modifier.width(8.dp))
                                VersionBadge(own.label)
                            }
                        }
                    }
                    SpriteButton(Sprites.heart, onClick = { viewModel.toggleLike(song) }, active = liked)
                    Spacer(Modifier.width(14.dp))
                    SpriteButton(Sprites.pin, onClick = { viewModel.togglePin(song) }, active = pinned)
                    Spacer(Modifier.width(14.dp))
                    DownloadButton(song.id, onDownload = { viewModel.download(song) })
                    Spacer(Modifier.width(14.dp))
                    SpriteButton(Sprites.plus, onClick = { showPlaylists = true })
                    Spacer(Modifier.width(14.dp))
                    SpriteButton(
                        rows = Sprites.share,
                        onClick = viewModel::shareCard,
                        active = sharing,
                    )
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
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(dragPosition ?: position),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.weight(1f))
                    // Шаг перемотки берётся из настроек: у подкаста и у
                    // трека на две минуты удобный шаг разный.
                    StepButton(
                        text = stringResource(R.string.player_seek_back, settings.seekStepSec),
                        onClick = { viewModel.seekBy(-settings.seekStepSec * 1000L) },
                    )
                    Spacer(Modifier.width(8.dp))
                    StepButton(
                        text = stringResource(R.string.player_seek_forward, settings.seekStepSec),
                        onClick = { viewModel.seekBy(settings.seekStepSec * 1000L) },
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatTime(state.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpriteButton(Sprites.shuffle, onClick = viewModel.player::toggleShuffle, active = state.shuffle)
                    TransportButton(Sprites.previous, size = 30, onClick = viewModel.player::skipPrevious)
                    PlayPauseButton(
                        isPlaying = state.isPlaying,
                        size = 46,
                        onClick = viewModel.player::togglePlayPause,
                    )
                    TransportButton(Sprites.next, size = 30, onClick = viewModel.player::skipNext)
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
                // Куда идёт звук. Показываем только когда это не сам
                // телефон: «играет через динамик» — очевидность, а вот
                // «играет через колонку на кухне» объясняет тишину в
                // наушниках.
                output?.takeIf { it.kind != OutputKind.SPEAKER }?.let { active ->
                    Row(
                        Modifier.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelSprite(
                            rows = Sprites.headphones,
                            color = colors.accent,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text =
                                stringResource(
                                    R.string.player_output,
                                    active.name ?: stringResource(active.kind.label),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            stickyHeader {
                // Полоса вкладок липнет к верху: список под ней едет, а
                // переключиться можно не возвращаясь наверх.
                Box(Modifier.background(colors.background).padding(bottom = 10.dp)) {
                    PixelSegmented(
                        options = PlayerTab.entries.map { stringResource(it.label) },
                        selectedIndex = PlayerTab.entries.indexOf(tab),
                        onSelect = { tab = PlayerTab.entries[it] },
                    )
                }
            }

            when (tab) {
                PlayerTab.QUEUE -> {
                    itemsIndexed(state.queue, key = { index, item -> "$index-${item.id}" }) { index, item ->
                        QueueRow(
                            index = index,
                            song = item,
                            active = index == state.currentIndex,
                            onClick = { viewModel.player.playAt(index) },
                            // Играющий трек из очереди не убираем: это не
                            // «убрать из очереди», а «выключить».
                            onRemove = if (index == state.currentIndex) null else ({ viewModel.removeFromQueue(index) }),
                            onMoveUp = if (index <= state.currentIndex + 1) null else ({ viewModel.moveUpInQueue(index) }),
                        )
                    }
                    item {
                        Spacer(Modifier.height(14.dp))
                        SectionLabel(stringResource(R.string.player_next_mode))
                        Spacer(Modifier.height(8.dp))
                        PixelSegmented(
                            options = QueueMode.entries.map { stringResource(it.label) },
                            selectedIndex = QueueMode.entries.indexOf(queueMode),
                            onSelect = { viewModel.setQueueMode(QueueMode.entries[it]) },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text =
                                if (radioLoading) {
                                    stringResource(R.string.player_finding_similar)
                                } else {
                                    stringResource(queueMode.hint)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (radioLoading) colors.accent else colors.textMuted,
                        )
                        // Таймер сна живёт здесь, а не в углу шапки: это
                        // ответ на вопрос «что будет дальше», а дальше —
                        // тишина. «До конца трека» важнее круглых минут:
                        // засыпают именно так.
                        Spacer(Modifier.height(18.dp))
                        SectionLabel(stringResource(R.string.player_sleep_title))
                        Spacer(Modifier.height(8.dp))
                        val sleepOptions = listOf(null, 0, 15, 30, 60)
                        val selectedSleep =
                            when {
                                sleepAfterTrack -> 1
                                sleepLeft != null -> -1
                                else -> 0
                            }
                        PixelSegmented(
                            options =
                                sleepOptions.map { minutes ->
                                    when (minutes) {
                                        null -> stringResource(R.string.player_sleep_off)
                                        0 -> stringResource(R.string.player_sleep_track)
                                        else -> stringResource(R.string.settings_sleep_minutes, minutes)
                                    }
                                },
                            selectedIndex = selectedSleep,
                            onSelect = { index ->
                                when (val minutes = sleepOptions[index]) {
                                    null -> viewModel.cancelSleepTimer()
                                    0 -> viewModel.sleepAfterTrack()
                                    else -> viewModel.startSleepTimer(minutes)
                                }
                            },
                        )
                    }
                }

                PlayerTab.LYRICS -> {
                    val text = lyrics
                    when {
                        text == null ->
                            item {
                                Text(
                                    text = stringResource(R.string.player_lyrics_none),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textMuted,
                                )
                            }

                        text.synced.isNotEmpty() -> {
                            val current = dragPosition ?: position
                            val activeIndex = text.synced.indexOfLast { it.timeMs <= current }
                            itemsIndexed(
                                items = text.synced,
                                key = { index, line -> "$index-${line.timeMs}" },
                            ) { index, line ->
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
                        }

                        else ->
                            item {
                                Text(
                                    text = text.plain.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textMuted,
                                )
                            }
                    }
                }

                PlayerTab.SOUND ->
                    item {
                        Text(
                            text = stringResource(R.string.player_version_title),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.text,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.player_version_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                        Spacer(Modifier.height(12.dp))
                        PixelSegmented(
                            options = SoundPreset.entries.map { stringResource(it.label) },
                            // −1 значит «ни один»: когда скорость подкручена
                            // руками в настройках, подсвечивать готовый пресет
                            // было бы враньём.
                            selectedIndex = SoundPreset.entries.indexOfFirst { it.matches(effectiveSound) },
                            onSelect = { viewModel.setSoundPreset(SoundPreset.entries[it]) },
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text =
                                    buildString {
                                        append(
                                            stringResource(
                                                R.string.player_sound_speed,
                                                effectiveSound.speed.toString(),
                                            ),
                                        )
                                        if (effectiveSound.pitch != 1f) {
                                            append(" ")
                                            append(
                                                stringResource(
                                                    R.string.player_sound_pitch,
                                                    effectiveSound.pitch.toString(),
                                                ),
                                            )
                                        }
                                        if (effectiveSound.reverb != Reverb.OFF) {
                                            append(" ")
                                            append(
                                                stringResource(
                                                    R.string.player_sound_reverb,
                                                    stringResource(effectiveSound.reverb.label).lowercase(),
                                                ),
                                            )
                                        }
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                                modifier = Modifier.weight(1f),
                            )
                            // Кнопка есть только когда есть что снимать:
                            // «сбросить» при отсутствии версии — пустышка.
                            if (trackSound != null) {
                                Spacer(Modifier.width(10.dp))
                                PixelButton(
                                    text = stringResource(R.string.player_version_clear),
                                    onClick = viewModel::clearTrackSound,
                                    fill = colors.surfaceHigh,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text =
                                if (trackSound == null) {
                                    stringResource(R.string.player_version_global)
                                } else {
                                    stringResource(R.string.player_version_own)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (trackSound == null) colors.textMuted else colors.accent,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.player_sound_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                        Spacer(Modifier.height(20.dp))
                        EditFinder(songId = song.id, edit = edit, onFind = viewModel::findEdit)
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
private fun QueueRow(
    index: Int,
    song: SongItem,
    active: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    onMoveUp: (() -> Unit)?,
) {
    val colors = LocalW0yColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = if (active) colors.accent else colors.textMuted,
            modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) colors.accent else colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                song.sound?.takeIf { !it.isPlain }?.let {
                    Spacer(Modifier.width(6.dp))
                    VersionBadge(it.label)
                }
            }
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Поднять и убрать — то, чего в очереди не было вообще. Перетаскивание
        // не делаю, пока не могу проверить жест на живом телефоне: жест,
        // который срабатывает через раз, хуже кнопки.
        onMoveUp?.let {
            Spacer(Modifier.width(10.dp))
            SpriteButton(Sprites.chevronUp, onClick = it, size = 16)
        }
        onRemove?.let {
            Spacer(Modifier.width(10.dp))
            SpriteButton(Sprites.close, onClick = it, size = 16)
        }
    }
}

/** Маленькая кнопка перемотки на фиксированный шаг. */
@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surfaceHigh)
            .border(2.dp, colors.border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = colors.text)
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
    var widthPx by remember { mutableIntStateOf(1) }
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
            .height(20.dp)
            // Ширина берётся из измерения, а не из жеста: иначе бегунок
            // стоит в левом краю, пока по нему не проведут пальцем.
            .onSizeChanged { widthPx = it.width }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        // Первое касание сразу ставит позицию под палец:
                        // иначе перемотка начинается с того места, где
                        // трек и был, и палец «догоняет» бегунок.
                        if (durationMs > 0) {
                            onDrag(((offset.x / size.width).coerceIn(0f, 1f) * durationMs).toLong())
                        }
                    },
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

/** Пока играет — четыре раза в секунду; на паузе опроса нет вовсе. */
private const val POSITION_POLL_MS = 250L

/** Смахивание короче этого — случайное движение, а не переключение трека. */
private const val SWIPE_THRESHOLD_PX = 120f

private const val GLOW_ALPHA = 0.3f
private const val GLOW_STOP = 0.55f

/**
 * Готовые переделки с YouTube — вторая дорога к slowed рядом со своей.
 *
 * Своя версия замедляет оригинал; здесь — чужой залитый эдит, со своим
 * сведением и эхом. Ответ показывается только для того трека, к которому
 * он относится: «не нашлось» от прошлой песни на новой было бы враньём.
 */
@Composable
private fun EditFinder(
    songId: String,
    edit: EditSearch,
    onFind: (EditKind) -> Unit,
) {
    val colors = LocalW0yColors.current
    SectionLabel(stringResource(R.string.player_edit_title))
    Text(
        text = stringResource(R.string.player_edit_desc),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textMuted,
    )
    Spacer(Modifier.height(10.dp))
    val searching = edit is EditSearch.Searching
    Row {
        PixelButton(
            text = "SLOWED",
            onClick = { onFind(EditKind.SLOWED) },
            enabled = !searching,
            fill = colors.surfaceHigh,
        )
        Spacer(Modifier.width(12.dp))
        PixelButton(
            text = "SPED UP",
            onClick = { onFind(EditKind.SPED_UP) },
            enabled = !searching,
            fill = colors.surfaceHigh,
        )
    }
    val status =
        when (edit) {
            is EditSearch.Searching -> if (edit.songId == songId) stringResource(R.string.player_edit_searching) else null
            is EditSearch.NotFound -> if (edit.songId == songId) stringResource(R.string.player_edit_none) else null
            is EditSearch.Playing -> if (edit.songId == songId) stringResource(R.string.player_edit_playing, edit.title) else null
            EditSearch.Idle -> null
        }
    if (status != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = if (edit is EditSearch.NotFound) colors.secondary else colors.accent,
        )
    }
}
