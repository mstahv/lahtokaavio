package fi.virit.lahtokaavio.plan;

import static org.assertj.core.api.Assertions.assertThat;

import fi.virit.lahtokaavio.domain.CompetitionParameters;
import fi.virit.lahtokaavio.domain.PlanWarning;
import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.domain.StartPlan;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class StartPlanValidatorTest {

    private final StartPlanValidator validator = new StartPlanValidator();

    @Test
    void classesOneAfterAnotherOnALaneAreFine() {
        assertThat(validate(false,
                start("H21", "STA1-1", 11, 0, 5, 144),
                start("H35", "STA1-1", 11, 14, 3, 144))).isEmpty();
    }

    @Test
    void sameFirstControlAtTheSameMinuteIsAClash() {
        List<PlanWarning> warnings = validate(false,
                start("H21", "STA1-1", 11, 0, 5, 144),
                start("H35", "STA1-2", 11, 4, 3, 144));

        assertThat(warnings).singleElement().satisfies(w -> {
            assertThat(w.series()).containsExactly("H21", "H35");
            assertThat(w.message()).isEqualTo("H21 and H35 start from STA1 to the same first "
                    + "control 144 at the same minute, 3 times between 11:04 and 11:08.");
        });
    }

    @Test
    void sameFirstControlCloserThanTheIntervalIsAClashUnlessSplittingIsAllowed() {
        SeriesStart even = start("H21", "STA1-1", 11, 0, 3, 144);
        SeriesStart odd = start("H35", "STA1-2", 11, 1, 3, 144);

        assertThat(validate(false, even, odd)).singleElement()
                .extracting(PlanWarning::message).asString()
                .contains("only 1 min apart, 5 times between 11:00 and 11:04");
        assertThat(validate(true, even, odd)).isEmpty();
    }

    @Test
    void differentFirstControlsOrStartPlacesDoNotClash() {
        assertThat(validate(false,
                start("H21", "STA1-1", 11, 0, 5, 144),
                start("H35", "STA1-2", 11, 0, 5, 145),
                start("D21", "STA2-1", 11, 0, 5, 144))).isEmpty();
    }

    @Test
    void tooManyStartersAMinuteFromOneStartPlace() {
        // The defaults allow 6 a minute
        List<SeriesStart> starts = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(i -> start("S" + i, "STA1-" + i, 11, 0, 2, 100 + i)).toList();

        assertThat(validator.validate(new StartPlan(CompetitionParameters.defaults(), starts)))
                .singleElement().extracting(PlanWarning::message)
                .isEqualTo("More than 6 starters a minute from STA1, 2 times between 11:00 and 11:02.");
    }

    private List<PlanWarning> validate(boolean splitting, SeriesStart... starts) {
        CompetitionParameters d = CompetitionParameters.defaults();
        return validator.validate(new StartPlan(new CompetitionParameters(d.firstStart(),
                d.defaultStartIntervalMinutes(), d.gapSlotsBetweenSeries(),
                d.maxStartersPerMinutePerStartPlace(), splitting), List.of(starts)));
    }

    /** At a 2 minute interval. */
    private static SeriesStart start(String name, String lane, int hour, int minute,
                                     int competitors, int firstControl) {
        LocalTime first = LocalTime.of(hour, minute);
        return new SeriesStart(name, first, first.plusMinutes(2L * (competitors - 1)), lane,
                competitors, 2, List.of(firstControl, 200));
    }
}
