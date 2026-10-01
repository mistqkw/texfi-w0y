package com.texfi.w0y.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.data.Reverb
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private enum class SSection(val title: String, val summary: String, val glyph: Glyph) {
    SOUND("Звук", "Качество, громкость, тишина", Glyph.Volume),
    TONE("Звучание", "Скорость, тон, эхо — свой SLOWED", Glyph.Wave),
    PLAYER("Плеер", "Очередь, перемотка, сон", Glyph.Play),
    SPEED("Скорость", "Замер старта и предзагрузка", Glyph.Timer),
    STORAGE("Память", "Загрузки и папка с музыкой", Glyph.Download),
    SEARCH("Поиск", "Подсказки и недавние запросы", Glyph.Search),
    ACCOUNT("Аккаунт", "Вход, плейлисты в YouTube, история", Glyph.Library),
    CLEAN("Без мата", "Чистые версии и метка «E»", Glyph.Check),
    LOOK("Вид", "Тема, схема, язык, старт", Glyph.Home),
    DEVICES("Устройства", "Куда уходит звук", Glyph.Queue),
    DATA("Данные", "Выгрузка настроек, о приложении", Glyph.Mic),
}

private sealed interface Control {
    class Toggle(val checked: Boolean, val onChange: (Boolean) -> Unit) : Control

    class Choice(val labels: List<String>, val selected: Int, val onSelect: (Int) -> Unit) : Control

    class Action(val button: String, val onClick: () -> Unit) : Control

    class Info(val value: String) : Control

    class Custom(val content: @Composable () -> Unit) : Control
}

private class Row(val section: SSection, val title: String, val description: String, val control: Control, val keywords: String = "")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(app: AppState) {
    var section by remember { mutableStateOf<SSection?>(null) }
    var query by remember { mutableStateOf("") }
    var aboutOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val st = app.st

    if (aboutOpen) {
        AboutPane(app) { aboutOpen = false }
        return
    }

    val rows = buildRows(app, onAbout = { aboutOpen = true }, onMessage = { message = it })
    val q = query.trim().lowercase()
    val shown =
        when {
            q.isNotEmpty() -> rows.filter { "${it.title} ${it.description} ${it.keywords} ${it.section.title}".lowercase().contains(q) }
            section != null -> rows.filter { it.section == section }
            else -> emptyList()
        }

    ScreenFrame(section?.title ?: "Настройки", app, canBack = false, actions = {
        if (section != null && q.isEmpty()) PixelButton("Назад", onClick = { section = null }, primary = false)
    }) {
        Column(Modifier.fillMaxSize()) {
            RowCenter(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(C.Surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Icon(Glyph.Search, C.Muted, 16.dp)
                Gap(w = 10)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Txt("Найти настройку", color = C.Muted, size = 14)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = C.Text, fontSize = 14.sp), cursorBrush = SolidColor(C.Blue), modifier = Modifier.fillMaxWidth())
                }
                if (query.isNotEmpty()) IconButton(Glyph.Close, onClick = { query = "" }, color = C.Muted, size = 12.dp, box = 26.dp)
            }
            message?.let {
                LaunchedEffect(it) { kotlinx.coroutines.delay(3000); message = null }
                Txt(it, color = C.Sand, size = 12, modifier = Modifier.padding(top = 8.dp), maxLines = 2)
            }
            Gap(h = 12)
            val state = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = state, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (q.isEmpty() && section == null) {
                        items(SSection.entries) { s -> SectionCard(s) { section = s } }
                    } else if (shown.isEmpty()) {
                        item {
                            Txt("НЕ НАШЛОСЬ", pixel = true, size = 12, color = C.Blue, modifier = Modifier.padding(top = 12.dp))
                            Txt("Такой настройки здесь нет. Попробуйте одно слово вместо двух.", color = C.Muted, size = 13, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
                        }
                    } else {
                        items(shown) { r -> SettingRowView(r, showSection = q.isNotEmpty()) }
                    }
                    item { Gap(h = 24) }
                }
                VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SectionCard(s: SSection, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    PixelCard(Modifier.fillMaxWidth().hoverable(source).clickable(onClick = onClick)) {
        RowCenter {
            Box(Modifier.width(40.dp).height(40.dp).clip(RoundedCornerShape(4.dp)).background(C.SurfaceHigh), contentAlignment = Alignment.Center) {
                Icon(s.glyph, C.Blue, 20.dp)
            }
            Gap(w = 14)
            Column(Modifier.weight(1f)) {
                Txt(s.title, size = 16, weight = FontWeight.Medium)
                Txt(s.summary, color = C.Muted, size = 12)
            }
            Icon(Glyph.Next, C.Muted, 14.dp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingRowView(r: Row, showSection: Boolean) {
    PixelCard(Modifier.fillMaxWidth()) {
        Column {
            if (showSection) SectionLabel(r.section.title, Modifier.padding(bottom = 6.dp))
            RowCenter {
                Column(Modifier.weight(1f)) {
                    Txt(r.title, size = 15, weight = FontWeight.Medium)
                    Txt(r.description, color = C.Muted, size = 12, maxLines = 6, modifier = Modifier.padding(top = 3.dp))
                }
                when (val c = r.control) {
                    is Control.Toggle -> {
                        Gap(w = 14)
                        PixelToggle(c.checked, c.onChange)
                    }
                    is Control.Action -> {
                        Gap(w = 14)
                        PixelButton(c.button, onClick = c.onClick, primary = false)
                    }
                    is Control.Info -> {
                        Gap(w = 14)
                        Txt(c.value, color = C.Blue, size = 14, weight = FontWeight.Medium)
                    }
                    else -> Unit
                }
            }
            when (val c = r.control) {
                is Control.Choice -> {
                    Gap(h = 10)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        c.labels.forEachIndexed { i, label ->
                            val on = i == c.selected
                            Box(
                                Modifier.clip(RoundedCornerShape(4.dp)).background(if (on) C.Blue else C.SurfaceHigh)
                                    .border(2.dp, if (on) C.BlueDeep else C.Border, RoundedCornerShape(4.dp))
                                    .clickable { c.onSelect(i) }.padding(horizontal = 12.dp, vertical = 7.dp),
                            ) { Txt(label, color = if (on) C.OnAccent else C.Text, size = 11, pixel = label.all { !it.isLowerCase() }, maxLines = 1) }
                        }
                    }
                }
                is Control.Custom -> {
                    Gap(h = 10)
                    c.content()
                }
                else -> Unit
            }
        }
    }
}

/** Квадратный переключатель: рамка и заливка, без плавных морфингов. */
@Composable
fun PixelToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.width(48.dp).height(26.dp).clip(RoundedCornerShape(3.dp))
            .background(if (checked) C.Blue else C.SurfaceHigh)
            .border(2.dp, if (checked) C.BlueDeep else C.Border, RoundedCornerShape(3.dp))
            .clickable { onChange(!checked) },
    ) {
        Box(
            Modifier.padding(3.dp).width(16.dp).height(16.dp).then(if (checked) Modifier.padding(start = 22.dp) else Modifier)
                .background(if (checked) C.OnAccent else C.Muted),
        )
    }
}

// ---------------------------------------------------------------- пункты

private fun <T> choice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) =
    Control.Choice(options.map(label), options.indexOf(selected).coerceAtLeast(0)) { onSelect(options[it]) }

@Composable
private fun buildRows(app: AppState, onAbout: () -> Unit, onMessage: (String) -> Unit): List<Row> {
    val st = app.st
    val set = app::setSt
    val devices by produceState<List<Pair<String, String>>?>(null) { value = withContext(Dispatchers.IO) { MpvDevices.list() } }
    return buildList {
        // ---- Звук
        add(Row(SSection.SOUND, "Качество звука", "Какой поток просить у YouTube. Максимум — лучший доступный, экономно — быстрее старт.",
            choice(Quality.entries, st.quality, { it.label }) { v -> set { it.copy(quality = v) } }))
        add(Row(SSection.SOUND, "Выравнивать громкость", "Тихие записи не теряются после громких — по данным самого YouTube.",
            Control.Toggle(st.normalizeVolume) { v -> set { it.copy(normalizeVolume = v) } }))
        add(Row(SSection.SOUND, "Пропускать тишину", "Молчание в начале записи проматывается.",
            Control.Toggle(st.skipSilence) { v -> set { it.copy(skipSilence = v) } }))
        // ---- Звучание
        add(Row(SSection.TONE, "Скорость", "Играет быстрее или медленнее оригинала. Замедление с эхом звучит как slowed-переделка, только из оригинального файла и без потери качества. Своя версия трека в плеере сильнее этой.",
            choice(listOf(0.5f, 0.75f, 0.85f, 1f, 1.1f, 1.25f, 1.5f, 2f), st.speed, { "${it}×".replace(".0×", "×") }) { v -> set { it.copy(speed = v) } }))
        add(Row(SSection.TONE, "Тон", "Насколько ниже или выше звучит голос. Отдельно от скорости: можно замедлить, не превращая вокал в бас.",
            choice(listOf(0.8f, 0.92f, 1f, 1.06f, 1.2f), st.pitch, { "%.2f".format(it) }) { v -> set { it.copy(pitch = v) } }))
        add(Row(SSection.TONE, "Эхо", "Реверб поверх трека — от небольшой комнаты до пещеры.",
            choice(Reverb.entries, st.reverb, { it.label.uppercase() }) { v -> set { it.copy(reverb = v) } }))
        // ---- Плеер
        add(Row(SSection.PLAYER, "Что играет дальше", st.queueMode.hint,
            choice(QueueMode.entries, st.queueMode, { it.label }) { v -> set { it.copy(queueMode = v) } }, "очередь радио перемешать"))
        add(Row(SSection.PLAYER, "Шаг перемотки", "На сколько прыгают кнопки «−» и «+» в плеере.",
            choice(listOf(5, 10, 15, 30), st.seekStepSec, { "$it с" }) { v -> set { it.copy(seekStepSec = v) } }))
        add(Row(SSection.PLAYER, "Свечение обложки", "Фон плеера берёт настоящий цвет обложки — он снят с самой картинки, а не выдуман.",
            Control.Toggle(st.playerCoverGlow) { v -> set { it.copy(playerCoverGlow = v) } }))
        add(Row(SSection.PLAYER, "Показывать лирику", "Текст песни в плеере, синхронизированный по строкам, из LRCLIB.",
            Control.Toggle(st.showLyrics) { v -> set { it.copy(showLyrics = v) } }))
        add(Row(SSection.PLAYER, "Таймер сна по умолчанию", "Сколько минут предлагать в плеере первым пунктом при включении таймера.",
            choice(listOf(10, 15, 30, 45, 60), st.sleepTimerDefaultMin, { "$it мин" }) { v -> set { it.copy(sleepTimerDefaultMin = v) } }))
        // ---- Скорость
        add(Row(SSection.SPEED, "Старт трека", "Время от нажатия до первого звука, замеренное на этом компьютере. ${app.player.startupSummary()}",
            Control.Info(app.player.lastStartupMs?.let { "$it мс" } ?: "—")))
        add(Row(SSection.SPEED, "Готовить следующий трек", "Ссылка следующего трека берётся заранее — переход без паузы.",
            Control.Toggle(st.preloadNext) { v -> set { it.copy(preloadNext = v) } }))
        // ---- Память
        add(Row(SSection.STORAGE, "Папка с музыкой", "Сюда скачиваются треки: один файл на трек, любой плеер их откроет. Сейчас занято: ${app.downloads.usedMb()} МБ.",
            Control.Action("ОТКРЫТЬ") { app.downloads.openFolder() }, "загрузки скачано папка"))
        add(Row(SSection.STORAGE, "Скачивать лайкнутое", "Трек, которому поставлен лайк, сразу уходит в загрузки.",
            Control.Toggle(st.autoDownloadLiked) { v -> set { it.copy(autoDownloadLiked = v) } }))
        // ---- Поиск
        add(Row(SSection.SEARCH, "Подсказки в поиске", "Пока набираете, YouTube подсказывает, что вы, возможно, ищете. Своих подсказок мы не выдумываем.",
            Control.Toggle(st.searchSuggestions) { v -> set { it.copy(searchSuggestions = v) } }))
        add(Row(SSection.SEARCH, "Хранить историю поиска", "Недавние запросы показываются под пустым полем поиска. Выключено — ничего не запоминается.",
            Control.Toggle(st.saveSearchHistory) { v -> set { it.copy(saveSearchHistory = v) } }))
        // ---- Аккаунт
        add(Row(SSection.ACCOUNT, "Аккаунт YouTube",
            if (app.signedIn) "Вошли как ${app.lib.accountName ?: "без имени"}" else "Без входа выдача общая по региону, а плейлистов и лайков нет.",
            Control.Custom {
                RowCenter {
                    if (app.signedIn) {
                        PixelButton("ВЫЙТИ", onClick = app::signOut, primary = false)
                    } else {
                        PixelButton(if (app.loggingIn) "ЖДУ ВХОД…" else "ВОЙТИ ЧЕРЕЗ GOOGLE", onClick = app::signInWithGoogle, enabled = !app.loggingIn)
                    }
                    app.accountStatus?.let { Txt("  $it", color = C.Sand, size = 12, maxLines = 2) }
                }
            }, "войти выйти google cookie"))
        add(Row(SSection.ACCOUNT, "Плейлисты и лайки в YouTube", "Правки здесь уходят в аккаунт, а правки в YouTube Music приходят сюда: плейлисты, треки, лайки. Выключено — аккаунт только читается.",
            Control.Toggle(st.syncPlaylists) { v -> set { it.copy(syncPlaylists = v) } }, "синхронизация"))
        add(Row(SSection.ACCOUNT, "Вести историю", "Запоминать, что включал. Выключено — раздел «история» перестаёт пополняться.",
            Control.Toggle(st.keepHistory) { v -> set { it.copy(keepHistory = v) } }))
        // ---- Без мата
        add(Row(SSection.CLEAN, "Искать чистую версию", "Трек с меткой «E» заменяется официальной clean-версией, если она выложена. Вырезать слова из готовой записи приложение не умеет и делать вид не будет.",
            Control.Toggle(st.cleanMode) { v -> set { it.copy(cleanMode = v) } }, "мат explicit"))
        add(Row(SSection.CLEAN, "Если чистой версии нет", "Играть оригинал как есть или пропустить его и перейти к следующему.",
            choice(ExplicitFallback.entries, st.explicitFallback, { it.label }) { v -> set { it.copy(explicitFallback = v) } }))
        add(Row(SSection.CLEAN, "Прятать «E» в выдаче", "Помеченные записи не показываются в поиске и рекомендациях.",
            Control.Toggle(st.hideExplicit) { v -> set { it.copy(hideExplicit = v) } }))
        add(Row(SSection.CLEAN, "Глушить строки с матом (бета)", "По синхронной лирике: строка с матом проигрывается без звука целиком. Отдельное слово убрать нельзя — для этого нужна дорожка без вокала. Работает только там, где нашлась синхронная лирика.",
            Control.Toggle(st.muteSwearLines) { v -> set { it.copy(muteSwearLines = v) } }))
        // ---- Вид
        add(Row(SSection.LOOK, "Тема", "Чёрная — полностью чёрный фон. Светлая — тёплая бумага, а не серо-белый.",
            choice(ThemeMode.entries, st.theme, { it.label }) { v -> set { it.copy(theme = v) } }, "оформление"))
        add(Row(SSection.LOOK, "Цветовая схема", "Акцент кнопок, прогресса и выделений. Синий — цвет семьи TexFi, остальное на ваш вкус.",
            Control.Custom { AccentPicker(app) }, "цвет акцент"))
        add(Row(SSection.LOOK, "Язык", "Язык лент и поиска YouTube и перевода текстов. Интерфейс десктопной версии пока только на русском.",
            choice(Language.entries, st.language, { it.label }) { v -> set { it.copy(language = v) } }))
        add(Row(SSection.LOOK, "Экран при запуске", "С чего начинать, когда открываешь приложение.",
            choice(StartTab.entries, st.startTab, { it.label }) { v -> set { it.copy(startTab = v) } }))
        add(Row(SSection.LOOK, "Ленты рекомендаций", "Блок с подборками YouTube Music на главной. Выключи — останется пустая главная.",
            Control.Toggle(st.showRecommendations) { v -> set { it.copy(showRecommendations = v) } }))
        add(Row(SSection.LOOK, "Живой фон", "Звёзды на фоне медленно мерцают. Выключите — фон отрисуется один раз: меньше нагрузки.",
            Control.Toggle(st.animatedBackground) { v -> set { it.copy(animatedBackground = v) } }))
        add(Row(SSection.LOOK, "Компактные списки", "Строки треков ниже, на экран помещается больше.",
            Control.Toggle(st.compactRows) { v -> set { it.copy(compactRows = v) } }))
        // ---- Устройства
        val list = devices
        add(Row(SSection.DEVICES, "Выход звука", when {
            list == null -> "Спрашиваю у системы…"
            list.size <= 1 -> "Система не назвала ни одного выхода звука. Бывает — тогда здесь честно пусто."
            else -> "Куда уходит звук. «auto» — как решит система."
        }, if (list == null || list.size <= 1) Control.Info("auto") else
            Control.Choice(list.map { it.second.take(48) }, list.indexOfFirst { it.first == st.audioDevice }.coerceAtLeast(0)) { i ->
                val id = list[i].first
                set { it.copy(audioDevice = id) }
                app.player.setAudioDevice(id)
            }, "наушники колонки bluetooth"))
        // ---- Данные
        add(Row(SSection.DATA, "Сохранить в файл", "Все настройки одним JSON — перенести на другой компьютер или на телефон не получится: форматы разные.",
            Control.Action("ЭКСПОРТ") {
                val dialog = FileDialog(null as Frame?, "Сохранить настройки", FileDialog.SAVE).apply { file = "w0y-settings.json"; isVisible = true }
                val f = dialog.file?.let { File(dialog.directory, it) }
                if (f != null) {
                    f.writeText(Json { prettyPrint = true }.encodeToString(DSettings.serializer(), st))
                    onMessage("Настройки сохранены: ${f.name}")
                }
            }))
        add(Row(SSection.DATA, "Загрузить из файла", "Применить ранее сохранённые настройки.",
            Control.Action("ИМПОРТ") {
                val dialog = FileDialog(null as Frame?, "Загрузить настройки", FileDialog.LOAD).apply { isVisible = true }
                val f = dialog.file?.let { File(dialog.directory, it) }
                if (f != null) {
                    runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(DSettings.serializer(), f.readText()) }
                        .onSuccess { v -> set { v }; onMessage("Настройки загружены") }
                        .onFailure { onMessage("Файл не разобрался: ${it.message}") }
                }
            }))
        add(Row(SSection.DATA, "О приложении", "Версия $APP_VERSION, лицензия, исходники и остальной TexFi.", Control.Action("ОТКРЫТЬ", onAbout)))
    }
}

const val APP_VERSION = "0.0.1 beta-1"

// ---------------------------------------------------------------- схема цвета

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(app: AppState) {
    val st = app.st
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Accent.entries.forEach { a ->
                val color = if (a == Accent.CUSTOM) Color(st.customAccent) else Color(a.accent)
                val on = a == st.accent
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { app.setSt { it.copy(accent = a) } }) {
                    Box(
                        Modifier.width(44.dp).height(44.dp).background(color, RoundedCornerShape(4.dp))
                            .border(if (on) 3.dp else 2.dp, if (on) C.Text else C.Border, RoundedCornerShape(4.dp)),
                    )
                    Txt(a.label, size = 8, pixel = true, color = if (on) C.Text else C.Muted, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
        if (st.accent == Accent.CUSTOM) {
            Gap(h = 14)
            val hue = hueOf(st.customAccent)
            Txt("Оттенок или HEX-код ниже", color = C.Muted, size = 12)
            Gap(h = 6)
            PixelSlider(hue, 0f..360f, { app.setSt { s -> s.copy(customAccent = fromHue(it)) } }, Modifier.fillMaxWidth(), step = 5f, segments = 36)
            Gap(h = 8)
            var hex by remember(st.customAccent) { mutableStateOf("%06X".format(st.customAccent and 0xFFFFFF)) }
            RowCenter {
                Txt("#", color = C.Muted, size = 14)
                Box(Modifier.width(110.dp).clip(RoundedCornerShape(4.dp)).background(C.SurfaceHigh).padding(8.dp)) {
                    BasicTextField(hex, {
                        hex = it.filter { c -> c.isLetterOrDigit() }.take(6).uppercase()
                        if (hex.length == 6) hex.toIntOrNull(16)?.let { v -> app.setSt { s -> s.copy(customAccent = 0xFF000000.toInt() or v) } }
                    }, singleLine = true, textStyle = TextStyle(color = C.Text, fontSize = 14.sp), cursorBrush = SolidColor(C.Blue))
                }
            }
        }
        Gap(h = 14)
        RowCenter {
            // Образцы с выбранным акцентом: кнопки живые, но ничего не делают.
            PixelButton("Образец", onClick = {})
            Gap(w = 10)
            PixelButton("Образец", onClick = {}, primary = false)
        }
    }
}

private fun hueOf(argb: Int): Float {
    val hsv = FloatArray(3)
    java.awt.Color.RGBtoHSB((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, hsv)
    return hsv[0] * 360f
}

private fun fromHue(hue: Float): Int = java.awt.Color.HSBtoRGB(hue / 360f, 0.62f, 1f)

// ---------------------------------------------------------------- о приложении

@Composable
private fun AboutPane(app: AppState, onBack: () -> Unit) {
    ScreenFrame("О приложении", app, canBack = false, actions = { PixelButton("Назад", onClick = onBack, primary = false) }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PixelCard(Modifier.fillMaxWidth()) {
                Column {
                    Txt("w0y", pixel = true, size = 26, color = C.Blue)
                    Txt("Музыка без рекламы и без Premium", size = 16, modifier = Modifier.padding(top = 8.dp))
                    Txt(
                        "Поток идёт с YouTube, аккаунт — ваш. Ни рекламы, ни аналитики, ни фоновой отправки «событий»: история и статистика лежат в файле на этом компьютере — проверяется по исходникам.",
                        color = C.Muted, size = 13, maxLines = 6, modifier = Modifier.padding(top = 8.dp),
                    )
                    Txt("Версия $APP_VERSION · десктоп для Linux", color = C.Muted, size = 12, modifier = Modifier.padding(top = 10.dp))
                }
            }
            PixelCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionLabel("Открытый код")
                    Gap(h = 10)
                    Txt("Лицензия GNU AGPL v3", size = 15, weight = FontWeight.Medium)
                    Txt("Кто выпустит изменённую версию — обязан открыть свои правки.", color = C.Muted, size = 12, maxLines = 2)
                    Gap(h = 10)
                    PixelButton("ИСХОДНИКИ НА GITHUB", onClick = { openUrl("https://github.com/mistqkw/texfi-w0y") }, primary = false)
                }
            }
            PixelCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionLabel("Остальные приложения")
                    Gap(h = 10)
                    Txt("Вся экосистема TexFi: f0kus — привычки и фокус, m0ney — личные финансы, files — отправка файлов себе.", color = C.Muted, size = 13, maxLines = 3)
                }
            }
        }
    }
}

fun openUrl(url: String) {
    runCatching { ProcessBuilder("xdg-open", url).start() }
}

/** Выходы звука из самого mpv: `mpv --audio-device=help` печатает их список. */
object MpvDevices {
    fun list(): List<Pair<String, String>> =
        runCatching {
            val p = ProcessBuilder("mpv", "--audio-device=help").redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().readText()
            p.waitFor()
            Regex("'([^']+)' \\((.*)\\)").findAll(text).map { it.groupValues[1] to it.groupValues[2] }.toList()
        }.getOrDefault(emptyList())
}
