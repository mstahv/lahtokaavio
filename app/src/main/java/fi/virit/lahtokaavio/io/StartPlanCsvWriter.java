package fi.virit.lahtokaavio.io;

import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.domain.StartPlan;
import java.io.ByteArrayOutputStream;
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

    public byte[] writeBytes(StartPlan plan) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            write(plan, writer);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to write start plan CSV", e);
        }
        return out.toByteArray();
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
            writer.write(String.join(CONTROL_SEPARATOR, row.controlCodes()));
            writer.write('\n');
        }
    }
}
