package com.texfi.w0y.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.LocalW0yColors

/**
 * Кнопка перемотки: вперёд, назад.
 *
 * Отклик резче, чем у плитки — перемотку жмут быстро и несколько раз подряд,
 * и анимация не должна отставать от пальца.
 */
@Composable
fun TransportButton(
    rows: List<String>,
    size: Int,
    onClick: () -> Unit,
    color: Color? = null,
    touchPadding: Int = 0,
) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    val tap = rememberTapHaptic()
    PixelSprite(
        rows = rows,
        color = color ?: colors.text,
        modifier =
            Modifier
                .pressScale(interaction, pressed = 0.78f)
                .clickable(interactionSource = interaction, indication = null) {
                    tap()
                    onClick()
                }
                .padding(touchPadding.dp)
                .size(size.dp),
    )
}

/**
 * Play/pause: одна иконка перетекает в другую через уменьшение.
 *
 * Отдельная анимация именно для этого действия: это главная кнопка
 * приложения, и по ней должно быть видно, что состояние сменилось, даже
 * если звук ещё не успел начаться.
 */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    size: Int,
    onClick: () -> Unit,
    color: Color? = null,
    touchPadding: Int = 0,
) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    val tap = rememberSelectHaptic()
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.55f) + fadeIn(tween(90)))
                .togetherWith(scaleOut(tween(110), targetScale = 0.55f) + fadeOut(tween(90)))
        },
        label = "playPause",
    ) { playing ->
        PixelSprite(
            rows = if (playing) Sprites.pause else Sprites.play,
            color = color ?: colors.accent,
            modifier =
                Modifier
                    .pressScale(interaction, pressed = 0.84f)
                    .clickable(interactionSource = interaction, indication = null) {
                        tap()
                        onClick()
                    }
                    .padding(touchPadding.dp)
                    .size(size.dp),
        )
    }
}
