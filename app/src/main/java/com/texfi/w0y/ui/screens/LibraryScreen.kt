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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.R
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.playback.DownloadState
import com.texfi.w0y.ui.components.DownloadButton
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
        LibraryRoute.Liked -> SongList(stringResource(R.string.library_likes), viewModel.liked.collectAsStateWithLifecycle().value, viewModel)
        LibraryRoute.History -> SongList(stringResource(R.string.library_history), viewModel.recent.collectAsStateWithLifecycle().value, viewModel, onClear = viewModel::clearHistory)
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
    val mirrorFailure by viewModel.mirrorFailure.collectAsStateWithLifecycle()
    var newPlaylist by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle(title = stringResource(R.string.library_title), horizontalPadding = 0.dp) }

        item {
            PixelCard(label = stringResource(R.string.library_account), modifier = Modifier.fillMaxWidth()) {
                if (signedIn) {
                    Text(
                        text = accountName ?: stringResource(R.string.library_signed_in),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        PixelButton(
                            text = if (syncing) stringResource(R.string.library_syncing) else stringResource(R.string.library_sync),
                            onClick = viewModel::sync,
                            enabled = !syncing,
                        )
                        Spacer(Modifier.width(10.dp))
                        PixelButton(text = stringResource(R.string.library_sign_out), onClick = viewModel::signOut, fill = colors.surfaceHigh)
                    }
                } else {
                    Text(
                        text = stringResource(R.string.library_sign_in_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    PixelButton(text = stringResource(R.string.library_sign_in), onClick = onOpenLogin)
                }
                syncError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondary)
                }
                // О неудачной записи плейлиста в аккаунт говорим прямо: на
                // телефоне изменение осталось, а в YouTube — нет, и молчать
                // об этом значило бы обещать синхронизацию, которой не было.
                mirrorFailure?.let {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.library_mirror_failed, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondary,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        SpriteButton(Sprites.close, onClick = viewModel::clearMirrorFailure, size = 14)
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShortcutCard(stringResource(R.string.library_likes), "${liked.size}", Sprites.heart, Modifier.weight(1f)) {
                        viewModel.open(LibraryRoute.Liked)
                    }
                    ShortcutCard(stringResource(R.string.library_history), "${recent.size}", Sprites.timer, Modifier.weight(1f)) {
                        viewModel.open(LibraryRoute.History)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShortcutCard(stringResource(R.string.library_downloaded), "${downloaded.size}", Sprites.download, Modifier.weight(1f)) {
                        viewModel.open(LibraryRoute.Downloads)
                    }
                    // Итоги — своя статистика по локальной истории, доступная
                    // в любой день, а не раз в год «рекапом».
                    ShortcutCard(stringResource(R.string.library_stats), stringResource(R.string.library_stats_hint), Sprites.stats, Modifier.weight(1f)) {
                        viewModel.open(LibraryRoute.Stats)
                    }
                }
            }
        }

        item {
            SectionHeader(
                label = stringResource(R.string.library_playlists),
                action = { SpriteButton(Sprites.plus, onClick = { newPlaylist = "" }, active = true, size = 18) },
            )
        }

        newPlaylist?.let { value ->
            item {
                PixelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.library_playlist_name), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
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
                            text = stringResource(R.string.library_create),
                            onClick = {
                                if (value.isNotBlank()) viewModel.createPlaylist(value)
                                newPlaylist = null
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        PixelButton(text = stringResource(R.string.library_cancel), onClick = { newPlaylist = null }, fill = colors.surfaceHigh)
                    }
                }
            }
        }

        if (playlists.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.library_no_playlists),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }

        items(playlists, key = { it.id }) { playlist ->
            CollectionRow(
                title = playlist.name,
                // Сразу видно, где плейлист лежит: только на телефоне или
                // ещё и в аккаунте. Без этой строчки «синхронизация» — слово
                // из настроек, которое никак не проверить.
                subtitle =
                    if (playlist.remoteId != null) {
                        stringResource(R.string.library_playlist_mirrored)
                    } else {
                        stringResource(R.string.library_own_playlist)
                    },
                thumbnailUrl = null,
                sprite = Sprites.library,
                onClick = { viewModel.open(LibraryRoute.Local(playlist.id)) },
            )
        }

        if (accountPlaylists.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                SectionHeader(stringResource(R.string.library_from_account))
            }
            items(accountPlaylists, key = { it.browseId }) { card ->
                CollectionRow(
                    title = card.title,
                    subtitle = card.subtitle ?: if (card.isAlbum) stringResource(R.string.card_album) else stringResource(R.string.card_playlist),
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
                title = stringResource(R.string.library_empty_title),
                text = stringResource(R.string.library_empty_text),
            )
            return
        }
        LazyColumn {
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    onClick = { viewModel.play(songs, songs.indexOf(song)) },
                    // Трек уезжает из списка сам: после нажатия «удалить»
                    // должно быть видно, что удалилось именно это, а не
                    // что список перерисовался целиком.
                    modifier = Modifier.animateItem(),
                    actions = { DownloadButton(song.id, onDownload = { viewModel.download(song) }) },
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
    val pending = progress.values.filter { it.state != DownloadState.DONE }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        ScreenTitle(title = stringResource(R.string.downloads_title), onBack = viewModel::back, horizontalPadding = 0.dp)
        Text(
            text =
                stringResource(
                    R.string.downloads_usage,
                    (viewModel.downloads.usedBytes() / 1024 / 1024).toInt(),
                    pending.size,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        // «Где это» — вопрос, который задали первым. Ответ честный: не в
        // папке «Музыка», а внутри приложения.
        Text(
            text = stringResource(R.string.downloads_where),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        if (songs.isEmpty() && pending.isEmpty()) {
            EmptyState(
                sprite = Sprites.download,
                title = stringResource(R.string.downloads_empty_title),
                text = stringResource(R.string.downloads_empty_text),
            )
            return
        }
        LazyColumn {
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    onClick = { viewModel.play(songs, songs.indexOf(song)) },
                    progressPercent = progress[song.id]?.percent,
                    modifier = Modifier.animateItem(),
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
    val signedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val pushResult by viewModel.pushResult.collectAsStateWithLifecycle()
    val entry = playlist.firstOrNull { it.id == playlistId }
    val name = entry?.name ?: stringResource(R.string.playlist_title)
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
        if (signedIn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSprite(
                    rows = Sprites.sync,
                    color = if (entry?.remoteId != null) colors.accent else colors.textMuted,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text =
                        pushResult
                            ?: if (entry?.remoteId != null) {
                                stringResource(R.string.playlist_mirrored)
                            } else {
                                stringResource(R.string.playlist_not_mirrored)
                            },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                PixelButton(
                    text = stringResource(R.string.playlist_push),
                    onClick = { viewModel.pushPlaylist(playlistId) },
                    fill = colors.surfaceHigh,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        if (songs.isEmpty()) {
            Text(
                stringResource(R.string.playlist_empty),
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
                    modifier = Modifier.animateItem(),
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
            Text(stringResource(R.string.playlist_loading), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            return
        }
        LazyColumn {
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    onClick = { viewModel.play(songs, songs.indexOf(song)) },
                    // Трек уезжает из списка сам: после нажатия «удалить»
                    // должно быть видно, что удалилось именно это, а не
                    // что список перерисовался целиком.
                    modifier = Modifier.animateItem(),
                    actions = { DownloadButton(song.id, onDownload = { viewModel.download(song) }) },
                )
            }
        }
    }
}

