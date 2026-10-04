package com.texfi.w0y.playback

import com.texfi.w0y.data.Quality
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.W0ySettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Последний снимок настроек для потоков загрузчика.
 *
 * Загрузчик спрашивает лимит скорости и качество на каждом куске; читать
 * для этого DataStore с блокировкой — значит тормозить загрузку на
 * пустом месте. Снимок обновляется сам, когда настройки меняются.
 */
@Singleton
class DownloadSettingsHolder @Inject constructor(settings: SettingsRepository) {
    @Volatile
    var current: W0ySettings = W0ySettings()
        private set

    init {
        settings.settings.onEach { current = it }.launchIn(CoroutineScope(Dispatchers.IO + SupervisorJob()))
    }

    fun downloadQuality(onWifi: Boolean): Quality =
        if (onWifi) current.downloadQualityWifi else current.downloadQualityMobile
}
