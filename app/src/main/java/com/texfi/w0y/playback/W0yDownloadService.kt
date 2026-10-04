package com.texfi.w0y.playback

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.texfi.w0y.MainActivity
import com.texfi.w0y.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Фоновое скачивание треков во внутреннее хранилище приложения.
 *
 * Уведомление с прогрессом здесь обязательное: без него система не даёт
 * службе работать в фоне. Отключается только уведомление о завершении.
 */
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

    @Inject lateinit var holder: DownloadSettingsHolder

    private val notificationHelper by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = manager

    /**
     * Планировщик системы: если приложение выгрузили, а очередь ждала сеть,
     * загрузки продолжатся сами, когда сеть появится. Выключено в
     * настройках — продолжатся только при следующем запуске.
     */
    override fun getScheduler(): Scheduler? =
        if (holder.current.downloadAutoResume) PlatformScheduler(this, JOB_ID) else null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification =
        notificationHelper.buildProgressNotification(
            this,
            R.mipmap.ic_launcher_monochrome,
            openApp(this),
            null,
            downloads,
            notMetRequirements,
        )

    companion object {
        const val CHANNEL_ID = "w0y_downloads"
        private const val FOREGROUND_NOTIFICATION_ID = 42
        private const val DONE_NOTIFICATION_ID = 43
        private const val JOB_ID = 4242

        fun ensureChannel(context: Context) {
            NotificationUtil.createNotificationChannel(
                context,
                CHANNEL_ID,
                R.string.downloads_channel,
                0,
                NotificationUtil.IMPORTANCE_LOW,
            )
        }

        private fun openApp(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** Итог пачки одним уведомлением — его можно выключить в настройках. */
        fun notifyFinished(context: Context, title: String?, count: Int) {
            val text =
                if (title != null) {
                    context.getString(R.string.dl_done_notification, title)
                } else {
                    context.getString(R.string.dl_done_notification_many, count)
                }
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                    .setContentTitle(text)
                    .setContentIntent(openApp(context))
                    .setAutoCancel(true)
                    .build()
            runCatching {
                context.getSystemService(NotificationManager::class.java)?.notify(DONE_NOTIFICATION_ID, notification)
            }
        }
    }
}
