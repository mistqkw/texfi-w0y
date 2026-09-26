package com.texfi.w0y.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.SimpleCache
import com.texfi.w0y.R
import com.texfi.w0y.data.Quality
import com.texfi.w0y.data.ExplicitFallback
import com.texfi.w0y.data.QueueMode
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.StartTab
import com.texfi.w0y.data.Language
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.ThemeMode
import com.texfi.w0y.data.W0ySettings
import com.texfi.w0y.playback.AudioSessionHolder
import com.texfi.w0y.playback.PlaybackStarter
import com.texfi.w0y.playback.StartupMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @param:ApplicationContext private val context: Context,
    private val repository: SettingsRepository,
    @param:Named("stream") private val streamCache: SimpleCache,
    private val audioSession: AudioSessionHolder,
    private val playback: PlaybackStarter,
    startupMetrics: StartupMetrics,
) : ViewModel() {
    /** Замер «нажал → пошёл звук»: главное обещание приложения, измеренное. */
    val startupAverage: StateFlow<Long?> = startupMetrics.average
    val startupLast: StateFlow<Long?> = startupMetrics.last
    val startupCount: StateFlow<Int> = startupMetrics.count

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
            _message.value = context.getString(R.string.settings_cache_cleared)
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

    fun setSpeed(value: Float) = update { repository.setSpeed(value) }

    fun setPitch(value: Float) = update { repository.setPitch(value) }

    fun setReverb(value: Reverb) = update { repository.setReverb(value) }

    fun setShowRecommendations(value: Boolean) = update { repository.setShowRecommendations(value) }

    fun setCompactRows(value: Boolean) = update { repository.setCompactRows(value) }

    fun setSaveSearchHistory(value: Boolean) = update { repository.setSaveSearchHistory(value) }

    fun setDownloadOnWifiOnly(value: Boolean) = update { repository.setDownloadOnWifiOnly(value) }

    fun setStartTab(value: StartTab) = update { repository.setStartTab(value) }

    fun setCleanMode(value: Boolean) = update { repository.setCleanMode(value) }

    fun setExplicitFallback(value: ExplicitFallback) = update { repository.setExplicitFallback(value) }

    fun setHideExplicit(value: Boolean) = update { repository.setHideExplicit(value) }

    fun setMuteSwearLines(value: Boolean) = update { repository.setMuteSwearLines(value) }

    suspend fun exportJson(): String = repository.export(repository.settings.first())

    fun importJson(json: String) =
        update {
            runCatching { repository.import(json) }
                .onSuccess { _message.value = context.getString(R.string.settings_imported) }
                .onFailure { _message.value = context.getString(R.string.settings_import_failed, it.message.orEmpty()) }
        }

    fun reportNoEqualizer() {
        _message.value = context.getString(R.string.settings_no_equalizer)
    }

    /**
     * Смена языка.
     *
     * Ресурсы читаются при создании экрана, поэтому после записи настройки
     * активити пересоздаётся — иначе половина экрана осталась бы на старом
     * языке до следующего запуска.
     */
    fun setLanguage(value: Language, onApplied: () -> Unit) =
        update {
            repository.setLanguage(value)
            onApplied()
        }

    fun consumeMessage() {
        _message.value = null
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
