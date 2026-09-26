package com.texfi.w0y.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Включена ли вибрация. Значение приходит из настроек и раздаётся всем
 * кнопкам разом: пробрасывать флаг параметром через каждый список,
 * строку и панель — то же самое, что не иметь настройки.
 */
val LocalHaptics = compositionLocalOf { true }

/**
 * Короткий отклик на нажатие.
 *
 * Характер подобран под утилиту, а не под игру: в f0kus отклики
 * выразительные, здесь — сухие и короткие. Одинаковые паттерны во всех
 * приложениях экосистемы были бы не единством, а потерей характера.
 */
@Composable
fun rememberTapHaptic(): () -> Unit {
    val enabled = LocalHaptics.current
    val feedback = LocalHapticFeedback.current
    return remember(enabled, feedback) {
        {
            if (enabled) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}

/** Отклик посильнее: смена экрана, переключение раздела, готовое действие. */
@Composable
fun rememberSelectHaptic(): () -> Unit {
    val enabled = LocalHaptics.current
    val feedback = LocalHapticFeedback.current
    return remember(enabled, feedback) {
        {
            if (enabled) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}
