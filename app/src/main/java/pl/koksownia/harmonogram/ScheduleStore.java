package pl.koksownia.harmonogram;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Trwałe przechowywanie harmonogramu w SharedPreferences (JSON). */
public final class ScheduleStore {
    public static final String PREFS = "schedule";
    private static final String KEY_DATE = "date";
    private static final String KEY_ITEMS = "items";

    public interface Transform {
        Schedule apply(Schedule s);
    }

    private ScheduleStore() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized Schedule load(Context context) {
        SharedPreferences p = prefs(context);
        LocalDate date = LocalDate.now();
        String dateStr = p.getString(KEY_DATE, null);
        if (dateStr != null) {
            try {
                date = LocalDate.parse(dateStr);
            } catch (Exception ignored) {
                // zostaje dzisiejsza data
            }
        }
        List<Chamber> items = new ArrayList<>();
        String json = p.getString(KEY_ITEMS, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    items.add(new Chamber(o.getString("n"), o.getString("t"),
                            o.has("d") ? o.getLong("d") : null));
                }
            } catch (JSONException ignored) {
                // uszkodzone dane – pusty harmonogram
            }
        }
        return new Schedule(date, items);
    }

    public static synchronized void save(Context context, Schedule schedule) {
        JSONArray arr = new JSONArray();
        try {
            for (Chamber c : schedule.chambers) {
                JSONObject o = new JSONObject();
                o.put("n", c.number);
                o.put("t", c.planTime);
                if (c.doneAt != null) o.put("d", c.doneAt.longValue());
                arr.put(o);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        prefs(context).edit()
                .putString(KEY_DATE, schedule.date.toString())
                .putString(KEY_ITEMS, arr.toString())
                .commit();
    }

    public static synchronized Schedule update(Context context, Transform transform) {
        Schedule updated = transform.apply(load(context));
        save(context, updated);
        return updated;
    }

    /** Oznacza następną komorę jako wypchniętą. Zwraca tę komorę albo null, gdy nie ma już żadnej. */
    public static synchronized Chamber markNextDone(Context context) {
        Schedule s = load(context);
        int idx = s.nextIndex();
        if (idx < 0) return null;
        List<Chamber> list = new ArrayList<>(s.chambers);
        Chamber marked = list.get(idx).withDoneAt(System.currentTimeMillis());
        list.set(idx, marked);
        save(context, s.withChambers(list));
        return marked;
    }

    /** Cofa ostatnie wypchnięcie (najpóźniej zatwierdzona komora). */
    public static synchronized Chamber undoLast(Context context) {
        Schedule s = load(context);
        int idx = -1;
        long latest = Long.MIN_VALUE;
        for (int i = 0; i < s.chambers.size(); i++) {
            Long d = s.chambers.get(i).doneAt;
            if (d != null && d >= latest) {
                latest = d;
                idx = i;
            }
        }
        if (idx < 0) return null;
        List<Chamber> list = new ArrayList<>(s.chambers);
        Chamber undone = list.get(idx).withDoneAt(null);
        list.set(idx, undone);
        save(context, s.withChambers(list));
        return undone;
    }
}
