package com.texfi.w0y.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.R
import com.texfi.w0y.data.StartTab
import com.texfi.w0y.ui.components.LocalCompactRows
import com.texfi.w0y.ui.components.LocalHaptics
import com.texfi.w0y.ui.components.LocalPlayingSongId
import com.texfi.w0y.ui.components.MiniPlayer
import com.texfi.w0y.ui.components.pressScale
import com.texfi.w0y.ui.components.popWhenActivated
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.DownloadToast
import com.texfi.w0y.ui.components.LocalDownloadProgress
import com.texfi.w0y.ui.components.Starfield
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.nav.BrowseNavigator
import com.texfi.w0y.ui.nav.BrowseRoute
import com.texfi.w0y.ui.nav.LocalBrowseNavigator
import com.texfi.w0y.ui.screens.AlbumScreen
import com.texfi.w0y.ui.screens.ArtistScreen
import com.texfi.w0y.ui.screens.HomeScreen
import com.texfi.w0y.ui.screens.LibraryScreen
import com.texfi.w0y.ui.screens.LibraryRoute
import com.texfi.w0y.ui.screens.LibraryViewModel
import com.texfi.w0y.ui.screens.LoginScreen
import com.texfi.w0y.ui.screens.PlayerScreen
import com.texfi.w0y.ui.screens.SearchScreen
import com.texfi.w0y.ui.screens.SettingsScreen
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.screenBackground

private enum class Tab(val labelRes: Int, val sprite: List<String>) {
    HOME(R.string.tab_home, Sprites.home),
    SEARCH(R.string.tab_search, Sprites.search),
    LIBRARY(R.string.tab_library, Sprites.library),
}

@Composable
fun W0yShell(viewModel: ShellViewModel = hiltViewModel()) {
    val colors = LocalW0yColors.current
    var tab by remember { mutableStateOf(Tab.HOME) }
    val startTab by viewModel.startTab.collectAsStateWithLifecycle()
    val compactRows by viewModel.compactRows.collectAsStateWithLifecycle()
    var startTabApplied by remember { mutableStateOf(false) }
    // Стартовый экран применяется один раз за запуск: иначе возврат на
    // «дом» перекидывал бы обратно при каждом чтении настроек.
    LaunchedEffect(startTab) {
        if (!startTabApplied && startTab != null) {
            tab = when (startTab) {
                StartTab.SEARCH -> Tab.SEARCH
                StartTab.LIBRARY -> Tab.LIBRARY
                else -> Tab.HOME
            }
            startTabApplied = true
        }
    }
    val playerState by viewModel.player.state.collectAsStateWithLifecycle()
    var playerExpanded by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var loginOpen by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var loginBusy by remember { mutableStateOf(false) }
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val welcomeSeen by viewModel.welcomeSeen.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val animatedBackground by viewModel.animatedBackground.collectAsStateWithLifecycle()
    val haptics by viewModel.haptics.collectAsStateWithLifecycle()
    // Заставка играет один раз за запуск, а не при каждом повороте экрана.
    var introDone by rememberSaveable { mutableStateOf(false) }
    val libraryRoute by libraryViewModel.route.collectAsStateWithLifecycle()
    val browseStack = remember { mutableStateListOf<BrowseRoute>() }
    val navigator = remember { BrowseNavigator { route -> browseStack.add(route) } }
    // Верхний экран стека нужен и во время анимации закрытия, когда стек
    // уже пуст, — иначе на последнем кадре рисовать нечего.
    var lastBrowse by remember { mutableStateOf<BrowseRoute?>(null) }
    browseStack.lastOrNull()?.let { lastBrowse = it }

    // Одна точка на весь «назад».
    //
    // Раньше обработчик стоял только на полноэкранных слоях, и на любом
    // другом экране системный жест уходил мимо приложения — прямо на рабочий
    // стол, вместе со всей навигацией. Порядок здесь — это порядок закрытия:
    // от самого верхнего слоя к вкладкам.
    BackHandler(
        enabled =
            playerExpanded || settingsOpen || loginOpen ||
                browseStack.isNotEmpty() || libraryRoute != LibraryRoute.Root || tab != Tab.HOME,
    ) {
        when {
            loginOpen -> loginOpen = false
            settingsOpen -> settingsOpen = false
            playerExpanded -> playerExpanded = false
            browseStack.isNotEmpty() -> browseStack.removeAt(browseStack.lastIndex)
            libraryRoute != LibraryRoute.Root -> libraryViewModel.back()
            else -> tab = Tab.HOME
        }
    }

    val downloadProgress by viewModel.downloads.progress.collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalDownloadProgress provides downloadProgress,
        LocalBrowseNavigator provides navigator,
        LocalCompactRows provides compactRows,
        LocalHaptics provides haptics,
        LocalPlayingSongId provides playerState.song?.id,
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .screenBackground(),
    ) {
        // Фон экосистемы: он виден в промежутках между карточками и
        // строками — как на сайте, где чёрный тоже не пустой.
        Starfield(Modifier.fillMaxSize(), animated = animatedBackground)
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
                        Tab.HOME ->
                            HomeScreen(
                                onOpenSettings = { settingsOpen = true },
                                onOpenLocalPlaylist = { id ->
                                    libraryViewModel.open(LibraryRoute.Local(id))
                                    tab = Tab.LIBRARY
                                },
                            )
                        Tab.SEARCH -> SearchScreen()
                        Tab.LIBRARY -> LibraryScreen(onOpenLogin = { loginOpen = true })
                    }
                }

                // Артист и альбом ложатся поверх вкладки, но не поверх
                // мини-плеера: на этих страницах чаще всего и переключают
                // трек, прятать управление на них было бы издевательством.
                BrowseOverlay(
                    visible = browseStack.isNotEmpty(),
                    route = lastBrowse,
                    onBack = { if (browseStack.isNotEmpty()) browseStack.removeAt(browseStack.lastIndex) },
                )
            }
            DownloadToast(
                notices = viewModel.downloads.notices,
                onOpen = {
                    browseStack.clear()
                    playerExpanded = false
                    settingsOpen = false
                    libraryViewModel.open(LibraryRoute.Downloads)
                    tab = Tab.LIBRARY
                },
            )
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
            PlayerScreen(onCollapse = { playerExpanded = false })
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInVertically(tween(200)) { it / 3 } + fadeIn(tween(150)),
            exit = fadeOut(tween(120)),
        ) {
            SettingsScreen(
                onClose = { settingsOpen = false },
                onOpenLogin = { loginOpen = true },
            )
        }

        // Приветствие поверх всего: на первом запуске за ним ещё нечего
        // смотреть, а сразу после него — уже настроенное приложение.
        if (welcomeSeen == false) {
            WelcomeScreen(
                theme = theme,
                startTab = startTab ?: StartTab.HOME,
                onTheme = viewModel::setTheme,
                onStartTab = viewModel::setStartTab,
                onSignIn = { loginOpen = true },
                onDone = viewModel::completeWelcome,
            )
        }

        if (!introDone) {
            FeatherIntro(onFinished = { introDone = true })
        }

        AnimatedVisibility(
            visible = loginOpen,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(120)),
        ) {
            LoginScreen(
                busy = loginBusy,
                error = loginError,
                onCookie = { cookie ->
                    if (!loginBusy) {
                        loginBusy = true
                        loginError = null
                        libraryViewModel.onSignedIn(cookie) { message ->
                            loginBusy = false
                            loginError = message
                            if (message == null) loginOpen = false
                        }
                    }
                },
                onClose = {
                    loginOpen = false
                    loginError = null
                },
            )
        }
    }
}
}

/**
 * Слой страниц артиста и альбома.
 *
 * Вынесен в отдельную функцию не для красоты: внутри `Column` компилятор
 * выбирает `ColumnScope.AnimatedVisibility`, а здесь нужен обычный.
 */
@Composable
private fun BrowseOverlay(visible: Boolean, route: BrowseRoute?, onBack: () -> Unit) {
    val colors = LocalW0yColors.current
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(160)),
        exit = slideOutHorizontally(tween(180)) { it / 3 } + fadeOut(tween(140)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .screenBackground(),
        ) {
            when (route) {
                is BrowseRoute.Artist -> ArtistScreen(route = route, onBack = onBack)
                is BrowseRoute.Album -> AlbumScreen(route = route, onBack = onBack)
                null -> Unit
            }
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
                val interaction = remember { MutableInteractionSource() }
                // Своя анимация на смену вкладки: иконка подпрыгивает,
                // подчёркивание разъезжается в стороны. Отклик отличается
                // от нажатия на трек, и поэтому по нему сразу понятно,
                // что сменился весь экран, а не сработала кнопка в списке.
                val iconColor by animateColorAsState(
                    targetValue = if (active) colors.accent else colors.textMuted,
                    animationSpec = tween(180),
                    label = "tabIcon",
                )
                val labelColor by animateColorAsState(
                    targetValue = if (active) colors.text else colors.textMuted,
                    animationSpec = tween(180),
                    label = "tabLabel",
                )
                val underline by animateDpAsState(
                    targetValue = if (active) 20.dp else 0.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "tabUnderline",
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .pressScale(interaction, pressed = 0.88f)
                            .clickable(interactionSource = interaction, indication = null) { onSelect(entry) }
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                ) {
                    PixelSprite(
                        rows = entry.sprite,
                        color = iconColor,
                        modifier = Modifier.size(24.dp).popWhenActivated(active, peak = 1.22f),
                    )
                    Text(
                        text = stringResource(entry.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = labelColor,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    // Подчёркивание активной вкладки: подсветки иконки мало,
                    // на ходу разницу цвета не поймать.
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .width(underline)
                            .height(3.dp)
                            .background(colors.accent),
                    )
                }
            }
        }
    }
}
