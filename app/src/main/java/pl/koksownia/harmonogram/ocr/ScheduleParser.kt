package pl.koksownia.harmonogram.ocr

import java.time.LocalDate

/** Słowo rozpoznane przez OCR wraz z położeniem na zdjęciu (w pikselach). */
data class OcrToken(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val cx: Float get() = (left + right) / 2f
    val cy: Float get() = (top + bottom) / 2f
    val height: Float get() = bottom - top
}

data class ParsedRow(val chamber: String, val planTime: String)

data class ParseResult(val rows: List<ParsedRow>, val date: LocalDate?)

/**
 * Wyciąga z wyniku OCR tabeli „Raport Obsługi Komór” pary: numer komory + planowana godzina wypychu.
 *
 * Wiersz tabeli wygląda tak: `Lp | Nr komory | Plan. godz. wypch. | Plan. czas koks. | Ostatnio obsadz. | ...`
 * Szukamy więc trzycyfrowego numeru komory i pierwszej komórki na prawo od niego w tym samym wierszu,
 * która jest godziną (H:MM). Dzięki temu nie mylimy jej z kolejnymi kolumnami (29:42, 0:28).
 */
object ScheduleParser {

    private val TIME = Regex("^(\\d{1,2})[:.;,](\\d{2})$")
    private val COMPACT_TIME = Regex("^(\\d{1,2})(\\d{2})$")
    private val CHAMBER = Regex("^\\d{3}$")
    private val DATE = Regex("(20\\d{2})-(\\d{1,2})-(\\d{1,2})")
    private val LINE_ROW = Regex("(?<![\\d:.])(\\d{3})\\s+(\\d{1,2})[:.](\\d{2})(?![\\d:])")

    /** Poprawia typowe pomyłki OCR w liczbach i usuwa śmieci (np. „157'” -> „157”, „6:1O” -> „6:10”). */
    fun normalize(raw: String): String {
        if (raw.none { it.isDigit() }) return ""
        val mapped = buildString {
            for (c in raw) {
                append(
                    when (c) {
                        'O', 'o', 'Q', 'D' -> '0'
                        'I', 'l', '|', '!', 'i' -> '1'
                        'S' -> '5'
                        'B' -> '8'
                        else -> c
                    }
                )
            }
        }
        return mapped.filter { it.isDigit() || it in ":.;," }.trim(':', '.', ';', ',')
    }

    /** Zwraca godzinę w formacie „H:MM” lub null. [allowCompact] dopuszcza zgubiony dwukropek („610”). */
    fun parseTime(normalized: String, allowCompact: Boolean = false): String? {
        val m = TIME.matchEntire(normalized)
            ?: (if (allowCompact) COMPACT_TIME.matchEntire(normalized) else null)
            ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h > 23 || min > 59) return null
        return "%d:%02d".format(h, min)
    }

    fun parse(tokens: List<OcrToken>, lines: List<String> = emptyList()): ParseResult {
        val fullText = (lines.ifEmpty { tokens.map { it.text } }).joinToString("\n")
        val date = findDate(fullText)

        var rows = parseTokens(tokens)
        if (rows.isEmpty()) rows = parseLines(lines)
        return ParseResult(rows, date)
    }

    private fun parseTokens(tokens: List<OcrToken>): List<ParsedRow> {
        val norm = tokens.map { normalize(it.text) }
        val heights = tokens.map { it.height }.filter { it > 0 }.sorted()
        val medianH = if (heights.isEmpty()) 1f else heights[heights.size / 2]

        val chamberIdx = tokens.indices
            .filter { CHAMBER.matches(norm[it]) && norm[it].toInt() >= 100 }
            .sortedBy { tokens[it].left }

        val consumed = BooleanArray(tokens.size)
        val found = mutableListOf<Pair<Float, ParsedRow>>()

        for (ci in chamberIdx) {
            if (consumed[ci]) continue
            val c = tokens[ci]
            val h = maxOf(medianH, c.height)

            // Sąsiedzi na prawo w tym samym wierszu, od najbliższego. Tolerancja w pionie rośnie
            // z odległością, żeby wybaczyć lekko przekrzywione zdjęcie.
            val neighbours = tokens.indices
                .filter { j ->
                    if (j == ci || norm[j].isEmpty()) return@filter false
                    val t = tokens[j]
                    if (t.cx <= c.right || t.left <= c.cx) return@filter false
                    val dx = t.left - c.right
                    kotlin.math.abs(t.cy - c.cy) <= 0.6f * h + 0.08f * dx
                }
                .sortedBy { tokens[it].left }

            if (neighbours.isEmpty()) continue
            val first = neighbours[0]
            var time = parseTime(norm[first], allowCompact = true)
            var used = listOf(first)
            if (time == null && neighbours.size >= 2) {
                // OCR mógł rozbić godzinę na dwa słowa, np. „6:” i „10”.
                val second = neighbours[1]
                time = parseTime(norm[first] + ":" + norm[second].trimStart(':'))
                    ?: parseTime(norm[first] + norm[second])
                used = listOf(first, second)
            }
            if (time == null) continue

            consumed[ci] = true
            used.forEach { consumed[it] = true }
            found += c.cy to ParsedRow(norm[ci], time)
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    private fun parseLines(lines: List<String>): List<ParsedRow> =
        lines.flatMap { line ->
            LINE_ROW.findAll(normalizeLine(line)).mapNotNull { m ->
                val time = parseTime("${m.groupValues[2]}:${m.groupValues[3]}") ?: return@mapNotNull null
                if (m.groupValues[1].toInt() < 100) null else ParsedRow(m.groupValues[1], time)
            }.toList()
        }

    private fun normalizeLine(line: String): String =
        line.split(Regex("\\s+")).joinToString(" ") { w -> normalize(w).ifEmpty { "_" } }

    private fun findDate(text: String): LocalDate? {
        val m = DATE.find(text) ?: return null
        return try {
            LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        } catch (e: Exception) {
            null
        }
    }
}
