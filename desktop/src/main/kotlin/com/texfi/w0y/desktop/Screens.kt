package com.texfi.w0y.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.SongItem

@Composable
fun ScreenFrame(title: String, app: AppState, canBack: Boolean, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Gap(h = 22)
        RowCenter {
            if (canBack) {
                IconButton(Glyph.Prev, onClick = app::back)
                Gap(w = 8)
            }
            Txt(title.uppercase(), pixel = true, size = 18, modifier = Modifier.weight(1f))
            actions()
        }
        Box(Modifier.padding(top = 10.dp).width(56.dp).height(3.dp).background(C.Blue))
        Gap(h = 14)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
fun Empty(text: String) = Txt(text, color = C.Muted, size = 14, modifier = Modifier.padding(vertical = 18.dp), maxLines = 4)

/** Строка трека: обложка, название, исполнитель, сердце и «в очередь». */
@Composable
fun SongRow(app: AppState, song: SongItem, onPlay: () -> Unit, extra: @Composable () -> Unit = {}) {
    val source = remember { MutableInteractionSource() }
    val bg = hoverBackground(source)
    val liked = song.id in app.likedIds
    val current = app.player.current?.id == song.id
    RowCenter(
        Modifier.fillMaxWidth().hoverable(source).clip(RoundedCornerShape(6.dp)).background(bg)
            .clickable(onClick = onPlay).padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Cover(song.thumbnailUrl, 44.dp)
        Gap(w = 12)
        Column(Modifier.weight(1f)) {
            Txt(song.title, color = if (current) C.Blue else C.Text, size = 15, weight = if (current) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal)
            Txt(song.artist, color = C.Muted, size = 12)
        }
        song.durationText?.let { Txt(it, color = C.Muted, size = 12, modifier = Modifier.padding(end = 8.dp)) }
        extra()
        IconButton(Glyph.Plus, onClick = { app.player.enqueue(song, next = false) }, color = C.Muted, size = 16.dp)
        IconButton(
            if (liked) Glyph.Heart else Glyph.HeartOff,
            onClick = { app.toggleLike(song) },
            color = if (liked) C.Danger else C.Muted,
            size = 18.dp,
        )
    }
}

@Composable
fun SongList(app: AppState, songs: List<SongItem>, state: LazyListState = rememberLazyListState(), header: @Composable () -> Unit = {}, extra: @Composable (SongItem) -> Unit = {}) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = state) {
            item { header() }
            itemsIndexed(songs, key = { i, s -> "${s.id}#$i" }) { i, song ->
                SongRow(app, song, onPlay = { app.player.play(songs, i) }) { extra(song) }
            }
            item { Gap(h = 24) }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
fun CardTile(title: String, subtitle: String?, thumb: String?, circle: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Column(
        Modifier.width(150.dp).hoverable(source).clip(RoundedCornerShape(6.dp)).background(hoverBackground(source))
            .clickable(onClick = onClick).padding(8.dp),
    ) {
        Cover(thumb, 134.dp, circle = circle)
        Gap(h = 8)
        Txt(title, size = 13)
        subtitle?.let { Txt(it, color = C.Muted, size = 11) }
    }
}

@Composable
fun HomeScreen(app: AppState) {
    ScreenFrame("Главная", app, canBack = false, actions = {
        PixelButton(if (app.homeLoading) "Грузим…" else "Обновить", onClick = app::loadHome, primary = false, enabled = !app.homeLoading)
    }) {
        val state = rememberLazyListState()
        if (app.shelves.isEmpty()) {
            Empty(app.homeError ?: if (app.homeLoading) "Загружаю ленты…" else "Лент пока нет.")
            return@ScreenFrame
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = state) {
                items(app.shelves) { shelf ->
                    if (shelf.songs.isEmpty() && shelf.cards.isEmpty()) return@items
                    SectionLabel(shelf.title, Modifier.padding(top = 8.dp, bottom = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(shelf.songs) { i, song ->
                            CardTile(song.title, song.artist, song.thumbnailUrl) { app.player.play(shelf.songs, i) }
                        }
                        items(shelf.cards) { card -> CardTile(card.title, card.subtitle, card.thumbnailUrl) { app.openCard(card) } }
                    }
                    Gap(h = 14)
                }
                item { Gap(h = 24) }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

fun AppState.openCard(card: PlaylistCard) {
    if (card.isAlbum) {
        open(Screen.Album(card.browseId, card.title, card.thumbnailUrl))
    } else {
        open(Screen.Remote(card.browseId, card.title, card.thumbnailUrl))
    }
}

@Composable
fun SearchScreen(app: AppState) {
    ScreenFrame("Поиск", app, canBack = false) {
        val state = rememberLazyListState()
        Column(Modifier.fillMaxSize()) {
            RowCenter(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(C.Surface).padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(Glyph.Search, C.Muted, 18.dp)
                Gap(w = 10)
                Box(Modifier.weight(1f)) {
                    if (app.searchQuery.isEmpty()) Txt("Треки, альбомы, артисты", color = C.Muted, size = 15)
                    BasicTextField(
                        value = app.searchQuery,
                        onValueChange = { app.searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = C.Text, fontSize = 15.sp),
                        cursorBrush = SolidColor(C.Blue),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { app.search(app.searchQuery) }),
                        modifier = Modifier.fillMaxWidth().onKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
                                app.search(app.searchQuery)
                                true
                            } else {
                                false
                            }
                        },
                    )
                }
                if (app.searchQuery.isNotEmpty()) IconButton(Glyph.Close, onClick = { app.searchQuery = ""; app.searchResult = null }, color = C.Muted, size = 14.dp, box = 28.dp)
            }
            Gap(h = 12)
            val result = app.searchResult
            when {
                app.searching -> Empty("Ищу…")
                app.searchError != null -> Empty(app.searchError!!)
                result == null -> Empty("Введи запрос и нажми Enter.")
                else ->
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = state) {
                            if (result.songs.isNotEmpty()) item { SectionLabel("Треки", Modifier.padding(vertical = 8.dp)) }
                            itemsIndexed(result.songs) { i, song -> SongRow(app, song, onPlay = { app.player.play(result.songs, i) }) }
                            if (result.albums.isNotEmpty()) {
                                item {
                                    SectionLabel("Альбомы", Modifier.padding(top = 16.dp, bottom = 8.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(result.albums) { c -> CardTile(c.title, c.subtitle, c.thumbnailUrl) { app.openCard(c) } }
                                    }
                                }
                            }
                            if (result.artists.isNotEmpty()) {
                                item {
                                    SectionLabel("Артисты", Modifier.padding(top = 16.dp, bottom = 8.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(result.artists) { a ->
                                            CardTile(a.name, a.subtitle, a.thumbnailUrl, circle = true) { app.open(Screen.Artist(a.browseId, a.name, a.thumbnailUrl)) }
                                        }
                                    }
                                }
                            }
                            item { Gap(h = 24) }
                        }
                        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    }
            }
        }
    }
}

@Composable
private fun DetailHeader(app: AppState, title: String, subtitle: String?, thumb: String?, songs: List<SongItem>, circle: Boolean = false) {
    RowCenter(Modifier.padding(bottom = 16.dp)) {
        Cover(thumb, 120.dp, circle = circle)
        Gap(w = 18)
        Column(Modifier.weight(1f)) {
            Txt(title, size = 22, weight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 2)
            subtitle?.let { Txt(it, color = C.Muted, size = 13) }
            Gap(h = 12)
            RowCenter {
                PixelButton("Играть", onClick = { if (songs.isNotEmpty()) app.player.play(songs, 0) }, enabled = songs.isNotEmpty())
                Gap(w = 10)
                PixelButton("Перемешать", onClick = { if (songs.isNotEmpty()) app.player.play(songs.shuffled(), 0) }, primary = false, enabled = songs.isNotEmpty())
            }
        }
    }
}

@Composable
fun AlbumScreen(app: AppState, s: Screen.Album) {
    val page by produceState<com.texfi.w0y.data.AlbumPage?>(null, s.browseId) {
        value = runCatching { app.yt.album(s.browseId) }.getOrNull()
    }
    ScreenFrame(s.title, app, canBack = true) {
        val songs = page?.songs.orEmpty()
        if (page == null) Empty("Загружаю…") else SongList(app, songs, header = { DetailHeader(app, s.title, page?.subtitle, page?.thumbnailUrl ?: s.thumb, songs) })
    }
}

@Composable
fun ArtistScreen(app: AppState, s: Screen.Artist) {
    val page by produceState<com.texfi.w0y.data.ArtistPage?>(null, s.browseId) {
        value = runCatching { app.yt.artist(s.browseId) }.getOrNull()
    }
    ScreenFrame(s.name, app, canBack = true) {
        val songs = page?.songs.orEmpty()
        if (page == null) {
            Empty("Загружаю…")
        } else {
            SongList(app, songs, header = {
                DetailHeader(app, s.name, page?.subtitle, page?.thumbnailUrl ?: s.thumb, songs, circle = true)
                if (songs.isNotEmpty()) SectionLabel("Популярное", Modifier.padding(bottom = 6.dp))
            })
        }
    }
}

@Composable
fun RemoteScreen(app: AppState, s: Screen.Remote) {
    val songs by produceState<List<SongItem>?>(null, s.browseId) {
        value = runCatching { app.yt.playlistSongs(s.browseId) }.getOrNull() ?: emptyList()
    }
    ScreenFrame(s.title, app, canBack = true) {
        val list = songs
        if (list == null) Empty("Загружаю…") else if (list.isEmpty()) Empty("Плейлист пуст или не открылся.") else SongList(app, list, header = { DetailHeader(app, s.title, "${list.size} треков", s.thumb, list) })
    }
}

@Composable
fun QueueScreen(app: AppState) {
    val p = app.player
    ScreenFrame("Очередь", app, canBack = false) {
        if (p.queue.isEmpty()) {
            Empty("Очередь пуста. Включи любой трек.")
            return@ScreenFrame
        }
        val state = rememberLazyListState()
        LaunchedEffect(p.index) { if (p.index >= 0) state.animateScrollToItem((p.index - 2).coerceAtLeast(0)) }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = state) {
                itemsIndexed(p.queue, key = { i, s -> "${s.id}#$i" }) { i, song ->
                    val source = remember { MutableInteractionSource() }
                    RowCenter(
                        Modifier.fillMaxWidth().hoverable(source).clip(RoundedCornerShape(6.dp))
                            .background(if (i == p.index) C.Surface else hoverBackground(source))
                            .clickable { p.jump(i) }.padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Txt(if (i == p.index) "▶" else "${i + 1}", color = if (i == p.index) C.Blue else C.Muted, size = 12, modifier = Modifier.width(30.dp))
                        Cover(song.thumbnailUrl, 40.dp)
                        Gap(w = 12)
                        Column(Modifier.weight(1f)) {
                            Txt(song.title, color = if (i == p.index) C.Blue else C.Text, size = 14)
                            Txt(song.artist, color = C.Muted, size = 12)
                        }
                        IconButton(Glyph.Close, onClick = { p.removeFromQueue(i) }, color = C.Muted, size = 14.dp)
                    }
                }
                item { Gap(h = 24) }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
fun LibraryScreen(app: AppState) {
    ScreenFrame("Моё", app, canBack = false) {
        val state = rememberLazyListState()
        var newName by remember { mutableStateOf<String?>(null) }
        var cookie by remember { mutableStateOf("") }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = state, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    PixelCard(Modifier.fillMaxWidth()) {
                        Column {
                            SectionLabel("Аккаунт")
                            Gap(h = 10)
                            if (app.signedIn) {
                                RowCenter {
                                    Cover(app.lib.accountAvatar, 40.dp, circle = true)
                                    Gap(w = 12)
                                    Txt(app.lib.accountName ?: "Вы вошли", size = 16, modifier = Modifier.weight(1f))
                                    PixelButton(if (app.syncing) "Сверяю…" else "Обновить", onClick = app::syncAccount, enabled = !app.syncing)
                                    Gap(w = 10)
                                    PixelButton("Выйти", onClick = app::signOut, primary = false)
                                }
                            } else {
                                Txt(
                                    "Вход по cookie: открой music.youtube.com в браузере, в DevTools → Network возьми заголовок Cookie любого запроса и вставь сюда.",
                                    color = C.Muted, size = 12, maxLines = 4,
                                )
                                Gap(h = 10)
                                RowCenter {
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(C.SurfaceHigh).padding(10.dp)) {
                                        if (cookie.isEmpty()) Txt("Cookie", color = C.Muted, size = 13)
                                        BasicTextField(cookie, { cookie = it }, singleLine = true, textStyle = TextStyle(color = C.Text, fontSize = 13.sp), cursorBrush = SolidColor(C.Blue), modifier = Modifier.fillMaxWidth())
                                    }
                                    Gap(w = 10)
                                    PixelButton("Войти", onClick = { app.signIn(cookie); cookie = "" })
                                }
                            }
                            app.accountStatus?.let { Txt(it, color = C.Sand, size = 12, modifier = Modifier.padding(top = 8.dp), maxLines = 3) }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Shortcut("Лайки", "${app.lib.liked.size}", Modifier.weight(1f)) { app.open(Screen.Liked) }
                        Shortcut("История", "${app.lib.history.size}", Modifier.weight(1f)) { app.open(Screen.History) }
                    }
                }
                item {
                    RowCenter {
                        SectionLabel("Плейлисты", Modifier.weight(1f))
                        IconButton(Glyph.Plus, onClick = { newName = "" }, color = C.Blue)
                    }
                }
                newName?.let { value ->
                    item {
                        PixelCard(Modifier.fillMaxWidth()) {
                            RowCenter {
                                Box(Modifier.weight(1f)) {
                                    if (value.isEmpty()) Txt("Название плейлиста", color = C.Muted, size = 14)
                                    BasicTextField(value, { newName = it }, singleLine = true, textStyle = TextStyle(color = C.Text, fontSize = 14.sp), cursorBrush = SolidColor(C.Blue), modifier = Modifier.fillMaxWidth())
                                }
                                Gap(w = 10)
                                PixelButton("Создать", onClick = { app.createPlaylist(value); newName = null })
                                Gap(w = 8)
                                PixelButton("Отмена", onClick = { newName = null }, primary = false)
                            }
                        }
                    }
                }
                if (app.lib.playlists.isEmpty() && newName == null) item { Empty("Плейлистов пока нет.") }
                itemsIndexed(app.lib.playlists) { i, pl ->
                    ListLine(pl.name, "${pl.songs.size} треков", pl.songs.firstOrNull()?.thumb) { app.open(Screen.Local(i)) }
                }
                if (app.accountPlaylists.isNotEmpty()) {
                    item { SectionLabel("Из аккаунта", Modifier.padding(top = 8.dp)) }
                    items(app.accountPlaylists, key = { it.browseId }) { c ->
                        ListLine(c.title, c.subtitle ?: "плейлист", c.thumbnailUrl) { app.openCard(c) }
                    }
                }
                item { Gap(h = 24) }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun Shortcut(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    PixelCard(modifier.clickable(onClick = onClick)) {
        Column {
            SectionLabel(label)
            Gap(h = 10)
            Txt(value, size = 22, weight = androidx.compose.ui.text.font.FontWeight.Medium)
        }
    }
}

@Composable
private fun ListLine(title: String, subtitle: String, thumb: String?, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    RowCenter(
        Modifier.fillMaxWidth().hoverable(source).clip(RoundedCornerShape(6.dp)).background(hoverBackground(source))
            .clickable(onClick = onClick).padding(8.dp),
    ) {
        Cover(thumb, 48.dp)
        Gap(w = 12)
        Column(Modifier.weight(1f)) {
            Txt(title, size = 15)
            Txt(subtitle, color = C.Muted, size = 12)
        }
    }
}

@Composable
fun LikedScreen(app: AppState) {
    ScreenFrame("Лайки", app, canBack = true) {
        val songs = app.lib.liked.map { it.toItem() }
        if (songs.isEmpty()) Empty("Лайкнутых треков пока нет.") else SongList(app, songs)
    }
}

@Composable
fun HistoryScreen(app: AppState) {
    ScreenFrame("История", app, canBack = true) {
        val songs = app.lib.history.map { it.toItem() }
        if (songs.isEmpty()) Empty("Вы ещё ничего не включали.") else SongList(app, songs)
    }
}

@Composable
fun LocalScreen(app: AppState, s: Screen.Local) {
    val pl = app.lib.playlists.getOrNull(s.index)
    ScreenFrame(pl?.name ?: "Плейлист", app, canBack = true, actions = {
        if (pl != null) PixelButton("Удалить", onClick = { app.deletePlaylist(s.index) }, primary = false)
    }) {
        val songs = pl?.songs?.map { it.toItem() }.orEmpty()
        if (songs.isEmpty()) {
            Empty("Плейлист пуст. Добавь треки кнопкой «в плейлист» в плеере.")
        } else {
            SongList(app, songs, extra = { song ->
                IconButton(Glyph.Trash, onClick = { app.removeFromPlaylist(s.index, song.id) }, color = C.Muted, size = 16.dp)
            })
        }
    }
}
