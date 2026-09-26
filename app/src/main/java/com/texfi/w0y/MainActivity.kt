package com.texfi.w0y

import android.Manifest
import android.os.Build
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.texfi.w0y.data.LocalePrefs
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

    // Язык подставляется до создания экрана: ресурсы читаются уже при
    // первом кадре, и менять их позже — значит показать один кадр на
    // системном языке, а следующий на выбранном.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalePrefs.wrap(newBase))
    }

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
            val accent by shellViewModel.accent.collectAsStateWithLifecycle()
            W0yTheme(mode = theme, accent = accent) {
                W0yShell(shellViewModel)
            }
        }
    }

    /**
     * Возвращение в приложение — повод перечитать состояние плеера.
     *
     * Пока экран не виден, связь с сервисом может оборваться: другое
     * приложение забирает звук, воспроизведение встаёт, сервис
     * останавливается — и сообщить об этом уже некому. Без этой строки
     * пользователь возвращается к кнопке «пауза», хотя музыка не играет.
     */
    override fun onStart() {
        super.onStart()
        playerConnection.refresh()
    }
}
