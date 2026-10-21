package fi.virit.lahtokaavio.io;

import static org.assertj.core.api.Assertions.assertThat;

import fi.virit.lahtokaavio.domain.CompetitionParameters;
import fi.virit.lahtokaavio.domain.DrawnStart;
import fi.virit.lahtokaavio.domain.Entry;
import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.domain.StartPlan;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class NavisportCsvWriterTest {

    private final NavisportCsvWriter writer = new NavisportCsvWriter();

    @Test
    void startListHasNavisportHeadersAndFirstnameFirst() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.writeStartList(List.of(
                new DrawnStart(LocalTime.of(10, 2), "STA1-1",
                        new Entry("D14", "123", "456789", "Äyräs Öljy", "Rasti-Vihti")),
                new DrawnStart(LocalTime.of(10, 3), "STA1-1",
                        new Entry("14 Avoin", "", "", "Meikäläinen Maija Liisa", "Turku, OK"))),
                out);

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("""
                Nimi,Kilpailukortti,Seura,Sarja,Lähtöaika,Lisenssinumero
                Öljy Äyräs,456789,Rasti-Vihti,D14,10:02:00,123
                Maija Liisa Meikäläinen,,"Turku, OK",14 Avoin,10:03:00,
                """);
    }

    @Test
    void startSchemeIsClassFirstStartAndIntervalInSecondsWithoutHeader() {
        StartPlan plan = new StartPlan(CompetitionParameters.defaults(), List.of(
                new SeriesStart("H21", LocalTime.of(10, 0), LocalTime.of(10, 38), "STA1-1",
                        20, 2, List.of()),
                new SeriesStart("D12", LocalTime.of(10, 30), LocalTime.of(10, 39), "STA1-2",
                        10, 1, List.of())));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.writeStartScheme(plan, out);

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("""
                H21,10:00,120
                D12,10:30,60
                """);
    }

    @Test
    void singleWordNameIsKeptAsIs() {
        assertThat(NavisportCsvWriter.firstnameFirst("Madonna")).isEqualTo("Madonna");
    }
}
