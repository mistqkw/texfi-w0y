package com.texfi.w0y.ui.shell

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.texfi.w0y.R
import com.texfi.w0y.data.StartTab
import com.texfi.w0y.data.ThemeMode
import com.texfi.w0y.ui.components.Gutter
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.PixelSegmented
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelScreenTitle
import com.texfi.w0y.ui.theme.PixelSectionLabel
import kotlinx.coroutines.launch

/**
 * Первый запуск.
 *
 * Три экрана, а не десять: что это, чем отличается от остальных клиентов и
 * пара настроек, которые потом лень искать. Пропустить можно с любого шага —
 * знакомство, которое нельзя прервать, раздражает сильнее, чем его отсутствие.
 */
@Composable
fun WelcomeScreen(
    theme: ThemeMode,
    startTab: StartTab,
    onTheme: (ThemeMode) -> Unit,
    onStartTab: (StartTab) -> Unit,
    onSignIn: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val state = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Gutter, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.welcome_skip),
                style = PixelSectionLabel,
                color = colors.textMuted,
                modifier =
                    Modifier
                        .clickable(onClick = onDone)
                        .padding(8.dp),
            )
        }

        HorizontalPager(state = state, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> Hello()
                1 -> Features()
                else ->
                    FirstSettings(
                        theme = theme,
                        startTab = startTab,
                        onTheme = onTheme,
                        onStartTab = onStartTab,
                        onSignIn = onSignIn,
                    )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Gutter, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .size(if (index == state.currentPage) 9.dp else 7.dp)
                        .background(if (index == state.currentPage) colors.accent else colors.border),
                )
            }
            Spacer(Modifier.weight(1f))
            PixelButton(
                text = if (state.currentPage == 2) stringResource(R.string.welcome_start) else stringResource(R.string.welcome_next),
                onClick = {
                    if (state.currentPage == 2) {
                        onDone()
                    } else {
                        scope.launch { state.animateScrollToPage(state.currentPage + 1) }
                    }
                },
            )
        }
    }
}

@Composable
private fun Hello() {
    val colors = LocalW0yColors.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Gutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(230.dp),
        )
        Text(stringResource(R.string.app_name), style = PixelScreenTitle, color = colors.text)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .width(40.dp)
                .height(3.dp)
                .background(colors.accent),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.welcome_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun Features() {
    val colors = LocalW0yColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gutter),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.welcome_own_title), style = PixelScreenTitle, color = colors.text)
        Spacer(Modifier.height(20.dp))
        Feature(
            Sprites.sliders,
            stringResource(R.string.welcome_sound_title),
            stringResource(R.string.welcome_sound_text),
        )
        Feature(
            Sprites.stats,
            stringResource(R.string.welcome_stats_title),
            stringResource(R.string.welcome_stats_text),
        )
        Feature(
            Sprites.check,
            stringResource(R.string.welcome_clean_title),
            stringResource(R.string.welcome_clean_text),
        )
        Feature(
            Sprites.pin,
            stringResource(R.string.welcome_dial_title),
            stringResource(R.string.welcome_dial_text),
        )
        Feature(
            Sprites.timer,
            stringResource(R.string.welcome_fast_title),
            stringResource(R.string.welcome_fast_text),
        )
    }
}

@Composable
private fun Feature(sprite: List<String>, title: String, text: String) {
    val colors = LocalW0yColors.current
    Row(Modifier.padding(bottom = 18.dp)) {
        PixelSprite(rows = sprite, color = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text)
            Text(text, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
    }
}

@Composable
private fun FirstSettings(
    theme: ThemeMode,
    startTab: StartTab,
    onTheme: (ThemeMode) -> Unit,
    onStartTab: (StartTab) -> Unit,
    onSignIn: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val themes = listOf(ThemeMode.DARK, ThemeMode.OLED, ThemeMode.LIGHT)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gutter),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.welcome_tune_title), style = PixelScreenTitle, color = colors.text)
        Spacer(Modifier.height(24.dp))

        Text(stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.bodyLarge, color = colors.text)
        Text(
            stringResource(R.string.welcome_theme_text),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(10.dp))
        PixelSegmented(
            options = listOf(stringResource(R.string.welcome_theme_dark), stringResource(R.string.welcome_theme_oled), stringResource(R.string.welcome_theme_light)),
            selectedIndex = themes.indexOf(theme),
            onSelect = { onTheme(themes[it]) },
        )

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.settings_start_tab_title), style = MaterialTheme.typography.bodyLarge, color = colors.text)
        Spacer(Modifier.height(10.dp))
        PixelSegmented(
            options = StartTab.entries.map { stringResource(it.label) },
            selectedIndex = StartTab.entries.indexOf(startTab),
            onSelect = { onStartTab(StartTab.entries[it]) },
        )

        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.welcome_account_title), style = MaterialTheme.typography.bodyLarge, color = colors.text)
        Text(
            stringResource(R.string.welcome_account_text),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(12.dp))
        PixelButton(text = stringResource(R.string.library_sign_in), onClick = onSignIn, fill = colors.surfaceHigh)
    }
}
