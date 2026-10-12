package pl.koksownia.harmonogram;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

/** Widżet na ekranie głównym z trzema najbliższymi komorami. */
public class ScheduleWidgetProvider extends AppWidgetProvider {

    private static final int[][] ROWS = {
            {R.id.row1, R.id.row1Chamber, R.id.row1Time},
            {R.id.row2, R.id.row2Chamber, R.id.row2Time},
            {R.id.row3, R.id.row3Chamber, R.id.row3Time},
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        RemoteViews views = buildViews(context);
        for (int id : ids) manager.updateAppWidget(id, views);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ScheduleWidgetProvider.class));
        if (ids.length == 0) return;
        RemoteViews views = buildViews(context);
        for (int id : ids) manager.updateAppWidget(id, views);
    }

    private static RemoteViews buildViews(Context context) {
        Schedule schedule = ScheduleStore.load(context);
        List<Integer> upcoming = schedule.upcoming(3);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_schedule);

        String title;
        if (schedule.chambers.isEmpty()) {
            title = context.getString(R.string.widget_empty);
        } else if (upcoming.isEmpty()) {
            title = "Wszystkie wypchnięte (" + schedule.chambers.size() + ")";
        } else {
            title = context.getString(R.string.widget_title) + "  "
                    + schedule.doneCount() + "/" + schedule.chambers.size();
        }
        views.setTextViewText(R.id.widgetTitle, title);

        for (int i = 0; i < ROWS.length; i++) {
            if (i < upcoming.size()) {
                Chamber c = schedule.chambers.get(upcoming.get(i));
                views.setViewVisibility(ROWS[i][0], View.VISIBLE);
                views.setTextViewText(ROWS[i][1], c.number);
                views.setTextViewText(ROWS[i][2], c.planTime);
            } else {
                views.setViewVisibility(ROWS[i][0], View.INVISIBLE);
            }
        }

        PendingIntent open = PendingIntent.getActivity(context, 1,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widgetRoot, open);
        return views;
    }
}
