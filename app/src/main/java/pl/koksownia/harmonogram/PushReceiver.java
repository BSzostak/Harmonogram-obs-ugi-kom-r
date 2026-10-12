package pl.koksownia.harmonogram;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Obsługuje przyciski „Wypchnięto” i „Cofnij” z powiadomienia. */
public class PushReceiver extends BroadcastReceiver {
    public static final String ACTION_PUSH = "pl.koksownia.harmonogram.PUSH";
    public static final String ACTION_UNDO = "pl.koksownia.harmonogram.UNDO";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_PUSH.equals(action)) {
            ScheduleStore.markNextDone(context);
        } else if (ACTION_UNDO.equals(action)) {
            ScheduleStore.undoLast(context);
        } else {
            return;
        }
        ScheduleUpdater.refresh(context);
    }

    public static PendingIntent pendingIntent(Context context, String action) {
        Intent intent = new Intent(context, PushReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(context, action.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
