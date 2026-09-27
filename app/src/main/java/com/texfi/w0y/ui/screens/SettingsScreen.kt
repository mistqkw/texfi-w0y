package com.texfi.w0y.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.BuildConfig
import com.texfi.w0y.R
import com.texfi.w0y.data.Accent
import com.texfi.w0y.data.ExplicitFallback
import com.texfi.w0y.data.Language
import com.texfi.w0y.data.Quality
import com.texfi.w0y.data.QueueMode
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.StartTab
import com.texfi.w0y.data.ThemeMode
import com.texfi.w0y.playback.AudioOutput
import com.texfi.w0y.ui.components.EmptyState
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.PixelSegmented
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.PixelSwitch
import com.texfi.w0y.ui.components.ScreenTitle
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.components.pressScale
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.screenBackground
import com.texfi.w0y.ui.theme.PixelSectionLabel
import kotlinx.coroutines.launch

/**
 * Настройки: сначала разделы, потом пункты.
 *
 * Раньше это была полоса из восьми вкладок над плоским списком. Вкладки
 * не вмещались, подписи приходилось резать до одного слова, и найти в них
 * нужный пункт можно было только перебором. Теперь сверху одиннадцать
 * разделов с подписью, что внутри, а над ними — поиск по всем пунктам
 * разом: с сорока настройками это единственный способ попасть в нужную
 * с первого раза.
 *
 * Список пунктов один на оба режима (раздел и поиск) и собирается
 * данными, а не разложен по экранам: два списка одних и тех же настроек
 * разошлись бы на первой же правке.
 */
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val colors = LocalW0yColors.current
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }

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

    if (aboutOpen) {
        val experiments = viewModel.settings.collectAsStateWithLifecycle().value.experiments
        BackHandler { aboutOpen = false }
        AboutScreen(
            onBack = { aboutOpen = false },
            experiments = experiments,
            onUnlockExperiments = viewModel::unlockExperiments,
        )
        return
    }

    val rows =
        settingsRows(
            viewModel = viewModel,
            onOpenLogin = onOpenLogin,
            onOpenAbout = { aboutOpen = true },
            onExport = { exportLauncher.launch("w0y-settings.json") },
            onImport = { importLauncher.launch(arrayOf("application/json")) },
        )
    val current = section?.let { name -> SettingsSection.entries.firstOrNull { it.name == name } }

    // Внутри раздела «назад» возвращает к списку разделов, а не закрывает
    // настройки целиком: закрытие с середины ощущается как сбой.
    if (current != null) {
        BackHandler { section = null }
    }

    Column(
        Modifier
            .fillMaxSize()
            .screenBackground()
            .statusBarsPadding(),
    ) {
        ScreenTitle(
            title = stringResource(current?.title ?: R.string.settings_title),
            onBack = if (current != null) ({ section = null }) else onClose,
        )
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }

        if (current == null) {
            SettingsSearchField(value = query, onValueChange = { query = it })
            Spacer(Modifier.height(12.dp))
        }

        val matches =
            if (current != null || query.isBlank()) {
                emptyList()
            } else {
                val needle = query.trim().lowercase()
                rows.filter { row ->
                    needle in row.title.lowercase() ||
                        needle in row.description.lowercase() ||
                        needle in row.keywords.lowercase()
                }
            }

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when {
                current != null -> {
                    item {
                        Text(
                            text = stringResource(current.summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (current == SettingsSection.DEVICES) {
                        item { DevicesBody(viewModel) }
                    }
                    items(
                        items = rows.filter { it.section == current },
                        key = { "${it.section}-${it.title}" },
                    ) { row -> SettingRowView(row) }
                    item { Spacer(Modifier.height(24.dp)) }
                }

                query.isNotBlank() ->
                    if (matches.isEmpty()) {
                        item {
                            EmptyState(
                                sprite = Sprites.search,
                                title = stringResource(R.string.settings_search_nothing_title),
                                text = stringResource(R.string.settings_search_nothing_text),
                            )
                        }
                    } else {
                        items(matches, key = { "${it.section}-${it.title}" }) { row ->
                            SettingRowView(row, sectionHint = stringResource(row.section.title))
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }

                else -> {
                    items(SettingsSection.entries, key = { it.name }) { entry ->
                        SectionCard(entry) { section = entry.name }
                    }
                    item {
                        Text(
                            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Разделы настроек. У каждого — подпись, что внутри: без неё список
 * названий заставляет открывать разделы по очереди, чтобы понять, где
 * лежит нужное.
 */
private enum class SettingsSection(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val sprite: List<String>,
) {
    SOUND(R.string.settings_section_sound, R.string.settings_section_sound_sum, Sprites.wave),
    TONE(R.string.settings_section_tone, R.string.settings_section_tone_sum, Sprites.sliders),
    PLAYER(R.string.settings_section_player, R.string.settings_section_player_sum, Sprites.play),
    SPEED(R.string.settings_section_speed, R.string.settings_section_speed_sum, Sprites.rocket),
    STORAGE(R.string.settings_section_storage, R.string.settings_section_storage_sum, Sprites.box),
    SEARCH(R.string.settings_section_search, R.string.settings_section_search_sum, Sprites.search),
    ACCOUNT(R.string.settings_section_account, R.string.settings_section_account_sum, Sprites.sync),
    CLEAN(R.string.settings_section_clean, R.string.settings_section_clean_sum, Sprites.shield),
    LOOK(R.string.settings_section_look, R.string.settings_section_look_sum, Sprites.palette),
    DEVICES(R.string.settings_section_devices, R.string.settings_section_devices_sum, Sprites.headphones),
    DATA(R.string.settings_section_data, R.string.settings_section_data_sum, Sprites.license),
}

/** Чем управляет пункт. Вид строки выбирается по этому, а не задаётся руками. */
private sealed interface SettingControl {
    data class Toggle(val checked: Boolean, val onChange: (Boolean) -> Unit) : SettingControl

    data class Choice<T>(
        val options: List<T>,
        val selected: T,
        val label: @Composable (T) -> String,
        val onSelect: (T) -> Unit,
    ) : SettingControl

    data class Action(val button: String, val onClick: () -> Unit) : SettingControl

    data class Info(val value: String) : SettingControl
}

/**
 * Пункт настроек.
 *
 * Подписи уже разрешены в строки: по ним идёт поиск, и искать нужно по
 * тому языку, который человек видит на экране, а не по именам ресурсов.
 */
private class SettingRow(
    val section: SettingsSection,
    val title: String,
    val description: String,
    val control: SettingControl,
    /** Слова, по которым пункт тоже должен находиться. */
    val keywords: String = "",
)

@Composable
private fun SectionCard(section: SettingsSection, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, pressed = 0.98f)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(2.dp, colors.border, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelSprite(rows = section.sprite, color = colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(section.title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
            )
            Text(
                text = stringResource(section.summary),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
        Spacer(Modifier.width(10.dp))
        PixelSprite(
            rows = Sprites.chevronRight,
            color = colors.textMuted,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun SettingsSearchField(value: String, onValueChange: (String) -> Unit) {
    val colors = LocalW0yColors.current
    Row(
        Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(2.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelSprite(rows = Sprites.search, color = colors.textMuted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_search_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textMuted,
                )
            }
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            SpriteButton(Sprites.close, onClick = { onValueChange("") }, size = 14)
        }
    }
}

/** Одна строка настроек. Вид зависит только от того, чем она управляет. */
@Composable
private fun SettingRowView(row: SettingRow, sectionHint: String? = null) {
    val colors = LocalW0yColors.current
    val control = row.control
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (sectionHint != null) {
            Text(
                text = "❯ ${sectionHint.uppercase()}",
                style = PixelSectionLabel,
                color = colors.accent,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        when (control) {
            is SettingControl.Toggle ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { control.onChange(!control.checked) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Labels(row, Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    PixelSwitch(checked = control.checked, onCheckedChange = control.onChange)
                }

            is SettingControl.Action ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Labels(row, Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    PixelButton(text = control.button, onClick = control.onClick, fill = colors.surfaceHigh)
                }

            is SettingControl.Info ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Labels(row, Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text(control.value, style = MaterialTheme.typography.bodyLarge, color = colors.accent)
                }

            is SettingControl.Choice<*> -> {
                Labels(row, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                ChoiceControl(control)
            }
        }
    }
}

/**
 * Обобщённый выбор. Приведение к `Any?` здесь неизбежно: список
 * настроек хранит варианты разных типов в одном месте, а вернуть выбор
 * обратно всё равно можно только тому, кто его отдал.
 */
@Composable
private fun ChoiceControl(control: SettingControl.Choice<*>) {
    @Suppress("UNCHECKED_CAST")
    val typed = control as SettingControl.Choice<Any?>
    PixelSegmented(
        options = typed.options.map { typed.label(it) },
        selectedIndex = typed.options.indexOf(typed.selected),
        onSelect = { typed.onSelect(typed.options[it]) },
    )
}

@Composable
private fun Labels(row: SettingRow, modifier: Modifier) {
    val colors = LocalW0yColors.current
    Column(modifier) {
        Text(row.title, style = MaterialTheme.typography.bodyLarge, color = colors.text)
        Text(row.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
}

/**
 * Выходы звука — то, что система действительно видит подключённым.
 *
 * Список приходит от AudioManager и обновляется на подключение и
 * отключение. Ничего «своего» здесь нет: показывать выдуманные
 * устройства в приложении, которое обещает не врать, нельзя.
 */
@Composable
private fun DevicesBody(viewModel: SettingsViewModel) {
    val colors = LocalW0yColors.current
    val outputs by viewModel.outputs.collectAsStateWithLifecycle()
    if (outputs.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_devices_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            modifier = Modifier.padding(vertical = 10.dp),
        )
        return
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        outputs.forEach { output -> DeviceRow(output) }
    }
}

@Composable
private fun DeviceRow(output: AudioOutput) {
    val colors = LocalW0yColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (output.active) colors.accent else colors.border),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = output.name ?: stringResource(output.kind.label),
                style = MaterialTheme.typography.bodyLarge,
                color = if (output.active) colors.text else colors.textMuted,
                maxLines = 1,
            )
            Text(
                text = stringResource(output.kind.label),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
        if (output.active) {
            Text(
                text = stringResource(R.string.settings_devices_active),
                style = PixelSectionLabel,
                color = colors.accent,
            )
        }
    }
}

/**
 * Все пункты настроек одним списком.
 *
 * Здесь они и живут: экран только показывает их — в разделе или в выдаче
 * поиска. Пункт, который ничего не меняет, в список не попадает: обещание
 * «у каждой настройки написано, что она делает» держится ровно до первого
 * тумблера-пустышки.
 */
@Composable
private fun settingsRows(
    viewModel: SettingsViewModel,
    onOpenLogin: () -> Unit,
    onOpenAbout: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
): List<SettingRow> {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val startupAverage by viewModel.startupAverage.collectAsStateWithLifecycle()
    val startupLast by viewModel.startupLast.collectAsStateWithLifecycle()
    val startupCount by viewModel.startupCount.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val context = LocalContext.current

    return buildList {
        // ЗВУК
        add(
            SettingRow(
                section = SettingsSection.SOUND,
                title = stringResource(R.string.settings_quality_wifi_title),
                description = stringResource(R.string.settings_quality_wifi_desc),
                keywords = "wifi wi-fi",
                control =
                    SettingControl.Choice(
                        options = Quality.entries,
                        selected = settings.qualityWifi,
                        label = { qualityLabel(it) },
                        onSelect = viewModel::setQualityWifi,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.SOUND,
                title = stringResource(R.string.settings_quality_mobile_title),
                description = stringResource(R.string.settings_quality_mobile_desc),
                keywords = "lte 4g 5g",
                control =
                    SettingControl.Choice(
                        options = Quality.entries,
                        selected = settings.qualityMobile,
                        label = { qualityLabel(it) },
                        onSelect = viewModel::setQualityMobile,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.SOUND,
                title = stringResource(R.string.settings_normalize_title),
                description = stringResource(R.string.settings_normalize_desc),
                control = SettingControl.Toggle(settings.normalizeVolume, viewModel::setNormalize),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.SOUND,
                title = stringResource(R.string.settings_skip_silence_title),
                description = stringResource(R.string.settings_skip_silence_desc),
                control = SettingControl.Toggle(settings.skipSilence, viewModel::setSkipSilence),
            ),
        )

        // ЗВУЧАНИЕ
        add(
            SettingRow(
                section = SettingsSection.TONE,
                title = stringResource(R.string.settings_speed_title),
                description = stringResource(R.string.settings_speed_desc),
                keywords = "slowed sped",
                control =
                    SettingControl.Choice(
                        options = listOf(0.75f, 0.85f, 1f, 1.25f, 1.5f),
                        selected = settings.speed,
                        label = { "${it}×".replace(".0×", "×") },
                        onSelect = viewModel::setSpeed,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.TONE,
                title = stringResource(R.string.settings_pitch_title),
                description = stringResource(R.string.settings_pitch_desc),
                control =
                    SettingControl.Choice(
                        options = listOf(0.9f, 0.95f, 1f, 1.05f, 1.1f),
                        selected = settings.pitch,
                        label = { it.toString().replace("1.0", stringResource(R.string.settings_pitch_normal)) },
                        onSelect = viewModel::setPitch,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.TONE,
                title = stringResource(R.string.settings_reverb_title),
                description = stringResource(R.string.settings_reverb_desc),
                keywords = "reverb",
                control =
                    SettingControl.Choice(
                        options = Reverb.entries,
                        selected = settings.reverb,
                        label = { stringResource(it.label) },
                        onSelect = viewModel::setReverb,
                    ),
            ),
        )

        // ПЛЕЕР
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_equalizer_title),
                description = stringResource(R.string.settings_equalizer_desc),
                keywords = "eq",
                control =
                    SettingControl.Action(stringResource(R.string.settings_equalizer_button)) {
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
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_queue_title),
                description = stringResource(R.string.settings_queue_desc),
                keywords = "radio",
                control =
                    SettingControl.Choice(
                        options = QueueMode.entries,
                        selected = settings.queueMode,
                        label = { stringResource(it.label) },
                        onSelect = viewModel::setQueueMode,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_seek_step_title),
                description = stringResource(R.string.settings_seek_step_desc),
                control =
                    SettingControl.Choice(
                        options = listOf(5, 10, 15, 30),
                        selected = settings.seekStepSec,
                        label = { stringResource(R.string.settings_seek_step_value, it) },
                        onSelect = viewModel::setSeekStep,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_cover_glow_title),
                description = stringResource(R.string.settings_cover_glow_desc),
                control = SettingControl.Toggle(settings.playerCoverGlow, viewModel::setPlayerCoverGlow),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_lyrics_title),
                description = stringResource(R.string.settings_lyrics_desc),
                control = SettingControl.Toggle(settings.showLyrics, viewModel::setShowLyrics),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_pause_headphones_title),
                description = stringResource(R.string.settings_pause_headphones_desc),
                control = SettingControl.Toggle(settings.pauseOnHeadphonesOut, viewModel::setPauseOnUnplug),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_resume_headphones_title),
                description = stringResource(R.string.settings_resume_headphones_desc),
                control = SettingControl.Toggle(settings.resumeOnHeadphonesIn, viewModel::setResumeOnPlug),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_sleep_title),
                description = stringResource(R.string.settings_sleep_desc),
                control =
                    SettingControl.Choice(
                        options = listOf(15, 30, 45, 60),
                        selected = settings.sleepTimerDefaultMin,
                        label = { stringResource(R.string.settings_sleep_minutes, it) },
                        onSelect = viewModel::setSleepDefault,
                    ),
            ),
        )

        // СКОРОСТЬ
        add(
            SettingRow(
                section = SettingsSection.SPEED,
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
                control =
                    SettingControl.Info(
                        startupAverage
                            ?.let { stringResource(R.string.settings_startup_value_ms, it.toInt()) }
                            ?: "—",
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.SPEED,
                title = stringResource(R.string.settings_preload_title),
                description = stringResource(R.string.settings_preload_desc),
                control = SettingControl.Toggle(settings.preloadNext, viewModel::setPreload),
            ),
        )

        // ПАМЯТЬ
        add(
            SettingRow(
                section = SettingsSection.STORAGE,
                title = stringResource(R.string.settings_wifi_only_title),
                description = stringResource(R.string.settings_wifi_only_desc),
                control = SettingControl.Toggle(settings.downloadOnWifiOnly, viewModel::setDownloadOnWifiOnly),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.STORAGE,
                title = stringResource(R.string.settings_cache_title),
                description = stringResource(R.string.settings_cache_desc, (cacheBytes / 1024 / 1024).toInt()),
                control =
                    SettingControl.Choice(
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
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.STORAGE,
                title = stringResource(R.string.settings_clear_cache_title),
                description = stringResource(R.string.settings_clear_cache_desc),
                control = SettingControl.Action(stringResource(R.string.settings_clear_cache_button), viewModel::clearCache),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.STORAGE,
                title = stringResource(R.string.settings_autodownload_title),
                description = stringResource(R.string.settings_autodownload_desc),
                control = SettingControl.Toggle(settings.autoDownloadLiked, viewModel::setAutoDownload),
            ),
        )

        // ПОИСК
        add(
            SettingRow(
                section = SettingsSection.SEARCH,
                title = stringResource(R.string.settings_suggestions_title),
                description = stringResource(R.string.settings_suggestions_desc),
                control = SettingControl.Toggle(settings.searchSuggestions, viewModel::setSearchSuggestions),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.SEARCH,
                title = stringResource(R.string.settings_search_history_title),
                description = stringResource(R.string.settings_search_history_desc),
                control = SettingControl.Toggle(settings.saveSearchHistory, viewModel::setSaveSearchHistory),
            ),
        )

        // АККАУНТ
        add(
            SettingRow(
                section = SettingsSection.ACCOUNT,
                title = stringResource(R.string.settings_account_title),
                description =
                    if (signedIn) {
                        stringResource(R.string.settings_account_in, accountName ?: stringResource(R.string.settings_account_unnamed))
                    } else {
                        stringResource(R.string.settings_account_out)
                    },
                keywords = "google youtube",
                control =
                    if (signedIn) {
                        SettingControl.Action(stringResource(R.string.settings_account_sign_out), viewModel::signOut)
                    } else {
                        SettingControl.Action(stringResource(R.string.settings_account_sign_in), onOpenLogin)
                    },
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.ACCOUNT,
                title = stringResource(R.string.settings_sync_playlists_title),
                description =
                    if (signedIn) {
                        stringResource(R.string.settings_sync_playlists_desc)
                    } else {
                        stringResource(R.string.settings_sync_playlists_desc_out)
                    },
                keywords = "sync",
                control = SettingControl.Toggle(settings.syncPlaylists, viewModel::setSyncPlaylists),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.ACCOUNT,
                title = stringResource(R.string.settings_history_title),
                description = stringResource(R.string.settings_history_desc),
                control = SettingControl.Toggle(settings.keepHistory, viewModel::setKeepHistory),
            ),
        )

        // БЕЗ МАТА
        add(
            SettingRow(
                section = SettingsSection.CLEAN,
                title = stringResource(R.string.settings_clean_title),
                description = stringResource(R.string.settings_clean_desc),
                control = SettingControl.Toggle(settings.cleanMode, viewModel::setCleanMode),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.CLEAN,
                title = stringResource(R.string.settings_clean_fallback_title),
                description = stringResource(R.string.settings_clean_fallback_desc),
                control =
                    SettingControl.Choice(
                        options = ExplicitFallback.entries,
                        selected = settings.explicitFallback,
                        label = { stringResource(it.label) },
                        onSelect = viewModel::setExplicitFallback,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.CLEAN,
                title = stringResource(R.string.settings_hide_explicit_title),
                description = stringResource(R.string.settings_hide_explicit_desc),
                control = SettingControl.Toggle(settings.hideExplicit, viewModel::setHideExplicit),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.CLEAN,
                title = stringResource(R.string.settings_mute_swear_title),
                description = stringResource(R.string.settings_mute_swear_desc),
                control = SettingControl.Toggle(settings.muteSwearLines, viewModel::setMuteSwearLines),
            ),
        )

        // ВИД
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_theme_title),
                description = stringResource(R.string.settings_theme_desc),
                keywords = "oled",
                control =
                    SettingControl.Choice(
                        // Стекло видно только тем, кто открыл эксперименты:
                        // в семье TexFi оно чужое, и в общем списке сбивало бы.
                        options = ThemeMode.entries.filter { it != ThemeMode.GLASS || settings.experiments || settings.theme == it },
                        selected = settings.theme,
                        label = { themeLabel(it) },
                        onSelect = viewModel::setTheme,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_accent_title),
                description = stringResource(R.string.settings_accent_desc),
                control =
                    SettingControl.Choice(
                        options = Accent.entries,
                        selected = settings.accent,
                        label = { stringResource(it.label) },
                        onSelect = viewModel::setAccent,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_language_title),
                description = stringResource(R.string.settings_language_desc),
                control =
                    SettingControl.Choice(
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
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_start_tab_title),
                description = stringResource(R.string.settings_start_tab_desc),
                control =
                    SettingControl.Choice(
                        options = StartTab.entries,
                        selected = settings.startTab,
                        label = { stringResource(it.label) },
                        onSelect = viewModel::setStartTab,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_shelves_title),
                description = stringResource(R.string.settings_shelves_desc),
                control = SettingControl.Toggle(settings.showRecommendations, viewModel::setShowRecommendations),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_live_background_title),
                description = stringResource(R.string.settings_live_background_desc),
                control = SettingControl.Toggle(settings.animatedBackground, viewModel::setAnimatedBackground),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_compact_title),
                description = stringResource(R.string.settings_compact_desc),
                control = SettingControl.Toggle(settings.compactRows, viewModel::setCompactRows),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_haptics_title),
                description = stringResource(R.string.settings_haptics_desc),
                keywords = "vibration",
                control = SettingControl.Toggle(settings.haptics, viewModel::setHaptics),
            ),
        )

        // ДАННЫЕ
        add(
            SettingRow(
                section = SettingsSection.DATA,
                title = stringResource(R.string.settings_export_title),
                description = stringResource(R.string.settings_export_desc),
                keywords = "json",
                control = SettingControl.Action(stringResource(R.string.settings_export_button), onExport),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DATA,
                title = stringResource(R.string.settings_import_title),
                description = stringResource(R.string.settings_import_desc),
                keywords = "json",
                control = SettingControl.Action(stringResource(R.string.settings_import_button), onImport),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DATA,
                title = stringResource(R.string.settings_about_title),
                description = stringResource(R.string.settings_about_desc, BuildConfig.VERSION_NAME),
                keywords = "license agpl",
                control = SettingControl.Action(stringResource(R.string.settings_about_button), onOpenAbout),
            ),
        )
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
        ThemeMode.GLASS -> stringResource(R.string.theme_glass)
    }
