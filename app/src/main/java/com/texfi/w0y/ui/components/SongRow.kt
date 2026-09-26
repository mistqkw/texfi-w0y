package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.theme.LocalW0yColors
import kotlin.math.sin
import kotlin.math.abs

/**
 * Насколько плотно рисуются строки списков. Задаётся один раз в оболочке
 * из настроек, чтобы каждый список не тащил их через параметры.
 */
val LocalCompactRows = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * Id играющего трека — на весь интерфейс сразу.
 *
 * Иначе каждый экран со списком тянул бы состояние плеера ради одной
 * подсветки, и рано или поздно один из них про неё забыл бы.
 */
val LocalPlayingSongId = androidx.compose.runtime.compositionLocalOf<String?> { null }

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
    highlighted: Boolean = song.id == LocalPlayingSongId.current,
    progressPercent: Float? = null,
    actions: @Composable () -> Unit = {},
) {
    val colors = LocalW0yColors.current
    val compact = LocalCompactRows.current
    val cover = if (compact) 40.dp else 48.dp
    val rowInteraction = remember { MutableInteractionSource() }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                // Строка сжимается слабее плитки: в длинном списке сильный
                // отклик выглядит как дёрганье всего списка.
                .pressScale(rowInteraction, pressed = 0.975f)
                .clickable(interactionSource = rowInteraction, indication = null, onClick = onClick)
                .padding(vertical = if (compact) 5.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(cover)) {
                CoverImage(
                    url = song.thumbnailUrl,
                    px = Thumbnails.ROW,
                    modifier = Modifier.fillMaxSize(),
                )
                // Играющий трек помечен живым столбиком, а не только цветом
                // названия: в длинном списке цвет строки глазом не найти,
                // а движение находится сразу.
                if (highlighted) {
                    PlayingMark(Modifier.align(Alignment.BottomStart).padding(3.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (song.explicit) {
                        ExplicitBadge()
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (highlighted) colors.accent else colors.text,
                        maxLines = 1,
                    )
                }
                Text(
                    text =
                        listOfNotNull(song.artist.takeIf { it.isNotBlank() }, song.album, song.plays)
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

/**
 * Пиксельная кнопка-иконка.
 *
 * Нажатие вдавливает, включение — подбрасывает: у лайка и у закрепления
 * должен быть виден сам момент, иначе непонятно, сработало или нет.
 */
@Composable
fun SpriteButton(
    rows: List<String>,
    onClick: () -> Unit,
    active: Boolean = false,
    size: Int = 20,
) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    val color by animateColorAsState(
        targetValue = if (active) colors.accent else colors.textMuted,
        animationSpec = tween(180),
        label = "spriteColor",
    )
    PixelSprite(
        rows = rows,
        color = color,
        modifier =
            Modifier
                .size(size.dp)
                .pressScale(interaction, pressed = 0.82f)
                .popWhenActivated(active)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    )
}

/**
 * Метка «E»: запись с ненормативной лексикой.
 *
 * Показывается так же, как у самого YouTube, — иначе непонятно, почему
 * режим «без мата» подменяет именно этот трек.
 */
@Composable
fun ExplicitBadge(modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    Box(
        modifier
            .size(14.dp)
            .background(colors.textMuted),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(
            text = "E",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp, lineHeight = 10.sp),
            color = colors.background,
        )
    }
}

/**
 * Отметка играющего трека: три столбика, которые дышат.
 *
 * Отдельная анимация именно под это состояние — не отклик на нажатие, а
 * признак того, что сейчас звучит. Поэтому она идёт сама и не привязана
 * к касанию.
 */
@Composable
private fun PlayingMark(modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val transition = rememberInfiniteTransition(label = "playingMark")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1_100), RepeatMode.Restart),
        label = "bars",
    )
    Row(
        modifier
            .background(colors.background.copy(alpha = 0.72f))
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        repeat(MARK_BARS) { index ->
            // Столбики сдвинуты по фазе: в один такт они читались бы как
            // один мигающий прямоугольник.
            val height = 3f + 6f * abs(sin(phase + index * 0.9f))
            Box(
                Modifier
                    .width(2.dp)
                    .height(height.dp)
                    .background(colors.accent),
            )
        }
    }
}

private const val MARK_BARS = 3
