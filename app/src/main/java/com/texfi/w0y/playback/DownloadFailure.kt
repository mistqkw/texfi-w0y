package com.texfi.w0y.playback

import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import com.texfi.w0y.R
import com.texfi.w0y.data.YouTubeRepository
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Почему трек не скачался — понятным словом, а не кодом.
 *
 * Причина хранится рядом с треком и показывается в строке загрузки:
 * «не удалось» без объяснения заставляет гадать, стоит ли пробовать снова.
 */
enum class DownloadFailure(@StringRes val label: Int, val retryable: Boolean) {
    /** YouTube не отдал адрес потока вообще. */
    NO_STREAM(R.string.dl_fail_no_stream, true),

    /** Возраст, регион, удалённое видео — повтор не поможет. */
    RESTRICTED(R.string.dl_fail_restricted, false),

    /** Адрес истёк или был отвергнут, и свежий тоже не взяли. */
    EXPIRED(R.string.dl_fail_expired, true),

    /** Сервер ответил ошибкой. */
    SERVER(R.string.dl_fail_server, true),

    /** Сеть пропала или не ответила. */
    NETWORK(R.string.dl_fail_network, true),

    /** Нет звуковой дорожки в нужном виде. */
    NO_AUDIO(R.string.dl_fail_no_audio, false),

    /** Не хватило места на телефоне. */
    STORAGE(R.string.dl_fail_storage, false),

    /** Скачанный файл оказался неполным. */
    INCOMPLETE(R.string.dl_fail_incomplete, true),

    UNKNOWN(R.string.dl_fail_unknown, true),
    ;

    companion object {
        /** Разбирает цепочку причин исключения загрузчика. */
        @OptIn(UnstableApi::class)
        fun of(error: Throwable?): DownloadFailure {
            var cause: Throwable? = error
            var depth = 0
            while (cause != null && depth++ < 8) {
                val message = cause.message.orEmpty().lowercase()
                when {
                    cause is YouTubeRepository.NoStreamException -> return restrictedOr(message, NO_STREAM)
                    cause is HttpDataSource.InvalidResponseCodeException ->
                        return when (cause.responseCode) {
                            403, 410 -> EXPIRED
                            404 -> RESTRICTED
                            in 500..599, 429 -> SERVER
                            else -> SERVER
                        }
                    cause is UnknownHostException || cause is SocketTimeoutException -> return NETWORK
                    "enospc" in message || "no space" in message -> return STORAGE
                    "no audio" in message || "audio format" in message -> return NO_AUDIO
                    "login_required" in message || ("age" in message && "restrict" in message) -> return RESTRICTED
                    "unplayable" in message || "region" in message || "country" in message -> return RESTRICTED
                }
                // Голая ошибка ввода-вывода без вложенной причины — обрыв связи.
                if (cause is IOException && cause.cause == null) return NETWORK
                cause = cause.cause
            }
            return UNKNOWN
        }

        private fun restrictedOr(message: String, fallback: DownloadFailure): DownloadFailure =
            if ("age" in message || "region" in message || "login" in message) RESTRICTED else fallback
    }
}
