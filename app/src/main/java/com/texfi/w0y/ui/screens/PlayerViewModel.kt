package com.texfi.w0y.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.data.LibraryRepository
import com.texfi.w0y.data.Lyrics
import com.texfi.w0y.data.LyricsRepository
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.SoundPreset
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.W0ySettings
import com.texfi.w0y.data.db.PinEntity
import com.texfi.w0y.data.db.PlaylistEntity
import com.texfi.w0y.data.QueueMode
import com.texfi.w0y.playback.AudioDevicesRepository
import com.texfi.w0y.playback.AudioOutput
import com.texfi.w0y.playback.DownloadsRepository
import com.texfi.w0y.playback.PlaybackStarter
import com.texfi.w0y.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val player: PlayerConnection,
    private val library: LibraryRepository,
    private val lyricsRepository: LyricsRepository,
    private val settingsRepository: SettingsRepository,
    private val downloads: DownloadsRepository,
    private val playback: PlaybackStarter,
    devices: AudioDevicesRepository,
) : ViewModel() {
    /**
     * Через что идёт звук прямо сейчас.
     *
     * Плеер показывает это только когда выход не сам телефон: «играет
     * через динамик» — очевидность, а вот «играет через колонку» объясняет
     * тишину в наушниках.
     */
    val activeOutput: StateFlow<AudioOutput?> =
        devices.outputs
            .map { outputs -> outputs.firstOrNull { it.active } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    /** Что играет после текущего трека и не подбираются ли сейчас похожие. */
    val queueMode: StateFlow<QueueMode> = playback.mode
    val loadingRadio: StateFlow<Boolean> = playback.loadingRadio
    val findingClean: StateFlow<Boolean> = playback.findingClean

    fun setQueueMode(mode: QueueMode) = playback.applyMode(mode)

    /** Один нажатием меняет скорость, тон и эхо разом. */
    fun setSoundPreset(preset: SoundPreset) = viewModelScope.launch {
        settingsRepository.setSoundPreset(preset.speed, preset.pitch, preset.reverb)
    }

    val settings: StateFlow<W0ySettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), W0ySettings())

    val playlists: StateFlow<List<PlaylistEntity>> =
        library.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val isLiked: StateFlow<Boolean> =
        player.state
            .map { it.song?.id }
            .flatMapLatest { id -> if (id == null) MutableStateFlow(false) else library.isLiked(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Закреплён ли текущий трек в быстром наборе. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isPinned: StateFlow<Boolean> =
        player.state
            .map { it.song?.id }
            .flatMapLatest { id ->
                library.pins.map { pins -> id != null && pins.any { it.key == PinEntity.key(PinEntity.KIND_SONG, id) } }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun togglePin(song: SongItem) = viewModelScope.launch {
        library.togglePin(
            kind = PinEntity.KIND_SONG,
            targetId = song.id,
            title = song.title,
            subtitle = song.artist.takeIf { it.isNotBlank() },
            thumbnailUrl = song.thumbnailUrl,
        )
    }

    private val _lyrics = MutableStateFlow<Lyrics?>(null)
    val lyrics: StateFlow<Lyrics?> = _lyrics.asStateFlow()

    private var lyricsForId: String? = null

    /** Лирика тянется один раз на трек и только если она включена. */
    fun ensureLyrics(song: SongItem?) {
        if (song == null || song.id == lyricsForId) return
        lyricsForId = song.id
        _lyrics.value = null
        viewModelScope.launch {
            if (!settingsRepository.settings.first().showLyrics) return@launch
            _lyrics.value = lyricsRepository.lyrics(song)
        }
    }

    fun toggleLike(song: SongItem) {
        viewModelScope.launch {
            val liked = library.toggleLike(song)
            // Авто-скачивание лайкнутого — отдельная настройка, поэтому
            // проверяем её здесь, а не в репозитории лайков.
            if (liked && settingsRepository.settings.first().autoDownloadLiked) {
                downloads.download(song)
            }
        }
    }

    fun download(song: SongItem) = downloads.download(song)

    fun addToPlaylist(playlistId: Long, song: SongItem) =
        viewModelScope.launch { library.addToPlaylist(playlistId, song) }

    fun createPlaylistWith(name: String, song: SongItem) =
        viewModelScope.launch {
            val id = library.createPlaylist(name)
            library.addToPlaylist(id, song)
        }

    fun startSleepTimer() =
        viewModelScope.launch {
            player.startSleepTimer(settingsRepository.settings.first().sleepTimerDefaultMin)
        }

    fun cancelSleepTimer() = player.cancelSleepTimer()

    /** Перемотка на фиксированный шаг — и назад, и вперёд одной функцией. */
    fun seekBy(deltaMs: Long) = player.seekBy(deltaMs)
}
