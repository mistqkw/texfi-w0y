package com.texfi.w0y.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.texfi.w0y.ui.icons.SmoothIcons
import com.texfi.w0y.ui.theme.isSmooth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Пиксельный спрайт: сетка символов, '#' — закрашенная ячейка. Рисуется
 * кодом, а не растровым ассетом, — масштабируется под любой размер и
 * правится в одну строку.
 *
 * Ячейки рисуются с нахлёстом (+0.5px к стороне): без него на субпиксельном
 * рендере между ними появляются щели и контур рассыпается.
 */
@Composable
fun PixelSprite(
    rows: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // Плавный стиль рисует ту же иконку из своего набора: имя одно, рисунок
    // разный. Фирменные знаки TexFi пиксельные в обоих стилях — у них
    // плавной версии нет и быть не должно.
    if (isSmooth) {
        SmoothIcons.forSprite(rows)?.let { vector ->
            Image(rememberVectorPainter(vector), null, modifier, colorFilter = ColorFilter.tint(color))
            return
        }
    }
    Canvas(modifier = modifier) {
        val cols = rows.maxOf { it.length }
        val cell = minOf(size.width / cols, size.height / rows.size)
        val originX = (size.width - cell * cols) / 2f
        val originY = (size.height - cell * rows.size) / 2f
        val cellSize = Size(cell + 0.5f, cell + 0.5f)
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                if (ch == '#') {
                    drawRect(
                        color = color,
                        topLeft = Offset(originX + x * cell, originY + y * cell),
                        size = cellSize,
                    )
                }
            }
        }
    }
}
