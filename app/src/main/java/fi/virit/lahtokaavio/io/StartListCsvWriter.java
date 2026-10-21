package fi.virit.lahtokaavio.io;

import fi.virit.lahtokaavio.domain.DrawnStart;
import fi.virit.lahtokaavio.domain.Entry;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes the drawn start list as a semicolon-delimited CSV, one competitor
 * per row in start time order: class, start time, name, club, Emit card,
 * licence and the lane (start queue).
 */
@org.springframework.stereotype.Component
public final class StartListCsvWriter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DELIMITER = ";";
    private static final String HEADER = "Sarja;Lähtöaika;Nimi;Seura;Emit;Lisenssi;Jono";

    public void writeBytes(List<DrawnStart> starts, OutputStream out) {
        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            write(starts, writer);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write start list CSV", e);
        }
    }

    public void write(List<DrawnStart> starts, Writer writer) throws IOException {
        writer.write(HEADER);
        writer.write('\n');
        for (DrawnStart start : starts) {
            Entry entry = start.entry();
            writer.write(String.join(DELIMITER,
                    entry.className(),
                    TIME.format(start.startTime()),
                    entry.name(),
                    entry.club(),
                    entry.emitCard(),
                    entry.licence(),
                    start.laneId()));
            writer.write('\n');
        }
    }
}
