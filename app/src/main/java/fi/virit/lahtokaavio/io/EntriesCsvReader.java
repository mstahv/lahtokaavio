package fi.virit.lahtokaavio.io;

import fi.virit.lahtokaavio.domain.Entry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the entries ("ilmoittautuneet") CSV exported from IRMA. The file has
 * no header row; the semicolon-separated columns are
 * {@code class;licence;emit card;name;club}, followed by an empty column.
 * The encoding is detected the same way as for the registration counts.
 */
@org.springframework.stereotype.Component
public final class EntriesCsvReader {

    private static final String DELIMITER = ";";
    private static final String BYTE_ORDER_MARK = "﻿";
    private static final int COLUMNS = 5;

    public List<Entry> read(InputStream input) {
        try {
            byte[] bytes = input.readAllBytes();
            Charset charset = RegistrationsCsvReader.detectCharset(bytes);
            String text = new String(bytes, charset);
            if (text.startsWith(BYTE_ORDER_MARK)) {
                text = text.substring(1);
            }
            List<Entry> entries = new ArrayList<>();
            int lineNumber = 0;
            for (String line : text.split("\\R")) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String[] cells = line.split(DELIMITER, -1);
                if (cells.length < COLUMNS) {
                    throw new IllegalArgumentException("Line " + lineNumber
                            + ": expected class;licence;emit;name;club, got: " + line);
                }
                String className = cells[0].trim();
                String name = cells[3].trim();
                if (className.isEmpty() || name.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Line " + lineNumber + ": class and name are required, got: " + line);
                }
                entries.add(new Entry(className, cells[1].trim(), cells[2].trim(), name,
                        cells[4].trim()));
            }
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("Entries CSV is empty");
            }
            return entries;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read entries CSV", e);
        }
    }
}
