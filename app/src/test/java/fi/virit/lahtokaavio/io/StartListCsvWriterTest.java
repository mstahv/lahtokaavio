package fi.virit.lahtokaavio.io;

import static org.assertj.core.api.Assertions.assertThat;

import fi.virit.lahtokaavio.domain.DrawnStart;
import fi.virit.lahtokaavio.domain.Entry;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class StartListCsvWriterTest {

    @Test
    void writesOneRowPerCompetitor() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new StartListCsvWriter().writeBytes(List.of(new DrawnStart(LocalTime.of(10, 2), "STA1-1",
                new Entry("D14", "123", "456789", "Äyräs Öljy", "Rasti-Vihti"))), out);

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("""
                Sarja;Lähtöaika;Nimi;Seura;Emit;Lisenssi;Jono
                D14;10:02;Äyräs Öljy;Rasti-Vihti;456789;123;STA1-1
                """);
    }
}
