package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.theme.LocalW0yColors

/**
 * Обложка: сразу в нужном размере и с проявлением вместо рывка.
 *
 * Отдельный компонент, а не голый `AsyncImage`, чтобы размер картинки
 * нельзя было забыть — забытый размер и есть та самая «плохая обложка».
 */
@Composable
fun CoverImage(
    url: String?,
    px: Int,
    modifier: Modifier = Modifier,
    corner: Int = 4,
) {
    val colors = LocalW0yColors.current
    val shape = RoundedCornerShape(corner.dp())
    Box(
        modifier
            .clip(shape)
            .background(colors.surfaceHigh),
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(Thumbnails.sized(url, px))
                    .crossfade(180)
                    .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(toFloat())
