package fi.virit.lahtokaavio.io;

import fi.virit.lahtokaavio.domain.DrawnStart;
import fi.virit.lahtokaavio.domain.Entry;
import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.domain.StartPlan;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Writes the CSV files Navisport imports
 * (https://navisport.com/docs/fi/import-export/overview), comma-delimited UTF-8:
 * <ul>
 * <li>the drawn start list for the participant import, with Navisport's
 * Finnish column headers and the name as "Firstname Lastname";</li>
 * <li>the start scheme ("lähtöaikataulu"): class, first start and interval
 * in seconds, no header, so that Navisport can do the draw itself.</li>
 * </ul>
 */
@org.springframework.stereotype.Component
public final class NavisportCsvWriter {

    static final String START_LIST_HEADER = "Nimi,Kilpailukortti,Seura,Sarja,Lähtöaika,Lisenssinumero";

    private static final DateTimeFormatter START_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FIRST_START = DateTimeFormatter.ofPattern("HH:mm");

    public void writeStartList(List<DrawnStart> starts, OutputStream out) {
        write(out, writer -> {
            writer.write(START_LIST_HEADER);
            writer.write('\n');
            for (DrawnStart start : starts) {
                Entry entry = start.entry();
                writeRow(writer, firstnameFirst(entry.name()), entry.emitCard(), entry.club(),
                        entry.className(), START_TIME.format(start.startTime()), entry.licence());
            }
        });
    }

    public void writeStartScheme(StartPlan plan, OutputStream out) {
        write(out, writer -> {
            for (SeriesStart row : plan.seriesStarts()) {
                writeRow(writer, row.seriesName(), FIRST_START.format(row.firstStartTime()),
                        String.valueOf(row.startIntervalMinutes() * 60));
            }
        });
    }

    /**
     * IRMA writes "Lastname Firstname(s)"; Navisport shows the name as
     * written. Finnish double surnames are hyphenated, so the first word is
     * taken as the surname.
     */
    static String firstnameFirst(String irmaName) {
        String[] parts = irmaName.strip().split("\\s+", 2);
        return parts.length < 2 ? irmaName.strip() : parts[1] + " " + parts[0];
    }

    private static void writeRow(Writer writer, String... values) throws IOException {
        writer.write(Stream.of(values).map(NavisportCsvWriter::quote)
                .collect(Collectors.joining(",")));
        writer.write('\n');
    }

    /** Quotes a value only when it would break the row, e.g. a club "Turku, OK". */
    private static String quote(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private interface Content {
        void write(Writer writer) throws IOException;
    }

    private static void write(OutputStream out, Content content) {
        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            content.write(writer);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Navisport CSV", e);
        }
    }
}
