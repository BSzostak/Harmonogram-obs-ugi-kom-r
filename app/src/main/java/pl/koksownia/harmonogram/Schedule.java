package pl.koksownia.harmonogram;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Harmonogram jednej zmiany: data raportu i komory w kolejności z tabeli. */
public final class Schedule {
    public final LocalDate date;
    public final List<Chamber> chambers;

    public Schedule(LocalDate date, List<Chamber> chambers) {
        this.date = date;
        this.chambers = Collections.unmodifiableList(new ArrayList<>(chambers));
    }

    /** Indeks pierwszej niewypchniętej komory albo -1. */
    public int nextIndex() {
        for (int i = 0; i < chambers.size(); i++) {
            if (!chambers.get(i).isDone()) return i;
        }
        return -1;
    }

    /** Indeksy co najwyżej {@code count} najbliższych niewypchniętych komór. */
    public List<Integer> upcoming(int count) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < chambers.size() && out.size() < count; i++) {
            if (!chambers.get(i).isDone()) out.add(i);
        }
        return out;
    }

    public int doneCount() {
        int n = 0;
        for (Chamber c : chambers) if (c.isDone()) n++;
        return n;
    }

    /**
     * Planowane momenty wypychu (ms) dla każdej komory, null gdy godzina jest nieczytelna.
     * Godziny w tabeli idą po kolei, więc gdy godzina „cofa się” (np. 23:50 -> 0:07),
     * przechodzimy na następny dzień.
     */
    public List<Long> plannedMillis() {
        List<Long> out = new ArrayList<>();
        LocalDate day = date;
        LocalTime prev = null;
        for (Chamber c : chambers) {
            LocalTime t = parseTime(c.planTime);
            if (t == null) {
                out.add(null);
                continue;
            }
            if (prev != null && t.isBefore(prev)) day = day.plusDays(1);
            prev = t;
            out.add(LocalDateTime.of(day, t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        return out;
    }

    public Schedule withDate(LocalDate newDate) {
        return new Schedule(newDate, chambers);
    }

    public Schedule withChambers(List<Chamber> newChambers) {
        return new Schedule(date, newChambers);
    }

    public static LocalTime parseTime(String s) {
        String[] parts = s.split(":");
        if (parts.length != 2) return null;
        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return LocalTime.of(h, m);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
