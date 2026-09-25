package com.texfi.w0y.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
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
