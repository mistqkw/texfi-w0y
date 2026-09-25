package com.texfi.w0y.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.ui.components.MiniPlayer
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.screens.HomeScreen
import com.texfi.w0y.ui.screens.LibraryScreen
import com.texfi.w0y.ui.screens.PlayerScreen
import com.texfi.w0y.ui.screens.SearchScreen
import com.texfi.w0y.ui.theme.LocalW0yColors

private enum class Tab(val labelRes: Int, val sprite: List<String>) {
    HOME(R.string.tab_home, Sprites.home),
    SEARCH(R.string.tab_search, Sprites.search),
    LIBRARY(R.string.tab_library, Sprites.library),
}

@Composable
fun W0yShell(viewModel: ShellViewModel = hiltViewModel()) {
    val colors = LocalW0yColors.current
    var tab by remember { mutableStateOf(Tab.HOME) }
    val playerState by viewModel.player.state.collectAsStateWithLifecycle()
    var playerExpanded by remember { mutableStateOf(false) }

    // Полноэкранный плеер живёт поверх оболочки: вкладки и очередь
    // сохраняют состояние, пока он открыт, и закрытие ничего не пересобирает.
    BackHandler(enabled = playerExpanded) { playerExpanded = false }

    Box(Modifier.fillMaxSize().background(colors.background)) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Box(Modifier.weight(1f)) {
            // Переход экранов — fade+scale на 180 мс. Material-slide поверх
            // резкой пиксельной графики читается как дефолт фреймворка.
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.98f)) togetherWith
                        fadeOut(tween(120))
                },
                label = "tab",
            ) { current ->
                when (current) {
                    Tab.HOME -> HomeScreen()
                    Tab.SEARCH -> SearchScreen()
                    Tab.LIBRARY -> LibraryScreen()
                }
            }
        }
        MiniPlayer(
            state = playerState,
            positionProvider = viewModel.player::positionMs,
            onToggle = viewModel.player::togglePlayPause,
            onNext = { viewModel.player.skipNext() },
            onExpand = { playerExpanded = true },
        )
        PixelNavBar(selected = tab, onSelect = { tab = it })
    }

    AnimatedVisibility(
        visible = playerExpanded && playerState.song != null,
        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(160)),
        exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(140)),
    ) {
        PlayerScreen(
            state = playerState,
            positionProvider = viewModel.player::positionMs,
            onCollapse = { playerExpanded = false },
            onToggle = viewModel.player::togglePlayPause,
            onNext = { viewModel.player.skipNext() },
            onPrevious = viewModel.player::skipPrevious,
            onSeek = viewModel.player::seekTo,
            onShuffle = viewModel.player::toggleShuffle,
            onRepeat = viewModel.player::cycleRepeat,
            onPlayAt = viewModel.player::playAt,
        )
    }
    }
}

@Composable
private fun PixelNavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val colors = LocalW0yColors.current
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.border),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { entry ->
                val active = entry == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onSelect(entry) }
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                ) {
                    PixelSprite(
                        rows = entry.sprite,
                        color = if (active) colors.accent else colors.textMuted,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = stringResource(entry.labelRes),
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        color = if (active) colors.text else colors.textMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
