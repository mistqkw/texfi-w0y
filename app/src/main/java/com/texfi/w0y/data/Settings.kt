package com.texfi.w0y.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.texfi.w0y.R
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
enum class QueueMode(
    @StringRes val label: Int,
    @StringRes val hint: Int,
) {
    ORDER(R.string.queue_mode_order, R.string.queue_mode_order_hint),
    SHUFFLE(R.string.queue_mode_shuffle, R.string.queue_mode_shuffle_hint),
    RADIO(R.string.queue_mode_radio, R.string.queue_mode_radio_hint),
}

/**
 * Реверб поверх трека.
 *
 * Не «эффект ради эффекта»: львиная доля того, что слушают в этом жанре, —
 * чужие slowed-переделки одних и тех же песен. Замедление с эхом прямо в
 * плеере делает такие переделки ненужными: любой оригинал звучит так же,
 * только без потери качества и без поиска нужной версии.
 */
enum class Reverb(@StringRes val label: Int) {
    OFF(R.string.reverb_none),
    ROOM(R.string.reverb_room),
    HALL(R.string.reverb_hall),
    CAVE(R.string.reverb_cave),
}

/**
 * Готовые сочетания скорости, тона и эха.
 *
 * Ровно те два, ради которых слушают чужие переделки: замедленное с эхом
 * и ускоренное. Тонкая настройка — в настройках, здесь одно нажатие.
 */
enum class SoundPreset(
    @StringRes val label: Int,
    val speed: Float,
    val pitch: Float,
    val reverb: Reverb,
) {
    SLOWED(R.string.preset_slowed, 0.85f, 0.92f, Reverb.HALL),
    NORMAL(R.string.preset_normal, 1f, 1f, Reverb.OFF),
    SPED(R.string.preset_sped, 1.25f, 1.06f, Reverb.OFF),
    ;

    fun matches(settings: W0ySettings): Boolean =
        settings.speed == speed && settings.pitch == pitch && settings.reverb == reverb
}

/**
 * Что делать с записью, помеченной «E», когда чистой версии не нашлось.
 *
 * Вырезать отдельное слово из готовой записи приложение не умеет и делать
 * вид, что умеет, не будет: выбор честный — играть как есть или пропустить.
 */
enum class ExplicitFallback(@StringRes val label: Int) {
    PLAY(R.string.explicit_play),
    SKIP(R.string.explicit_skip),
}

/** Куда попадаешь при запуске. */
enum class StartTab(@StringRes val label: Int) {
    HOME(R.string.start_tab_home),
    SEARCH(R.string.start_tab_search),
    LIBRARY(R.string.start_tab_library),
}

/**
 * Язык приложения.
 *
 * SYSTEM — как на телефоне; остальное — явный выбор, который сильнее
 * системного. Тег хранится рядом с остальными настройками, но дублируется
 * в SharedPreferences: локаль нужна ещё до того, как успеет прочитаться
 * DataStore, в attachBaseContext (см. [LocalePrefs]).
 */
enum class Language(@StringRes val label: Int, val tag: String?) {
    SYSTEM(R.string.language_system, null),
    EN(R.string.language_en, "en"),
    RU(R.string.language_ru, "ru"),
    UK(R.string.language_uk, "uk"),
    PL(R.string.language_pl, "pl"),
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
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val reverb: Reverb = Reverb.OFF,
    val showRecommendations: Boolean = true,
    val compactRows: Boolean = false,
    val saveSearchHistory: Boolean = true,
    val downloadOnWifiOnly: Boolean = true,
    val startTab: StartTab = StartTab.HOME,
    val cleanMode: Boolean = false,
    val explicitFallback: ExplicitFallback = ExplicitFallback.PLAY,
    val hideExplicit: Boolean = false,
    val muteSwearLines: Boolean = false,
    val welcomeSeen: Boolean = false,
    val language: Language = Language.SYSTEM,
    val animatedBackground: Boolean = true,
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
                    speed = prefs[Keys.SPEED] ?: 1f,
                    pitch = prefs[Keys.PITCH] ?: 1f,
                    reverb = prefs.enum(Keys.REVERB, Reverb.OFF),
                    showRecommendations = prefs[Keys.SHOW_RECOMMENDATIONS] ?: true,
                    compactRows = prefs[Keys.COMPACT_ROWS] ?: false,
                    saveSearchHistory = prefs[Keys.SAVE_SEARCHES] ?: true,
                    downloadOnWifiOnly = prefs[Keys.WIFI_ONLY_DOWNLOADS] ?: true,
                    startTab = prefs.enum(Keys.START_TAB, StartTab.HOME),
                    cleanMode = prefs[Keys.CLEAN_MODE] ?: false,
                    explicitFallback = prefs.enum(Keys.EXPLICIT_FALLBACK, ExplicitFallback.PLAY),
                    hideExplicit = prefs[Keys.HIDE_EXPLICIT] ?: false,
                    muteSwearLines = prefs[Keys.MUTE_SWEAR_LINES] ?: false,
                    welcomeSeen = prefs[Keys.WELCOME_SEEN] ?: false,
                    language = prefs.enum(Keys.LANGUAGE, Language.SYSTEM),
                    animatedBackground = prefs[Keys.ANIMATED_BACKGROUND] ?: true,
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

    suspend fun setSpeed(value: Float) = put(Keys.SPEED, value)

    suspend fun setPitch(value: Float) = put(Keys.PITCH, value)

    suspend fun setReverb(value: Reverb) = put(Keys.REVERB, value.name)

    /** Пресет звучания меняет скорость, тон и эхо разом — как одну ручку. */
    suspend fun setSoundPreset(speed: Float, pitch: Float, reverb: Reverb) {
        context.dataStore.edit {
            it[Keys.SPEED] = speed
            it[Keys.PITCH] = pitch
            it[Keys.REVERB] = reverb.name
        }
    }

    suspend fun setShowRecommendations(value: Boolean) = put(Keys.SHOW_RECOMMENDATIONS, value)

    suspend fun setCompactRows(value: Boolean) = put(Keys.COMPACT_ROWS, value)

    suspend fun setSaveSearchHistory(value: Boolean) = put(Keys.SAVE_SEARCHES, value)

    suspend fun setDownloadOnWifiOnly(value: Boolean) = put(Keys.WIFI_ONLY_DOWNLOADS, value)

    suspend fun setStartTab(value: StartTab) = put(Keys.START_TAB, value.name)

    suspend fun setCleanMode(value: Boolean) = put(Keys.CLEAN_MODE, value)

    suspend fun setExplicitFallback(value: ExplicitFallback) = put(Keys.EXPLICIT_FALLBACK, value.name)

    suspend fun setHideExplicit(value: Boolean) = put(Keys.HIDE_EXPLICIT, value)

    suspend fun setMuteSwearLines(value: Boolean) = put(Keys.MUTE_SWEAR_LINES, value)

    suspend fun setWelcomeSeen(value: Boolean) = put(Keys.WELCOME_SEEN, value)

    suspend fun setAnimatedBackground(value: Boolean) = put(Keys.ANIMATED_BACKGROUND, value)

    /**
     * Язык пишется сразу в двух местах.
     *
     * DataStore — источник истины для экрана настроек, SharedPreferences —
     * потому что локаль нужна в `attachBaseContext`, где нельзя ждать
     * корутину: там читают [LocalePrefs].
     */
    suspend fun setLanguage(value: Language) {
        LocalePrefs.save(context, value)
        put(Keys.LANGUAGE, value.name)
    }

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
            .put("language", current.language.name)
            .put("animatedBackground", current.animatedBackground)
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
            if (obj.has("animatedBackground")) {
                prefs[Keys.ANIMATED_BACKGROUND] = obj.getBoolean("animatedBackground")
            }
            // Язык из выгрузки нужно продублировать в синхронное хранилище:
            // именно оттуда его читает attachBaseContext при следующем старте.
            obj.optString("language").takeIf { it.isNotBlank() }?.let { name ->
                prefs[Keys.LANGUAGE] = name
                Language.entries.firstOrNull { it.name == name }?.let { LocalePrefs.save(context, it) }
            }
        }
    }

    private suspend fun put(key: Preferences.Key<Boolean>, value: Boolean) =
        context.dataStore.edit { it[key] = value }.let { }

    private suspend fun put(key: Preferences.Key<Int>, value: Int) =
        context.dataStore.edit { it[key] = value }.let { }

    private suspend fun put(key: Preferences.Key<String>, value: String) =
        context.dataStore.edit { it[key] = value }.let { }

    private suspend fun put(key: Preferences.Key<Float>, value: Float) =
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
        val SPEED = floatPreferencesKey("speed")
        val PITCH = floatPreferencesKey("pitch")
        val REVERB = stringPreferencesKey("reverb")
        val SHOW_RECOMMENDATIONS = booleanPreferencesKey("show_recommendations")
        val COMPACT_ROWS = booleanPreferencesKey("compact_rows")
        val SAVE_SEARCHES = booleanPreferencesKey("save_searches")
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        val START_TAB = stringPreferencesKey("start_tab")
        val CLEAN_MODE = booleanPreferencesKey("clean_mode")
        val EXPLICIT_FALLBACK = stringPreferencesKey("explicit_fallback")
        val HIDE_EXPLICIT = booleanPreferencesKey("hide_explicit")
        val MUTE_SWEAR_LINES = booleanPreferencesKey("mute_swear_lines")
        val WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
        val LANGUAGE = stringPreferencesKey("language")
        val ANIMATED_BACKGROUND = booleanPreferencesKey("animated_background")
    }
}
