package com.texfi.w0y.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.texfi.w0y.BuildConfig
import com.texfi.w0y.MainActivity
import com.texfi.w0y.data.LibraryRepository
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.W0ySettings
import com.texfi.w0y.data.YouTubeRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Фоновое воспроизведение с медиа-сессией: уведомление, экран блокировки
 * и наушники работают через неё же.
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class W0yPlayerService : MediaSessionService() {
    @Inject lateinit var repository: YouTubeRepository

    @Inject lateinit var library: LibraryRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject
    @Named("player")
    lateinit var dataSourceFactory: DataSource.Factory

    @Inject lateinit var audioSession: AudioSessionHolder

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val settings = MutableStateFlow(W0ySettings())
    private var session: MediaSession? = null
    private var playRequestedAt: Long = 0L
    private var headsetReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()

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

        settingsRepository.settings
            .onEach { current ->
                settings.value = current
                player.skipSilenceEnabled = current.skipSilence
                player.setHandleAudioBecomingNoisy(current.pauseOnHeadphonesOut)
                applyLoudness(player)
            }.launchIn(scope)

        audioSession.update(player.audioSessionId)
        player.addAnalyticsListener(
            object : AnalyticsListener {
                override fun onAudioSessionIdChanged(
                    eventTime: AnalyticsListener.EventTime,
                    audioSessionId: Int,
                ) = audioSession.update(audioSessionId)
            },
        )
        player.addListener(TrackListener(player))
        if (BuildConfig.DEBUG) attachFirstAudioTrace(player)

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

        registerHeadsetReceiver(player)
    }

    /**
     * «Продолжать при подключении наушников». Первое событие после
     * регистрации приходит липким — текущее состояние гнезда, а не
     * подключение, — поэтому его пропускаем, иначе музыка включалась бы
     * сама при каждом старте сервиса.
     */
    private fun registerHeadsetReceiver(player: Player) {
        var skippedSticky = false
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != AudioManager.ACTION_HEADSET_PLUG) return
                    if (!skippedSticky) {
                        skippedSticky = true
                        return
                    }
                    val plugged = intent.getIntExtra("state", 0) == 1
                    if (plugged &&
                        settings.value.resumeOnHeadphonesIn &&
                        player.mediaItemCount > 0 &&
                        !player.isPlaying
                    ) {
                        player.play()
                    }
                }
            }
        headsetReceiver = receiver
        registerReceiver(receiver, IntentFilter(AudioManager.ACTION_HEADSET_PLUG))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        headsetReceiver?.let { runCatching { unregisterReceiver(it) } }
        headsetReceiver = null
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * Выравнивание громкости по данным самого YouTube: в потоке приходит
     * измеренная громкость трека, и тихие записи перестают теряться после
     * громких. Выключено — играем как есть.
     */
    private fun applyLoudness(player: Player) {
        val id = player.currentMediaItem?.mediaId
        if (!settings.value.normalizeVolume || id == null) {
            player.volume = 1f
            return
        }
        val loudness = repository.cachedLoudnessDb(id) ?: run { player.volume = 1f; return }
        // Приводим к -14 дБ: типовой ориентир стриминговых сервисов.
        val gainDb = (-14.0 - loudness).coerceIn(-6.0, 6.0)
        player.volume = 10.0.pow(gainDb / 20.0).toFloat().coerceIn(0.2f, 1f)
    }

    private inner class TrackListener(
        private val player: Player,
    ) : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            prefetchNext()
            applyLoudness(player)
            val item = mediaItem ?: return
            if (!settings.value.keepHistory) return
            scope.launch {
                library.remember(
                    SongItem(
                        id = item.mediaId,
                        title = item.mediaMetadata.title?.toString().orEmpty(),
                        artist = item.mediaMetadata.artist?.toString().orEmpty(),
                        album = item.mediaMetadata.albumTitle?.toString(),
                        thumbnailUrl = item.mediaMetadata.artworkUri?.toString(),
                    ),
                )
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) = prefetchNext()

        /** Греет ссылку следующего трека — переход должен быть без паузы. */
        private fun prefetchNext() {
            if (!settings.value.preloadNext) return
            val next = player.nextMediaItemIndex
            if (next == C.INDEX_UNSET) return
            val id = player.getMediaItemAt(next).mediaId
            if (id.isNotEmpty()) repository.prefetch(id)
        }
    }

    /** Главная метрика проекта: время от команды до первого звука. */
    private fun attachFirstAudioTrace(player: ExoPlayer) {
        player.addListener(
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (playWhenReady) playRequestedAt = System.nanoTime()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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
                    Timber.i("Звук пошёл через $ms мс после команды")
                }
            },
        )
    }
}
