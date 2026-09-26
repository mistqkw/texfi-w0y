package com.texfi.w0y.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.texfi.w0y.R
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.nav.BrowseRoute
import com.texfi.w0y.ui.nav.LocalBrowseNavigator
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import com.texfi.w0y.ui.components.AddToPlaylistPanel
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.PixelChip
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.SkeletonRow
import com.texfi.w0y.ui.components.SongRow
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.theme.PixelTitle

@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val colors = LocalW0yColors.current
    val navigator = LocalBrowseNavigator.current
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val diagnosis by viewModel.diagnosis.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var pickPlaylistFor by remember { mutableStateOf<SongItem?>(null) }
    var newPlaylistName by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.tab_search), style = PixelTitle, color = colors.text)
        Spacer(Modifier.height(14.dp))
        SearchField(value = query, onValueChange = viewModel::onQueryChange)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchFilter.entries.forEach { entry ->
                PixelChip(
                    text = entry.label,
                    selected = entry == filter,
                    onClick = { viewModel.onFilterChange(entry) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        when (val current = state) {
            SearchState.Idle ->
                Hint(stringResource(R.string.search_hint))

            SearchState.Loading ->
                // Скелетоны вместо спиннера: экран сразу показывает форму
                // будущего списка, и ожидание не читается как пустота.
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(6) { SkeletonRow() }
                }

            is SearchState.Failed ->
                Column {
                    Hint(current.message)
                    current.detail?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        com.texfi.w0y.ui.components.PixelButton(
                            text = "ЕЩЁ РАЗ",
                            onClick = viewModel::retry,
                        )
                        Spacer(Modifier.width(12.dp))
                        com.texfi.w0y.ui.components.PixelButton(
                            text = "ПРОВЕРКА",
                            onClick = viewModel::diagnose,
                        )
                    }
                    diagnosis?.let { text ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.text,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    }
                }

            is SearchState.Results ->
                if (current.isEmpty) {
                    Hint(stringResource(R.string.search_nothing))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // Ключ по id: без него Compose пересобирает строки
                        // при каждом обновлении списка, и прокрутка дёргается.
                        items(current.songs, key = { it.id }) { song ->
                            SongRow(
                                song = song,
                                onClick = {
                                    viewModel.playFrom(current.songs, current.songs.indexOf(song))
                                },
                                actions = {
                                    SpriteButton(
                                        rows = Sprites.download,
                                        onClick = { viewModel.download(song) },
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    SpriteButton(
                                        rows = Sprites.plus,
                                        onClick = { pickPlaylistFor = song },
                                    )
                                },
                            )
                        }
                        items(current.albums, key = { it.browseId }) { card ->
                            CardRow(
                                title = card.title,
                                subtitle = card.subtitle ?: if (card.isAlbum) "Альбом" else "Плейлист",
                                thumbnailUrl = card.thumbnailUrl,
                                round = false,
                                onClick = {
                                    navigator.open(
                                        BrowseRoute.Album(card.browseId, card.title, card.thumbnailUrl),
                                    )
                                },
                            )
                        }
                        items(current.artists, key = { it.browseId }) { card ->
                            CardRow(
                                title = card.name,
                                subtitle = card.subtitle ?: "Артист",
                                thumbnailUrl = card.thumbnailUrl,
                                round = true,
                                onClick = {
                                    navigator.open(
                                        BrowseRoute.Artist(card.browseId, card.name, card.thumbnailUrl),
                                    )
                                },
                            )
                        }
                    }
                }
        }
    }

    pickPlaylistFor?.let { song ->
        SearchPlaylistPanel(
            song = song,
            playlists = playlists.map { it.id to it.name },
            newName = newPlaylistName,
            onNewNameChange = { newPlaylistName = it },
            onPick = {
                viewModel.addToPlaylist(it, song)
                pickPlaylistFor = null
            },
            onCreate = {
                viewModel.createPlaylistWith(it, song)
                newPlaylistName = null
                pickPlaylistFor = null
            },
            onDismiss = {
                pickPlaylistFor = null
                newPlaylistName = null
            },
        )
    }
}

/** Строка альбома или артиста в выдаче: та же высота, что у трека. */
@Composable
private fun CardRow(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    round: Boolean,
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
        CoverImage(
            url = thumbnailUrl,
            px = Thumbnails.ROW,
            corner = if (round) 24 else 4,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1)
        }
        PixelSprite(
            rows = Sprites.next,
            color = colors.textMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val colors = LocalW0yColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(2.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(
                text = stringResource(R.string.search_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textMuted,
            )
        }
    }
}


@Composable
private fun Hint(text: String) {
    val colors = LocalW0yColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textMuted,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
internal fun SectionLabel(text: String) {
    val colors = LocalW0yColors.current
    Text("❯ $text", style = PixelSectionLabel, color = colors.accent)
}

/** Панель выбора плейлиста живёт поверх экрана поиска. */
@Composable
private fun SearchPlaylistPanel(
    song: SongItem,
    playlists: List<Pair<Long, String>>,
    newName: String?,
    onNewNameChange: (String) -> Unit,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) = AddToPlaylistPanel(song, playlists, newName, onNewNameChange, onPick, onCreate, onDismiss)
