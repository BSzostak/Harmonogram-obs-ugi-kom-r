package pl.koksownia.harmonogram

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class Chamber(
    val number: String,
    val planTime: String,
    val doneAt: Long? = null,
) {
    val done: Boolean get() = doneAt != null
}

data class Schedule(val date: LocalDate, val chambers: List<Chamber>) {

    /** Indeks pierwszej niewypchniętej komory (kolejność z tabeli). */
    val nextIndex: Int? get() = chambers.indexOfFirst { !it.done }.takeIf { it >= 0 }

    fun upcoming(count: Int): List<Pair<Int, Chamber>> =
        chambers.withIndex().filter { !it.value.done }.take(count).map { it.index to it.value }

    /**
     * Planowane momenty wypychu. Godziny w tabeli idą po kolei, więc gdy godzina „cofa się”
     * (np. 23:50 -> 0:07), przechodzimy na następny dzień.
     */
    fun plannedDateTimes(): List<LocalDateTime?> {
        var day = date
        var prev: LocalTime? = null
        return chambers.map { c ->
            val t = parseTime(c.planTime) ?: return@map null
            if (prev != null && t.isBefore(prev)) day = day.plusDays(1)
            prev = t
            LocalDateTime.of(day, t)
        }
    }

    fun plannedMillis(index: Int): Long? =
        plannedDateTimes().getOrNull(index)?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    companion object {
        fun parseTime(s: String): LocalTime? {
            val parts = s.split(':')
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return LocalTime.of(h, m)
        }
    }
}

/** Trwałe przechowywanie harmonogramu w SharedPreferences (JSON). */
object ScheduleStore {
    const val PREFS = "schedule"
    private const val KEY_DATE = "date"
    private const val KEY_ITEMS = "items"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(context: Context): Schedule {
        val p = prefs(context)
        val date = p.getString(KEY_DATE, null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
        val items = mutableListOf<Chamber>()
        val json = p.getString(KEY_ITEMS, null)
        if (json != null) {
            runCatching {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    items += Chamber(
                        number = o.getString("n"),
                        planTime = o.getString("t"),
                        doneAt = if (o.has("d")) o.getLong("d") else null,
                    )
                }
            }
        }
        return Schedule(date, items)
    }

    @Synchronized
    fun save(context: Context, schedule: Schedule) {
        val arr = JSONArray()
        schedule.chambers.forEach { c ->
            arr.put(JSONObject().apply {
                put("n", c.number)
                put("t", c.planTime)
                c.doneAt?.let { put("d", it) }
            })
        }
        prefs(context).edit()
            .putString(KEY_DATE, schedule.date.toString())
            .putString(KEY_ITEMS, arr.toString())
            .commit()
    }

    @Synchronized
    fun update(context: Context, transform: (Schedule) -> Schedule): Schedule {
        val updated = transform(load(context))
        save(context, updated)
        return updated
    }

    /** Oznacza następną komorę jako wypchniętą. Zwraca tę komorę albo null, gdy nie ma już żadnej. */
    fun markNextDone(context: Context): Chamber? {
        var marked: Chamber? = null
        update(context) { s ->
            val idx = s.nextIndex ?: return@update s
            val list = s.chambers.toMutableList()
            list[idx] = list[idx].copy(doneAt = System.currentTimeMillis())
            marked = list[idx]
            s.copy(chambers = list)
        }
        return marked
    }

    /** Cofa ostatnie wypchnięcie (najpóźniej zatwierdzona komora). */
    fun undoLast(context: Context): Chamber? {
        var undone: Chamber? = null
        update(context) { s ->
            val idx = s.chambers.withIndex()
                .filter { it.value.done }
                .maxByOrNull { it.value.doneAt ?: 0L }
                ?.index ?: return@update s
            val list = s.chambers.toMutableList()
            list[idx] = list[idx].copy(doneAt = null)
            undone = list[idx]
            s.copy(chambers = list)
        }
        return undone
    }
}
