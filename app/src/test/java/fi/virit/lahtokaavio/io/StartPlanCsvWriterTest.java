package fi.virit.lahtokaavio.io;

import static org.assertj.core.api.Assertions.assertThat;

import fi.virit.lahtokaavio.domain.CompetitionParameters;
import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.domain.StartPlan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class StartPlanCsvWriterTest {

    @Test
    void writesAllColumnsForEachRow() throws IOException {
        SeriesStart row = new SeriesStart("H21E",
                LocalTime.of(11, 0), LocalTime.of(11, 38), "STA1-1",
                20, 2, List.of(100, 101, 102));
        StartPlan plan = new StartPlan(CompetitionParameters.defaults(), List.of(row));


        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        new StartPlanCsvWriter().writeBytes(plan, bout);

        String csv = new String(bout.toByteArray(), StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");
        assertThat(lines[0]).contains("Sarja").contains("Rastikoodit");
        assertThat(lines[1]).isEqualTo("H21E;11:00;11:38;STA1-1;100-101-102");
    }
}
