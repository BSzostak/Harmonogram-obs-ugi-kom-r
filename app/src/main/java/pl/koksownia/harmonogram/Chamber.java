package pl.koksownia.harmonogram;

/** Komora w harmonogramie. {@code doneAt} to czas zatwierdzenia wypchnięcia (ms) albo null. */
public final class Chamber {
    public final String number;
    public final String planTime;
    public final Long doneAt;

    public Chamber(String number, String planTime, Long doneAt) {
        this.number = number;
        this.planTime = planTime;
        this.doneAt = doneAt;
    }

    public Chamber(String number, String planTime) {
        this(number, planTime, null);
    }

    public boolean isDone() {
        return doneAt != null;
    }

    public Chamber withDoneAt(Long when) {
        return new Chamber(number, planTime, when);
    }

    public Chamber withData(String newNumber, String newPlanTime) {
        return new Chamber(newNumber, newPlanTime, doneAt);
    }
}
