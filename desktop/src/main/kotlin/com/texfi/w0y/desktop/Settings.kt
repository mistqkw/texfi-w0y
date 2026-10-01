package com.texfi.w0y.desktop

import com.texfi.w0y.data.Reverb
import kotlinx.serialization.Serializable

/** Качество звука: с чем идти в извлечение потока. */
@Serializable
enum class Quality(val label: String) { LOW("экономно"), MEDIUM("авто"), HIGH("максимум") }

/** Что играет после выбранного трека — то же, что на телефоне. */
@Serializable
enum class QueueMode(val label: String, val hint: String) {
    ORDER("ПО ОЧЕРЕДИ", "Дальше идёт список, из которого включили трек."),
    SHUFFLE("ПЕРЕМЕШАТЬ", "Тот же список, но вперемешку."),
    RADIO("РЕКОМЕНДАЦИИ β", "Дальше — похожее по звучанию и жанру, с учётом того, что ты уже слушал и искал."),
}

@Serializable
enum class ExplicitFallback(val label: String) { PLAY("ИГРАТЬ"), SKIP("ПРОПУСТИТЬ") }

@Serializable
enum class StartTab(val label: String) { HOME("ДОМ"), SEARCH("ПОИСК"), LIBRARY("МОЁ"), LAST("ПОСЛЕДНИЙ") }

@Serializable
enum class Language(val label: String, val tag: String?) {
    SYSTEM("СИСТЕМА", null),
    EN("ENGLISH", "en"),
    RU("РУССКИЙ", "ru"),
    UK("УКРАЇНСЬКА", "uk"),
    PL("POLSKI", "pl"),
}

@Serializable
enum class ThemeMode(val label: String) { DARK("тёмная"), OLED("чёрная"), LIGHT("светлая") }

/** Пара «акцент + вторичный», те же значения, что на телефоне. */
@Serializable
enum class Accent(val label: String, val accent: Long, val deep: Long, val secondary: Long) {
    BLUE("СИНЯЯ", 0xFF4A7CFB, 0xFF1E3F8F, 0xFFE0A860),
    PINK("РОЗОВАЯ", 0xFFFB4A8D, 0xFF8F1E4C, 0xFFFFB2CF),
    VIOLET("ФИОЛЕТ", 0xFF9A6BFF, 0xFF4C2E99, 0xFFE0A860),
    MINT("МЯТНАЯ", 0xFF3ED9A4, 0xFF167A5B, 0xFFE0A860),
    CRIMSON("АЛАЯ", 0xFFFF5A5A, 0xFF8F2020, 0xFFE0A860),
    SAND("ПЕСОК", 0xFFE0A860, 0xFF8A5F26, 0xFF7FB5FF),
    CUSTOM("СВОЙ", 0xFFA06CFF, 0xFF4E3580, 0xFFE0A860),
}

/**
 * Настройки десктопной версии. Названия и значения по умолчанию совпадают с
 * телефонными; нет только тех, что про телефон (вибрация, наушники, мобильная
 * сеть, системный эквалайзер, быстрый набор).
 */
@Serializable
data class DSettings(
    val quality: Quality = Quality.HIGH,
    val normalizeVolume: Boolean = true,
    val skipSilence: Boolean = false,
    val preloadNext: Boolean = true,
    val sleepTimerDefaultMin: Int = 30,
    val queueMode: QueueMode = QueueMode.ORDER,
    val seekStepSec: Int = 10,
    val playerCoverGlow: Boolean = true,
    val showLyrics: Boolean = true,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val reverb: Reverb = Reverb.OFF,
    val autoDownloadLiked: Boolean = false,
    val searchSuggestions: Boolean = true,
    val saveSearchHistory: Boolean = true,
    val syncPlaylists: Boolean = true,
    val keepHistory: Boolean = true,
    val cleanMode: Boolean = false,
    val explicitFallback: ExplicitFallback = ExplicitFallback.PLAY,
    val hideExplicit: Boolean = false,
    val muteSwearLines: Boolean = false,
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: Accent = Accent.BLUE,
    val customAccent: Int = 0xFFA06CFF.toInt(),
    val language: Language = Language.SYSTEM,
    val startTab: StartTab = StartTab.LAST,
    val lastTab: StartTab = StartTab.HOME,
    val showRecommendations: Boolean = true,
    val animatedBackground: Boolean = true,
    val compactRows: Boolean = false,
    val audioDevice: String = "auto",
)
