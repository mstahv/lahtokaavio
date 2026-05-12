package fi.virit.lahtokaavio.io;

import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.domain.StartPlan;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/**
 * Writes a {@link StartPlan} as a semicolon-delimited CSV with columns:
 * series name, first start time, last start time, lane id, controls (dash-separated).
 * Intended for consumption by downstream results-service tooling.
 */
@org.springframework.stereotype.Component
public final class StartPlanCsvWriter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DELIMITER = ";";
    private static final String CONTROL_SEPARATOR = "-";
    private static final String HEADER =
            "Sarja;Ensimmäinen lähtö;Viimeinen lähtö;Jono;Rastikoodit";

    public void writeBytes(StartPlan plan, OutputStream out) throws IOException {
        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            write(plan, writer);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to write start plan CSV", e);
        }
    }

    private static String joinControlCodes(SeriesStart row) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int code : row.controlCodes()) {
            if (!first) {
                sb.append(CONTROL_SEPARATOR);
            }
            sb.append(code);
            first = false;
        }
        return sb.toString();
    }

    public void write(StartPlan plan, Writer writer) throws java.io.IOException {
        writer.write(HEADER);
        writer.write('\n');
        for (SeriesStart row : plan.seriesStarts()) {
            writer.write(row.seriesName());
            writer.write(DELIMITER);
            writer.write(TIME.format(row.firstStartTime()));
            writer.write(DELIMITER);
            writer.write(TIME.format(row.lastStartTime()));
            writer.write(DELIMITER);
            writer.write(row.laneId());
            writer.write(DELIMITER);
            writer.write(joinControlCodes(row));
            writer.write('\n');
        }
    }
}
