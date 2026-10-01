package com.texfi.w0y.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.min

@Composable
fun App(app: AppState) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxSize().background(C.Background).focusRequester(focus).focusable().onKeyEvent {
            // Пробел — пауза, но только если его не съело поле ввода.
            if (it.type == KeyEventType.KeyDown && it.key == Key.Spacebar) {
                app.player.toggle()
                true
            } else {
                false
            }
        },
    ) {
        Sidebar(app)
        Box(Modifier.width(2.dp).fillMaxHeight().background(C.Border))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val s = app.screen) {
                    Screen.Home -> HomeScreen(app)
                    Screen.Search -> SearchScreen(app)
                    Screen.Library -> LibraryScreen(app)
                    Screen.Queue, Screen.Player -> PlayerScreen(app)
                    Screen.Downloaded -> DownloadedScreen(app)
                    Screen.Liked -> LikedScreen(app)
                    Screen.History -> HistoryScreen(app)
                    is Screen.Album -> AlbumScreen(app, s)
                    is Screen.Artist -> ArtistScreen(app, s)
                    is Screen.Remote -> RemoteScreen(app, s)
                    is Screen.Local -> LocalScreen(app, s)
                }
            }
            PlayerBar(app)
        }
    }
}

@Composable
private fun Sidebar(app: AppState) {
    Column(Modifier.width(190.dp).fillMaxHeight().background(C.Surface).padding(vertical = 22.dp, horizontal = 14.dp)) {
        Txt("w0y", pixel = true, size = 22, color = C.Blue, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
        Txt("texfi", pixel = true, size = 9, color = C.Sand, modifier = Modifier.padding(start = 8.dp, bottom = 24.dp))
        NavItem("Главная", Glyph.Home, app.screen == Screen.Home) { app.tab(Screen.Home) }
        NavItem("Поиск", Glyph.Search, app.screen == Screen.Search) { app.tab(Screen.Search) }
        NavItem("Моё", Glyph.Library, app.screen in listOf(Screen.Library, Screen.Liked, Screen.History, Screen.Downloaded) || app.screen is Screen.Local) { app.tab(Screen.Library) }
        NavItem("Плеер", Glyph.Wave, app.screen == Screen.Player || app.screen == Screen.Queue) { app.tab(Screen.Player) }
        Box(Modifier.weight(1f))
        app.lib.accountName?.let {
            RowCenter(Modifier.padding(8.dp)) {
                Cover(app.lib.accountAvatar, 28.dp, circle = true)
                Gap(w = 8)
                Txt(it, color = C.Muted, size = 12)
            }
        }
    }
}

@Composable
private fun NavItem(label: String, glyph: Glyph, active: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    RowCenter(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).hoverable(source).clip(RoundedCornerShape(6.dp))
            .background(if (active) C.SurfaceHigh else hoverBackground(source))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Icon(glyph, if (active) C.Blue else C.Muted, 18.dp)
        Gap(w = 12)
        Txt(label, color = if (active) C.Text else C.Muted, size = 15)
    }
}

private fun fmt(seconds: Double): String {
    val s = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun PlayerBar(app: AppState) {
    val p = app.player
    val song = p.current
    Column(Modifier.fillMaxWidth().background(C.Surface)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(C.Border))
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 10.dp)) {
            SegmentedSeek(p.position, p.duration, onSeek = p::seekFraction)
        }
        RowCenter(Modifier.fillMaxWidth().height(78.dp).padding(horizontal = 18.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (song != null) {
                    Cover(song.thumbnailUrl, 52.dp, Modifier.clickable { app.open(Screen.Player) })
                    Gap(w = 12)
                    Column(Modifier.weight(1f, fill = false).clickable { app.open(Screen.Player) }) {
                        Txt(song.title, size = 15)
                        Txt(p.error ?: if (p.loading) "Загружаю…" else song.artist, color = if (p.error != null) C.Sand else C.Muted, size = 12)
                    }
                    Gap(w = 6)
                    val liked = song.id in app.likedIds
                    IconButton(if (liked) Glyph.Heart else Glyph.HeartOff, onClick = { app.toggleLike(song) }, color = if (liked) C.Danger else C.Muted, size = 18.dp)
                    DownloadButton(app, song)
                    PlaylistMenu(app, song)
                } else {
                    Txt(p.error ?: "Ничего не играет", color = if (p.error != null) C.Sand else C.Muted, size = 14)
                }
            }
            RowCenter {
                IconButton(Glyph.Prev, onClick = p::previous, size = 22.dp, box = 40.dp)
                Gap(w = 6)
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(4.dp)).background(C.Blue).border(2.dp, C.BlueDeep, RoundedCornerShape(4.dp)).clickable(onClick = p::toggle),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (p.playing) Glyph.Pause else Glyph.Play, C.Text, 24.dp) }
                Gap(w = 6)
                IconButton(Glyph.Next, onClick = { p.nextClick() }, size = 22.dp, box = 40.dp)
            }
            RowCenter(Modifier.weight(1f), ) {
                Box(Modifier.weight(1f))
                Txt("${fmt(p.position)} / ${fmt(p.duration)}", color = C.Muted, size = 12)
                Gap(w = 16)
                SleepMenu(app)
                Gap(w = 10)
                Icon(Glyph.Volume, C.Muted, 18.dp)
                Gap(w = 6)
                VolumeBar(p.volume, onChange = p::changeVolume)
            }
        }
    }
}

private fun Modifier.size(s: androidx.compose.ui.unit.Dp) = this.then(Modifier.width(s).height(s))

/** Полоса перемотки из сегментов, как на телефоне: клик и перетаскивание. */
@Composable
fun SegmentedSeek(position: Double, duration: Double, onSeek: (Double) -> Unit) {
    val fraction = if (duration > 0) (position / duration).coerceIn(0.0, 1.0).toFloat() else 0f
    Box(
        Modifier.fillMaxWidth().height(14.dp)
            .pointerInput(duration) {
                detectTapGestures { onSeek((it.x / size.width).toDouble().coerceIn(0.0, 1.0)) }
            }.pointerInput(duration) {
                detectDragGestures { change, _ -> onSeek((change.position.x / size.width).toDouble().coerceIn(0.0, 1.0)) }
            }.drawBehind {
                val segments = 80
                val gap = 2.dp.toPx()
                val w = (size.width - gap * (segments - 1)) / segments
                val filled = (fraction * segments).toInt()
                repeat(segments) { i ->
                    drawRect(
                        if (i < filled) C.BlueLight else C.SurfaceHigh,
                        Offset(i * (w + gap), size.height / 2 - 3.dp.toPx()),
                        Size(w, 6.dp.toPx()),
                    )
                }
            },
    )
}

@Composable
private fun VolumeBar(volume: Int, onChange: (Int) -> Unit) {
    Box(
        Modifier.width(96.dp).height(14.dp)
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / size.width * 100).toInt()) } }
            .pointerInput(Unit) { detectDragGestures { c, _ -> onChange((c.position.x / size.width * 100).toInt().coerceIn(0, 100)) } }
            .drawBehind {
                val segments = 10
                val gap = 2.dp.toPx()
                val w = (size.width - gap * (segments - 1)) / segments
                val filled = volume * segments / 100
                repeat(segments) { i ->
                    drawRect(if (i < filled) C.Sand else C.SurfaceHigh, Offset(i * (w + gap), size.height / 2 - 3.dp.toPx()), Size(w, 6.dp.toPx()))
                }
            },
    )
}

/** «В плейлист»: список своих плейлистов во всплывающем окне над кнопкой. */
@Composable
private fun PlaylistMenu(app: AppState, song: com.texfi.w0y.data.SongItem) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(Glyph.Plus, onClick = { open = !open }, color = C.Muted, size = 18.dp)
        if (open) {
            Popup(alignment = Alignment.TopStart, offset = IntOffset(0, -220), onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
                Column(Modifier.width(230.dp).background(C.SurfaceHigh, RoundedCornerShape(6.dp)).border(2.dp, C.Border, RoundedCornerShape(6.dp)).padding(6.dp)) {
                    Txt("В ПЛЕЙЛИСТ", pixel = true, size = 9, color = C.Blue, modifier = Modifier.padding(8.dp))
                    if (app.lib.playlists.isEmpty()) Txt("Сначала создай плейлист в «Моё».", color = C.Muted, size = 12, modifier = Modifier.padding(8.dp), maxLines = 3)
                    app.lib.playlists.forEachIndexed { i, pl ->
                        val has = pl.songs.any { it.id == song.id }
                        Txt(
                            if (has) "✓ ${pl.name}" else pl.name,
                            color = if (has) C.Sand else C.Text,
                            size = 14,
                            modifier = Modifier.fillMaxWidth().clickable { app.addToPlaylist(i, song); open = false }.padding(8.dp),
                        )
                    }
                    Txt("Следующим в очереди", size = 14, modifier = Modifier.fillMaxWidth().clickable { app.player.enqueue(song, next = true); open = false }.padding(8.dp))
                }
            }
        }
    }
}
