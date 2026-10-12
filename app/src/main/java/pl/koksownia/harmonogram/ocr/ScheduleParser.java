package pl.koksownia.harmonogram.ocr;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wyciąga z wyniku OCR tabeli „Raport Obsługi Komór” pary: numer komory + planowana godzina wypychu.
 *
 * <p>Wiersz tabeli wygląda tak: {@code Lp | Nr komory | Plan. godz. wypch. | Plan. czas koks. | Ostatnio obsadz. | ...}
 * Szukamy więc trzycyfrowego numeru komory i pierwszej komórki na prawo od niego w tym samym wierszu,
 * która jest godziną (H:MM). Dzięki temu nie mylimy jej z kolejnymi kolumnami (29:42, 0:28).
 */
public final class ScheduleParser {

    private static final Pattern TIME = Pattern.compile("^(\\d{1,2})[:.;,](\\d{2})$");
    private static final Pattern COMPACT_TIME = Pattern.compile("^(\\d{1,2})(\\d{2})$");
    private static final Pattern CHAMBER = Pattern.compile("^\\d{3}$");
    private static final Pattern DATE = Pattern.compile("(20\\d{2})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern LINE_ROW =
            Pattern.compile("(?<![\\d:.])(\\d{3})\\s+(\\d{1,2})[:.](\\d{2})(?![\\d:])");

    private ScheduleParser() {
    }

    /** Poprawia typowe pomyłki OCR w liczbach i usuwa śmieci (np. „157'” -> „157”, „6:1O” -> „6:10”). */
    public static String normalize(String raw) {
        boolean hasDigit = false;
        for (int i = 0; i < raw.length(); i++) {
            if (Character.isDigit(raw.charAt(i))) {
                hasDigit = true;
                break;
            }
        }
        if (!hasDigit) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case 'O': case 'o': case 'Q': case 'D':
                    c = '0';
                    break;
                case 'I': case 'l': case '|': case '!': case 'i':
                    c = '1';
                    break;
                case 'S':
                    c = '5';
                    break;
                case 'B':
                    c = '8';
                    break;
                default:
                    break;
            }
            if ((c >= '0' && c <= '9') || c == ':' || c == '.' || c == ';' || c == ',') sb.append(c);
        }
        int start = 0;
        int end = sb.length();
        while (start < end && isSeparator(sb.charAt(start))) start++;
        while (end > start && isSeparator(sb.charAt(end - 1))) end--;
        return sb.substring(start, end);
    }

    private static boolean isSeparator(char c) {
        return c == ':' || c == '.' || c == ';' || c == ',';
    }

    public static String parseTime(String normalized) {
        return parseTime(normalized, false);
    }

    /** Zwraca godzinę w formacie „H:MM” lub null. {@code allowCompact} dopuszcza zgubiony dwukropek („610”). */
    public static String parseTime(String normalized, boolean allowCompact) {
        Matcher m = TIME.matcher(normalized);
        if (!m.matches()) {
            if (!allowCompact) return null;
            m = COMPACT_TIME.matcher(normalized);
            if (!m.matches()) return null;
        }
        int h = Integer.parseInt(m.group(1));
        int min = Integer.parseInt(m.group(2));
        if (h > 23 || min > 59) return null;
        return String.format(Locale.ROOT, "%d:%02d", h, min);
    }

    public static ParseResult parse(List<OcrToken> tokens, List<String> lines) {
        StringBuilder full = new StringBuilder();
        if (lines.isEmpty()) {
            for (OcrToken t : tokens) full.append(t.text).append('\n');
        } else {
            for (String l : lines) full.append(l).append('\n');
        }
        LocalDate date = findDate(full.toString());

        List<ParsedRow> rows = parseTokens(tokens);
        if (rows.isEmpty()) rows = parseLines(lines);
        return new ParseResult(rows, date);
    }

    private static List<ParsedRow> parseTokens(final List<OcrToken> tokens) {
        final int n = tokens.size();
        final String[] norm = new String[n];
        List<Float> heights = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            norm[i] = normalize(tokens.get(i).text);
            float h = tokens.get(i).height();
            if (h > 0) heights.add(h);
        }
        Collections.sort(heights);
        float medianH = heights.isEmpty() ? 1f : heights.get(heights.size() / 2);

        List<Integer> chamberIdx = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (CHAMBER.matcher(norm[i]).matches() && Integer.parseInt(norm[i]) >= 100) chamberIdx.add(i);
        }
        Collections.sort(chamberIdx, Comparator.comparingDouble(i -> tokens.get(i).left));

        boolean[] consumed = new boolean[n];
        final List<float[]> order = new ArrayList<>(); // [cy, indeks w found]
        List<ParsedRow> found = new ArrayList<>();

        for (int ci : chamberIdx) {
            if (consumed[ci]) continue;
            OcrToken c = tokens.get(ci);
            float h = Math.max(medianH, c.height());

            // Sąsiedzi na prawo w tym samym wierszu, od najbliższego. Tolerancja w pionie rośnie
            // z odległością, żeby wybaczyć lekko przekrzywione zdjęcie.
            List<Integer> neighbours = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                if (j == ci || norm[j].isEmpty()) continue;
                OcrToken t = tokens.get(j);
                if (t.cx() <= c.right || t.left <= c.cx()) continue;
                float dx = t.left - c.right;
                if (Math.abs(t.cy() - c.cy()) <= 0.6f * h + 0.08f * dx) neighbours.add(j);
            }
            if (neighbours.isEmpty()) continue;
            Collections.sort(neighbours, Comparator.comparingDouble(j -> tokens.get(j).left));

            int first = neighbours.get(0);
            String time = parseTime(norm[first], true);
            int second = -1;
            if (time == null && neighbours.size() >= 2) {
                // OCR mógł rozbić godzinę na dwa słowa, np. „6:” i „10”.
                second = neighbours.get(1);
                String b = norm[second];
                while (b.startsWith(":")) b = b.substring(1);
                time = parseTime(norm[first] + ":" + b);
                if (time == null) time = parseTime(norm[first] + norm[second]);
            }
            if (time == null) continue;

            consumed[ci] = true;
            consumed[first] = true;
            if (second >= 0) consumed[second] = true;
            order.add(new float[]{c.cy(), found.size()});
            found.add(new ParsedRow(norm[ci], time));
        }

        Collections.sort(order, (a, b) -> Float.compare(a[0], b[0]));
        List<ParsedRow> rows = new ArrayList<>();
        for (float[] o : order) rows.add(found.get((int) o[1]));
        return rows;
    }

    private static List<ParsedRow> parseLines(List<String> lines) {
        List<ParsedRow> rows = new ArrayList<>();
        for (String line : lines) {
            Matcher m = LINE_ROW.matcher(normalizeLine(line));
            while (m.find()) {
                String time = parseTime(m.group(2) + ":" + m.group(3));
                if (time != null && Integer.parseInt(m.group(1)) >= 100) {
                    rows.add(new ParsedRow(m.group(1), time));
                }
            }
        }
        return rows;
    }

    private static String normalizeLine(String line) {
        StringBuilder sb = new StringBuilder();
        for (String w : line.trim().split("\\s+")) {
            if (sb.length() > 0) sb.append(' ');
            String nw = normalize(w);
            sb.append(nw.isEmpty() ? "_" : nw);
        }
        return sb.toString();
    }

    private static LocalDate findDate(String text) {
        Matcher m = DATE.matcher(text);
        if (!m.find()) return null;
        try {
            return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)));
        } catch (Exception e) {
            return null;
        }
    }
}
