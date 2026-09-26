package com.texfi.w0y.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.texfi.w0y.R
import com.texfi.w0y.BuildConfig
import com.texfi.w0y.data.Diagnostics
import com.texfi.w0y.data.ArtistCard
import com.texfi.w0y.data.LibraryRepository
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.data.db.PlaylistEntity
import com.texfi.w0y.data.SearchHistoryRepository
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.playback.DownloadsRepository
import com.texfi.w0y.playback.PlaybackStarter
import com.texfi.w0y.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/** Раздел выдачи. Один запрос к YouTube на раздел — фильтры у них разные. */
enum class SearchFilter(@StringRes val label: Int) {
    SONGS(R.string.search_filter_songs),
    ALBUMS(R.string.search_filter_albums),
    ARTISTS(R.string.search_filter_artists),
}

sealed interface SearchState {
    data object Idle : SearchState

    data object Loading : SearchState

    data class Results(
        val songs: List<SongItem> = emptyList(),
        val albums: List<PlaylistCard> = emptyList(),
        val artists: List<ArtistCard> = emptyList(),
    ) : SearchState {
        val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
    }

    data class Failed(val message: String, val detail: String? = null) : SearchState
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: YouTubeRepository,
    private val diagnostics: Diagnostics,
    private val library: LibraryRepository,
    private val downloads: DownloadsRepository,
    private val history: SearchHistoryRepository,
    settingsRepository: SettingsRepository,
    private val playback: PlaybackStarter,
    val player: PlayerConnection,
) : ViewModel() {
    /** Прятать ли записи с меткой «E» — решает настройка «без мата». */
    val hideExplicit: StateFlow<Boolean> =
        settingsRepository.settings
            .map { it.hideExplicit }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), false)

    /** Недавние запросы: и подсказка под пустым полем, и сигнал рекомендациям. */
    val recentQueries: StateFlow<List<String>> =
        history.recent.stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )
    val playlists: StateFlow<List<PlaylistEntity>> =
        library.playlists.stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    fun download(song: SongItem) = downloads.download(song)

    fun addToPlaylist(playlistId: Long, song: SongItem) =
        viewModelScope.launch { library.addToPlaylist(playlistId, song) }

    fun createPlaylistWith(name: String, song: SongItem) =
        viewModelScope.launch {
            val id = library.createPlaylist(name)
            library.addToPlaylist(id, song)
        }

    private val _diagnosis = MutableStateFlow<String?>(null)
    val diagnosis: StateFlow<String?> = _diagnosis.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.SONGS)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    /**
     * Готовые ответы по паре «запрос + раздел».
     *
     * Переключение вкладок туда-обратно не должно ходить в сеть: выдача за
     * секунду не меняется, а ожидание там, где уже всё показывали, — ровно
     * то ощущение медленного поиска, от которого уходили.
     */
    private val cache = mutableMapOf<Pair<String, SearchFilter>, SearchState.Results>()

    init {
        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        combine(
            // Пауза в наборе, а не запрос на каждую букву.
            _query.debounce(280).distinctUntilChanged(),
            _filter,
        ) { query, filter -> query to filter }
            // flatMapLatest отменяет устаревший запрос: пользователь видит
            // результат по тому, что набрано сейчас, а не три буквы назад.
            .flatMapLatest { (query, filter) ->
                flow {
                    if (query.isBlank()) {
                        emit(SearchState.Idle)
                        return@flow
                    }
                    cache[query to filter]?.let {
                        emit(it)
                        return@flow
                    }
                    emit(SearchState.Loading)
                    val result =
                        runCatching {
                            when (filter) {
                                SearchFilter.SONGS -> SearchState.Results(songs = repository.searchSongs(query))
                                SearchFilter.ALBUMS -> SearchState.Results(albums = repository.searchAlbums(query))
                                SearchFilter.ARTISTS -> SearchState.Results(artists = repository.searchArtists(query))
                            }
                        }
                    emit(
                        result.fold(
                            onSuccess = {
                                cache[query to filter] = it
                                // Запоминаем только то, что действительно
                                // нашлось: опечатки в подсказках не нужны.
                                if (!it.isEmpty) viewModelScope.launch { history.remember(query) }
                                it
                            },
                            onFailure = { error ->
                                Timber.w(error, "Поиск «$query» не удался")
                                SearchState.Failed(
                                    message = context.getString(R.string.search_failed),
                                    // В debug показываем настоящую причину прямо
                                    // на экране: телефон у пользователя, логи
                                    // читать неоткуда, а «проверь сеть» скрывает
                                    // любую ошибку кода под видом проблем связи.
                                    detail =
                                        if (BuildConfig.DEBUG) {
                                            buildString {
                                                append(error::class.qualifiedName)
                                                error.message?.let {
                                                    append(": ")
                                                    append(it.take(300))
                                                }
                                                error.cause?.let {
                                                    append("\n← ")
                                                    append(it::class.simpleName)
                                                    append(": ")
                                                    append(it.message?.take(200).orEmpty())
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                )
                            },
                        ),
                    )
                }
            }.onEach { _state.value = it }
            .launchIn(viewModelScope)
    }

    fun onFilterChange(value: SearchFilter) {
        _filter.value = value
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** Три пробных запроса мимо библиотеки — видно, что именно не нравится YouTube. */
    fun diagnose() {
        viewModelScope.launch {
            _diagnosis.value = context.getString(R.string.search_checking)
            _diagnosis.value = runCatching { diagnostics.run(_query.value.ifBlank { context.getString(R.string.search_query_fallback) }) }
                .getOrElse { context.getString(R.string.search_diagnostics_failed, it.message.orEmpty()) }
        }
    }

    /** Повтор того же запроса: сеть на телефоне отваливается чаще, чем код. */
    fun retry() {
        cache.remove(_query.value to _filter.value)
        val current = _query.value
        _query.value = ""
        _query.value = current
    }

    fun playFrom(songs: List<SongItem>, index: Int) = playback.play(songs, index)

    fun clearHistory() = viewModelScope.launch { history.clear() }
}
