package com.texfi.w0y.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import com.texfi.w0y.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn

/** Куда может уйти звук. Тип важнее названия: имя даёт не каждое устройство. */
enum class OutputKind(@StringRes val label: Int) {
    SPEAKER(R.string.device_speaker),
    EARPIECE(R.string.device_earpiece),
    WIRED(R.string.device_wired),
    BLUETOOTH(R.string.device_bluetooth),
    USB(R.string.device_usb),
    HDMI(R.string.device_hdmi),
    CAST(R.string.device_cast),
    OTHER(R.string.device_other),
}

/**
 * Подключённый выход звука.
 *
 * [name] может быть пустым: настоящее имя Bluetooth-устройства система
 * отдаёт не всем и не всегда, а просить ради подписи разрешение на
 * Bluetooth — обмен несоразмерный. Тогда показывается тип.
 */
data class AudioOutput(
    val id: Int,
    val name: String?,
    val kind: OutputKind,
    val active: Boolean,
)

/**
 * Список выходов звука и тот, через который играет сейчас.
 *
 * Здесь нет ничего придуманного: всё приходит от системы. Показывать
 * «подключённые устройства» списком собственной выдумки было бы ровно тем
 * обманом, которого в приложении быть не должно — поэтому при отказе
 * системы список честно остаётся пустым.
 */
@Singleton
class AudioDevicesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val manager get() = context.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Обновляется при каждом подключении и отключении: наушники вынимают
     * и вставляют чаще, чем открывают настройки, и список, собранный
     * однажды, врал бы уже через минуту.
     *
     * Подписка одна на всё приложение: настройки и плеер читают тот же
     * список, и второй слушатель системы ради этого не нужен.
     */
    val outputs: Flow<List<AudioOutput>> =
        callbackFlow {
            val audio = manager
            if (audio == null) {
                send(emptyList())
                awaitClose { }
                return@callbackFlow
            }
            val callback =
                object : AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) {
                        trySend(read(audio))
                    }

                    override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) {
                        trySend(read(audio))
                    }
                }
            audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
            awaitClose { audio.unregisterAudioDeviceCallback(callback) }
        }.onStart { manager?.let { emit(read(it)) } ?: emit(emptyList()) }
            .shareIn(scope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), replay = 1)

    private fun read(audio: AudioManager): List<AudioOutput> {
        val devices =
            runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }
                .getOrDefault(emptyList())
        val activeIds = activeIds(audio, devices)
        return devices
            .map { device ->
                AudioOutput(
                    id = device.id,
                    name = device.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                    kind = kindOf(device.type),
                    active = device.id in activeIds,
                )
            }.filterNot { it.kind == OutputKind.OTHER }
            // Один тип может приехать несколькими строками (телефон отдаёт
            // и «динамик», и «безопасный динамик»); человеку нужен один.
            .distinctBy { it.kind to it.name }
            .sortedByDescending { it.active }
    }

    /**
     * Какой выход система выбрала для музыки.
     *
     * Спросить прямо можно только с Android 13; ниже действует правило
     * самой системы: подключённая гарнитура перебивает динамик.
     */
    private fun activeIds(audio: AudioManager, devices: List<AudioDeviceInfo>): Set<Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attributes =
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            val routed =
                runCatching { audio.getAudioDevicesForAttributes(attributes) }
                    .getOrDefault(emptyList())
            if (routed.isNotEmpty()) return routed.map { it.id }.toSet()
        }
        val order = listOf(OutputKind.BLUETOOTH, OutputKind.USB, OutputKind.WIRED, OutputKind.HDMI, OutputKind.SPEAKER)
        val chosen =
            order.firstNotNullOfOrNull { kind ->
                devices.firstOrNull { kindOf(it.type) == kind }
            }
        return setOfNotNull(chosen?.id)
    }

    private fun kindOf(type: Int): OutputKind =
        when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
            -> OutputKind.SPEAKER

            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> OutputKind.EARPIECE

            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_AUX_LINE,
            -> OutputKind.WIRED

            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            -> OutputKind.BLUETOOTH

            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_DOCK,
            -> OutputKind.USB

            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC,
            -> OutputKind.HDMI

            AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> OutputKind.CAST

            else -> OutputKind.OTHER
        }

    private companion object {
        /**
         * Держим подписку недолго после последнего читателя: между закрытием
         * настроек и открытием плеера список не надо собирать заново.
         */
        const val SHARE_TIMEOUT_MS = 5_000L
    }
}
