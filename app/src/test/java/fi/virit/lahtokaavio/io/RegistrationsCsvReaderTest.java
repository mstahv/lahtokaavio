package fi.virit.lahtokaavio.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fi.virit.lahtokaavio.domain.Registrations;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegistrationsCsvReaderTest {

    private final RegistrationsCsvReader reader = new RegistrationsCsvReader();

    @Test
    void readsSampleFile() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/ilmoittautumiset.csv")) {
            Registrations registrations = reader.read(in);
            // "Koko tapahtuma" is an aggregate, not a day — ignored.
            assertThat(registrations.dayLabels()).containsExactly("Päivä #1");

            Map<String, Integer> day1 = registrations.countsForDay("Päivä #1");
            assertThat(day1).containsEntry("D10RR", 20);
            assertThat(day1).containsEntry("H21E", 20);
            assertThat(day1).containsEntry("H75", 21);
            assertThat(day1).doesNotContainKey("Yhteensä");
            assertThat(day1).hasSize(42);
            assertThat(day1.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(405);
        }
    }

    @Test
    void parsesMultiDayCsv() {
        String csv = """
                Sarja;Päivä #1;Päivä #2;Koko tapahtuma
                D14;5;7;12
                H21E;20;18;38
                Yhteensä;25;25;50
                """;
        Registrations r = reader.read(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
        assertThat(r.dayLabels()).containsExactly("Päivä #1", "Päivä #2");
        assertThat(r.countsForDay("Päivä #2")).containsEntry("H21E", 18).hasSize(2);
    }

    @Test
    void ignoresEntireEventColumnRegardlessOfPosition() {
        // The aggregate column doesn't have to be last.
        String csv = """
                Sarja;Koko tapahtuma;Päivä #1
                D14;12;5
                """;
        Registrations r = reader.read(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
        assertThat(r.dayLabels()).containsExactly("Päivä #1");
        assertThat(r.countsForDay("Päivä #1")).containsEntry("D14", 5);
    }

    @Test
    void rejectsMissingHeader() {
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream("D14;5\n".getBytes()),
                StandardCharsets.UTF_8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sarja");
    }

    @Test
    void rejectsNonNumericCount() {
        String bad = "Sarja;Päivä #1\nD14;ei numero\n";
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(bad.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("D14");
    }
}
