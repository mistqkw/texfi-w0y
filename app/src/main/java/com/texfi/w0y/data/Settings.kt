package com.texfi.w0y.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("w0y")

/** Качество звука: с чем идти в извлечение потока. */
enum class Quality {
    LOW,
    MEDIUM,
    HIGH,
}

/**
 * Что играет после выбранного трека.
 *
 * Выбор запоминается: это привычка слушателя, а не настройка одного
 * нажатия, и спрашивать о ней при каждом запуске трека — издевательство.
 */
enum class QueueMode(val label: String, val hint: String) {
    ORDER("ПО ОЧЕРЕДИ", "Дальше идёт список, из которого включили трек."),
    SHUFFLE("ПЕРЕМЕШАТЬ", "Тот же список, но вперемешку."),
    RADIO("РЕКОМЕНДАЦИИ β", "Дальше — похожее по звучанию и жанру, с учётом того, что ты уже слушал и искал."),
}

enum class ThemeMode {
    DARK,
    OLED,
    LIGHT,
}

/**
 * Все настройки одним снимком — так экраны и плеер читают согласованное
 * состояние, а не собирают его из десятка отдельных потоков.
 */
data class W0ySettings(
    val qualityWifi: Quality = Quality.HIGH,
    val qualityMobile: Quality = Quality.MEDIUM,
    val preloadNext: Boolean = true,
    val cacheLimitMb: Int = 512,
    val autoDownloadLiked: Boolean = false,
    val normalizeVolume: Boolean = true,
    val skipSilence: Boolean = false,
    val pauseOnHeadphonesOut: Boolean = true,
    val resumeOnHeadphonesIn: Boolean = false,
    val sleepTimerDefaultMin: Int = 30,
    val theme: ThemeMode = ThemeMode.DARK,
    val showLyrics: Boolean = true,
    val keepHistory: Boolean = true,
    val queueMode: QueueMode = QueueMode.ORDER,
)

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val settings: Flow<W0ySettings> =
        context.dataStore.data
            // Повреждённый файл настроек не должен ронять приложение —
            // лучше стартовать со значениями по умолчанию.
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { prefs ->
                W0ySettings(
                    qualityWifi = prefs.enum(Keys.QUALITY_WIFI, Quality.HIGH),
                    qualityMobile = prefs.enum(Keys.QUALITY_MOBILE, Quality.MEDIUM),
                    preloadNext = prefs[Keys.PRELOAD] ?: true,
                    cacheLimitMb = prefs[Keys.CACHE_MB] ?: 512,
                    autoDownloadLiked = prefs[Keys.AUTO_DOWNLOAD] ?: false,
                    normalizeVolume = prefs[Keys.NORMALIZE] ?: true,
                    skipSilence = prefs[Keys.SKIP_SILENCE] ?: false,
                    pauseOnHeadphonesOut = prefs[Keys.PAUSE_ON_UNPLUG] ?: true,
                    resumeOnHeadphonesIn = prefs[Keys.RESUME_ON_PLUG] ?: false,
                    sleepTimerDefaultMin = prefs[Keys.SLEEP_MIN] ?: 30,
                    theme = prefs.enum(Keys.THEME, ThemeMode.DARK),
                    showLyrics = prefs[Keys.LYRICS] ?: true,
                    keepHistory = prefs[Keys.HISTORY] ?: true,
                    queueMode = prefs.enum(Keys.QUEUE_MODE, QueueMode.ORDER),
                )
            }

    suspend fun setQualityWifi(value: Quality) = put(Keys.QUALITY_WIFI, value.name)

    suspend fun setQualityMobile(value: Quality) = put(Keys.QUALITY_MOBILE, value.name)

    suspend fun setPreload(value: Boolean) = put(Keys.PRELOAD, value)

    suspend fun setCacheLimit(mb: Int) = put(Keys.CACHE_MB, mb)

    suspend fun setAutoDownload(value: Boolean) = put(Keys.AUTO_DOWNLOAD, value)

    suspend fun setNormalize(value: Boolean) = put(Keys.NORMALIZE, value)

    suspend fun setSkipSilence(value: Boolean) = put(Keys.SKIP_SILENCE, value)

    suspend fun setPauseOnUnplug(value: Boolean) = put(Keys.PAUSE_ON_UNPLUG, value)

    suspend fun setResumeOnPlug(value: Boolean) = put(Keys.RESUME_ON_PLUG, value)

    suspend fun setSleepDefault(minutes: Int) = put(Keys.SLEEP_MIN, minutes)

    suspend fun setTheme(value: ThemeMode) = put(Keys.THEME, value.name)

    suspend fun setShowLyrics(value: Boolean) = put(Keys.LYRICS, value)

    suspend fun setKeepHistory(value: Boolean) = put(Keys.HISTORY, value)

    suspend fun setQueueMode(value: QueueMode) = put(Keys.QUEUE_MODE, value.name)

    /** Экспорт всех настроек одной строкой JSON — её можно сохранить в файл. */
    suspend fun export(current: W0ySettings): String =
        JSONObject()
            .put("qualityWifi", current.qualityWifi.name)
            .put("qualityMobile", current.qualityMobile.name)
            .put("preloadNext", current.preloadNext)
            .put("cacheLimitMb", current.cacheLimitMb)
            .put("autoDownloadLiked", current.autoDownloadLiked)
            .put("normalizeVolume", current.normalizeVolume)
            .put("skipSilence", current.skipSilence)
            .put("pauseOnHeadphonesOut", current.pauseOnHeadphonesOut)
            .put("resumeOnHeadphonesIn", current.resumeOnHeadphonesIn)
            .put("sleepTimerDefaultMin", current.sleepTimerDefaultMin)
            .put("theme", current.theme.name)
            .put("showLyrics", current.showLyrics)
            .put("keepHistory", current.keepHistory)
            .toString(2)

    suspend fun import(json: String) {
        val obj = JSONObject(json)
        context.dataStore.edit { prefs ->
            obj.optString("qualityWifi").takeIf { it.isNotBlank() }?.let { prefs[Keys.QUALITY_WIFI] = it }
            obj.optString("qualityMobile").takeIf { it.isNotBlank() }?.let { prefs[Keys.QUALITY_MOBILE] = it }
            obj.optString("theme").takeIf { it.isNotBlank() }?.let { prefs[Keys.THEME] = it }
            if (obj.has("preloadNext")) prefs[Keys.PRELOAD] = obj.getBoolean("preloadNext")
            if (obj.has("cacheLimitMb")) prefs[Keys.CACHE_MB] = obj.getInt("cacheLimitMb")
            if (obj.has("autoDownloadLiked")) prefs[Keys.AUTO_DOWNLOAD] = obj.getBoolean("autoDownloadLiked")
            if (obj.has("normalizeVolume")) prefs[Keys.NORMALIZE] = obj.getBoolean("normalizeVolume")
            if (obj.has("skipSilence")) prefs[Keys.SKIP_SILENCE] = obj.getBoolean("skipSilence")
            if (obj.has("pauseOnHeadphonesOut")) prefs[Keys.PAUSE_ON_UNPLUG] = obj.getBoolean("pauseOnHeadphonesOut")
            if (obj.has("resumeOnHeadphonesIn")) prefs[Keys.RESUME_ON_PLUG] = obj.getBoolean("resumeOnHeadphonesIn")
            if (obj.has("sleepTimerDefaultMin")) prefs[Keys.SLEEP_MIN] = obj.getInt("sleepTimerDefaultMin")
            if (obj.has("showLyrics")) prefs[Keys.LYRICS] = obj.getBoolean("showLyrics")
            if (obj.has("keepHistory")) prefs[Keys.HISTORY] = obj.getBoolean("keepHistory")
        }
    }

    private suspend fun put(key: Preferences.Key<Boolean>, value: Boolean) =
        context.dataStore.edit { it[key] = value }.let { }

    private suspend fun put(key: Preferences.Key<Int>, value: Int) =
        context.dataStore.edit { it[key] = value }.let { }

    private suspend fun put(key: Preferences.Key<String>, value: String) =
        context.dataStore.edit { it[key] = value }.let { }

    private inline fun <reified T : Enum<T>> Preferences.enum(
        key: Preferences.Key<String>,
        fallback: T,
    ): T = this[key]?.let { name -> runCatching { enumValueOf<T>(name) }.getOrNull() } ?: fallback

    private object Keys {
        val QUALITY_WIFI = stringPreferencesKey("quality_wifi")
        val QUALITY_MOBILE = stringPreferencesKey("quality_mobile")
        val PRELOAD = booleanPreferencesKey("preload_next")
        val CACHE_MB = intPreferencesKey("cache_limit_mb")
        val AUTO_DOWNLOAD = booleanPreferencesKey("auto_download_liked")
        val NORMALIZE = booleanPreferencesKey("normalize_volume")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val PAUSE_ON_UNPLUG = booleanPreferencesKey("pause_on_unplug")
        val RESUME_ON_PLUG = booleanPreferencesKey("resume_on_plug")
        val SLEEP_MIN = intPreferencesKey("sleep_timer_default")
        val THEME = stringPreferencesKey("theme")
        val LYRICS = booleanPreferencesKey("show_lyrics")
        val HISTORY = booleanPreferencesKey("keep_history")
        val QUEUE_MODE = stringPreferencesKey("queue_mode")
    }
}
