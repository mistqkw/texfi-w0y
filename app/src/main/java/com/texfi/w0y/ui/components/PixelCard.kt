package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel

private val CardShape = RoundedCornerShape(8.dp)

/**
 * Карточка TexFi: бордер 2dp, тень — сдвинутый прямоугольник без блюра.
 * Material elevation с размытием ломает резкость пиксельной графики, поэтому
 * тень здесь рисуется вторым слоем, а не эффектом.
 */
@Composable
fun PixelCard(
    modifier: Modifier = Modifier,
    label: String? = null,
    shadowOffset: Int = 4,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalW0yColors.current
    Box(modifier = modifier) {
        Box(
            Modifier
                .matchParentSize()
                .offset(shadowOffset.dp, shadowOffset.dp)
                .clip(CardShape)
                .background(colors.shadow),
        )
        Column(
            Modifier
                .clip(CardShape)
                .background(colors.surface)
                .border(2.dp, colors.border, CardShape)
                .padding(14.dp),
        ) {
            if (label != null) {
                Text(
                    text = "❯ $label",
                    style = PixelSectionLabel,
                    color = colors.accent,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            content()
        }
    }
}
