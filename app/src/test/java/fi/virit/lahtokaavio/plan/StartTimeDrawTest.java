package fi.virit.lahtokaavio.plan;

import static org.assertj.core.api.Assertions.assertThat;

import fi.virit.lahtokaavio.domain.CompetitionParameters;
import fi.virit.lahtokaavio.domain.DrawnStart;
import fi.virit.lahtokaavio.domain.Entry;
import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.domain.StartDraw;
import fi.virit.lahtokaavio.domain.StartPlan;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StartTimeDrawTest {

    // D14 on lane STA1-1: 3 starts from 10:00, every 2 minutes
    // H21 on lane STA1-2: 2 starts from 10:01, every 1 minute
    private final StartPlan plan = new StartPlan(CompetitionParameters.defaults(), List.of(
            new SeriesStart("D14", LocalTime.of(10, 0), LocalTime.of(10, 4), "STA1-1", 3, 2, List.of()),
            new SeriesStart("H21", LocalTime.of(10, 1), LocalTime.of(10, 2), "STA1-2", 2, 1, List.of())));

    @Test
    void competitorsGetTheirClassSlotsInRandomOrder() {
        List<Entry> d14 = List.of(entry("D14", "Aa"), entry("D14", "Bb"), entry("D14", "Cc"));

        StartDraw draw = new StartTimeDraw(new Random(1)).draw(plan, d14);

        assertThat(draw.starts()).extracting(DrawnStart::startTime)
                .containsExactly(LocalTime.of(10, 0), LocalTime.of(10, 2), LocalTime.of(10, 4));
        assertThat(draw.starts()).extracting(DrawnStart::entry).containsExactlyInAnyOrderElementsOf(d14);
        assertThat(draw.starts()).extracting(DrawnStart::laneId).containsOnly("STA1-1");
        assertThat(draw.overbookedClasses()).isEmpty();
        assertThat(draw.unplannedClasses()).isEmpty();
    }

    @Test
    void orderDependsOnTheRandomSource() {
        List<Entry> d14 = List.of(entry("D14", "Aa"), entry("D14", "Bb"), entry("D14", "Cc"),
                entry("D14", "Dd"), entry("D14", "Ee"));
        List<Entry> first = names(new StartTimeDraw(new Random(1)).draw(plan, d14));
        assertThat(names(new StartTimeDraw(new Random(1)).draw(plan, d14))).isEqualTo(first);
        assertThat(names(new StartTimeDraw(new Random(2)).draw(plan, d14))).isNotEqualTo(first);
    }

    @Test
    void lateEntriesStartAfterThePlannedLastStart() {
        StartDraw draw = new StartTimeDraw(new Random(1)).draw(plan, List.of(
                entry("H21", "Aa"), entry("H21", "Bb"), entry("H21", "Late")));

        assertThat(draw.starts()).extracting(DrawnStart::startTime)
                .containsExactly(LocalTime.of(10, 1), LocalTime.of(10, 2), LocalTime.of(10, 3));
        assertThat(draw.overbookedClasses()).containsExactly("H21");
    }

    @Test
    void classesWithoutPlannedStartsAreReportedAndLeftOut() {
        StartDraw draw = new StartTimeDraw(new Random(1)).draw(plan, List.of(
                entry("H21", "Aa"), entry("10 Avoin", "Bb")));

        assertThat(draw.starts()).extracting(s -> s.entry().name()).containsExactly("Aa");
        assertThat(draw.unplannedClasses()).containsExactly("10 Avoin");
    }

    @Test
    void startListIsInStartTimeOrderAcrossClasses() {
        StartDraw draw = new StartTimeDraw(new Random(1)).draw(plan, List.of(
                entry("D14", "Aa"), entry("D14", "Bb"), entry("H21", "Cc"), entry("H21", "Dd")));

        assertThat(draw.starts()).extracting(DrawnStart::startTime).containsExactly(
                LocalTime.of(10, 0), LocalTime.of(10, 1), LocalTime.of(10, 2), LocalTime.of(10, 2));
        // Same minute: lane order
        assertThat(draw.starts().get(2).laneId()).isEqualTo("STA1-1");
    }

    private static List<Entry> names(StartDraw draw) {
        return draw.starts().stream().map(DrawnStart::entry).toList();
    }

    private static Entry entry(String className, String name) {
        return new Entry(className, "1", "100000", name, "Club");
    }
}
