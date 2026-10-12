package pl.koksownia.harmonogram;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Przywraca powiadomienie i widżet po restarcie telefonu lub aktualizacji aplikacji. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            ScheduleUpdater.refresh(context);
        }
    }
}
