package fi.virit.lahtokaavio.io;

import static org.assertj.core.api.Assertions.assertThat;

import fi.virit.lahtokaavio.domain.CompetitionParameters;
import fi.virit.lahtokaavio.domain.Course;
import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.domain.StartPlace;
import fi.virit.lahtokaavio.domain.StartPlan;
import fi.virit.lahtokaavio.domain.WorkspaceSnapshot;
import java.io.ByteArrayInputStream;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkspaceJsonRoundTripTest {

    private final WorkspaceJsonWriter writer = new WorkspaceJsonWriter();
    private final WorkspaceJsonReader reader = new WorkspaceJsonReader();

    @Test
    void roundTripPreservesAllFields() {
        Course main = new Course("1", new StartPlace("STA1"), List.of("144", "100"));
        Course kunto = new Course("Kunto3", new StartPlace("STA2"), List.of("160", "100"));

        Map<String, Course> courses = new LinkedHashMap<>();
        courses.put("H21E", main);
        courses.put("Kunto3", kunto);

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("H21E", 20);
        counts.put("Kunto3", 5);

        CompetitionParameters params = new CompetitionParameters(
                LocalTime.of(11, 30), 2, 2, 6, true);

        StartPlan plan = new StartPlan(params, List.of(
                new SeriesStart("H21E", LocalTime.of(11, 30), LocalTime.of(12, 8),
                        "STA1-1", 20, 2, List.of("144", "100")),
                new SeriesStart("Kunto3", LocalTime.of(11, 30), LocalTime.of(11, 38),
                        "STA2-1", 5, 2, List.of("160", "100"))));

        WorkspaceSnapshot original = new WorkspaceSnapshot(
                courses, counts, "Päivä #1", params, plan);

        byte[] bytes = writer.writeBytes(original);
        WorkspaceSnapshot loaded = reader.read(new ByteArrayInputStream(bytes));

        assertThat(loaded.coursesByClass()).isEqualTo(original.coursesByClass());
        assertThat(loaded.counts()).isEqualTo(original.counts());
        assertThat(loaded.selectedDay()).isEqualTo("Päivä #1");
        assertThat(loaded.parameters()).isEqualTo(params);
        assertThat(loaded.plan().seriesStarts()).hasSize(2);
        assertThat(loaded.plan().seriesStarts().getFirst().firstStartTime())
                .isEqualTo(LocalTime.of(11, 30));
    }

    @Test
    void emptyMapsAndMissingPlanRoundTripCleanly() {
        WorkspaceSnapshot original = new WorkspaceSnapshot(
                Map.of(), Map.of(), null, CompetitionParameters.defaults(), null);

        byte[] bytes = writer.writeBytes(original);
        WorkspaceSnapshot loaded = reader.read(new ByteArrayInputStream(bytes));

        assertThat(loaded.coursesByClass()).isEmpty();
        assertThat(loaded.counts()).isEmpty();
        assertThat(loaded.selectedDay()).isNull();
        assertThat(loaded.plan()).isNull();
    }
}
