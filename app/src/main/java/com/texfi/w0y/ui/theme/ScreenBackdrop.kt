package com.texfi.w0y.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop

/**
 * Экран под стеклянными панелями — источник для их размытия.
 *
 * Свой, а не LayerBackdrop из библиотеки: тот рисует содержимое дважды —
 * на экран и в запись, — и у экрана со списками запись выходила пустой
 * (слои строк не попадают во второго родителя). Здесь содержимое
 * записывается один раз и уже запись выводится на экран, как в Haze.
 */
@Stable
class ScreenBackdrop internal constructor(internal val layer: GraphicsLayer) : Backdrop {
    internal var coordinates: LayoutCoordinates? by mutableStateOf(null)

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val target = coordinates ?: return
        val source = this@ScreenBackdrop.coordinates ?: return
        if (!target.isAttached || !source.isAttached) return
        val offset = target.positionInWindow() - source.positionInWindow()
        translate(-offset.x, -offset.y) { drawLayer(layer) }
    }
}

@Composable
fun rememberScreenBackdrop(): ScreenBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { ScreenBackdrop(layer) }
}

/** Содержимое с этим модификатором видно сквозь стекло [backdrop]. */
fun Modifier.screenBackdrop(backdrop: ScreenBackdrop): Modifier =
    onGloballyPositioned { backdrop.coordinates = it }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop.layer)
        }
