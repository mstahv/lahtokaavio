package fi.virit.lahtokaavio.plan;

import static org.assertj.core.api.Assertions.assertThat;

import fi.virit.lahtokaavio.domain.CompetitionParameters;
import fi.virit.lahtokaavio.domain.Course;
import fi.virit.lahtokaavio.domain.Series;
import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.domain.StartPlace;
import fi.virit.lahtokaavio.domain.StartPlan;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class StartPlanRetimerTest {

    private static final CompetitionParameters PARAMETERS = CompetitionParameters.defaults();

    private final StartPlanRetimer retimer = new StartPlanRetimer();

    // STA1-1: H21 11:00–11:08 (5), gap 2 slots, H35 11:14–11:18 (3)
    // STA1-2: D21 11:01–11:05 (3)
    private final StartPlan plan = new StartPlan(PARAMETERS, List.of(
            start("H21", "STA1-1", 11, 0, 5, 144),
            start("H35", "STA1-1", 11, 14, 3, 144),
            start("D21", "STA1-2", 11, 1, 3, 150)));

    @Test
    void longerClassPushesTheRestOfItsLaneLater() {
        StartPlan retimed = retimer.retime(plan, List.of(
                series("H21", 8, 144), series("H35", 3, 144), series("D21", 3, 150)), PARAMETERS);

        assertThat(retimed.seriesStarts()).containsExactly(
                start("H21", "STA1-1", 11, 0, 8, 144),   // 11:00–11:14
                start("H35", "STA1-1", 11, 20, 3, 144),  // pushed 6 min later
                start("D21", "STA1-2", 11, 1, 3, 150));  // other lane untouched
    }

    @Test
    void classDroppingToZeroLeavesItsLaneAndTheNextMovesUp() {
        StartPlan retimed = retimer.retime(plan, List.of(
                series("H21", 0, 144), series("H35", 3, 144), series("D21", 3, 150)), PARAMETERS);

        assertThat(retimed.seriesStarts()).containsExactly(
                start("H35", "STA1-1", 11, 0, 3, 144),
                start("D21", "STA1-2", 11, 1, 3, 150));
    }

    @Test
    void newClassGoesAfterTheClassesHeadingToTheSameFirstControl() {
        StartPlan retimed = retimer.retime(plan, List.of(
                series("H21", 5, 144), series("H35", 3, 144), series("D21", 3, 150),
                series("H40", 2, 150)), PARAMETERS);

        // STA1-2 finishes first anyway, but it also heads to 150
        assertThat(retimed.seriesStarts()).contains(start("H40", "STA1-2", 11, 11, 2, 150));
    }

    @Test
    void newClassWithANewFirstControlGoesToTheLaneFinishingFirst() {
        StartPlan retimed = retimer.retime(plan, List.of(
                series("H21", 5, 144), series("H35", 3, 144), series("D21", 3, 150),
                series("H40", 2, 999)), PARAMETERS);

        assertThat(retimed.seriesStarts()).contains(start("H40", "STA1-2", 11, 11, 2, 999));
    }

    @Test
    void renamedLanesStillCountAsLanesOfTheirStartPlace() {
        StartPlan renamed = new StartPlan(PARAMETERS, List.of(
                start("H21", "North", 11, 0, 5, 144),
                start("D21", "South", 11, 1, 3, 150)));

        StartPlan retimed = retimer.retime(renamed, List.of(
                series("H21", 5, 144), series("D21", 3, 150), series("H40", 2, 999)), PARAMETERS);

        assertThat(retimed.seriesStarts()).contains(start("H40", "South", 11, 11, 2, 999));
    }

    private static Series series(String name, int competitors, int firstControl) {
        return new Series(name, new Course(name, new StartPlace("STA1"),
                List.of(firstControl, 200)), competitors, 2);
    }

    /** At a 2 minute interval. */
    private static SeriesStart start(String name, String lane, int hour, int minute,
                                     int competitors, int firstControl) {
        LocalTime first = LocalTime.of(hour, minute);
        return new SeriesStart(name, first, first.plusMinutes(2L * (competitors - 1)), lane,
                competitors, 2, List.of(firstControl, 200));
    }
}
