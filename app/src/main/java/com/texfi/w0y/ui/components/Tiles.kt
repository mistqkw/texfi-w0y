package com.texfi.w0y.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.ArtistCard
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel

private val TileShape = RoundedCornerShape(8.dp)

/**
 * Плитка быстрого набора.
 *
 * Обложка во всю плитку, название поверх неё по тёмному градиенту — иначе
 * на светлых обложках белый текст пропадает. Метка-кнопка в углу означает
 * «закреплено вручную», остальные плитки набираются по частоте.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedDialTile(
    title: String,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    pinned: Boolean = false,
    kindSprite: List<String>? = null,
    round: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val colors = LocalW0yColors.current
    val shape = if (round) CircleShape else TileShape
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .offset(3.dp, 3.dp)
                .clip(shape)
                .background(colors.shadow),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(colors.surface)
                .border(2.dp, colors.border, shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            CoverImage(
                url = thumbnailUrl,
                px = Thumbnails.TILE,
                corner = 0,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
            )
            if (pinned) {
                PixelSprite(
                    rows = Sprites.pin,
                    color = colors.accent,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(14.dp),
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                if (kindSprite != null) {
                    PixelSprite(rows = kindSprite, color = colors.accent, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Карточка релиза: альбом или плейлист в горизонтальной ленте. */
@Composable
fun ReleaseTile(card: PlaylistCard, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    Column(
        Modifier
            .width(132.dp)
            .clickable(onClick = onClick),
    ) {
        CoverImage(
            url = card.thumbnailUrl,
            px = Thumbnails.TILE,
            corner = 6,
            modifier =
                Modifier
                    .size(132.dp)
                    .border(2.dp, colors.border, RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = card.title,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = card.subtitle ?: if (card.isAlbum) "Альбом" else "Плейлист",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Карточка артиста: круг, как во всех музыкальных сервисах — так её узнают. */
@Composable
fun ArtistTile(card: ArtistCard, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    Column(
        Modifier
            .width(104.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverImage(
            url = card.thumbnailUrl,
            px = Thumbnails.TILE,
            corner = 52,
            modifier =
                Modifier
                    .size(104.dp)
                    .border(2.dp, colors.border, CircleShape),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = card.name,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Переключатель раздела выдачи: квадратный, без Material-таблеток. */
@Composable
fun PixelChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) colors.accent else colors.surface)
            .border(2.dp, if (selected) colors.accent else colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = PixelSectionLabel,
            color = if (selected) colors.background else colors.textMuted,
        )
    }
}
