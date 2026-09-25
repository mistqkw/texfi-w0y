package com.texfi.w0y

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.texfi.w0y.playback.PlayerConnection
import javax.inject.Inject
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.ui.shell.ShellViewModel
import com.texfi.w0y.ui.shell.W0yShell
import com.texfi.w0y.ui.theme.W0yTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var playerConnection: PlayerConnection

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Системный сплэш снимается сразу: он только закрывает белую вспышку
        // холодного старта. Задерживать его ради анимации нельзя — скорость
        // запуска здесь дороже эффекта.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        playerConnection.connect()
        // Без разрешения Android 13+ просто не покажет медиа-уведомление —
        // музыка играть будет, а управлять ею с шторки не выйдет.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val shellViewModel: ShellViewModel = hiltViewModel()
            val theme by shellViewModel.theme.collectAsStateWithLifecycle()
            W0yTheme(mode = theme) {
                W0yShell(shellViewModel)
            }
        }
    }
}
