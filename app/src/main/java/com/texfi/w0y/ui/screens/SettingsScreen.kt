package com.texfi.w0y.ui.screens

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.BuildConfig
import com.texfi.w0y.data.Quality
import com.texfi.w0y.data.QueueMode
import com.texfi.w0y.data.ThemeMode
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.PixelSegmented
import com.texfi.w0y.ui.components.PixelSwitch
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import com.texfi.w0y.ui.theme.PixelTitle
import kotlinx.coroutines.launch

/**
 * Настройки — часть ценности приложения, поэтому у каждого пункта есть
 * короткое описание, что он делает. Пунктов, которые ничего не меняют,
 * здесь нет.
 */
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val colors = LocalW0yColors.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch {
                val json = viewModel.exportJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            val json =
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            if (json != null) viewModel.importJson(json)
        }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.consumeMessage()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpriteButton(Sprites.previous, onClick = onClose)
            Spacer(Modifier.width(12.dp))
            Text("настройки", style = PixelTitle, color = colors.text)
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondary)
            Spacer(Modifier.height(6.dp))
        }

        LazyColumn(
            Modifier.navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Group("ЗВУК") }
            item {
                ChoiceRow(
                    title = "Качество по Wi-Fi",
                    description = "Какой поток просить, когда телефон в Wi-Fi.",
                    options = Quality.entries,
                    selected = settings.qualityWifi,
                    label = ::qualityLabel,
                    onSelect = viewModel::setQualityWifi,
                )
            }
            item {
                ChoiceRow(
                    title = "Качество в мобильной сети",
                    description = "Отдельно от Wi-Fi: экономит трафик и ускоряет старт.",
                    options = Quality.entries,
                    selected = settings.qualityMobile,
                    label = ::qualityLabel,
                    onSelect = viewModel::setQualityMobile,
                )
            }
            item {
                SwitchRow(
                    title = "Выравнивать громкость",
                    description = "Тихие записи не теряются после громких — по данным самого YouTube.",
                    checked = settings.normalizeVolume,
                    onChange = viewModel::setNormalize,
                )
            }
            item {
                SwitchRow(
                    title = "Пропускать тишину",
                    description = "Молчание в начале и конце записи проматывается.",
                    checked = settings.skipSilence,
                    onChange = viewModel::setSkipSilence,
                )
            }

            item { Group("СКОРОСТЬ") }
            item {
                SwitchRow(
                    title = "Готовить следующий трек",
                    description = "Ссылка следующего трека берётся заранее — переход без паузы.",
                    checked = settings.preloadNext,
                    onChange = viewModel::setPreload,
                )
            }

            item { Group("ХРАНИЛИЩЕ") }
            item {
                ChoiceRow(
                    title = "Предел кэша",
                    description = "Сколько прослушанного держать на телефоне. Новый предел вступает в силу после перезапуска. Сейчас занято: ${cacheBytes / 1024 / 1024} МБ.",
                    options = listOf(256, 512, 1024, 2048),
                    selected = settings.cacheLimitMb,
                    label = { if (it >= 1024) "${it / 1024} ГБ" else "$it МБ" },
                    onSelect = viewModel::setCacheLimit,
                )
            }
            item {
                ActionRow(
                    title = "Очистить кэш",
                    description = "Скачанные треки не трогает — они хранятся отдельно.",
                    button = "ОЧИСТИТЬ",
                    onClick = viewModel::clearCache,
                )
            }
            item {
                SwitchRow(
                    title = "Скачивать лайкнутое",
                    description = "Трек, которому поставлен лайк, сразу уходит в загрузки.",
                    checked = settings.autoDownloadLiked,
                    onChange = viewModel::setAutoDownload,
                )
            }

            item { Group("ВОСПРОИЗВЕДЕНИЕ") }
            item {
                ActionRow(
                    title = "Эквалайзер",
                    description = "Открывает системный эквалайзер для звука w0y — тот же, что у остальных приложений.",
                    button = "ОТКРЫТЬ",
                    onClick = {
                        val intent =
                            Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, viewModel.audioSessionId)
                                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                            }
                        // Честно говорим, если эквалайзера в системе нет,
                        // вместо кнопки, которая молча ничего не делает.
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            viewModel.reportNoEqualizer()
                        }
                    },
                )
            }
            item {
                ChoiceRow(
                    title = "Что играет дальше",
                    description =
                        "По очереди — список, из которого включили трек. Перемешать — тот же список вразнобой. " +
                            "Рекомендации (бета) — похожее по жанру и звучанию, с учётом того, что ты слушал и искал.",
                    options = QueueMode.entries,
                    selected = settings.queueMode,
                    label = { it.label },
                    onSelect = viewModel::setQueueMode,
                )
            }
            item {
                SwitchRow(
                    title = "Пауза при отключении наушников",
                    description = "Вытащил наушники — музыка не продолжит играть в динамик.",
                    checked = settings.pauseOnHeadphonesOut,
                    onChange = viewModel::setPauseOnUnplug,
                )
            }
            item {
                SwitchRow(
                    title = "Продолжать при подключении",
                    description = "Вставил наушники — воспроизведение возобновится само.",
                    checked = settings.resumeOnHeadphonesIn,
                    onChange = viewModel::setResumeOnPlug,
                )
            }
            item {
                ChoiceRow(
                    title = "Таймер сна по умолчанию",
                    description = "Сколько минут предлагать в плеере при включении таймера.",
                    options = listOf(15, 30, 45, 60),
                    selected = settings.sleepTimerDefaultMin,
                    label = { "$it мин" },
                    onSelect = viewModel::setSleepDefault,
                )
            }

            item { Group("ВИД") }
            item {
                ChoiceRow(
                    title = "Тема",
                    description = "OLED — полностью чёрный фон, экономит батарею на AMOLED.",
                    options = ThemeMode.entries,
                    selected = settings.theme,
                    label = ::themeLabel,
                    onSelect = viewModel::setTheme,
                )
            }
            item {
                SwitchRow(
                    title = "Показывать лирику",
                    description = "Текст песни в плеере, синхронизированный по строкам, из LRCLIB.",
                    checked = settings.showLyrics,
                    onChange = viewModel::setShowLyrics,
                )
            }
            item {
                SwitchRow(
                    title = "Вести историю",
                    description = "Запоминать, что включал. Выключено — раздел «история» перестаёт пополняться.",
                    checked = settings.keepHistory,
                    onChange = viewModel::setKeepHistory,
                )
            }

            item { Group("НАСТРОЙКИ ЦЕЛИКОМ") }
            item {
                ActionRow(
                    title = "Сохранить в файл",
                    description = "Все настройки одним JSON — перенести на другой телефон.",
                    button = "ЭКСПОРТ",
                    onClick = { exportLauncher.launch("w0y-settings.json") },
                )
            }
            item {
                ActionRow(
                    title = "Загрузить из файла",
                    description = "Применить ранее сохранённые настройки.",
                    button = "ИМПОРТ",
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                )
            }
            item {
                Text(
                    text = "TexFi w0y ${BuildConfig.VERSION_NAME} · AGPL-3.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(vertical = 18.dp),
                )
            }
        }
    }
}

private fun qualityLabel(quality: Quality): String =
    when (quality) {
        Quality.LOW -> "экономно"
        Quality.MEDIUM -> "автоматически"
        Quality.HIGH -> "максимум"
    }

private fun themeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.DARK -> "тёмная"
        ThemeMode.OLED -> "чёрная"
        ThemeMode.LIGHT -> "светлая"
    }

@Composable
private fun Group(title: String) {
    val colors = LocalW0yColors.current
    Text(
        text = "❯ $title",
        style = PixelSectionLabel,
        color = colors.accent,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = LocalW0yColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Spacer(Modifier.width(12.dp))
        PixelSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    description: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = LocalW0yColors.current
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text)
        Text(description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        Spacer(Modifier.height(10.dp))
        // Квадратный переключатель во всю ширину вместо скруглённых
        // «таблеток»: Material-овальность здесь чужая, а равные секции
        // читаются как один переключатель, а не как россыпь кнопок.
        PixelSegmented(
            options = options.map(label),
            selectedIndex = options.indexOf(selected),
            onSelect = { onSelect(options[it]) },
        )
    }
}

@Composable
private fun ActionRow(title: String, description: String, button: String, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Spacer(Modifier.width(12.dp))
        PixelButton(text = button, onClick = onClick, fill = colors.surfaceHigh)
    }
}
