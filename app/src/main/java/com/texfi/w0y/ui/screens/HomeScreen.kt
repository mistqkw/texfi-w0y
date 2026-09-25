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
import com.texfi.w0y.ui.components.PixelCard
import com.texfi.w0y.ui.components.SongRow
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import com.texfi.w0y.ui.theme.PixelTitle

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val colors = LocalW0yColors.current
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()

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

        if (recent.isEmpty()) {
            item {
                PixelCard(label = "СЕЙЧАС", modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Здесь появится то, что ты слушаешь. Пока пусто — начни с поиска.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
            }
        } else {
            item { Text("❯ НЕДАВНО", style = PixelSectionLabel, color = colors.accent) }
            items(recent.take(20), key = { it.id }) { song ->
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
