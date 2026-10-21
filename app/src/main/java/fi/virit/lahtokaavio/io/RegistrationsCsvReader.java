package fi.virit.lahtokaavio.io;

import fi.virit.lahtokaavio.domain.Registrations;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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
 * The file typically comes from IRMA, which exports UTF-8 with a byte
 * order mark. The encoding is still detected, as older Pirilä-style files
 * are Windows-1252 ({@link #DEFAULT_CHARSET}): input that is valid UTF-8 is
 * read as UTF-8, anything else as Windows-1252.
 */
@org.springframework.stereotype.Component
public final class RegistrationsCsvReader {

    public static final Charset DEFAULT_CHARSET = Charset.forName("Windows-1252");
    private static final String DELIMITER = ";";
    private static final String CLASS_COLUMN_HEADER = "Sarja";
    private static final String TOTAL_ROW_LABEL = "Yhteensä";
    private static final String ENTIRE_EVENT_COLUMN = "Koko tapahtuma";
    private static final String BYTE_ORDER_MARK = "\uFEFF";

    /**
     * UTF-8 if the bytes decode as UTF-8 (with or without a byte order mark),
     * otherwise {@link #DEFAULT_CHARSET}. Windows-1252 text with Finnish
     * letters is practically never valid UTF-8.
     */
    static Charset detectCharset(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException e) {
            return DEFAULT_CHARSET;
        }
    }

    public Registrations read(InputStream input) {
        try {
            byte[] bytes = input.readAllBytes();
            return read(new ByteArrayInputStream(bytes), detectCharset(bytes));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read registrations CSV", e);
        }
    }

    public Registrations read(InputStream input, Charset charset) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, charset))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("Registrations CSV is empty");
            }
            if (headerLine.startsWith(BYTE_ORDER_MARK)) {
                headerLine = headerLine.substring(1);
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
