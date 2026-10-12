package pl.koksownia.harmonogram.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ScheduleParserTest {

    private static final float[] XS = {330f, 400f, 500f, 600f, 700f};

    /** Buduje wiersz jak na zdjęciu raportu: kolumny co ~100 px, opcjonalny przechył. */
    private static List<OcrToken> row(float y, float skew, String... cells) {
        List<OcrToken> out = new ArrayList<>();
        for (int i = 0; i < cells.length; i++) {
            float x = XS[i];
            float dy = skew * (x - 330f);
            out.add(new OcrToken(cells[i], x, y - 12 + dy, x + 20f * cells[i].length(), y + 12 + dy));
        }
        return out;
    }

    @Test
    public void parsesTableFromReport() {
        List<OcrToken> tokens = new ArrayList<>();
        tokens.add(new OcrToken("13", 300f, 500f, 330f, 525f));
        tokens.add(new OcrToken("2026-10-12", 510f, 600f, 690f, 625f));
        tokens.addAll(row(770f, 0f, "1", "125", "6:10", "29:42", "0:28"));
        tokens.addAll(row(810f, 0f, "2", "135", "6:27", "29:29", "0:58"));
        tokens.addAll(row(850f, 0f, "3", "145", "6:44", "29:05", "1:39"));
        tokens.addAll(row(1200f, 0f, "12", "137", "11:00", "30:52", "4:08"));
        tokens.addAll(row(1280f, 0f, "14", "157'", "11:34", "30:52", "4:42"));
        tokens.addAll(row(1580f, 0f, "", "", "21", "29:55")); // wiersz sumy

        ParseResult result = ScheduleParser.parse(tokens, Collections.emptyList());

        assertEquals(LocalDate.of(2026, 10, 12), result.date);
        assertEquals(Arrays.asList(
                new ParsedRow("125", "6:10"),
                new ParsedRow("135", "6:27"),
                new ParsedRow("145", "6:44"),
                new ParsedRow("137", "11:00"),
                new ParsedRow("157", "11:34")), result.rows);
    }

    @Test
    public void toleratesSkewAndOcrMistakes() {
        List<OcrToken> tokens = new ArrayList<>();
        tokens.addAll(row(770f, 0.03f, "1", "1O7", "8:26", "29:10", "3:16"));
        tokens.addAll(row(810f, 0.03f, "2", "117", "843", "29:08", "3:35"));
        tokens.addAll(row(850f, 0.03f, "3", "127", "9.00", "29:06", "3:54"));

        assertEquals(Arrays.asList(
                new ParsedRow("107", "8:26"),
                new ParsedRow("117", "8:43"),
                new ParsedRow("127", "9:00")),
                ScheduleParser.parse(tokens, Collections.emptyList()).rows);
    }

    @Test
    public void fallsBackToLineText() {
        List<ParsedRow> rows = ScheduleParser.parse(Collections.emptyList(),
                Arrays.asList("1 125 6:10 29:42 0:28", "2 135 6:27 29:29 0:58")).rows;
        assertEquals(Arrays.asList(new ParsedRow("125", "6:10"), new ParsedRow("135", "6:27")), rows);
    }

    @Test
    public void parseTimeRejectsInvalid() {
        assertNull(ScheduleParser.parseTime("29:42"));
        assertNull(ScheduleParser.parseTime("6:75"));
        assertEquals("6:10", ScheduleParser.parseTime("06:10"));
        assertEquals("13:33", ScheduleParser.parseTime("1333", true));
    }
}
