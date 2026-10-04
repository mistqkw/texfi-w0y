package com.texfi.w0y.ui.shell

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.texfi.w0y.data.UiStyle
import kotlin.math.hypot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Смена стиля: [switch] с точкой, откуда пойдёт круг (в координатах окна).
 * null — из центра.
 */
fun interface StyleSwitcher {
    fun switch(style: UiStyle, from: Offset?)
}

val LocalStyleSwitcher = staticCompositionLocalOf<StyleSwitcher> { StyleSwitcher { _, _ -> } }

/**
 * Переход между стилями без пересоздания экрана.
 *
 * Перед сменой берётся снимок текущего вида; новый стиль рисуется под ним,
 * а снимок вырезается расширяющимся кругом от места нажатия — за 500 мс.
 * Activity не пересоздаётся, поэтому музыка, позиция в списках и открытые
 * экраны остаются как были. При выключенных системных анимациях стиль
 * меняется сразу.
 */
@Composable
fun StyleRevealHost(
    current: UiStyle,
    onApply: (UiStyle) -> Unit,
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val progress = remember { Animatable(1f) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var origin by remember { mutableStateOf<Offset?>(null) }
    val style by rememberUpdatedState(current)
    val apply by rememberUpdatedState(onApply)

    val switcher =
        remember {
            StyleSwitcher { target, from ->
                if (target == style || snapshot != null) return@StyleSwitcher
                val reduceMotion =
                    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
                if (reduceMotion) {
                    apply(target)
                    return@StyleSwitcher
                }
                scope.launch {
                    snapshot = runCatching { layer.toImageBitmap() }.getOrNull()
                    if (snapshot == null) {
                        apply(target)
                        return@launch
                    }
                    origin = from
                    progress.snapTo(0f)
                    apply(target)
                    // Ждём, пока новый стиль реально придёт из настроек, —
                    // иначе круг открыл бы тот же старый вид.
                    withTimeoutOrNull(APPLY_WAIT_MS) { snapshotFlow { style }.first { it == target } }
                    progress.animateTo(1f, tween(REVEAL_MS, easing = FastOutSlowInEasing))
                    snapshot = null
                }
            }
        }

    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                val image = snapshot ?: return@drawWithContent
                val center = origin ?: Offset(size.width / 2, size.height / 2)
                val far =
                    hypot(
                        maxOf(center.x, size.width - center.x),
                        maxOf(center.y, size.height - center.y),
                    )
                val radius = far * progress.value
                val hole =
                    Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        addOval(Rect(center, radius))
                    }
                clipPath(hole) { drawImage(image) }
            },
    ) {
        CompositionLocalProvider(LocalStyleSwitcher provides switcher) { content() }
    }
}

private const val REVEAL_MS = 500
private const val APPLY_WAIT_MS = 400L
