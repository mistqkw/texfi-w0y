package com.texfi.w0y.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.isSmooth
import com.texfi.w0y.ui.theme.liquidGlass
import androidx.compose.ui.graphics.Color
import com.texfi.w0y.ui.theme.styleTokens
import kotlin.math.roundToInt

/** Пункт нижней навигации. */
data class NavItem(val label: String, val sprite: List<String>)

/**
 * Плавающая подложка нижних панелей — навигации и мини-плеера.
 *
 * Pixel: квадратный блок с рамкой и жёсткой смещённой тенью без размытия.
 * Smooth: капсула с мягкой тенью. Панели стоят над контентом в общей
 * колонке, а не поверх него: список кончается над ними, и ничего не
 * перекрывается при любой высоте панелей.
 */
@Composable
fun FloatingSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalW0yColors.current
    val tokens = styleTokens
    if (isSmooth || colors.glass) {
        Box(
            modifier
                .shadow(14.dp, tokens.bar, clip = false, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
                .liquidGlass(tokens.bar, colors.surfaceHigh),
            content = content,
        )
        return
    }
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .offset(FLOAT_SHADOW, FLOAT_SHADOW)
                .background(colors.shadow),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceHigh)
                .border(2.dp, colors.border),
            content = content,
        )
    }
}

/**
 * Нижняя навигация: один компонент, два вида.
 *
 * Выбранный пункт подсвечен блоком, который переезжает под новый пункт —
 * в Pixel ступенями (5 шагов за 200 мс), в Smooth плавно. Каждый пункт не
 * меньше 48 dp по высоте и по ширине.
 */
@Composable
fun FloatingNavBar(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalW0yColors.current
    val smooth = isSmooth
    val tokens = styleTokens
    val position = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected, smooth) {
        position.animateTo(
            selected.toFloat(),
            if (smooth) spring(dampingRatio = 0.8f, stiffness = 500f) else tween(NAV_MS, easing = SteppedEasing(NAV_STEPS)),
        )
    }
    FloatingSurface(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(NAV_INSET)) {
            val slot = maxWidth / items.size
            // Блок выбранного пункта: рисуется под иконками, сдвиг читается в слое раскладки.
            Box(
                Modifier
                    .offset { IntOffset((slot.toPx() * position.value).roundToInt(), 0) }
                    .width(slot)
                    .height(NAV_HEIGHT)
                    .clip(if (smooth) tokens.bar else tokens.chip)
                    .background(colors.accent),
            )
            Row(Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    val active = index == selected
                    val interaction = remember { MutableInteractionSource() }
                    val tint by animateColorAsState(
                        targetValue = if (active) colors.onAccent else colors.textMuted,
                        animationSpec = if (smooth) tween(180) else snap(),
                        label = "navTint",
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(NAV_HEIGHT)
                                .pressScale(interaction, pressed = 0.9f)
                                .clickable(interactionSource = interaction, indication = null) { onSelect(index) }
                                .padding(vertical = 6.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        PixelSprite(
                            rows = item.sprite,
                            color = tint,
                            modifier = Modifier.size(22.dp).popWhenActivated(active, peak = 1.18f),
                        )
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Высота пункта навигации: 48 dp касания плюс подпись. */
val NAV_HEIGHT: Dp = 54.dp
private val NAV_INSET = 5.dp
private val FLOAT_SHADOW = 4.dp
private const val NAV_MS = 200
private const val NAV_STEPS = 5
