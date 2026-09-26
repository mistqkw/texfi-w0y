package com.texfi.w0y.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews
import com.texfi.w0y.MainActivity
import com.texfi.w0y.R
import com.texfi.w0y.playback.W0yPlayerService
import java.io.File

/**
 * Виджет «что играет».
 *
 * Состояние берётся не у плеера, а из [WidgetState]: систему просят
 * перерисовать виджет и тогда, когда процесс приложения уже убит, и
 * спросить живой плеер в этот момент не у кого.
 */
class W0yWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val views = build(context)
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
    }

    internal companion object {
        /** Перерисовать все размещённые виджеты по последнему состоянию. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, W0yWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        private fun build(context: Context): RemoteViews {
            val state = WidgetState.read(context)
            val views = RemoteViews(context.packageName, R.layout.widget_player)

            if (state.controllable) {
                views.setTextViewText(R.id.widget_title, state.title)
                views.setTextViewText(R.id.widget_artist, state.artist)
                views.setViewVisibility(R.id.widget_artist, View.VISIBLE)
                views.setViewVisibility(R.id.widget_controls, View.VISIBLE)
                views.setImageViewResource(
                    R.id.widget_toggle,
                    if (state.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                )
            } else {
                // Честное пустое состояние: играть нечего или плеер с тех
                // пор перезапускался — кнопки, которые ничего не сделают,
                // здесь не рисуются.
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_idle))
                views.setViewVisibility(R.id.widget_artist, View.GONE)
                views.setViewVisibility(R.id.widget_controls, View.GONE)
            }

            val cover = state.coverPath?.let { path -> File(path).takeIf { it.exists() } }
            val bitmap = cover?.let { BitmapFactory.decodeFile(it.path) }
            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_cover, bitmap)
            } else {
                views.setImageViewResource(R.id.widget_cover, R.drawable.widget_cover)
            }

            views.setOnClickPendingIntent(R.id.widget_body, openApp(context))
            views.setOnClickPendingIntent(R.id.widget_previous, transport(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            views.setOnClickPendingIntent(R.id.widget_toggle, transport(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_next, transport(context, KeyEvent.KEYCODE_MEDIA_NEXT))
            return views
        }

        private fun openApp(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /**
         * Кнопка управления — медиа-клавиша прямо в сервис.
         *
         * Именно так виджет разговаривает с уже живым плеером: свой
         * контроллер здесь заводить не на чем, а `MediaSessionService`
         * разбирает `ACTION_MEDIA_BUTTON` сам. Кнопки показываются только
         * когда трек загружен, то есть сервис уже поднят, и запуск его из
         * фона не требуется.
         */
        private fun transport(context: Context, keyCode: Int): PendingIntent {
            val intent =
                Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setComponent(ComponentName(context, W0yPlayerService::class.java))
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            return PendingIntent.getService(
                context,
                keyCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
