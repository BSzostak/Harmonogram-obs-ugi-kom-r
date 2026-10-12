package pl.koksownia.harmonogram

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/** Widżet na ekranie głównym z trzema najbliższymi komorami. */
class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = buildViews(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        private val ROWS = listOf(
            Triple(R.id.row1, R.id.row1Chamber, R.id.row1Time),
            Triple(R.id.row2, R.id.row2Chamber, R.id.row2Time),
            Triple(R.id.row3, R.id.row3Chamber, R.id.row3Time),
        )

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val schedule = ScheduleStore.load(context)
            val upcoming = schedule.upcoming(3)
            val views = RemoteViews(context.packageName, R.layout.widget_schedule)

            val title = when {
                schedule.chambers.isEmpty() -> context.getString(R.string.widget_empty)
                upcoming.isEmpty() -> "Wszystkie wypchnięte (${schedule.chambers.size})"
                else -> "${context.getString(R.string.widget_title)}  " +
                    "${schedule.chambers.count { it.done }}/${schedule.chambers.size}"
            }
            views.setTextViewText(R.id.widgetTitle, title)

            ROWS.forEachIndexed { i, (row, chamberId, timeId) ->
                val item = upcoming.getOrNull(i)?.second
                if (item == null) {
                    views.setViewVisibility(row, View.INVISIBLE)
                } else {
                    views.setViewVisibility(row, View.VISIBLE)
                    views.setTextViewText(chamberId, item.number)
                    views.setTextViewText(timeId, item.planTime)
                }
            }

            val open = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, open)
            return views
        }
    }
}
