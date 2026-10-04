package com.texfi.w0y.playback

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Один запуск трека по частям, мс от команды.
 *
 * [resolveMs] — получение адреса (null — адрес уже был в кэше),
 * [firstByteMs] — первый байт звука, [readyMs] — буфер наполнен и плеер
 * готов, [totalMs] — звук реально пошёл. [fromDisk] — первые байты пришли
 * с диска (скачано или подготовлено заранее), а не из сети.
 */
data class StartupSample(
    val totalMs: Long,
    val resolveMs: Long?,
    val firstByteMs: Long?,
    val readyMs: Long?,
    val fromDisk: Boolean,
)

/** Медиана и худший случай одной части. */
data class PartStat(val median: Long, val worst: Long)

data class StartupBreakdown(
    val count: Int,
    val total: PartStat?,
    val resolve: PartStat?,
    val firstByte: PartStat?,
    val ready: PartStat?,
    /** Сколько запусков из окна начались с диска. */
    val fromDisk: Int,
)

/**
 * Сколько проходит от нажатия до звука — и из чего это время складывается.
 *
 * Быстрый старт — главное обещание приложения, и проверять его «на глаз»
 * нечестно: замер идёт всегда и показывается в настройках, чтобы ухудшение
 * было видно, а не только чувствовалось. Разбор по частям показывает, что
 * именно тормозит: извлечение адреса, сеть или буфер.
 */
@Singleton
class StartupMetrics @Inject constructor() {
    private val samples = ArrayDeque<StartupSample>()

    private val _last = MutableStateFlow<Long?>(null)
    val last: StateFlow<Long?> = _last.asStateFlow()

    private val _average = MutableStateFlow<Long?>(null)
    val average: StateFlow<Long?> = _average.asStateFlow()

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    private val _breakdown = MutableStateFlow<StartupBreakdown?>(null)
    val breakdown: StateFlow<StartupBreakdown?> = _breakdown.asStateFlow()

    @Synchronized
    fun record(ms: Long) = record(StartupSample(ms, null, null, null, fromDisk = false))

    @Synchronized
    fun record(sample: StartupSample) {
        // Окно на последние запуски: среднее за всё время быстро перестаёт
        // реагировать на изменения и становится бесполезным.
        samples.addLast(sample)
        if (samples.size > WINDOW) samples.removeFirst()
        _last.value = sample.totalMs
        _average.value = samples.map { it.totalMs }.average().toLong()
        _count.value = samples.size
        _breakdown.value = breakdownOf(samples)
    }

    companion object {
        private const val WINDOW = 20

        fun breakdownOf(list: Collection<StartupSample>): StartupBreakdown =
            StartupBreakdown(
                count = list.size,
                total = stat(list.map { it.totalMs }),
                resolve = stat(list.mapNotNull { it.resolveMs }),
                firstByte = stat(list.mapNotNull { it.firstByteMs }),
                ready = stat(list.mapNotNull { it.readyMs }),
                fromDisk = list.count { it.fromDisk },
            )

        fun stat(values: List<Long>): PartStat? {
            if (values.isEmpty()) return null
            val sorted = values.sorted()
            val mid = sorted.size / 2
            val median = if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
            return PartStat(median, sorted.last())
        }
    }
}
