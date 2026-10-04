package com.texfi.w0y.ui.components

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.texfi.w0y.ui.theme.LocalW0yColors

/**
 * Фон плавного стиля: мягкое пятно цвета обложки играющего трека.
 *
 * Градиент, а не размытие картинки: ни одного лишнего прохода по кадру, и
 * цвет посчитан один раз на трек ([rememberCoverTint] его кэширует). В
 * чёрной теме и в режиме энергосбережения фон простой — пикселям OLED
 * лучше быть выключенными.
 */
@Composable
fun SmoothBackdrop(coverUrl: String?, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val context = LocalContext.current
    val oled = colors.background == Color.Black && colors.surface == Color.Black
    val saver = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    if (oled || saver) return
    val tint by rememberCoverTint(coverUrl, colors.accent)
    val shown by animateColorAsState(tint, tween(600), label = "backdrop")
    Canvas(modifier) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(shown.copy(alpha = 0.30f), Color.Transparent),
                center = Offset(size.width * 0.2f, 0f),
                radius = size.maxDimension * 0.8f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(shown.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width, size.height * 0.75f),
                radius = size.maxDimension * 0.6f,
            ),
        )
    }
}
