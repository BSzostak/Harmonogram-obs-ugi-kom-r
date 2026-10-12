package pl.koksownia.harmonogram

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {
    private const val CHANNEL_ID = "next_chamber"
    private const val NOTIFICATION_ID = 1

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_description)
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun show(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        val schedule = ScheduleStore.load(context)
        if (schedule.chambers.isEmpty()) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        if (!canNotify(context)) return
        ensureChannel(context)

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val upcoming = schedule.upcoming(3)
        val doneCount = schedule.chambers.count { it.done }
        val total = schedule.chambers.size

        if (upcoming.isEmpty()) {
            builder
                .setContentTitle("Wszystkie komory wypchnięte")
                .setContentText("$doneCount z $total")
                .setOngoing(false)
                .addAction(0, context.getString(R.string.action_undo), PushReceiver.pendingIntent(context, PushReceiver.ACTION_UNDO))
        } else {
            val (idx, next) = upcoming.first()
            val later = upcoming.drop(1).joinToString("   ") { (_, c) -> "${c.number} (${c.planTime})" }
            val text = if (later.isEmpty()) "Ostatnia komora" else "Dalej: $later"
            builder
                .setContentTitle("Komora ${next.number}  •  ${next.planTime}")
                .setContentText(text)
                .setSubText("${doneCount + 1}/$total")
                .setOngoing(true)
                .addAction(0, context.getString(R.string.action_pushed), PushReceiver.pendingIntent(context, PushReceiver.ACTION_PUSH))
                .addAction(0, context.getString(R.string.action_undo), PushReceiver.pendingIntent(context, PushReceiver.ACTION_UNDO))

            // Odliczanie do planowanej godziny (po jej przekroczeniu pokazuje opóźnienie ze znakiem minus).
            schedule.plannedMillis(idx)?.let { whenMillis ->
                builder.setWhen(whenMillis)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
            }
        }

        try {
            nm.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Brak uprawnienia do powiadomień – nic nie robimy.
        }
    }
}
