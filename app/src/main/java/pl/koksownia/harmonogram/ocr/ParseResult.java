package pl.koksownia.harmonogram.ocr;

import java.time.LocalDate;
import java.util.List;

public final class ParseResult {
    public final List<ParsedRow> rows;
    /** Data raportu odczytana ze zdjęcia albo null. */
    public final LocalDate date;

    public ParseResult(List<ParsedRow> rows, LocalDate date) {
        this.rows = rows;
        this.date = date;
    }
}
