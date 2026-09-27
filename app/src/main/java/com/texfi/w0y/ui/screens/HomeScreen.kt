package com.texfi.w0y.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.BuildConfig
import com.texfi.w0y.R
import com.texfi.w0y.data.db.PinEntity
import com.texfi.w0y.ui.components.DownloadButton
import com.texfi.w0y.ui.components.ArtistTile
import com.texfi.w0y.ui.components.Gutter
import com.texfi.w0y.ui.components.ReleaseTile
import com.texfi.w0y.ui.components.ScreenTitle
import com.texfi.w0y.ui.components.SectionHeader
import com.texfi.w0y.ui.components.ShelfTitle
import com.texfi.w0y.ui.components.SkeletonRow
import com.texfi.w0y.ui.components.SongRow
import com.texfi.w0y.ui.components.SongTile
import com.texfi.w0y.ui.components.SpeedDialTile
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.nav.BrowseRoute
import com.texfi.w0y.ui.nav.LocalBrowseNavigator
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel

private const val DIAL_COLUMNS = 3
private const val DIAL_ROWS = 3
private const val DIAL_PAGE = DIAL_COLUMNS * DIAL_ROWS
private val TILE_GAP = 10.dp

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenLocalPlaylist: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    home: HomeViewModel = hiltViewModel(),
) {
    val colors = LocalW0yColors.current
    val navigator = LocalBrowseNavigator.current
    val dial by viewModel.speedDial.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val shelves by home.shelves.collectAsStateWithLifecycle()
    val loadingShelves by home.loading.collectAsStateWithLifecycle()
    val recommendationsFailed by home.failed.collectAsStateWithLifecycle()
    val showRecommendations by home.showRecommendations.collectAsStateWithLifecycle()
    val startupAverage by home.startupAverage.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenTitle(
                title = stringResource(R.string.app_name),
                // Замер старта стоит рядом с названием: обещание «быстро»
                // проверяется на этом же экране, а не на слово.
                subtitle =
                    startupAverage?.let {
                        stringResource(R.string.home_meta, it.toInt(), BuildConfig.VERSION_NAME)
                    } ?: stringResource(R.string.home_meta_plain, BuildConfig.VERSION_NAME),
                actions = { SpriteButton(Sprites.gear, onClick = onOpenSettings, size = 22) },
            )
        }

        item {
            Column(Modifier.padding(horizontal = Gutter)) {
                SectionHeader(
                    index = 1,
                    label = stringResource(R.string.home_dial),
                    hint = if (dial.isEmpty()) null else stringResource(R.string.home_dial_hint),
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        if (dial.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = Gutter)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                        repeat(DIAL_COLUMNS) { GhostTile(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.home_dial_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        } else {
            item {
                SpeedDialPager(
                    items = dial,
                    onOpen = { item ->
                        when (item.kind) {
                            PinEntity.KIND_ARTIST ->
                                navigator.open(BrowseRoute.Artist(item.id, item.title, item.thumbnailUrl))

                            // Свой плейлист живёт в базе, а не на YouTube:
                            // его открывает библиотека, а не страница альбома.
                            PinEntity.KIND_PLAYLIST if item.localPlaylistId != null ->
                                onOpenLocalPlaylist(item.localPlaylistId)

                            PinEntity.KIND_ALBUM, PinEntity.KIND_PLAYLIST ->
                                navigator.open(BrowseRoute.Album(item.id, item.title, item.thumbnailUrl))

                            else -> viewModel.playDial(item)
                        }
                    },
                    onPin = viewModel::togglePin,
                )
            }
        }

        if (showRecommendations) {
            item {
                Column(Modifier.padding(horizontal = Gutter)) {
                    Spacer(Modifier.height(22.dp))
                    SectionHeader(
                        index = 2,
                        label = stringResource(R.string.home_recommendations),
                        action = { SpriteButton(Sprites.repeat, onClick = home::refresh, size = 18) },
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (recommendationsFailed) {
                item {
                    Text(
                        text = stringResource(R.string.home_recommendations_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 10.dp),
                    )
                }
            } else if (shelves.isEmpty() && loadingShelves) {
                items(3) { SkeletonRow(Modifier.padding(horizontal = Gutter)) }
            }

            items(shelves, key = { "shelf-${it.title}" }) { shelf ->
                Column(Modifier.padding(top = 16.dp)) {
                    ShelfTitle(shelf.title, Modifier.padding(horizontal = Gutter))
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Gutter),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shelf.songs, key = { "s-${it.id}" }) { song ->
                            SongTile(song) { home.play(shelf.songs, shelf.songs.indexOf(song)) }
                        }
                        items(shelf.cards, key = { "c-${it.browseId}" }) { card ->
                            ReleaseTile(card) {
                                navigator.open(BrowseRoute.Album(card.browseId, card.title, card.thumbnailUrl))
                            }
                        }
                        items(shelf.artists, key = { "a-${it.browseId}" }) { card ->
                            ArtistTile(card) {
                                navigator.open(BrowseRoute.Artist(card.browseId, card.name, card.thumbnailUrl))
                            }
                        }
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = Gutter)) {
                    Spacer(Modifier.height(26.dp))
                    SectionHeader(stringResource(R.string.home_recent), index = 3)
                    Spacer(Modifier.height(6.dp))
                }
            }
            items(recent.take(20), key = { "recent-${it.id}" }) { song ->
                SongRow(
                    song = song,
                    modifier = Modifier.padding(horizontal = Gutter),
                    onClick = { viewModel.play(recent, recent.indexOf(song)) },
                    actions = { DownloadButton(song.id, onDownload = { viewModel.download(song) }) },
                )
            }
        }

        if (liked.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = Gutter)) {
                    Spacer(Modifier.height(26.dp))
                    SectionHeader(stringResource(R.string.library_likes), index = 4)
                    Spacer(Modifier.height(6.dp))
                }
            }
            items(liked.take(10), key = { "liked-${it.id}" }) { song ->
                SongRow(
                    song = song,
                    modifier = Modifier.padding(horizontal = Gutter),
                    onClick = { viewModel.play(liked, liked.indexOf(song)) },
                )
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }
}

/**
 * Пустое место быстрого набора.
 *
 * Пустой экран с одинокой плиткой выглядит как недогруженный; призрачные
 * клетки сразу показывают, сколько их будет и что с ними делать.
 */
@Composable
private fun GhostTile(modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    Box(
        modifier
            .aspectRatio(1f)
            .background(colors.surface)
            .border(2.dp, colors.border),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            style = PixelSectionLabel,
            color = colors.border,
            textAlign = TextAlign.Center,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Быстрый набор страницами.
 *
 * Девять плиток на страницу, страницы листаются вбок — так на главной
 * помещается несколько десятков закреплений, не превращая её в
 * бесконечную ленту плиток.
 */
@Composable
private fun SpeedDialPager(
    items: List<DialItem>,
    onOpen: (DialItem) -> Unit,
    onPin: (DialItem) -> Unit,
) {
    val colors = LocalW0yColors.current
    // Страница всегда из девяти мест: на неполной странице плитки иначе
    // липнут к верху, и вторая страница выглядит обрубком.
    val pages = items.chunked(DIAL_PAGE).map { page -> page + List(DIAL_PAGE - page.size) { null } }
    val state = rememberPagerState(pageCount = { pages.size })
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Высота считается от ширины: плитка квадратная, а у страницы
        // внутри LazyColumn своей высоты нет.
        val tile = (maxWidth - Gutter * 2 - TILE_GAP * (DIAL_COLUMNS - 1)) / DIAL_COLUMNS
        val pageHeight = tile * DIAL_ROWS + TILE_GAP * (DIAL_ROWS - 1)
        Column {
            HorizontalPager(
                state = state,
                pageSpacing = Gutter * 2,
                contentPadding = PaddingValues(horizontal = Gutter),
                modifier = Modifier.height(pageHeight),
            ) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                    pages[page].chunked(DIAL_COLUMNS).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                            row.forEach { item ->
                                if (item == null) {
                                    GhostTile(Modifier.weight(1f))
                                    return@forEach
                                }
                                SpeedDialTile(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    thumbnailUrl = item.thumbnailUrl,
                                    pinned = item.pinned,
                                    kindSprite =
                                        when (item.kind) {
                                            PinEntity.KIND_ARTIST -> Sprites.artist
                                            PinEntity.KIND_ALBUM, PinEntity.KIND_PLAYLIST -> Sprites.release
                                            else -> null
                                        },
                                    round = item.kind == PinEntity.KIND_ARTIST,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOpen(item) },
                                    onLongClick = { onPin(item) },
                                )
                            }
                        }
                    }
                }
            }
            if (pages.size > 1) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(pages.size) { index ->
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (index == state.currentPage) 8.dp else 6.dp)
                                .background(if (index == state.currentPage) colors.accent else colors.border),
                        )
                    }
                }
            }
        }
    }
}
