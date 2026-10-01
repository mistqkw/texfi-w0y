package com.texfi.w0y.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun main() = application {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val app = remember { AppState(scope, Yt(), Store()).also { it.start() } }
    Window(
        onCloseRequest = {
            app.shutdown()
            exitApplication()
        },
        title = "w0y",
        icon = painterResource("icon.png"),
        state = rememberWindowState(size = DpSize(1180.dp, 760.dp)),
    ) {
        App(app)
    }
}
