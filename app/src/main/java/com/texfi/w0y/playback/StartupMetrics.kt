package com.texfi.w0y.playback

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Сколько проходит от нажатия до звука.
 *
 * Быстрый старт — главное обещание приложения, и проверять его «на глаз»
 * нечестно: замер идёт всегда и показывается в настройках, чтобы ухудшение
 * было видно, а не только чувствовалось.
 */
@Singleton
class StartupMetrics @Inject constructor() {
    private val samples = ArrayDeque<Long>()

    private val _last = MutableStateFlow<Long?>(null)
    val last: StateFlow<Long?> = _last.asStateFlow()

    private val _average = MutableStateFlow<Long?>(null)
    val average: StateFlow<Long?> = _average.asStateFlow()

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    @Synchronized
    fun record(ms: Long) {
        // Окно на последние запуски: среднее за всё время быстро перестаёт
        // реагировать на изменения и становится бесполезным.
        samples.addLast(ms)
        if (samples.size > WINDOW) samples.removeFirst()
        _last.value = ms
        _average.value = samples.average().toLong()
        _count.value = samples.size
    }

    private companion object {
        const val WINDOW = 20
    }
}
