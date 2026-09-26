package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.theme.LocalW0yColors

/**
 * Строка трека — одна на все списки: поиск, плейлист, лайки, загрузки.
 * Действия справа задаёт вызывающий экран, чтобы в загрузках не было
 * кнопки «скачать», а в поиске — «удалить».
 */
@Composable
fun SongRow(
    song: SongItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    progressPercent: Float? = null,
    actions: @Composable () -> Unit = {},
) {
    val colors = LocalW0yColors.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                url = song.thumbnailUrl,
                px = Thumbnails.ROW,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (highlighted) colors.accent else colors.text,
                    maxLines = 1,
                )
                Text(
                    text =
                        listOfNotNull(song.artist.takeIf { it.isNotBlank() }, song.album)
                            .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                )
            }
            song.durationText?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            Spacer(Modifier.width(8.dp))
            actions()
        }
        if (progressPercent != null && progressPercent < 100f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.surfaceHigh),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progressPercent / 100f)
                        .height(2.dp)
                        .background(colors.secondary),
                )
            }
        }
    }
}

/** Пиксельная кнопка-иконка: одинаковая во всех списках. */
@Composable
fun SpriteButton(
    rows: List<String>,
    onClick: () -> Unit,
    active: Boolean = false,
    size: Int = 20,
) {
    val colors = LocalW0yColors.current
    PixelSprite(
        rows = rows,
        color = if (active) colors.accent else colors.textMuted,
        modifier =
            Modifier
                .size(size.dp)
                .clickable(onClick = onClick),
    )
}
