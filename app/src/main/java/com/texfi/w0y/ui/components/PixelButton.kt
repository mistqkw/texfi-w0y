package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.luminance
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel

private val ButtonShape = RoundedCornerShape(8.dp)

/**
 * Кнопка вдавливается при нажатии: тень уезжает внутрь, а содержимое
 * сдвигается на её место. Без этого отклика пиксельная кнопка выглядит
 * мёртвой картинкой — проверено на f0kus.
 */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color? = null,
    enabled: Boolean = true,
) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Тень лежит на +4dp; при нажатии содержимое съезжает ровно на неё.
    val sink = if (pressed) 4 else 0
    val body = fill ?: colors.accent

    Box(modifier = modifier) {
        Box(
            Modifier
                .matchParentSize()
                .offset(4.dp, 4.dp)
                .clip(ButtonShape)
                .background(if (enabled) colors.accentDeep else colors.shadow),
        )
        Box(
            Modifier
                .offset(sink.dp, sink.dp)
                .clip(ButtonShape)
                .background(if (enabled) body else colors.surfaceHigh)
                .border(2.dp, colors.border, ButtonShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                )
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = PixelSectionLabel,
                // Цвет подписи считается от заливки, а не задан раз и навсегда:
                // на синей кнопке нужен тёмный текст, а на тёмной — светлый.
                // Раньше он всегда был тёмным, и «К АРТИСТУ» на сером фоне
                // просто не читалось.
                color =
                    when {
                        !enabled -> colors.textMuted
                        body.luminance() > CONTRAST_SWITCH -> colors.background
                        else -> colors.text
                    },
            )
        }
    }
}

/** Граница, за которой заливка считается светлой и требует тёмной подписи. */
private const val CONTRAST_SWITCH = 0.35f
