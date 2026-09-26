package com.texfi.w0y.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.texfi.w0y.R

/**
 * Пиксельный шрифт — только заголовки, лейблы секций и крупные числа.
 * Всё, что читают, а не разглядывают (названия треков, пункты настроек,
 * описания), набирается системным: Press Start 2P на 12sp нечитаем.
 */
val PixelFamily = FontFamily(Font(R.font.press_start_2p, FontWeight.Normal))
val BodyFamily = FontFamily.Default

val PixelTitle = TextStyle(
    fontFamily = PixelFamily,
    fontSize = 15.sp,
    lineHeight = 24.sp,
)

/** Заголовок экрана — крупнее внутренних: он задаёт верх страницы. */
val PixelScreenTitle = TextStyle(
    fontFamily = PixelFamily,
    fontSize = 19.sp,
    lineHeight = 26.sp,
)

val PixelSectionLabel = TextStyle(
    fontFamily = PixelFamily,
    fontSize = 9.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.sp,
)

val PixelBigNumber = TextStyle(
    fontFamily = PixelFamily,
    fontSize = 22.sp,
    lineHeight = 30.sp,
)

val W0yTypography = Typography(
    titleLarge = PixelTitle,
    titleMedium = PixelTitle.copy(fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = PixelSectionLabel,
    bodyLarge = TextStyle(fontFamily = BodyFamily, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = BodyFamily, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = BodyFamily, fontSize = 12.sp, lineHeight = 17.sp),
    labelMedium = TextStyle(fontFamily = BodyFamily, fontSize = 11.sp, lineHeight = 15.sp),
)
