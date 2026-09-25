package com.texfi.w0y.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.texfi.w0y.BuildConfig
import com.texfi.w0y.MainActivity
import com.texfi.w0y.data.YouTubeRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * Фоновое воспроизведение с медиа-сессией: уведомление, экран блокировки
 * и наушники работают через неё же.
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class W0yPlayerService : MediaSessionService() {
    @Inject lateinit var repository: YouTubeRepository

    @Inject lateinit var cache: SimpleCache

    @Inject lateinit var okHttpClient: OkHttpClient

    private var session: MediaSession? = null
    private var playRequestedAt: Long = 0L

    override fun onCreate() {
        super.onCreate()
        val dataSourceFactory =
            CacheDataSource
                .Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(
                    ResolvingDataSource.Factory(
                        OkHttpDataSource.Factory(okHttpClient),
                        StreamResolver(repository),
                    ),
                )
                // Ошибка записи в кэш не должна ронять воспроизведение:
                // лучше играть без кэша, чем не играть вовсе.
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val loadControl =
            DefaultLoadControl
                .Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 15_000,
                    /* maxBufferMs = */ 50_000,
                    // Старт после 250 мс буфера, а не после 2.5 с по умолчанию:
                    // это самая дешёвая доля секунды между нажатием и звуком.
                    /* bufferForPlaybackMs = */ 250,
                    /* bufferForPlaybackAfterRebufferMs = */ 1_000,
                ).build()

        val player =
            ExoPlayer
                .Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .setLoadControl(loadControl)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus = */ true,
                ).setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()

        player.addListener(PrefetchListener(player))
        if (BuildConfig.DEBUG) {
            player.addListener(
                object : Player.Listener {
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (playWhenReady) playRequestedAt = System.nanoTime()
                    }

                    override fun onMediaItemTransition(
                        mediaItem: androidx.media3.common.MediaItem?,
                        reason: Int,
                    ) {
                        playRequestedAt = System.nanoTime()
                    }
                },
            )
            player.addAnalyticsListener(
                object : AnalyticsListener {
                    override fun onAudioPositionAdvancing(
                        eventTime: AnalyticsListener.EventTime,
                        playoutStartSystemTimeMs: Long,
                    ) {
                        if (playRequestedAt == 0L) return
                        val ms = (System.nanoTime() - playRequestedAt) / 1_000_000
                        playRequestedAt = 0L
                        // Главная метрика проекта: время от команды до звука.
                        Timber.i("Звук пошёл через $ms мс после команды")
                    }
                },
            )
        }

        val sessionActivity =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        session =
            MediaSession
                .Builder(this, player)
                .setSessionActivity(sessionActivity)
                .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * Греет ссылку следующего трека, как только определилась очередь.
     * Переход между треками должен быть без паузы на извлечение потока.
     */
    private inner class PrefetchListener(
        private val player: Player,
    ) : Player.Listener {
        override fun onMediaItemTransition(
            mediaItem: androidx.media3.common.MediaItem?,
            reason: Int,
        ) = prefetchNext()

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = prefetchNext()

        private fun prefetchNext() {
            val next = player.nextMediaItemIndex
            if (next == C.INDEX_UNSET) return
            val id = player.getMediaItemAt(next).mediaId
            if (id.isNotEmpty()) repository.prefetch(id)
        }
    }
}
