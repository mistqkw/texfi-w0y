package com.texfi.w0y.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.StartTab
import com.texfi.w0y.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.texfi.w0y.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ShellViewModel @Inject constructor(
    val player: PlayerConnection,
    settings: SettingsRepository,
) : ViewModel() {
    /** null, пока настройки не прочитаны: до этого экран не переключаем. */
    val startTab: StateFlow<StartTab?> =
        settings.settings
            .map { it.startTab }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val compactRows: StateFlow<Boolean> =
        settings.settings
            .map { it.compactRows }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val theme: StateFlow<ThemeMode> =
        settings.settings
            .map { it.theme }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)
}
