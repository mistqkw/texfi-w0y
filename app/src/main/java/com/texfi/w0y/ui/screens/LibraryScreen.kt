package com.texfi.w0y.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.EmptyState
import com.texfi.w0y.ui.components.Gutter
import com.texfi.w0y.ui.components.ScreenTitle
import com.texfi.w0y.ui.components.SectionHeader
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.PixelCard
import com.texfi.w0y.ui.components.SongRow
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.nav.BrowseRoute
import com.texfi.w0y.ui.nav.LocalBrowseNavigator
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import com.texfi.w0y.ui.theme.PixelTitle

@Composable
fun LibraryScreen(
    onOpenLogin: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val route by viewModel.route.collectAsStateWithLifecycle()
    when (val current = route) {
        LibraryRoute.Root -> LibraryRoot(viewModel, onOpenLogin)
        is LibraryRoute.Local -> LocalPlaylist(current.playlistId, viewModel)
        is LibraryRoute.Remote -> RemotePlaylist(current, viewModel)
        LibraryRoute.Liked -> SongList("ЛАЙКИ", viewModel.liked.collectAsStateWithLifecycle().value, viewModel)
        LibraryRoute.History -> SongList("ИСТОРИЯ", viewModel.recent.collectAsStateWithLifecycle().value, viewModel, onClear = viewModel::clearHistory)
        LibraryRoute.Downloads -> DownloadsList(viewModel)
        LibraryRoute.Stats -> StatsScreen(onBack = viewModel::back)
    }
}

@Composable
private fun LibraryRoot(viewModel: LibraryViewModel, onOpenLogin: () -> Unit) {
    val colors = LocalW0yColors.current
    val navigator = LocalBrowseNavigator.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    val signedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val accountPlaylists by viewModel.accountPlaylists.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    var newPlaylist by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle(title = "моё", horizontalPadding = 0.dp) }

        item {
            PixelCard(label = "АККАУНТ", modifier = Modifier.fillMaxWidth()) {
                if (signedIn) {
                    Text(
                        text = accountName ?: "Вход выполнен",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        PixelButton(
                            text = if (syncing) "СИНХРОНИЗАЦИЯ…" else "ОБНОВИТЬ",
                            onClick = viewModel::sync,
                            enabled = !syncing,
                        )
                        Spacer(Modifier.width(10.dp))
                        PixelButton(text = "ВЫЙТИ", onClick = viewModel::signOut, fill = colors.surfaceHigh)
                    }
                } else {
                    Text(
                        text = "Войди в аккаунт Google, чтобы подтянуть свои плейлисты и лайки.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    PixelButton(text = "ВОЙТИ", onClick = onOpenLogin)
                }
                syncError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondary)
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShortcutCard("ЛАЙКИ", "${liked.size}", Sprites.heart, Modifier.weight(1f)) {
                        viewModel.open(LibraryRoute.Liked)
                    }
                    ShortcutCard("ИСТОРИЯ", "${recent.size}", Sprites.timer, Modifier.weight(1f)) {
                        viewModel.open(LibraryRoute.History)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShortcutCard("СКАЧАНО", "${downloaded.size}", Sprites.download, Modifier.weight(1f)) {
                        viewModel.open(LibraryRoute.Downloads)
                    }
                    // Итоги — своя статистика по локальной истории, доступная
                    // в любой день, а не раз в год «рекапом».
                    ShortcutCard("ИТОГИ", "за неделю", Sprites.stats, Modifier.weight(1f)) {
                        viewModel.open(LibraryRoute.Stats)
                    }
                }
            }
        }

        item {
            SectionHeader(
                label = "ПЛЕЙЛИСТЫ",
                action = { SpriteButton(Sprites.plus, onClick = { newPlaylist = "" }, active = true, size = 18) },
            )
        }

        newPlaylist?.let { value ->
            item {
                PixelCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Название плейлиста", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    Spacer(Modifier.height(8.dp))
                    BasicTextField(
                        value = value,
                        onValueChange = { newPlaylist = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        PixelButton(
                            text = "СОЗДАТЬ",
                            onClick = {
                                if (value.isNotBlank()) viewModel.createPlaylist(value)
                                newPlaylist = null
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        PixelButton(text = "ОТМЕНА", onClick = { newPlaylist = null }, fill = colors.surfaceHigh)
                    }
                }
            }
        }

        if (playlists.isEmpty()) {
            item {
                Text(
                    "Своих плейлистов пока нет. Кнопка «+» создаст первый.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }

        items(playlists, key = { it.id }) { playlist ->
            CollectionRow(
                title = playlist.name,
                subtitle = "Свой плейлист",
                thumbnailUrl = null,
                sprite = Sprites.library,
                onClick = { viewModel.open(LibraryRoute.Local(playlist.id)) },
            )
        }

        if (accountPlaylists.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                SectionHeader("ИЗ АККАУНТА")
            }
            items(accountPlaylists, key = { it.browseId }) { card ->
                CollectionRow(
                    title = card.title,
                    subtitle = card.subtitle ?: if (card.isAlbum) "Альбом" else "Плейлист",
                    thumbnailUrl = card.thumbnailUrl,
                    sprite = Sprites.release,
                    onClick = {
                        // Альбом открывается своей страницей, плейлист — списком:
                        // у альбома есть обложка и год, у плейлиста только треки.
                        if (card.isAlbum) {
                            navigator.open(BrowseRoute.Album(card.browseId, card.title, card.thumbnailUrl))
                        } else {
                            viewModel.open(LibraryRoute.Remote(card))
                        }
                    },
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Строка коллекции: плейлист, альбом — с обложкой или пиксельной заглушкой. */
@Composable
private fun CollectionRow(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    sprite: List<String>,
    onClick: () -> Unit,
) {
    val colors = LocalW0yColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbnailUrl != null) {
            CoverImage(url = thumbnailUrl, px = Thumbnails.ROW, modifier = Modifier.size(44.dp))
        } else {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                PixelSprite(rows = sprite, color = colors.accent, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1)
        }
        PixelSprite(rows = Sprites.chevronRight, color = colors.textMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ShortcutCard(
    label: String,
    value: String,
    sprite: List<String>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalW0yColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(2.dp, colors.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSprite(rows = sprite, color = colors.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = PixelSectionLabel, color = colors.accent)
            }
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, color = colors.text)
        }
    }
}

@Composable
private fun SongList(
    title: String,
    songs: List<SongItem>,
    viewModel: LibraryViewModel,
    onClear: (() -> Unit)? = null,
) {
    val colors = LocalW0yColors.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        ScreenTitle(title = title.lowercase(), onBack = viewModel::back, horizontalPadding = 0.dp) {
            if (songs.isNotEmpty()) {
                SpriteButton(Sprites.download, onClick = { viewModel.downloadAll(songs) })
                onClear?.let {
                    Spacer(Modifier.width(12.dp))
                    SpriteButton(Sprites.trash, onClick = it)
                }
            }
        }
        if (songs.isEmpty()) {
            EmptyState(
                sprite = Sprites.library,
                title = "ПУСТО",
                text = "Здесь появится то, что ты сюда положишь.",
            )
            return
        }
        LazyColumn {
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    onClick = { viewModel.play(songs, songs.indexOf(song)) },
                    actions = { SpriteButton(Sprites.download, onClick = { viewModel.download(song) }) },
                )
            }
        }
    }
}

@Composable
private fun DownloadsList(viewModel: LibraryViewModel) {
    val colors = LocalW0yColors.current
    val songs by viewModel.downloaded.collectAsStateWithLifecycle()
    val progress by viewModel.downloads.progress.collectAsStateWithLifecycle()
    val pending = progress.values.filter { it.state != Download.STATE_COMPLETED }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        ScreenTitle(title = "загрузки", onBack = viewModel::back, horizontalPadding = 0.dp)
        Text(
            text = "Занято: ${viewModel.downloads.usedBytes() / 1024 / 1024} МБ · в очереди: ${pending.size}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(10.dp))
        if (songs.isEmpty() && pending.isEmpty()) {
            EmptyState(
                sprite = Sprites.download,
                title = "НИЧЕГО НЕ СКАЧАНО",
                text = "Кнопка со стрелкой в любом списке кладёт трек сюда — он будет играть без сети.",
            )
            return
        }
        LazyColumn {
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    onClick = { viewModel.play(songs, songs.indexOf(song)) },
                    progressPercent = progress[song.id]?.percent,
                    actions = { SpriteButton(Sprites.trash, onClick = { viewModel.cancelDownload(song.id) }) },
                )
            }
        }
    }
}

@Composable
private fun LocalPlaylist(playlistId: Long, viewModel: LibraryViewModel) {
    val colors = LocalW0yColors.current
    val songs by viewModel.currentPlaylistSongs.collectAsStateWithLifecycle()
    val playlist by viewModel.playlists.collectAsStateWithLifecycle()
    val name = playlist.firstOrNull { it.id == playlistId }?.name ?: "ПЛЕЙЛИСТ"
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        ScreenTitle(title = name.lowercase(), onBack = viewModel::back, horizontalPadding = 0.dp) {
            SpriteButton(Sprites.download, onClick = { viewModel.downloadAll(songs) })
            Spacer(Modifier.width(12.dp))
            SpriteButton(Sprites.trash, onClick = { viewModel.deletePlaylist(playlistId) })
        }
        if (songs.isEmpty()) {
            Text(
                "Плейлист пуст. Добавляй треки кнопкой «+» в поиске.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
            return
        }
        LazyColumn {
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    onClick = { viewModel.play(songs, songs.indexOf(song)) },
                    actions = {
                        SpriteButton(
                            Sprites.trash,
                            onClick = { viewModel.removeFromPlaylist(playlistId, song.id) },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun RemotePlaylist(route: LibraryRoute.Remote, viewModel: LibraryViewModel) {
    val colors = LocalW0yColors.current
    val songs by viewModel.remoteSongs.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        ScreenTitle(title = route.card.title.lowercase(), onBack = viewModel::back, horizontalPadding = 0.dp) {
            if (songs.isNotEmpty()) SpriteButton(Sprites.download, onClick = { viewModel.downloadAll(songs) })
        }
        if (songs.isEmpty()) {
            Text("Загружаю треки плейлиста…", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            return
        }
        LazyColumn {
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    onClick = { viewModel.play(songs, songs.indexOf(song)) },
                    actions = { SpriteButton(Sprites.download, onClick = { viewModel.download(song) }) },
                )
            }
        }
    }
}

