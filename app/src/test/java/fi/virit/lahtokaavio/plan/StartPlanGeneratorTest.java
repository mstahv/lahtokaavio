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
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StartPlanGeneratorTest {

    private final StartPlanGenerator generator = new StartPlanGenerator();

    @Test
    void singleSeriesGetsFirstStartAtParameters() {
        Course c = course("1", "STA1");
        Series s = new Series("H21E", c, 5, 2);
        StartPlan plan = generator.generate(List.of(s), CompetitionParameters.defaults());

        SeriesStart placement = plan.seriesStarts().getFirst();
        assertThat(placement.firstStartTime()).isEqualTo(LocalTime.of(11, 0));
        // 5 starters at 2-min interval → last start = 11:00 + 4*2 = 11:08
        assertThat(placement.lastStartTime()).isEqualTo(LocalTime.of(11, 8));
        assertThat(placement.laneId()).isEqualTo("STA1-1");
    }

    @Test
    void multipleSeriesAtSameStartShareLanesUntilFull() {
        // 13 series of 4 starters each at one start, max 6/min, interval 2
        // → 12 lanes available; 13 series will require one lane to host two.
        // Give each its own first control so the lane assignment is governed
        // by LPT balancing, not by first-control grouping.
        List<Series> series = java.util.stream.IntStream.rangeClosed(1, 13)
                .mapToObj(i -> new Series("S" + i, courseWithControl("STA1", "C" + i), 4, 2))
                .toList();

        StartPlan plan = generator.generate(series, CompetitionParameters.defaults());
        assertThat(plan.seriesStarts()).hasSize(13);

        LocalTime lastOverall = plan.seriesStarts().stream()
                .map(SeriesStart::lastStartTime)
                .max(LocalTime::compareTo).orElseThrow();
        // One lane hosts two series of 4: 4 + 2 gap + 4 = 10 slots → 9 intervals → 18 min from start
        // plus possible 1-min lane offset.
        assertThat(lastOverall).isBetween(LocalTime.of(11, 18), LocalTime.of(11, 19));
    }

    @Test
    void differentStartPlacesPlanIndependently() {
        Course main = course("1", "STA1");
        Course kunto = course("Kunto", "STA2");
        List<Series> series = List.of(
                new Series("H21E", main, 20, 2),
                new Series("Kunto3", kunto, 5, 2));

        StartPlan plan = generator.generate(series, CompetitionParameters.defaults());

        var laneIds = plan.seriesStarts().stream()
                .collect(Collectors.toMap(SeriesStart::seriesName, SeriesStart::laneId));
        assertThat(laneIds.get("H21E")).startsWith("STA1-");
        assertThat(laneIds.get("Kunto3")).startsWith("STA2-");
    }

    @Test
    void splittingFirstControlGroupsShortensCompetition() {
        // 10 small series that all share first control "144" — strict mode
        // forces them all onto one lane (10 starters back-to-back). Splitting
        // mode is allowed to spread them across two parallel lanes (offset 0
        // and offset 1), so the longest queue ends roughly half as far out.
        Course shared = new Course("c", new StartPlace("STA1"), List.of("144", "100"));
        List<Series> series = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(i -> new Series("S" + i, shared, 1, 2))
                .toList();

        CompetitionParameters defaults = CompetitionParameters.defaults();
        CompetitionParameters relaxed = new CompetitionParameters(
                defaults.firstStart(),
                defaults.defaultStartIntervalMinutes(),
                defaults.gapSlotsBetweenSeries(),
                defaults.maxStartersPerMinutePerStartPlace(),
                true);

        LocalTime strictMakespan = lastStartOf(generator.generate(series, defaults));
        LocalTime relaxedMakespan = lastStartOf(generator.generate(series, relaxed));
        assertThat(relaxedMakespan).isBefore(strictMakespan);
    }

    private LocalTime lastStartOf(StartPlan plan) {
        return plan.seriesStarts().stream()
                .map(SeriesStart::lastStartTime)
                .max(LocalTime::compareTo).orElseThrow();
    }

    @Test
    void mixedStartIntervalsAtOnePlaceFireOnDistinctMinutes() {
        // Two series at STA1 with different intervals (1 min and 2 min) and
        // distinct first controls. Each starter must fire at the cadence of
        // its own series, and per-minute capacity (6/min default) must hold.
        Course fastCourse = courseWithControl("STA1", "A");
        Course slowCourse = courseWithControl("STA1", "B");
        List<Series> series = List.of(
                new Series("FAST", fastCourse, 5, 1),
                new Series("SLOW", slowCourse, 5, 2));

        StartPlan plan = generator.generate(series, CompetitionParameters.defaults());

        SeriesStart fast = placement(plan, "FAST");
        SeriesStart slow = placement(plan, "SLOW");

        // FAST: 5 starters at 1 min apart → 4 minutes span.
        assertThat(java.time.Duration.between(fast.firstStartTime(), fast.lastStartTime()))
                .isEqualTo(java.time.Duration.ofMinutes(4));
        // SLOW: 5 starters at 2 min apart → 8 minutes span.
        assertThat(java.time.Duration.between(slow.firstStartTime(), slow.lastStartTime()))
                .isEqualTo(java.time.Duration.ofMinutes(8));
        // Soft start aligns ends.
        assertThat(fast.lastStartTime()).isEqualTo(slow.lastStartTime());
    }

    private SeriesStart placement(StartPlan plan, String name) {
        return plan.seriesStarts().stream()
                .filter(s -> s.seriesName().equals(name))
                .findFirst().orElseThrow();
    }

    @Test
    void seriesSharingFirstControlEndUpOnSameLane() {
        Course shared = new Course("shared", new StartPlace("STA1"),
                List.of("144", "100", "101"));
        Course other = new Course("other", new StartPlace("STA1"),
                List.of("160", "100"));
        List<Series> series = List.of(
                new Series("D10", shared, 2, 2),
                new Series("H14", shared, 3, 2),
                new Series("H21E", other, 20, 2));

        StartPlan plan = generator.generate(series, CompetitionParameters.defaults());

        String d10Lane = laneOf(plan, "D10");
        String h14Lane = laneOf(plan, "H14");
        assertThat(d10Lane).isEqualTo(h14Lane);
        // The "other" series has a different first control so it can safely
        // sit on a different lane.
        assertThat(laneOf(plan, "H21E")).isNotEqualTo(d10Lane);
    }

    private String laneOf(StartPlan plan, String seriesName) {
        return plan.seriesStarts().stream()
                .filter(s -> s.seriesName().equals(seriesName))
                .map(SeriesStart::laneId)
                .findFirst().orElseThrow();
    }

    @Test
    void resultsAreSortedByLaneIdNumerically() {
        // 12 series of 1 starter each — each on its own course (distinct first
        // control) so balancing spreads them one per lane (STA1-1 .. STA1-12).
        // Default string sort would interleave STA1-10/11/12 between -1 and -2;
        // the natural comparator must keep them at the end.
        List<Series> series = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(i -> new Series("S" + i, courseWithControl("STA1", "C" + i), 1, 2))
                .toList();

        StartPlan plan = generator.generate(series, CompetitionParameters.defaults());
        List<String> laneOrder = plan.seriesStarts().stream()
                .map(SeriesStart::laneId).toList();
        assertThat(laneOrder).containsExactly(
                "STA1-1", "STA1-2", "STA1-3", "STA1-4", "STA1-5", "STA1-6",
                "STA1-7", "STA1-8", "STA1-9", "STA1-10", "STA1-11", "STA1-12");
    }

    @Test
    void smallBucketsAreCombinedToReducePeakConcurrency() {
        // One big bucket (10) + four small ones (1 each). Old LPT would open
        // five lanes; BFD packs all four small buckets onto a single lane
        // alongside the big one (or onto a second lane that ends at the same
        // slot). Either way, we don't end up with five lanes all firing at
        // minute 0.
        CompetitionParameters params = CompetitionParameters.defaults();
        List<Series> series = new java.util.ArrayList<>();
        series.add(new Series("BIG", courseWithControl("STA1", "BIG"), 10, 2));
        for (int i = 0; i < 4; i++) {
            series.add(new Series("s" + i, courseWithControl("STA1", "k" + i), 1, 2));
        }

        StartPlan plan = generator.generate(series, params);

        long distinctLanes = plan.seriesStarts().stream()
                .map(SeriesStart::laneId).distinct().count();
        // Five buckets total would have produced five lanes under LPT;
        // BFD should consolidate them onto two lanes (one big + bundle).
        assertThat(distinctLanes).isLessThanOrEqualTo(2);
    }

    @Test
    void softStartAlignsLaneEndsWithinGcdMinutesAndShiftsShorterLanesLater() {
        // Three series with their own first control → BFD makes the longest
        // one set the makespan target, the shorter ones either share a lane
        // (when they fit) or get their own. Soft start aligns every used
        // lane's end to the global last slot; physical offset minutes can
        // still split the absolute last-start by up to (gcd-1) minutes.
        CompetitionParameters params = CompetitionParameters.defaults();
        List<Series> series = List.of(
                new Series("A", courseWithControl("STA1", "A"), 10, 2),
                new Series("B", courseWithControl("STA1", "B"), 8, 2),
                new Series("C", courseWithControl("STA1", "C"), 6, 2));

        StartPlan plan = generator.generate(series, params);

        // Last starts fall within a single offset-minute spread (gcd = 2 → ≤ 1 min).
        java.time.LocalTime minLast = plan.seriesStarts().stream()
                .map(SeriesStart::lastStartTime).min(java.time.LocalTime::compareTo).orElseThrow();
        java.time.LocalTime maxLast = plan.seriesStarts().stream()
                .map(SeriesStart::lastStartTime).max(java.time.LocalTime::compareTo).orElseThrow();
        assertThat(java.time.Duration.between(minLast, maxLast).toMinutes()).isLessThanOrEqualTo(1);

        // Shorter series gets the later first start (soft start property).
        java.util.Map<String, java.time.LocalTime> firstByName = plan.seriesStarts().stream()
                .collect(java.util.stream.Collectors.toMap(
                        SeriesStart::seriesName, SeriesStart::firstStartTime));
        assertThat(firstByName.get("A")).isBeforeOrEqualTo(firstByName.get("B"));
        assertThat(firstByName.get("B")).isBeforeOrEqualTo(firstByName.get("C"));
    }

    private Course course(String name, String startPlaceCode) {
        return new Course(name, new StartPlace(startPlaceCode), List.of("100", "101"));
    }

    private Course courseWithControl(String startPlaceCode, String firstControl) {
        return new Course("c-" + firstControl, new StartPlace(startPlaceCode),
                List.of(firstControl, "100"));
    }
}
