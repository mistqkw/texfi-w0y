package com.texfi.w0y.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texfi.w0y.data.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Очередь и воспроизведение. mpv играет, этот класс решает, что играть
 * дальше: следующий в очереди, а когда очередь кончилась — радио по
 * последнему треку, как на телефоне.
 */
class PlayerCtl(
    private val scope: CoroutineScope,
    private val yt: Yt,
    private val app: AppState,
) {
    var queue by mutableStateOf<List<SongItem>>(emptyList())
        private set
    var index by mutableIntStateOf(-1)
        private set
    var playing by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var position by mutableDoubleStateOf(0.0)
        private set
    var duration by mutableDoubleStateOf(0.0)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var volume by mutableIntStateOf(100)
        private set

    val current: SongItem? get() = queue.getOrNull(index)

    /** Сколько секунд осталось до остановки таймером сна; null — таймер не заведён. */
    var sleepRemaining by mutableStateOf<Int?>(null)
        private set

    /** Таймер «до конца трека»: музыка встанет, когда доиграет текущий. */
    var sleepAfterTrack by mutableStateOf(false)
        private set
    private var sleepJob: Job? = null

    /** Есть ли у mpv загруженный трек: после рестарта очередь восстановлена, а звука ещё нет. */
    private var loaded = false
    private var startJob: Job? = null
    private var mpvOk = true

    private val mpv =
        Mpv(
            onTime = { position = it },
            onDuration = { duration = it },
            onPause = { playing = !it && loaded },
            onEnd = { reason ->
                when (reason) {
                    "eof" ->
                        scope.launch {
                            if (sleepAfterTrack) {
                                sleepAfterTrack = false
                                playing = false
                                // Позиция остаётся в конце, следующий трек готов к запуску.
                                if (index + 1 < queue.size) index += 1
                                loaded = false
                                app.saveQueue(queue, index)
                            } else {
                                next(auto = true)
                            }
                        }
                    "error" -> {
                        loading = false
                        playing = false
                        error = "Не удалось проиграть трек"
                        scope.launch { delay(1500); next(auto = true) }
                    }
                }
            },
            onFileLoaded = {
                loading = false
                loaded = true
                playing = true
            },
        )

    fun start(restored: List<SongItem>, restoredIndex: Int, volume: Int) {
        mpvOk = mpv.start()
        if (!mpvOk) error = "Не найден mpv: поставь его (sudo pacman -S mpv)"
        queue = restored
        index = restoredIndex.coerceIn(-1, restored.lastIndex)
        this.volume = volume
        mpv.setVolume(volume)
    }

    fun shutdown() = mpv.shutdown()

    fun play(songs: List<SongItem>, at: Int) {
        queue = songs
        index = at.coerceIn(0, songs.lastIndex)
        loadCurrent()
        app.saveQueue(queue, index)
    }

    fun playOne(song: SongItem) = play(listOf(song), 0)

    private fun loadCurrent() {
        val song = current ?: return
        startJob?.cancel()
        error = null
        loading = true
        playing = false
        loaded = false
        position = 0.0
        duration = 0.0
        mpv.applySound(app.soundOf(song.id).also { current -> soundNow = current })
        val local = app.downloadedPath(song.id)
        if (local != null) {
            // Скачанный трек играет с диска: ни сети, ни ссылки, которая истекает.
            startJob = scope.launch {
                mpv.load(local, emptyMap())
                app.remember(song)
            }
            return
        }
        startJob =
            scope.launch {
                runCatching { yt.stream(song.id) }
                    .onSuccess {
                        mpv.load(it.audioUrl, it.headers)
                        app.remember(song)
                        // Следующий трек греем заранее: старт без паузы.
                        queue.getOrNull(index + 1)?.let { n -> launch { runCatching { yt.stream(n.id) } } }
                    }.onFailure {
                        loading = false
                        error = "YouTube не отдал звук этого трека"
                        delay(1500)
                        next(auto = true)
                    }
            }
    }

    /** Звучание текущего трека: для панели «Звук». */
    var soundNow by mutableStateOf(com.texfi.w0y.data.SoundProfile.Plain)
        private set

    fun setSound(profile: com.texfi.w0y.data.SoundProfile) {
        val song = current ?: return
        soundNow = profile
        app.saveSound(song.id, profile)
        mpv.applySound(profile)
    }

    /** Таймер сна: через [minutes] минут пауза. */
    fun startSleep(minutes: Int) {
        cancelSleep()
        sleepRemaining = minutes * 60
        sleepJob =
            scope.launch {
                while ((sleepRemaining ?: 0) > 0) {
                    delay(1000)
                    sleepRemaining = (sleepRemaining ?: 1) - 1
                }
                sleepRemaining = null
                if (playing) mpv.setPause(true)
            }
    }

    fun sleepUntilTrackEnds() {
        cancelSleep()
        sleepAfterTrack = true
    }

    fun cancelSleep() {
        sleepJob?.cancel()
        sleepJob = null
        sleepRemaining = null
        sleepAfterTrack = false
    }

    fun toggle() {
        if (!mpvOk) return
        if (current == null) return
        if (!loaded) {
            loadCurrent()
            return
        }
        mpv.setPause(playing)
    }

    suspend fun next(auto: Boolean = false) {
        if (queue.isEmpty()) return
        if (index + 1 < queue.size) {
            index += 1
            loadCurrent()
            app.saveQueue(queue, index)
            return
        }
        // Очередь кончилась: радио по последнему треку.
        val last = current ?: return
        val more = runCatching { yt.radio(last.id) }.getOrDefault(emptyList()).filter { s -> queue.none { it.id == s.id } }
        if (more.isNotEmpty()) {
            queue = queue + more
            index += 1
            loadCurrent()
            app.saveQueue(queue, index)
        } else if (auto) {
            playing = false
        }
    }

    fun nextClick() = scope.launch { next() }

    fun previous() {
        if (position > 4 || index <= 0) {
            mpv.seek(0.0)
            if (!loaded) loadCurrent()
            return
        }
        index -= 1
        loadCurrent()
        app.saveQueue(queue, index)
    }

    fun seekFraction(fraction: Double) {
        if (duration > 0 && loaded) {
            position = duration * fraction
            mpv.seek(duration * fraction)
        }
    }

    fun seekSeconds(seconds: Double) {
        if (loaded) {
            position = seconds
            mpv.seek(seconds)
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices) return
        val list = queue.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        // Индекс играющего трека едет вместе с ним.
        index =
            when {
                from == index -> to
                from < index && to >= index -> index - 1
                from > index && to <= index -> index + 1
                else -> index
            }
        queue = list
        app.saveQueue(queue, index)
    }

    fun changeVolume(percent: Int) {
        volume = percent.coerceIn(0, 100)
        mpv.setVolume(volume)
        app.saveVolume(volume)
    }

    fun enqueue(song: SongItem, next: Boolean) {
        if (queue.isEmpty()) {
            play(listOf(song), 0)
            return
        }
        queue =
            if (next) {
                queue.toMutableList().apply { add(index + 1, song) }
            } else {
                queue + song
            }
        app.saveQueue(queue, index)
    }

    fun removeFromQueue(position: Int) {
        if (position !in queue.indices) return
        val wasCurrent = position == index
        queue = queue.toMutableList().apply { removeAt(position) }
        if (position < index) index -= 1
        if (wasCurrent) {
            if (queue.isEmpty()) {
                index = -1
                mpv.stop()
                playing = false
                loaded = false
            } else {
                index = index.coerceAtMost(queue.lastIndex)
                loadCurrent()
            }
        }
        app.saveQueue(queue, index)
    }

    fun jump(to: Int) {
        index = to
        loadCurrent()
        app.saveQueue(queue, index)
    }
}
