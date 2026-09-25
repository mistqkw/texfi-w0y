package com.texfi.w0y.playback

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.texfi.w0y.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Фоновое скачивание треков во внутреннее хранилище приложения. */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class W0yDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.downloads_channel,
    0,
) {
    @Inject lateinit var manager: DownloadManager

    private val notificationHelper by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = manager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification =
        notificationHelper.buildProgressNotification(
            this,
            R.mipmap.ic_launcher_monochrome,
            null,
            null,
            downloads,
            notMetRequirements,
        )

    companion object {
        const val CHANNEL_ID = "w0y_downloads"
        private const val FOREGROUND_NOTIFICATION_ID = 42

        fun ensureChannel(context: android.content.Context) {
            NotificationUtil.createNotificationChannel(
                context,
                CHANNEL_ID,
                R.string.downloads_channel,
                0,
                NotificationUtil.IMPORTANCE_LOW,
            )
        }
    }
}
