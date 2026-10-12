package pl.koksownia.harmonogram

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Przywraca powiadomienie i widżet po restarcie telefonu lub aktualizacji aplikacji. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ScheduleUpdater.refresh(context)
        }
    }
}
