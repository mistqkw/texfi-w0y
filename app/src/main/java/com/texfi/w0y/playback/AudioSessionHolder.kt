package com.texfi.w0y.playback

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Идентификатор аудиосессии плеера.
 *
 * Нужен, чтобы открыть системный эквалайзер именно для нашего звука.
 * Своего эквалайзера в приложении нет сознательно: системный уже умеет
 * пресеты и усиление баса, а второй такой же внутри — лишний код и
 * расхождение с тем, что слышно в других приложениях.
 */
@Singleton
class AudioSessionHolder @Inject constructor() {
    @Volatile
    var sessionId: Int = 0
        internal set

    fun update(id: Int) {
        sessionId = id
    }
}
