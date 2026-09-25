package com.texfi.w0y.ui.shell

import androidx.lifecycle.ViewModel
import com.texfi.w0y.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ShellViewModel @Inject constructor(
    val player: PlayerConnection,
) : ViewModel()
