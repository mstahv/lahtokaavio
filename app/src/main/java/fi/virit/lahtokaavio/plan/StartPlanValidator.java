package fi.virit.lahtokaavio.plan;

import fi.virit.lahtokaavio.domain.PlanWarning;
import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.domain.StartPlan;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Checks a start plan, typically after the user has dragged series around in
 * the Gantt view, against the rules the generator keeps:
 * <ul>
 * <li>Runners of different classes heading from the same start place to the
 * same first control must not start closer together than the start
 * interval, as if the classes shared one lane: otherwise they would run
 * together. When splitting first-control groups is allowed, only the same
 * minute is a clash. This also covers two classes on the same course
 * starting together (lajisäännöt 10.85).</li>
 * <li>A start place can start at most
 * {@link fi.virit.lahtokaavio.domain.CompetitionParameters#maxStartersPerMinutePerStartPlace()}
 * runners a minute.</li>
 * </ul>
 */
@org.springframework.stereotype.Component
public final class StartPlanValidator {

    public List<PlanWarning> validate(StartPlan plan) {
        List<PlanWarning> warnings = new ArrayList<>();
        warnings.addAll(runnersToTheSameFirstControlTooClose(plan));
        warnings.addAll(tooManyStartersAMinute(plan));
        return warnings;
    }

    private List<PlanWarning> runnersToTheSameFirstControlTooClose(StartPlan plan) {
        boolean splitting = plan.parameters().allowSplittingFirstControlGroups();
        // start place + first control → every start heading there
        Map<String, List<Start>> starts = new TreeMap<>();
        for (SeriesStart s : plan.seriesStarts()) {
            if (s.controlCodes().isEmpty()) {
                continue;
            }
            String key = startPlace(s) + " to the same first control " + s.controlCodes().getFirst();
            for (LocalTime minute : startMinutes(s)) {
                starts.computeIfAbsent(key, k -> new ArrayList<>()).add(new Start(minute, s));
            }
        }
        List<PlanWarning> warnings = new ArrayList<>();
        starts.forEach((where, list) -> {
            list.sort(Comparator.comparing(Start::minute));
            // The classes involved, e.g. "D45, H65" → the earlier start of each clash
            Map<List<String>, List<LocalTime>> clashes = new TreeMap<>(
                    Comparator.comparing((List<String> classes) -> String.join(",", classes)));
            Map<List<String>, Long> closest = new HashMap<>();
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    Start a = list.get(i);
                    Start b = list.get(j);
                    long apart = Duration.between(a.minute(), b.minute()).toMinutes();
                    int required = splitting ? 1 : Math.min(a.series().startIntervalMinutes(),
                            b.series().startIntervalMinutes());
                    if (apart >= Math.max(1, required)) {
                        break;
                    }
                    if (a.series() == b.series()) {
                        continue;
                    }
                    List<String> classes = Stream.of(a.series().seriesName(), b.series().seriesName())
                            .sorted().toList();
                    clashes.computeIfAbsent(classes, c -> new ArrayList<>()).add(a.minute());
                    closest.merge(classes, apart, Math::min);
                }
            }
            clashes.forEach((classes, minutes) -> warnings.add(new PlanWarning(classes,
                    String.join(" and ", classes) + " start from " + where + " "
                            + (closest.get(classes) == 0 ? "at the same minute"
                                    : "only " + closest.get(classes) + " min apart")
                            + ", " + when(minutes) + ".")));
        });
        return warnings;
    }

    private record Start(LocalTime minute, SeriesStart series) {
    }

    private List<PlanWarning> tooManyStartersAMinute(StartPlan plan) {
        int max = plan.parameters().maxStartersPerMinutePerStartPlace();
        // start place → minute → classes starting
        Map<String, TreeMap<LocalTime, Set<String>>> starts = new TreeMap<>();
        for (SeriesStart s : plan.seriesStarts()) {
            for (LocalTime minute : startMinutes(s)) {
                starts.computeIfAbsent(startPlace(s), k -> new TreeMap<>())
                        .computeIfAbsent(minute, m -> new TreeSet<>()).add(s.seriesName());
            }
        }
        List<PlanWarning> warnings = new ArrayList<>();
        starts.forEach((place, byMinute) -> {
            List<LocalTime> minutes = new ArrayList<>();
            Set<String> classes = new TreeSet<>();
            byMinute.forEach((minute, starting) -> {
                if (starting.size() > max) {
                    minutes.add(minute);
                    classes.addAll(starting);
                }
            });
            if (!minutes.isEmpty()) {
                warnings.add(new PlanWarning(List.copyOf(classes),
                        "More than " + max + " starters a minute from " + place + ", "
                                + when(minutes) + "."));
            }
        });
        return warnings;
    }

    private static String when(List<LocalTime> minutes) {
        if (minutes.size() == 1) {
            return "at " + minutes.getFirst();
        }
        return minutes.size() + " times between " + minutes.getFirst()
                + " and " + minutes.getLast();
    }

    /** Lane ids are the start place code plus a lane number, e.g. STA1-2. */
    private static String startPlace(SeriesStart s) {
        int dash = s.laneId().lastIndexOf('-');
        return dash < 0 ? s.laneId() : s.laneId().substring(0, dash);
    }

    private static List<LocalTime> startMinutes(SeriesStart s) {
        List<LocalTime> minutes = new ArrayList<>(s.competitors());
        for (int i = 0; i < s.competitors(); i++) {
            minutes.add(s.firstStartTime().plusMinutes((long) i * s.startIntervalMinutes()));
        }
        return minutes;
    }
}
