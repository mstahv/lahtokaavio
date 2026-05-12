package fi.virit.lahtokaavio.io;

import fi.virit.lahtokaavio.domain.Registrations;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the semicolon-delimited registrations CSV.
 *
 * Example header: {@code Sarja;Päivä #1;Päivä #2;Koko tapahtuma}.
 * Each data row holds a class name in the first column and an integer
 * competitor count per day column. Two columns are ignored:
 * <ul>
 *   <li>The trailing aggregate column "Koko tapahtuma" — it is a sum across
 *       days and not a day in its own right.</li>
 *   <li>The "Yhteensä" total row — a hand-maintained sum, not entry data.</li>
 * </ul>
 *
 * The CSV files exported from the Pirilä / IRMA tooling that the orienteering
 * community uses are typically Windows-1252 encoded — see {@link #DEFAULT_CHARSET}.
 */
@org.springframework.stereotype.Component
public final class RegistrationsCsvReader {

    public static final Charset DEFAULT_CHARSET = Charset.forName("Windows-1252");
    private static final String DELIMITER = ";";
    private static final String CLASS_COLUMN_HEADER = "Sarja";
    private static final String TOTAL_ROW_LABEL = "Yhteensä";
    private static final String ENTIRE_EVENT_COLUMN = "Koko tapahtuma";

    public Registrations read(InputStream input) {
        return read(input, DEFAULT_CHARSET);
    }

    public Registrations read(InputStream input, Charset charset) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, charset))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("Registrations CSV is empty");
            }
            List<DayColumn> dayColumns = parseHeader(headerLine);

            Map<String, Map<String, Integer>> countsByDay = new LinkedHashMap<>();
            for (DayColumn day : dayColumns) {
                countsByDay.put(day.label(), new LinkedHashMap<>());
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cells = line.split(DELIMITER, -1);
                String className = cells[0].trim();
                if (className.equalsIgnoreCase(TOTAL_ROW_LABEL)) {
                    continue;
                }
                for (DayColumn day : dayColumns) {
                    if (day.sourceIndex() >= cells.length) {
                        throw new IllegalArgumentException(
                                "Row '" + className + "' has fewer columns than the header");
                    }
                    int count = parseCount(cells[day.sourceIndex()].trim(), className, day.label());
                    countsByDay.get(day.label()).put(className, count);
                }
            }
            return new Registrations(countsByDay);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read registrations CSV", e);
        }
    }

    private List<DayColumn> parseHeader(String headerLine) {
        String[] headers = headerLine.split(DELIMITER, -1);
        if (headers.length < 2 || !headers[0].trim().equalsIgnoreCase(CLASS_COLUMN_HEADER)) {
            throw new IllegalArgumentException(
                    "Expected header starting with 'Sarja', got: " + headerLine);
        }
        List<DayColumn> days = new ArrayList<>(headers.length - 1);
        for (int i = 1; i < headers.length; i++) {
            String label = headers[i].trim();
            if (label.equalsIgnoreCase(ENTIRE_EVENT_COLUMN)) {
                continue;
            }
            days.add(new DayColumn(label, i));
        }
        return days;
    }

    private record DayColumn(String label, int sourceIndex) {
    }

    private int parseCount(String cell, String className, String dayLabel) {
        try {
            return Integer.parseInt(cell);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT,
                            "Invalid competitor count '%s' for class '%s' on day '%s'",
                            cell, className, dayLabel),
                    e);
        }
    }
}
