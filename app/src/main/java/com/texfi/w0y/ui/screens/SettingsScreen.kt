package com.texfi.w0y.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.texfi.w0y.ui.components.pressScale
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.BuildConfig
import com.texfi.w0y.R
import com.texfi.w0y.data.Language
import com.texfi.w0y.data.Quality
import com.texfi.w0y.data.ExplicitFallback
import com.texfi.w0y.data.QueueMode
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.StartTab
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
    val startupAverage by viewModel.startupAverage.collectAsStateWithLifecycle()
    val startupLast by viewModel.startupLast.collectAsStateWithLifecycle()
    val startupCount by viewModel.startupCount.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Вкладки вместо одной простыни на девяносто пунктов: «много настроек»
    // ценно, только если нужную можно найти. Выбранная вкладка живёт до
    // закрытия экрана — возвращаясь, попадаешь туда, где был.
    var tab by remember { mutableStateOf(SettingsTab.SOUND) }

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
            SpriteButton(Sprites.chevronLeft, onClick = onClose)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.settings_title), style = PixelTitle, color = colors.text)
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondary)
            Spacer(Modifier.height(6.dp))
        }

        SettingsTabs(selected = tab, onSelect = { tab = it })
        Spacer(Modifier.height(14.dp))

        LazyColumn(
            Modifier.navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (tab == SettingsTab.SOUND) {
                item {
                    ChoiceRow(
                        title = stringResource(R.string.settings_quality_wifi_title),
                        description = stringResource(R.string.settings_quality_wifi_desc),
                        options = Quality.entries,
                        selected = settings.qualityWifi,
                        label = { qualityLabel(it) },
                        onSelect = viewModel::setQualityWifi,
                    )
                }
                item {
                    ChoiceRow(
                        title = stringResource(R.string.settings_quality_mobile_title),
                        description = stringResource(R.string.settings_quality_mobile_desc),
                        options = Quality.entries,
                        selected = settings.qualityMobile,
                        label = { qualityLabel(it) },
                        onSelect = viewModel::setQualityMobile,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_normalize_title),
                        description = stringResource(R.string.settings_normalize_desc),
                        checked = settings.normalizeVolume,
                        onChange = viewModel::setNormalize,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_skip_silence_title),
                        description = stringResource(R.string.settings_skip_silence_desc),
                        checked = settings.skipSilence,
                        onChange = viewModel::setSkipSilence,
                    )
                }
            }
            if (tab == SettingsTab.TONE) {
                item {
                    ChoiceRow(
                        title = stringResource(R.string.settings_speed_title),
                        description = stringResource(R.string.settings_speed_desc),
                        options = listOf(0.75f, 0.85f, 1f, 1.25f, 1.5f),
                        selected = settings.speed,
                        label = { "${it}×".replace(".0×", "×") },
                        onSelect = viewModel::setSpeed,
                    )
                }
                item {
                    ChoiceRow(
                        title = stringResource(R.string.settings_pitch_title),
                        description = stringResource(R.string.settings_pitch_desc),
                        options = listOf(0.9f, 0.95f, 1f, 1.05f, 1.1f),
                        selected = settings.pitch,
                        label = { it.toString().replace("1.0", stringResource(R.string.settings_pitch_normal)) },
                        onSelect = viewModel::setPitch,
                    )
                }
                item {
                    ChoiceRow(
                        title = stringResource(R.string.settings_reverb_title),
                        description = stringResource(R.string.settings_reverb_desc),
                        options = Reverb.entries,
                        selected = settings.reverb,
                        label = { stringResource(it.label) },
                        onSelect = viewModel::setReverb,
                    )
                }
            }
            if (tab == SettingsTab.SPEED) {
                item {
                    InfoRow(
                        title = stringResource(R.string.settings_startup_title),
                        description =
                            stringResource(
                                R.string.settings_startup_desc,
                                if (startupCount == 0) {
                                    stringResource(R.string.settings_startup_desc_empty)
                                } else {
                                    stringResource(
                                        R.string.settings_startup_desc_stats,
                                        startupCount,
                                        (startupLast ?: 0).toInt(),
                                    )
                                },
                            ),
                        value =
                            startupAverage
                                ?.let { stringResource(R.string.settings_startup_value_ms, it.toInt()) }
                                ?: "—",
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_preload_title),
                        description = stringResource(R.string.settings_preload_desc),
                        checked = settings.preloadNext,
                        onChange = viewModel::setPreload,
                    )
                }
            }
            if (tab == SettingsTab.STORAGE) {
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_wifi_only_title),
                        description = stringResource(R.string.settings_wifi_only_desc),
                        checked = settings.downloadOnWifiOnly,
                        onChange = viewModel::setDownloadOnWifiOnly,
                    )
                }
                item {
                    ChoiceRow(
                        title = stringResource(R.string.settings_cache_title),
                        description =
                            stringResource(
                                R.string.settings_cache_desc,
                                (cacheBytes / 1024 / 1024).toInt(),
                            ),
                        options = listOf(256, 512, 1024, 2048),
                        selected = settings.cacheLimitMb,
                        label = {
                            if (it >= 1024) {
                                stringResource(R.string.settings_cache_gb, it / 1024)
                            } else {
                                stringResource(R.string.settings_cache_mb, it)
                            }
                        },
                        onSelect = viewModel::setCacheLimit,
                    )
                }
                item {
                    ActionRow(
                        title = stringResource(R.string.settings_clear_cache_title),
                        description = stringResource(R.string.settings_clear_cache_desc),
                        button = stringResource(R.string.settings_clear_cache_button),
                        onClick = viewModel::clearCache,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_autodownload_title),
                        description = stringResource(R.string.settings_autodownload_desc),
                        checked = settings.autoDownloadLiked,
                        onChange = viewModel::setAutoDownload,
                    )
                }
            }
            if (tab == SettingsTab.PLAYBACK) {
                item {
                    ActionRow(
                        title = stringResource(R.string.settings_equalizer_title),
                        description = stringResource(R.string.settings_equalizer_desc),
                        button = stringResource(R.string.settings_equalizer_button),
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
                        title = stringResource(R.string.settings_queue_title),
                        description = stringResource(R.string.settings_queue_desc),
                        options = QueueMode.entries,
                        selected = settings.queueMode,
                        label = { stringResource(it.label) },
                        onSelect = viewModel::setQueueMode,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_pause_headphones_title),
                        description = stringResource(R.string.settings_pause_headphones_desc),
                        checked = settings.pauseOnHeadphonesOut,
                        onChange = viewModel::setPauseOnUnplug,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_resume_headphones_title),
                        description = stringResource(R.string.settings_resume_headphones_desc),
                        checked = settings.resumeOnHeadphonesIn,
                        onChange = viewModel::setResumeOnPlug,
                    )
                }
                item {
                    ChoiceRow(
                        title = stringResource(R.string.settings_sleep_title),
                        description = stringResource(R.string.settings_sleep_desc),
                        options = listOf(15, 30, 45, 60),
                        selected = settings.sleepTimerDefaultMin,
                        label = { stringResource(R.string.settings_sleep_minutes, it) },
                        onSelect = viewModel::setSleepDefault,
                    )
                }
            }
            if (tab == SettingsTab.CLEAN) {
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_clean_title),
                        description = stringResource(R.string.settings_clean_desc),
                        checked = settings.cleanMode,
                        onChange = viewModel::setCleanMode,
                    )
                }
                item {
                    ChoiceRow(
                        title = stringResource(R.string.settings_clean_fallback_title),
                        description = stringResource(R.string.settings_clean_fallback_desc),
                        options = ExplicitFallback.entries,
                        selected = settings.explicitFallback,
                        label = { stringResource(it.label) },
                        onSelect = viewModel::setExplicitFallback,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_hide_explicit_title),
                        description = stringResource(R.string.settings_hide_explicit_desc),
                        checked = settings.hideExplicit,
                        onChange = viewModel::setHideExplicit,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_mute_swear_title),
                        description = stringResource(R.string.settings_mute_swear_desc),
                        checked = settings.muteSwearLines,
                        onChange = viewModel::setMuteSwearLines,
                    )
                }
            }
            if (tab == SettingsTab.LOOK) {
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_shelves_title),
                        description = stringResource(R.string.settings_shelves_desc),
                        checked = settings.showRecommendations,
                        onChange = viewModel::setShowRecommendations,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_live_background_title),
                        description = stringResource(R.string.settings_live_background_desc),
                        checked = settings.animatedBackground,
                        onChange = viewModel::setAnimatedBackground,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_compact_title),
                        description = stringResource(R.string.settings_compact_desc),
                        checked = settings.compactRows,
                        onChange = viewModel::setCompactRows,
                    )
                }
                item {
                    ChoiceRow(
                        title = stringResource(R.string.settings_language_title),
                        description = stringResource(R.string.settings_language_desc),
                        options = Language.entries,
                        selected = settings.language,
                        label = { stringResource(it.label) },
                        // Экран пересоздаётся сразу после записи: ресурсы
                        // читаются при создании, и без этого новый язык
                        // появился бы только на части экрана.
                        onSelect = { choice ->
                            viewModel.setLanguage(choice) {
                                (context as? Activity)?.recreate()
                            }
                        },
                    )
                }
                item {
                    ChoiceRow(
                        title = stringResource(R.string.settings_start_tab_title),
                        description = stringResource(R.string.settings_start_tab_desc),
                        options = StartTab.entries,
                        selected = settings.startTab,
                        label = { stringResource(it.label) },
                        onSelect = viewModel::setStartTab,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_search_history_title),
                        description = stringResource(R.string.settings_search_history_desc),
                        checked = settings.saveSearchHistory,
                        onChange = viewModel::setSaveSearchHistory,
                    )
                }
                item {
                    ChoiceRow(
                        title = stringResource(R.string.settings_theme_title),
                        description = stringResource(R.string.settings_theme_desc),
                        options = ThemeMode.entries,
                        selected = settings.theme,
                        label = { themeLabel(it) },
                        onSelect = viewModel::setTheme,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_lyrics_title),
                        description = stringResource(R.string.settings_lyrics_desc),
                        checked = settings.showLyrics,
                        onChange = viewModel::setShowLyrics,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_history_title),
                        description = stringResource(R.string.settings_history_desc),
                        checked = settings.keepHistory,
                        onChange = viewModel::setKeepHistory,
                    )
                }
            }
            if (tab == SettingsTab.DATA) {
                item {
                    ActionRow(
                        title = stringResource(R.string.settings_export_title),
                        description = stringResource(R.string.settings_export_desc),
                        button = stringResource(R.string.settings_export_button),
                        onClick = { exportLauncher.launch("w0y-settings.json") },
                    )
                }
                item {
                    ActionRow(
                        title = stringResource(R.string.settings_import_title),
                        description = stringResource(R.string.settings_import_desc),
                        button = stringResource(R.string.settings_import_button),
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
}

/**
 * Разделы настроек.
 *
 * Подписи короткие намеренно: полоса вкладок листается, но если каждая
 * подпись в два слова, листать её приходится вдвое дольше.
 */
private enum class SettingsTab(@StringRes val label: Int) {
    SOUND(R.string.settings_tab_sound),
    TONE(R.string.settings_tab_tone),
    SPEED(R.string.settings_tab_speed),
    STORAGE(R.string.settings_tab_storage),
    PLAYBACK(R.string.settings_tab_queue),
    CLEAN(R.string.settings_tab_clean),
    LOOK(R.string.settings_tab_look),
    DATA(R.string.settings_tab_data),
}

/**
 * Полоса вкладок настроек: листается по горизонтали, активная вкладка
 * подчёркнута акцентом.
 *
 * Material `ScrollableTabRow` притащил бы с собой подчёркивание с
 * закруглениями и свою анимацию — здесь всё своё, как в остальной
 * экосистеме: рубленый прямоугольник и подчёркивание в 3dp.
 */
@Composable
private fun SettingsTabs(
    selected: SettingsTab,
    onSelect: (SettingsTab) -> Unit,
) {
    val colors = LocalW0yColors.current
    val state = rememberLazyListState()
    // Выбранная вкладка подъезжает к краю сама: иначе после выбора
    // последней вкладки её подчёркивание остаётся за пределами экрана.
    LaunchedEffect(selected) {
        state.animateScrollToItem(selected.ordinal.coerceAtLeast(0))
    }
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(SettingsTab.entries, key = { it.name }) { entry ->
            val active = entry == selected
            val interaction = remember { MutableInteractionSource() }
            val fill by animateColorAsState(
                targetValue = if (active) colors.surfaceHigh else colors.surface,
                animationSpec = tween(160),
                label = "tabFill",
            )
            val underline by animateDpAsState(
                targetValue = if (active) 3.dp else 0.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "tabRule",
            )
            Column(
                Modifier
                    .pressScale(interaction, pressed = 0.94f)
                    .clickable(interactionSource = interaction, indication = null) { onSelect(entry) },
            ) {
                Box(
                    Modifier
                        .background(fill)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(entry.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) colors.text else colors.textMuted,
                        maxLines = 1,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(underline)
                        .background(colors.accent),
                )
            }
        }
    }
}

@Composable
private fun qualityLabel(quality: Quality): String =
    when (quality) {
        Quality.LOW -> stringResource(R.string.quality_low)
        Quality.MEDIUM -> stringResource(R.string.quality_auto)
        Quality.HIGH -> stringResource(R.string.quality_high)
    }

@Composable
private fun themeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.DARK -> stringResource(R.string.theme_dark)
        ThemeMode.OLED -> stringResource(R.string.theme_oled)
        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
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

/** Строка-показание: значение, которое нельзя менять, но важно видеть. */
@Composable
private fun InfoRow(title: String, description: String, value: String) {
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
        Text(value, style = MaterialTheme.typography.bodyLarge, color = colors.accent)
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    description: String,
    options: List<T>,
    selected: T,
    // Подпись варианта тянется из ресурсов, поэтому лямбда composable:
    // иначе каждый вызов пришлось бы разворачивать в строку заранее и
    // терять смену языка без перезапуска экрана.
    label: @Composable (T) -> String,
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
            options = options.map { label(it) },
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
