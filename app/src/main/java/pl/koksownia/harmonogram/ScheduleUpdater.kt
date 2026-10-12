package pl.koksownia.harmonogram

import android.content.Context

/** Odświeża wszystko, co pokazuje harmonogram poza aplikacją: widżet i powiadomienie. */
object ScheduleUpdater {
    fun refresh(context: Context) {
        ScheduleWidgetProvider.updateAll(context)
        NotificationHelper.show(context)
    }
}
