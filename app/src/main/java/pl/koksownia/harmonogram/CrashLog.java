package pl.koksownia.harmonogram;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Zapisuje treść ostatniego błędu, żeby po ponownym uruchomieniu można ją było pokazać i przesłać. */
public final class CrashLog {
    private static final String PREFS = "crash";
    private static final String KEY = "last";
    private static boolean installed;

    private CrashLog() {
    }

    public static synchronized void install(Context context) {
        if (installed) return;
        installed = true;
        Context app = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            try {
                prefs(app).edit().putString(KEY, describe(app, e)).commit();
            } catch (Throwable ignored) {
                // zapis błędu nie może sam wywołać kolejnego błędu
            }
            if (previous != null) previous.uncaughtException(thread, e);
        });
    }

    /** Opis błędu z urządzeniem, wersją aplikacji i pełnym stosem wywołań (z przyczynami). */
    public static String describe(Context context, Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + "), "
                + Build.MANUFACTURER + " " + Build.MODEL + ", wersja " + BuildConfigInfo.version(context)
                + "\n\n" + sw;
    }

    /** Zwraca i usuwa zapisany błąd albo null. */
    public static String take(Context context) {
        SharedPreferences p = prefs(context);
        String text = p.getString(KEY, null);
        if (text != null) p.edit().remove(KEY).apply();
        return text;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Wersja aplikacji bez włączania generowania BuildConfig. */
    private static final class BuildConfigInfo {
        static String version(Context c) {
            try {
                return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "?";
            }
        }
    }
}
