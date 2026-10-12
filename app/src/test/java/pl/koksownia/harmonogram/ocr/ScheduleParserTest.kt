package pl.koksownia.harmonogram.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ScheduleParserTest {

    /** Buduje wiersz jak na zdjęciu raportu: kolumny co ~100 px, lekki przechył w dół w prawo. */
    private fun row(y: Float, vararg cells: String, skew: Float = 0f): List<OcrToken> {
        val xs = listOf(330f, 400f, 500f, 600f, 700f)
        return cells.mapIndexed { i, text ->
            val x = xs[i]
            val dy = skew * (x - 330f)
            OcrToken(text, x, y - 12 + dy, x + 20f * text.length, y + 12 + dy)
        }
    }

    @Test
    fun parsesTableFromReport() {
        val tokens = mutableListOf<OcrToken>()
        tokens += OcrToken("13", 300f, 500f, 330f, 525f)
        tokens += OcrToken("2026-10-12", 510f, 600f, 690f, 625f)
        tokens += row(770f, "1", "125", "6:10", "29:42", "0:28")
        tokens += row(810f, "2", "135", "6:27", "29:29", "0:58")
        tokens += row(850f, "3", "145", "6:44", "29:05", "1:39")
        tokens += row(1200f, "12", "137", "11:00", "30:52", "4:08")
        tokens += row(1280f, "14", "157'", "11:34", "30:52", "4:42")
        tokens += row(1580f, "", "", "21", "29:55") // wiersz sumy

        val result = ScheduleParser.parse(tokens)

        assertEquals(LocalDate.of(2026, 10, 12), result.date)
        assertEquals(
            listOf(
                ParsedRow("125", "6:10"),
                ParsedRow("135", "6:27"),
                ParsedRow("145", "6:44"),
                ParsedRow("137", "11:00"),
                ParsedRow("157", "11:34"),
            ),
            result.rows,
        )
    }

    @Test
    fun toleratesSkewAndOcrMistakes() {
        val tokens = mutableListOf<OcrToken>()
        tokens += row(770f, "1", "1O7", "8:26", "29:10", "3:16", skew = 0.03f)
        tokens += row(810f, "2", "117", "843", "29:08", "3:35", skew = 0.03f)
        tokens += row(850f, "3", "127", "9.00", "29:06", "3:54", skew = 0.03f)

        val rows = ScheduleParser.parse(tokens).rows

        assertEquals(
            listOf(ParsedRow("107", "8:26"), ParsedRow("117", "8:43"), ParsedRow("127", "9:00")),
            rows,
        )
    }

    @Test
    fun fallsBackToLineText() {
        val rows = ScheduleParser.parse(
            tokens = emptyList(),
            lines = listOf("1 125 6:10 29:42 0:28", "2 135 6:27 29:29 0:58"),
        ).rows
        assertEquals(listOf(ParsedRow("125", "6:10"), ParsedRow("135", "6:27")), rows)
    }

    @Test
    fun parseTimeRejectsInvalid() {
        assertNull(ScheduleParser.parseTime("29:42"))
        assertNull(ScheduleParser.parseTime("6:75"))
        assertEquals("6:10", ScheduleParser.parseTime("06:10"))
        assertEquals("13:33", ScheduleParser.parseTime("1333", allowCompact = true))
    }
}
