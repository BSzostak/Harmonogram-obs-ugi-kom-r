package pl.koksownia.harmonogram.ocr;

import java.util.Objects;

/** Jeden wiersz harmonogramu odczytany ze zdjęcia. */
public final class ParsedRow {
    public final String chamber;
    public final String planTime;

    public ParsedRow(String chamber, String planTime) {
        this.chamber = chamber;
        this.planTime = planTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParsedRow)) return false;
        ParsedRow r = (ParsedRow) o;
        return chamber.equals(r.chamber) && planTime.equals(r.planTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chamber, planTime);
    }

    @Override
    public String toString() {
        return chamber + " " + planTime;
    }
}
