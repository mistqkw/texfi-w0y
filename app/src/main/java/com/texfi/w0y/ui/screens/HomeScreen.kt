package com.texfi.w0y.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.data.db.PinEntity
import com.texfi.w0y.ui.components.PixelCard
import com.texfi.w0y.ui.components.ArtistTile
import com.texfi.w0y.ui.components.ReleaseTile
import com.texfi.w0y.ui.components.SongRow
import com.texfi.w0y.ui.components.SongTile
import com.texfi.w0y.ui.components.SpeedDialTile
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.nav.BrowseRoute
import com.texfi.w0y.ui.nav.LocalBrowseNavigator
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import com.texfi.w0y.ui.theme.PixelTitle

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    home: HomeViewModel = hiltViewModel(),
) {
    val colors = LocalW0yColors.current
    val navigator = LocalBrowseNavigator.current
    val dial by viewModel.speedDial.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val shelves by home.shelves.collectAsStateWithLifecycle()
    val recommendationsFailed by home.failed.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("w0y", style = PixelTitle, color = colors.text, modifier = Modifier.weight(1f))
                SpriteButton(Sprites.gear, onClick = onOpenSettings)
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "❯ БЫСТРЫЙ НАБОР",
                    style = PixelSectionLabel,
                    color = colors.accent,
                    modifier = Modifier.weight(1f),
                )
                if (dial.isNotEmpty()) {
                    Text(
                        "долгое нажатие — закрепить",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
        }

        if (dial.isEmpty()) {
            item {
                PixelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text =
                            "Здесь соберётся то, что ты слушаешь чаще всего. " +
                                "Что-то нужное можно закрепить долгим нажатием — оно останется на первом месте.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
            }
        } else {
            // Сетка внутри LazyColumn собирается рядами по три: вложенный
            // LazyVerticalGrid здесь запрещён — бесконечная высота.
            // Ключ с приставкой обязателен: тот же трек лежит и в быстром
            // наборе, и в «недавно», а одинаковый ключ дважды в одном
            // списке — это падение, а не просто перерисовка.
            items(dial.chunked(3), key = { row -> "dial-${row.first().kind}-${row.first().id}" }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { item ->
                        SpeedDialTile(
                            title = item.title,
                            subtitle = item.subtitle,
                            thumbnailUrl = item.thumbnailUrl,
                            pinned = item.pinned,
                            kindSprite =
                                when (item.kind) {
                                    PinEntity.KIND_ARTIST -> Sprites.artist
                                    PinEntity.KIND_ALBUM, PinEntity.KIND_PLAYLIST -> Sprites.release
                                    else -> null
                                },
                            round = item.kind == PinEntity.KIND_ARTIST,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when (item.kind) {
                                    PinEntity.KIND_ARTIST ->
                                        navigator.open(
                                            BrowseRoute.Artist(item.id, item.title, item.thumbnailUrl),
                                        )

                                    PinEntity.KIND_ALBUM, PinEntity.KIND_PLAYLIST ->
                                        navigator.open(
                                            BrowseRoute.Album(item.id, item.title, item.thumbnailUrl),
                                        )

                                    else -> viewModel.playDial(item)
                                }
                            },
                            onLongClick = { viewModel.togglePin(item) },
                        )
                    }
                    // Неполный ряд не должен растягивать плитки: добиваем пустотой.
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        if (shelves.isNotEmpty() || recommendationsFailed) {
            item {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "❯ РЕКОМЕНДАЦИИ",
                        style = PixelSectionLabel,
                        color = colors.accent,
                        modifier = Modifier.weight(1f),
                    )
                    SpriteButton(Sprites.repeat, onClick = home::refresh)
                }
            }
        }

        if (recommendationsFailed) {
            item {
                Text(
                    "Рекомендации не пришли. Нажми обновить или проверь сеть.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }

        items(shelves, key = { "shelf-${it.title}" }) { shelf ->
            Column {
                Text(
                    text = shelf.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.text,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shelf.songs, key = { "s-${it.id}" }) { song ->
                        SongTile(song) { home.play(shelf.songs, shelf.songs.indexOf(song)) }
                    }
                    items(shelf.cards, key = { "c-${it.browseId}" }) { card ->
                        ReleaseTile(card) {
                            navigator.open(BrowseRoute.Album(card.browseId, card.title, card.thumbnailUrl))
                        }
                    }
                    items(shelf.artists, key = { "a-${it.browseId}" }) { card ->
                        ArtistTile(card) {
                            navigator.open(BrowseRoute.Artist(card.browseId, card.name, card.thumbnailUrl))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        if (recent.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("❯ НЕДАВНО", style = PixelSectionLabel, color = colors.accent)
            }
            items(recent.take(20), key = { "recent-${it.id}" }) { song ->
                SongRow(
                    song = song,
                    onClick = { viewModel.play(recent, recent.indexOf(song)) },
                    actions = { SpriteButton(Sprites.download, onClick = { viewModel.download(song) }) },
                )
            }
        }

        if (liked.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("❯ ЛАЙКИ", style = PixelSectionLabel, color = colors.accent)
            }
            items(liked.take(10), key = { "liked-${it.id}" }) { song ->
                SongRow(song = song, onClick = { viewModel.play(liked, liked.indexOf(song)) })
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
