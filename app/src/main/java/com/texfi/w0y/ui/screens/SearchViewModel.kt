package com.texfi.w0y.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.BuildConfig
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

sealed interface SearchState {
    data object Idle : SearchState

    data object Loading : SearchState

    data class Results(val songs: List<SongItem>) : SearchState

    data class Failed(val message: String, val detail: String? = null) : SearchState
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    val player: PlayerConnection,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    init {
        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        _query
            // Пауза в наборе, а не запрос на каждую букву. flatMapLatest
            // отменяет устаревший запрос: пользователь видит результат по
            // тому, что набрано сейчас, а не по тому, что было три буквы назад.
            .debounce(280)
            .distinctUntilChanged()
            .flatMapLatest { query ->
                flow {
                    if (query.isBlank()) {
                        emit(SearchState.Idle)
                        return@flow
                    }
                    emit(SearchState.Loading)
                    val result = runCatching { repository.searchSongs(query) }
                    emit(
                        result.fold(
                            onSuccess = { SearchState.Results(it) },
                            onFailure = { error ->
                                Timber.w(error, "Поиск «$query» не удался")
                                SearchState.Failed(
                                    message = "Не получилось спросить YouTube Music.",
                                    // В debug показываем настоящую причину прямо
                                    // на экране: телефон у пользователя, логи
                                    // читать неоткуда, а «проверь сеть» скрывает
                                    // любую ошибку кода под видом проблем связи.
                                    detail =
                                        if (BuildConfig.DEBUG) {
                                            buildString {
                                                append(error::class.qualifiedName)
                                                error.message?.let { append(": ")
                                                    append(it.take(300)) }
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

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** Повтор того же запроса: сеть на телефоне отваливается чаще, чем код. */
    fun retry() {
        val current = _query.value
        _query.value = ""
        _query.value = current
    }

    fun playFrom(songs: List<SongItem>, index: Int) = player.play(songs, index)
}
