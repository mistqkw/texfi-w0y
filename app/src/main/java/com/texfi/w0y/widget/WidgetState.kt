package com.texfi.w0y.widget

import android.content.Context

/**
 * Что показывает виджет — отдельно от живого плеера.
 *
 * Виджет обновляется системой и тогда, когда процесс приложения давно
 * убит: спрашивать плеер в этот момент не у кого. Поэтому последнее
 * известное состояние лежит в крошечном SharedPreferences-файле и
 * читается синхронно прямо в `onUpdate`.
 */
internal data class WidgetState(
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    /** Файл обложки во внутреннем хранилище; null — обложки нет. */
    val coverPath: String? = null,
    /**
     * Процесс, который это состояние записал.
     *
     * По нему видно, жив ли ещё тот плеер. Виджет рисуется в нашем же
     * процессе — система его для этого и поднимает, — поэтому чужой pid
     * означает ровно одно: приложение с тех пор перезапускалось, а
     * управлять нечем.
     */
    val pid: Int = 0,
) {
    val hasSong: Boolean get() = title.isNotBlank()

    /** Можно ли показывать кнопки: плеер, записавший состояние, ещё жив. */
    val controllable: Boolean get() = hasSong && pid == android.os.Process.myPid()

    companion object {
        private const val FILE = "w0y_widget"

        fun read(context: Context): WidgetState {
            val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return WidgetState(
                title = prefs.getString("title", "").orEmpty(),
                artist = prefs.getString("artist", "").orEmpty(),
                playing = prefs.getBoolean("playing", false),
                coverPath = prefs.getString("cover", null),
                pid = prefs.getInt("pid", 0),
            )
        }

        fun write(context: Context, state: WidgetState) {
            context
                .getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit()
                .putString("title", state.title)
                .putString("artist", state.artist)
                .putBoolean("playing", state.playing)
                .putString("cover", state.coverPath)
                .putInt("pid", state.pid)
                .apply()
        }
    }
}
