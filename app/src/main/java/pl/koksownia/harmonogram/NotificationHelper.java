package pl.koksownia.harmonogram;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

public final class NotificationHelper {
    private static final String CHANNEL_ID = "next_chamber";
    private static final int NOTIFICATION_ID = 1;

    private NotificationHelper() {
    }

    public static void ensureChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(context.getString(R.string.channel_description));
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    public static boolean canNotify(Context context) {
        return Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void show(Context context) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        Schedule schedule = ScheduleStore.load(context);
        if (schedule.chambers.isEmpty()) {
            nm.cancel(NOTIFICATION_ID);
            return;
        }
        if (!canNotify(context)) return;
        ensureChannel(context);

        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openApp = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openApp)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        List<Integer> upcoming = schedule.upcoming(3);
        int done = schedule.doneCount();
        int total = schedule.chambers.size();
        PendingIntent undo = PushReceiver.pendingIntent(context, PushReceiver.ACTION_UNDO);

        if (upcoming.isEmpty()) {
            builder.setContentTitle("Wszystkie komory wypchnięte")
                    .setContentText(done + " z " + total)
                    .setOngoing(false)
                    .addAction(0, context.getString(R.string.action_undo), undo);
        } else {
            int idx = upcoming.get(0);
            Chamber next = schedule.chambers.get(idx);
            StringBuilder later = new StringBuilder();
            for (int i = 1; i < upcoming.size(); i++) {
                Chamber c = schedule.chambers.get(upcoming.get(i));
                if (later.length() > 0) later.append("   ");
                later.append(c.number).append(" (").append(c.planTime).append(')');
            }
            builder.setContentTitle("Komora " + next.number + "  •  " + next.planTime)
                    .setContentText(later.length() == 0 ? "Ostatnia komora" : "Dalej: " + later)
                    .setSubText((done + 1) + "/" + total)
                    .setOngoing(true)
                    .addAction(0, context.getString(R.string.action_pushed),
                            PushReceiver.pendingIntent(context, PushReceiver.ACTION_PUSH))
                    .addAction(0, context.getString(R.string.action_undo), undo);

            // Odliczanie do planowanej godziny (po jej przekroczeniu pokazuje opóźnienie ze znakiem minus).
            Long when = schedule.plannedMillis().get(idx);
            if (when != null) {
                builder.setWhen(when)
                        .setShowWhen(true)
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true);
            }
        }

        try {
            nm.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
            // Brak uprawnienia do powiadomień.
        }
    }
}
