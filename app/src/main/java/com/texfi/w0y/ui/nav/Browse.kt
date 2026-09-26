package com.texfi.w0y.ui.nav

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Страницы, которые открываются поверх вкладок: артист и альбом.
 *
 * Они доступны отовсюду — из поиска, из плеера, из плитки на главной, —
 * поэтому маршрут не принадлежит ни одной вкладке и живёт в своём стеке
 * в оболочке приложения.
 */
@Immutable
sealed interface BrowseRoute {
    val browseId: String

    data class Artist(
        override val browseId: String,
        val name: String,
        val thumbnailUrl: String? = null,
    ) : BrowseRoute

    data class Album(
        override val browseId: String,
        val title: String,
        val thumbnailUrl: String? = null,
    ) : BrowseRoute
}

/** Открыть страницу из любого места интерфейса. */
fun interface BrowseNavigator {
    fun open(route: BrowseRoute)
}

/**
 * Навигатор пробрасывается через окружение, а не параметрами: иначе
 * колбэк «открыть артиста» пришлось бы тянуть через каждый список,
 * строку и панель по пути.
 */
val LocalBrowseNavigator = staticCompositionLocalOf { BrowseNavigator { } }
