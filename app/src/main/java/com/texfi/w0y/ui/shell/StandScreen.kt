package com.texfi.w0y.ui.shell

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.R
import com.texfi.w0y.ui.components.LyricsTicker
import com.texfi.w0y.ui.components.PlayPauseButton
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.SteppedEasing
import com.texfi.w0y.ui.components.TransportButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.components.Visualizer
import com.texfi.w0y.ui.components.forUi
import com.texfi.w0y.ui.screens.PlayerViewModel
import com.texfi.w0y.ui.theme.LocalW0yColors
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Почему подставка закрылась сама — чтобы сказать об этом, а не молча исчезнуть. */
enum class StandExit(val message: Int?) {
    USER(null),
    UNPLUGGED(R.string.stand_exit_unplugged),
    TIMEOUT(R.string.stand_exit_timeout),
    LOW_BATTERY(R.string.stand_exit_battery),
}

/**
 * Режим подставки — свой, внутри приложения, а не системный Always-On.
 *
 * Чёрный экран без системных панелей, крупные название и исполнитель,
 * большие кнопки. Экран не гаснет, только пока режим открыт; яркость
 * приглушена. Против выгорания всё содержимое раз в минуту сдвигается на
 * несколько точек. Нажатие показывает и прячет кнопки. Выход — кнопкой
 * или жестом «назад»; сам — по пределу времени, при отключении зарядки
 * (если выбрано «только на зарядке») и при низком заряде.
 */
@Composable
fun StandScreen(onExit: (StandExit) -> Unit, viewModel: PlayerViewModel = hiltViewModel()) {
    val colors = LocalW0yColors.current
    val state by viewModel.player.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val exit by rememberUpdatedState(onExit)
    val song = state.song

    BackHandler { exit(StandExit.USER) }

    // Окно: без панелей, не гаснет, приглушено. Всё возвращается при выходе.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        val before = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window?.attributes = window?.attributes?.apply { screenBrightness = settings.standBrightness }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
            window?.attributes = window?.attributes?.apply { screenBrightness = before }
        }
    }
    LaunchedEffect(settings.standBrightness) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        window.attributes = window.attributes.apply { screenBrightness = settings.standBrightness }
    }

    // Заряд: отключили зарядку или заряд упал — по настройкам.
    var lowBattery by remember { mutableStateOf(false) }
    DisposableEffect(settings.standChargingOnly, settings.standLowBatteryExit) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    intent ?: return
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val percent = if (level >= 0) level * 100 / scale else 100
                    lowBattery = !charging && percent <= LOW_BATTERY
                    when {
                        settings.standChargingOnly && !charging -> exit(StandExit.UNPLUGGED)
                        settings.standLowBatteryExit && lowBattery -> exit(StandExit.LOW_BATTERY)
                    }
                }
            }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    LaunchedEffect(settings.standMaxMinutes) {
        if (settings.standMaxMinutes <= 0) return@LaunchedEffect
        delay(settings.standMaxMinutes * 60_000L)
        exit(StandExit.TIMEOUT)
    }

    // Сдвиг против выгорания: раз в минуту на несколько точек, ступенькой.
    var shift by remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SHIFT_MS)
            shift = IntOffset(Random.nextInt(-SHIFT_PX, SHIFT_PX + 1), Random.nextInt(-SHIFT_PX, SHIFT_PX + 1))
        }
    }
    // Кнопки прячутся сами через несколько секунд.
    var controls by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    LaunchedEffect(controls, touch) {
        if (controls) {
            delay(CONTROLS_MS)
            controls = false
        }
    }
    // Вход ступенями, как всё движение TexFi.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(ENTER_MS, easing = SteppedEasing(5))) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controls = !controls
                touch++
            },
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .offset { shift }
                .graphicsLayer {
                    alpha = enter.value
                    scaleX = 0.94f + 0.06f * enter.value
                    scaleY = 0.94f + 0.06f * enter.value
                }.padding(28.dp),
        ) {
            val wide = maxWidth > maxHeight
            val text = Color(0xFFE8E4DA)
            val dim = Color(0xFF8A877F)
            @Composable
            fun Info(modifier: Modifier) {
                Column(modifier, verticalArrangement = Arrangement.Center) {
                    Text(
                        song?.title.orEmpty(),
                        // Название читают — обычный шрифт, просто крупно.
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (wide) 32.sp else 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
                        color = text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        song?.artist.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp),
                        color = dim,
                        maxLines = 1,
                        textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val lines = lyrics?.synced.orEmpty()
                    if (settings.standLyrics && lines.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        LyricsTicker(
                            lines = lines,
                            positionProvider = viewModel.player::positionMs,
                            playing = state.isPlaying,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                            color = colors.accentText,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (lowBattery) {
                        Spacer(Modifier.height(14.dp))
                        Text(stringResource(R.string.stand_low_battery), style = MaterialTheme.typography.bodySmall, color = colors.secondary)
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Info(Modifier.weight(1f))
                    if (settings.standVisualizer) {
                        Spacer(Modifier.width(24.dp))
                        StandVisualizer(viewModel, state.isPlaying, Modifier.weight(0.8f).height(200.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (settings.standVisualizer) {
                        StandVisualizer(viewModel, state.isPlaying, Modifier.fillMaxWidth().height(160.dp))
                        Spacer(Modifier.height(28.dp))
                    }
                    Info(Modifier.fillMaxWidth())
                }
            }
        }

        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(tween(160, easing = SteppedEasing(4))),
            exit = fadeOut(tween(160, easing = SteppedEasing(4))),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier.padding(bottom = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(Sprites.previous, size = 34, onClick = { viewModel.player.skipPrevious(); touch++ }, color = Color(0xFFE8E4DA), touchPadding = 12)
                PlayPauseButton(isPlaying = state.isPlaying, size = 48, onClick = { viewModel.player.togglePlayPause(); touch++ }, touchPadding = 12)
                TransportButton(Sprites.next, size = 34, onClick = { viewModel.player.skipNext(); touch++ }, color = Color(0xFFE8E4DA), touchPadding = 12)
            }
        }
        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Box(Modifier.padding(16.dp).size(48.dp), contentAlignment = Alignment.Center) {
                SpriteButton(Sprites.close, onClick = { exit(StandExit.USER) }, size = 26)
            }
        }
    }
}

@Composable
private fun StandVisualizer(viewModel: PlayerViewModel, playing: Boolean, modifier: Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Visualizer(
        bus = viewModel.spectrum,
        style = settings.visualizerStyle.forUi(settings.uiStyle),
        playing = playing,
        sensitivity = settings.visualizerSensitivity,
        // Подставка стоит часами: 30 кадров хватает и экономят заряд.
        fps = 30,
        backdropUrl = null,
        modifier = modifier,
    )
}

private const val SHIFT_MS = 60_000L
private const val SHIFT_PX = 14
private const val CONTROLS_MS = 5_000L
private const val ENTER_MS = 400
private const val LOW_BATTERY = 15
