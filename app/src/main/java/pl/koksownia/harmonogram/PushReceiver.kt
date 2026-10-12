package pl.koksownia.harmonogram

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Obsługuje przyciski „Wypchnięto” i „Cofnij” z powiadomienia. */
class PushReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PUSH -> ScheduleStore.markNextDone(context)
            ACTION_UNDO -> ScheduleStore.undoLast(context)
            else -> return
        }
        ScheduleUpdater.refresh(context)
    }

    companion object {
        const val ACTION_PUSH = "pl.koksownia.harmonogram.PUSH"
        const val ACTION_UNDO = "pl.koksownia.harmonogram.UNDO"

        fun pendingIntent(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                Intent(context, PushReceiver::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
