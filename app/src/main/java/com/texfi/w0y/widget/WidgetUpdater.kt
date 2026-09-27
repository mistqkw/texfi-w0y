package com.texfi.w0y.widget

import android.content.Context
import android.graphics.Bitmap
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.texfi.w0y.data.Thumbnails
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Держит виджет в курсе того, что играет.
 *
 * Обложка сохраняется файлом, а не передаётся только в момент обновления:
 * систему перерисовать виджет может попросить кто угодно и когда угодно —
 * уже после того, как наш процесс убит, — и тогда картинку нужно взять
 * с диска.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastCoverUrl: String? = null

    fun push(
        title: String,
        artist: String,
        playing: Boolean,
        coverUrl: String?,
    ) {
        // Сначала текст и состояние кнопки — они должны появиться сразу,
        // не дожидаясь сети за обложкой.
        val previous = WidgetState.read(context)
        WidgetState.write(
            context,
            WidgetState(
                title = title,
                artist = artist,
                playing = playing,
                coverPath = previous.coverPath,
                pid = android.os.Process.myPid(),
            ),
        )
        W0yWidget.refresh(context)
        if (coverUrl == lastCoverUrl) return
        lastCoverUrl = coverUrl
        scope.launch { updateCover(coverUrl) }
    }

    /**
     * Плеер закончился — виджет должен это показать.
     *
     * Иначе на домашнем экране осталась бы строка с треком и кнопки,
     * которые уже никому не адресованы.
     */
    fun clear() {
        WidgetState.write(context, WidgetState())
        lastCoverUrl = null
        W0yWidget.refresh(context)
    }

    private suspend fun updateCover(coverUrl: String?) {
        val file = File(context.cacheDir, COVER_FILE)
        val bitmap = coverUrl?.let { loadCover(it) }
        if (bitmap == null) {
            file.delete()
        } else {
            runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                .onFailure { Timber.w(it, "Обложка виджета не сохранилась") }
        }
        val state = WidgetState.read(context)
        WidgetState.write(context, state.copy(coverPath = file.takeIf { it.exists() }?.path))
        withContext(Dispatchers.Main) { W0yWidget.refresh(context) }
    }

    private suspend fun loadCover(url: String): Bitmap? {
        val request =
            ImageRequest
                .Builder(context)
                .data(Thumbnails.sized(url, COVER_PX))
                .size(COVER_PX, COVER_PX)
                // RemoteViews отдаёт картинку другому процессу, а
                // hardware-битмап туда не переживает передачу.
                .allowHardware(false)
                .build()
        val result = SingletonImageLoader.get(context).execute(request)
        return (result.image as? BitmapImage)?.bitmap?.let { Thumbnails.squareOf(it, url) }
    }

    private companion object {
        const val COVER_FILE = "widget_cover.png"

        /** Больше не нужно: виджет рисует обложку в 56dp. */
        const val COVER_PX = 192
    }
}
