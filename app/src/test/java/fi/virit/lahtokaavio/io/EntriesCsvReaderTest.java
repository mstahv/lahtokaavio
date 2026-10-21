package fi.virit.lahtokaavio.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fi.virit.lahtokaavio.domain.Entry;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntriesCsvReaderTest {

    private final EntriesCsvReader reader = new EntriesCsvReader();

    @Test
    void readsIrmaExport() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/metsasprintti/ilmoittautuneet.csv")) {
            List<Entry> entries = reader.read(in);

            assertThat(entries).hasSize(143);
            assertThat(entries.getFirst()).isEqualTo(
                    new Entry("H21", "3094", "536400", "Arola Topias", "Sweden"));
            assertThat(entries).filteredOn(e -> e.className().equals("10 Avoin")).hasSize(3);
        }
    }

    @Test
    void readsWindows1252() {
        byte[] csv = "D14;123;456789;Äyräs Öljy;Ässät;\n".getBytes(Charset.forName("Windows-1252"));
        assertThat(reader.read(new ByteArrayInputStream(csv)).getFirst().name()).isEqualTo("Äyräs Öljy");
    }

    @Test
    void rejectsLineWithTooFewColumns() {
        byte[] csv = "H21;3094;536400;Arola Topias;Sweden;\nH21;Dahlen Mats\n".getBytes();
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(csv)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Line 2");
    }
}
