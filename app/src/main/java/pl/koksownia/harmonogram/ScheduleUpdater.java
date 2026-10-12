package pl.koksownia.harmonogram;

import android.content.Context;

/** Odświeża wszystko, co pokazuje harmonogram poza aplikacją: widżet i powiadomienie. */
public final class ScheduleUpdater {
    private ScheduleUpdater() {
    }

    public static void refresh(Context context) {
        ScheduleWidgetProvider.updateAll(context);
        NotificationHelper.show(context);
    }
}
