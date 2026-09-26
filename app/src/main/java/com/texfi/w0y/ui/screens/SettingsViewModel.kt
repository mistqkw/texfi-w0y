package com.texfi.w0y.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.SimpleCache
import com.texfi.w0y.data.Quality
import com.texfi.w0y.data.QueueMode
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.ThemeMode
import com.texfi.w0y.data.W0ySettings
import com.texfi.w0y.playback.AudioSessionHolder
import com.texfi.w0y.playback.PlaybackStarter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    @param:Named("stream") private val streamCache: SimpleCache,
    private val audioSession: AudioSessionHolder,
    private val playback: PlaybackStarter,
) : ViewModel() {
    val audioSessionId: Int get() = audioSession.sessionId
    val settings: StateFlow<W0ySettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), W0ySettings())

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refreshCacheSize()
    }

    fun refreshCacheSize() {
        viewModelScope.launch { _cacheBytes.value = withContext(Dispatchers.IO) { streamCache.cacheSpace } }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                streamCache.keys.toList().forEach { key ->
                    streamCache.removeResource(key)
                }
            }
            refreshCacheSize()
            _message.value = "Кэш очищен"
        }
    }

    fun setQualityWifi(value: Quality) = update { repository.setQualityWifi(value) }

    fun setQualityMobile(value: Quality) = update { repository.setQualityMobile(value) }

    fun setPreload(value: Boolean) = update { repository.setPreload(value) }

    fun setCacheLimit(mb: Int) = update { repository.setCacheLimit(mb) }

    fun setAutoDownload(value: Boolean) = update { repository.setAutoDownload(value) }

    fun setNormalize(value: Boolean) = update { repository.setNormalize(value) }

    fun setSkipSilence(value: Boolean) = update { repository.setSkipSilence(value) }

    fun setPauseOnUnplug(value: Boolean) = update { repository.setPauseOnUnplug(value) }

    fun setResumeOnPlug(value: Boolean) = update { repository.setResumeOnPlug(value) }

    fun setSleepDefault(minutes: Int) = update { repository.setSleepDefault(minutes) }

    fun setTheme(value: ThemeMode) = update { repository.setTheme(value) }

    fun setShowLyrics(value: Boolean) = update { repository.setShowLyrics(value) }

    fun setKeepHistory(value: Boolean) = update { repository.setKeepHistory(value) }

    /**
     * Смена режима идёт через стартер, а не прямо в настройки: он ещё и
     * перестроит хвост уже играющей очереди, иначе выбор подействовал бы
     * только на следующий запуск.
     */
    fun setQueueMode(value: QueueMode) = playback.applyMode(value)

    suspend fun exportJson(): String = repository.export(repository.settings.first())

    fun importJson(json: String) =
        update {
            runCatching { repository.import(json) }
                .onSuccess { _message.value = "Настройки загружены" }
                .onFailure { _message.value = "Файл не разобрался: ${it.message}" }
        }

    fun reportNoEqualizer() {
        _message.value = "На этом телефоне нет системного эквалайзера"
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
