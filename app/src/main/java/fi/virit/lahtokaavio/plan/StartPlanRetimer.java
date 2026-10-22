package fi.virit.lahtokaavio.plan;

import fi.virit.lahtokaavio.domain.CompetitionParameters;
import fi.virit.lahtokaavio.domain.Series;
import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.domain.StartPlan;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Applies changed competitor counts to a plan without re-optimising it: every
 * lane keeps its start time and its classes in the same order, the classes are
 * only re-timed one after another (with the gap slots between them), the same
 * way the Gantt view re-times a lane after a drag. A longer class pushes the
 * rest of its lane later, so the competition gets as long as it gets.
 * <ul>
 * <li>A class whose count drops to zero leaves the plan.</li>
 * <li>A class that wasn't in the plan goes to the end of a lane at its start
 * place: one already heading to the same first control if there is one,
 * otherwise the lane finishing first.</li>
 * </ul>
 */
public final class StartPlanRetimer {

    public StartPlan retime(StartPlan plan, List<Series> series, CompetitionParameters parameters) {
        Map<String, Series> byName = series.stream().filter(s -> s.competitors() > 0)
                .collect(Collectors.toMap(Series::name, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));

        // lane → classes in start order, and when the lane starts
        Map<String, List<String>> lanes = new TreeMap<>(StartPlanGenerator.LANE_ID_NATURAL);
        Map<String, LocalTime> laneStart = new LinkedHashMap<>();
        plan.seriesStarts().stream()
                .sorted(Comparator.comparing(SeriesStart::firstStartTime))
                .forEach(s -> {
                    lanes.computeIfAbsent(s.laneId(), l -> new ArrayList<>()).add(s.seriesName());
                    laneStart.merge(s.laneId(), s.firstStartTime(),
                            (a, b) -> a.isBefore(b) ? a : b);
                });
        lanes.values().forEach(names -> names.removeIf(name -> !byName.containsKey(name)));

        for (Series s : byName.values()) {
            boolean planned = lanes.values().stream().anyMatch(names -> names.contains(s.name()));
            if (!planned) {
                String lane = laneFor(s, lanes, laneStart, byName, parameters);
                lanes.computeIfAbsent(lane, l -> new ArrayList<>()).add(s.name());
                laneStart.putIfAbsent(lane, parameters.firstStart());
            }
        }

        List<SeriesStart> starts = new ArrayList<>();
        lanes.forEach((lane, names) -> {
            LocalTime cursor = laneStart.get(lane);
            boolean first = true;
            for (String name : names) {
                Series s = byName.get(name);
                int step = s.startIntervalMinutes();
                if (!first) {
                    cursor = cursor.plusMinutes((long) parameters.gapSlotsBetweenSeries() * step);
                }
                LocalTime last = cursor.plusMinutes((long) (s.competitors() - 1) * step);
                starts.add(new SeriesStart(name, cursor, last, lane, s.competitors(), step,
                        s.course().controlCodes()));
                cursor = last.plusMinutes(step);
                first = false;
            }
        });
        starts.sort(Comparator.comparing(SeriesStart::laneId, StartPlanGenerator.LANE_ID_NATURAL)
                .thenComparing(SeriesStart::firstStartTime));
        return new StartPlan(parameters, starts);
    }

    private static String laneFor(Series newcomer, Map<String, List<String>> lanes,
                                  Map<String, LocalTime> laneStart, Map<String, Series> byName,
                                  CompetitionParameters parameters) {
        String place = newcomer.course().startPlace().code();
        Integer firstControl = firstControl(newcomer);
        List<String> atPlace = lanes.keySet().stream()
                .filter(lane -> lane.startsWith(place + "-")).toList();
        // Same first control: its runners are then spaced like the others'
        for (String lane : atPlace) {
            boolean sameControl = lanes.get(lane).stream().map(byName::get)
                    .anyMatch(s -> firstControl != null && firstControl.equals(firstControl(s)));
            if (sameControl) {
                return lane;
            }
        }
        return atPlace.stream()
                .min(Comparator.comparing(lane -> laneEnd(lane, lanes, laneStart, byName, parameters)))
                .orElseGet(() -> place + "-" + (atPlace.size() + 1));
    }

    private static LocalTime laneEnd(String lane, Map<String, List<String>> lanes,
                                     Map<String, LocalTime> laneStart, Map<String, Series> byName,
                                     CompetitionParameters parameters) {
        LocalTime cursor = laneStart.get(lane);
        for (String name : lanes.get(lane)) {
            Series s = byName.get(name);
            cursor = cursor.plusMinutes((long) (s.competitors() + parameters.gapSlotsBetweenSeries())
                    * s.startIntervalMinutes());
        }
        return cursor;
    }

    private static Integer firstControl(Series s) {
        List<Integer> controls = Objects.requireNonNull(s).course().controlCodes();
        return controls.isEmpty() ? null : controls.getFirst();
    }
}
