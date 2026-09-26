package com.texfi.w0y.playback

import com.texfi.w0y.data.QueueMode
import com.texfi.w0y.data.RecommendationRepository
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.SongItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Единая точка запуска воспроизведения.
 *
 * Все экраны включают треки через неё, а не через плеер напрямую: выбранный
 * режим очереди должен действовать одинаково откуда угодно — из поиска, из
 * плейлиста, с плитки на главной.
 */
@Singleton
class PlaybackStarter @Inject constructor(
    private val player: PlayerConnection,
    private val settings: SettingsRepository,
    private val recommendations: RecommendationRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var radioJob: Job? = null

    private val _mode = MutableStateFlow(QueueMode.ORDER)
    val mode: StateFlow<QueueMode> = _mode.asStateFlow()

    private val _loadingRadio = MutableStateFlow(false)
    val loadingRadio: StateFlow<Boolean> = _loadingRadio.asStateFlow()

    /** Для какого трека уже подобрано продолжение — чтобы не просить дважды. */
    private var extendedFor: String? = null

    init {
        // Режим держим под рукой готовым значением: решать, что играть
        // дальше, приходится в момент нажатия, и ждать чтение настроек там
        // нельзя — это прямая задержка между нажатием и звуком.
        scope.launch {
            settings.settings.map { it.queueMode }.collect { _mode.value = it }
        }
        // В режиме рекомендаций очередь не должна заканчиваться: дойдя до
        // последнего трека, продолжаем от него же. Иначе «дальше похожее»
        // упиралось бы в тишину через двадцать пять треков.
        scope.launch {
            player.state.collect { state ->
                val song = state.song ?: return@collect
                if (_mode.value != QueueMode.RADIO) return@collect
                if (state.currentIndex < state.queue.lastIndex) return@collect
                if (extendedFor == song.id) return@collect
                extendWithRadio(song)
            }
        }
    }

    fun play(songs: List<SongItem>, index: Int) {
        if (songs.isEmpty()) return
        val chosen = songs[index.coerceIn(songs.indices)]
        radioJob?.cancel()
        when (_mode.value) {
            QueueMode.ORDER -> player.play(songs, index)

            QueueMode.SHUFFLE -> {
                // Выбранный трек остаётся первым: человек нажал именно на
                // него, а не «включи что-нибудь».
                val rest = songs.filterNot { it.id == chosen.id }.shuffled()
                player.play(listOf(chosen) + rest, 0)
            }

            QueueMode.RADIO -> {
                // Звук начинается сразу, рекомендации догружаются следом:
                // ждать сеть перед первым тактом — недопустимо.
                player.play(listOf(chosen), 0)
                extendWithRadio(chosen)
            }
        }
    }

    /** Смена режима на ходу: перестраивает хвост очереди, не трогая текущий трек. */
    fun applyMode(mode: QueueMode) {
        scope.launch { settings.setQueueMode(mode) }
        _mode.value = mode
        radioJob?.cancel()
        val current = player.state.value.song ?: return
        when (mode) {
            QueueMode.ORDER -> Unit
            QueueMode.SHUFFLE -> player.replaceUpcoming(player.upcoming().shuffled())
            QueueMode.RADIO -> extendWithRadio(current)
        }
    }

    private fun extendWithRadio(seed: SongItem) {
        extendedFor = seed.id
        radioJob =
            scope.launch {
                _loadingRadio.value = true
                val played = player.state.value.queue.map { it.id }.toSet()
                val next =
                    runCatching {
                        withContext(Dispatchers.IO) { recommendations.continuation(seed, exclude = played) }
                    }.onFailure { Timber.w(it, "Рекомендации для ${seed.id} не пришли") }
                        .getOrDefault(emptyList())
                if (next.isNotEmpty()) player.replaceUpcoming(next)
                _loadingRadio.value = false
            }
    }
}
