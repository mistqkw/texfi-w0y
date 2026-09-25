package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.ui.theme.LocalW0yColors

/**
 * Выбор плейлиста для трека: одна панель на поиск, плеер и списки —
 * чтобы добавление работало одинаково везде, где встречается трек.
 */
@Composable
fun AddToPlaylistPanel(
    song: SongItem,
    playlists: List<Pair<Long, String>>,
    newName: String?,
    onNewNameChange: (String) -> Unit,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalW0yColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.shadow.copy(alpha = 0.85f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        PixelCard(
            label = "В ПЛЕЙЛИСТ",
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                maxLines = 1,
            )
            Spacer(Modifier.height(10.dp))
            if (playlists.isEmpty() && newName == null) {
                Text(
                    "Плейлистов ещё нет.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(10.dp))
            }
            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                items(playlists, key = { it.first }) { (id, name) ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.text,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(id) }
                                .padding(vertical = 8.dp),
                    )
                }
            }
            if (newName != null) {
                BasicTextField(
                    value = newName,
                    onValueChange = onNewNameChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                PixelButton(
                    text = "СОЗДАТЬ И ДОБАВИТЬ",
                    onClick = { if (newName.isNotBlank()) onCreate(newName) },
                )
            } else {
                PixelButton(text = "НОВЫЙ ПЛЕЙЛИСТ", onClick = { onNewNameChange("") })
            }
        }
    }
}
