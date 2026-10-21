package fi.virit.lahtokaavio.plan;

import fi.virit.lahtokaavio.domain.CompetitionParameters;
import fi.virit.lahtokaavio.domain.Series;
import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.domain.StartPlace;
import fi.virit.lahtokaavio.domain.StartPlan;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Start plan generator.
 *
 * <p>Two competing objectives, both spec-mandated:
 * <ol>
 *   <li><b>Primary</b> — minimise competition makespan (the latest last-start
 *       time).</li>
 *   <li><b>Secondary</b> — keep the start point's "pressure" as low as
 *       possible <i>throughout</i> the run. Short first-control buckets are
 *       combined onto the same lane (different controls don't conflict), and
 *       the resulting lanes are distributed evenly across offset minutes,
 *       so peak firing rate stays uniform from 11:00 to the last starter
 *       instead of front-loading.</li>
 * </ol>
 *
 * <p>Algorithm per start place:
 * <ol>
 *   <li>Group series by first-control code (constraint: no two starters can
 *       head to the same control on the same minute).</li>
 *   <li>Compute the time grid: GCD of the per-series start intervals.</li>
 *   <li>If splitting is enabled, slice every multi-series bucket into
 *       {@code gcd} alternating halves, tagging each with the offset minute
 *       it must end up on. The original constraint still holds — only one
 *       half per offset.</li>
 *   <li>Set the target makespan to the largest single (sub-)bucket — anything
 *       less would violate the bucket's own atomicity.</li>
 *   <li>Best-Fit-Decreasing pack the buckets into virtual lanes (each tagged
 *       with an offset to allocate the available pre-minute capacity evenly),
 *       never exceeding the target. This minimises lane count → fewer
 *       simultaneous firings.</li>
 *   <li>Soft start pass shifts shorter lanes later so all non-empty lanes
 *       end together — no straggler queue running long after the rest.</li>
 * </ol>
 */
@org.springframework.stereotype.Component
public final class StartPlanGenerator {

    /**
     * Upper bound for the per-lane soft-start delay. The aim of soft start
     * is to relieve pressure at minute zero — staggering lanes within the
     * same offset minute so they don't all fire together — without pushing
     * shorter lanes into a long, empty late stretch. Capping the delay at
     * a few minutes keeps everything front-loaded.
     */
    private static final int MAX_LANE_START_DELAY_MINUTES = 20;

    /**
     * Plans all series. Open classes ("avoin", see
     * {@link CourseMatcher#isOpenClass}) start after every other class: they
     * are planned separately, from the first free start after the others'
     * last start, on lanes of their own numbered after the other lanes of the
     * start place.
     */
    public StartPlan generate(List<Series> series, CompetitionParameters parameters) {
        List<Series> regular = series.stream().filter(s -> !CourseMatcher.isOpenClass(s.name())).toList();
        List<Series> open = series.stream().filter(s -> CourseMatcher.isOpenClass(s.name())).toList();

        List<SeriesStart> placements = new ArrayList<>(plan(regular, parameters));
        if (!open.isEmpty()) {
            LocalTime regularEnd = placements.stream().map(SeriesStart::lastStartTime)
                    .max(LocalTime::compareTo).orElse(null);
            CompetitionParameters openParameters = regularEnd == null ? parameters
                    : new CompetitionParameters(
                            regularEnd.plusMinutes(parameters.defaultStartIntervalMinutes()),
                            parameters.defaultStartIntervalMinutes(),
                            parameters.gapSlotsBetweenSeries(),
                            parameters.maxStartersPerMinutePerStartPlace(),
                            parameters.allowSplittingFirstControlGroups());
            Map<String, Integer> lanesInUse = new LinkedHashMap<>();
            for (SeriesStart p : placements) {
                lanesInUse.merge(lanePrefix(p.laneId()), laneNumber(p.laneId()), Math::max);
            }
            for (SeriesStart p : plan(open, openParameters)) {
                String prefix = lanePrefix(p.laneId());
                String laneId = prefix + "-" + (lanesInUse.getOrDefault(prefix, 0) + laneNumber(p.laneId()));
                placements.add(new SeriesStart(p.seriesName(), p.firstStartTime(), p.lastStartTime(),
                        laneId, p.competitors(), p.startIntervalMinutes(), p.controlCodes()));
            }
        }
        placements.sort(Comparator.comparing(SeriesStart::laneId, LANE_ID_NATURAL)
                .thenComparing(SeriesStart::firstStartTime));
        return new StartPlan(parameters, placements);
    }

    private static String lanePrefix(String laneId) {
        return laneId.substring(0, laneId.lastIndexOf('-'));
    }

    private static int laneNumber(String laneId) {
        return Integer.parseInt(laneId.substring(laneId.lastIndexOf('-') + 1));
    }

    private List<SeriesStart> plan(List<Series> series, CompetitionParameters parameters) {
        Map<StartPlace, List<Series>> seriesByStartPlace = new LinkedHashMap<>();
        for (Series s : series) {
            seriesByStartPlace
                    .computeIfAbsent(s.course().startPlace(), k -> new ArrayList<>())
                    .add(s);
        }

        List<SeriesStart> placements = new ArrayList<>();
        for (Map.Entry<StartPlace, List<Series>> entry : seriesByStartPlace.entrySet()) {
            packStartPlace(entry.getKey(), entry.getValue(), parameters, placements);
        }
        return placements;
    }

    /**
     * Compares lane ids like {@code STA1-2} vs {@code STA1-10} so that the
     * numeric suffix sorts numerically, not lexically.
     */
    public static final Comparator<String> LANE_ID_NATURAL = (a, b) -> {
        int aDash = a.lastIndexOf('-');
        int bDash = b.lastIndexOf('-');
        if (aDash < 0 || bDash < 0) {
            return a.compareTo(b);
        }
        int prefixCmp = a.substring(0, aDash).compareTo(b.substring(0, bDash));
        if (prefixCmp != 0) {
            return prefixCmp;
        }
        try {
            return Integer.compare(
                    Integer.parseInt(a.substring(aDash + 1)),
                    Integer.parseInt(b.substring(bDash + 1)));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    };

    private void packStartPlace(StartPlace startPlace,
                                List<Series> seriesAtPlace,
                                CompetitionParameters parameters,
                                List<SeriesStart> output) {
        int gcd = gcdInterval(seriesAtPlace, parameters.defaultStartIntervalMinutes());
        int lanesPerMinute = Math.max(1, parameters.maxStartersPerMinutePerStartPlace());
        int physicalLaneCount = lanesPerMinute * gcd;

        List<FirstControlGroup> groups = groupByFirstControl(seriesAtPlace, gcd,
                parameters.gapSlotsBetweenSeries());
        if (parameters.allowSplittingFirstControlGroups() && gcd > 1) {
            groups = splitMultiSeriesGroups(groups, gcd);
        }
        groups.sort(Comparator.comparingInt(FirstControlGroup::totalLaneSlots).reversed());
        if (groups.isEmpty()) {
            return;
        }

        List<VirtualLane> virtualLanes = packIntoVirtualLanes(groups,
                physicalLaneCount, parameters.gapSlotsBetweenSeries(),
                gcd, lanesPerMinute);

        int[] nextFreeSlot = new int[physicalLaneCount];
        List<TempPlacement> tempPlacements = new ArrayList<>();
        emitVirtualLanes(virtualLanes, startPlace, gcd, lanesPerMinute,
                parameters.gapSlotsBetweenSeries(), nextFreeSlot, tempPlacements);

        emitWithSoftStart(tempPlacements, nextFreeSlot, lanesPerMinute, gcd,
                parameters, output);
    }

    /**
     * Slices each multi-series bucket into {@code gcd} alternating halves
     * (round-robin assignment), tagging each half with the offset minute
     * it must end up on. This keeps the same-first-control constraint
     * intact (one half per offset minute) while letting the bucket span
     * multiple lanes — exactly what the splitting feature is for.
     */
    private List<FirstControlGroup> splitMultiSeriesGroups(List<FirstControlGroup> groups,
                                                           int gcd) {
        List<FirstControlGroup> out = new ArrayList<>();
        for (FirstControlGroup g : groups) {
            if (g.series().size() <= 1) {
                out.add(g);
                continue;
            }
            List<List<Series>> halves = new ArrayList<>();
            for (int i = 0; i < gcd; i++) {
                halves.add(new ArrayList<>());
            }
            for (int i = 0; i < g.series().size(); i++) {
                halves.get(i % gcd).add(g.series().get(i));
            }
            for (int offset = 0; offset < gcd; offset++) {
                if (halves.get(offset).isEmpty()) {
                    continue;
                }
                out.add(new FirstControlGroup(g.firstControl(),
                        halves.get(offset), gcd, g.gapSlotsParam(), offset));
            }
        }
        return out;
    }

    /**
     * Best-Fit-Decreasing pack the (possibly split) buckets onto virtual
     * lanes targeting {@code targetSlots = longestBucket}. Each virtual
     * lane carries an explicit offset minute so split halves can claim
     * specific offsets and the resulting per-minute load stays balanced.
     */
    private List<VirtualLane> packIntoVirtualLanes(List<FirstControlGroup> groups,
                                                   int physicalLaneCount,
                                                   int gapSlots,
                                                   int gcd,
                                                   int lanesPerMinute) {
        int target = groups.getFirst().totalLaneSlots();
        while (true) {
            List<VirtualLane> packed = tryPackWithTarget(groups, target, gapSlots, gcd);
            if (fitsWithinCapacity(packed, gcd, lanesPerMinute)
                    && packed.size() <= physicalLaneCount) {
                return packed;
            }
            // Couldn't fit — relax the target by ~10% (at least one slot)
            // and retry. Eventually one big lane absorbs everything.
            target = Math.max(target + 1, (int) Math.ceil(target * 1.1));
        }
    }

    private boolean fitsWithinCapacity(List<VirtualLane> lanes, int gcd, int lanesPerMinute) {
        int[] perOffset = new int[gcd];
        for (VirtualLane vl : lanes) {
            perOffset[vl.offset()]++;
            if (perOffset[vl.offset()] > lanesPerMinute) {
                return false;
            }
        }
        return true;
    }

    private List<VirtualLane> tryPackWithTarget(List<FirstControlGroup> groups,
                                                int targetSlots,
                                                int gapSlots,
                                                int gcd) {
        List<VirtualLane> lanes = new ArrayList<>();
        for (FirstControlGroup group : groups) {
            VirtualLane best = null;
            int bestSlack = Integer.MAX_VALUE;
            for (VirtualLane lane : lanes) {
                if (group.requiredOffset() != null && lane.offset() != group.requiredOffset()) {
                    continue;
                }
                int newTotal = lane.totalSlotsAfterAdding(group, gapSlots);
                if (newTotal <= targetSlots) {
                    int slack = targetSlots - newTotal;
                    if (slack < bestSlack) {
                        bestSlack = slack;
                        best = lane;
                    }
                }
            }
            if (best == null) {
                int chosenOffset = group.requiredOffset() != null
                        ? group.requiredOffset()
                        : leastUsedOffset(lanes, gcd);
                best = new VirtualLane(chosenOffset);
                lanes.add(best);
            }
            best.add(group, gapSlots);
        }
        return lanes;
    }

    private int leastUsedOffset(List<VirtualLane> lanes, int gcd) {
        int[] count = new int[gcd];
        for (VirtualLane vl : lanes) {
            count[vl.offset()]++;
        }
        int best = 0;
        for (int i = 1; i < gcd; i++) {
            if (count[i] < count[best]) {
                best = i;
            }
        }
        return best;
    }

    /**
     * Maps each virtual lane to a physical lane index. Lanes with the same
     * offset get consecutive indices within that offset's physical slot
     * range, so the first lane of offset 0 is physical lane 0, the first
     * lane of offset 1 is physical lane {@code lanesPerMinute}, etc.
     */
    private void emitVirtualLanes(List<VirtualLane> virtualLanes,
                                  StartPlace startPlace,
                                  int gcd,
                                  int lanesPerMinute,
                                  int gapSlotsParam,
                                  int[] nextFreeSlot,
                                  List<TempPlacement> output) {
        int[] nextLaneWithinOffset = new int[gcd];
        for (VirtualLane vl : virtualLanes) {
            int offset = vl.offset();
            int physIdx = offset * lanesPerMinute + nextLaneWithinOffset[offset];
            nextLaneWithinOffset[offset]++;
            String laneId = startPlace.code() + "-" + (physIdx + 1);

            int cursor = 0;
            boolean firstSeriesOnLane = true;
            for (FirstControlGroup g : vl.groups()) {
                for (Series s : g.series()) {
                    int step = s.startIntervalMinutes() / gcd;
                    int leadingGap = firstSeriesOnLane ? 0 : gapSlotsParam * step;
                    int firstSlot = cursor + leadingGap;
                    int lastSlot = firstSlot + Math.max(0, s.competitors() - 1) * step;
                    output.add(new TempPlacement(s, physIdx, firstSlot, lastSlot, laneId));
                    cursor = lastSlot + 1;
                    firstSeriesOnLane = false;
                }
            }
            nextFreeSlot[physIdx] = cursor;
        }
    }

    private void emitWithSoftStart(List<TempPlacement> placements,
                                   int[] nextFreeSlot,
                                   int lanesPerMinute,
                                   int gcd,
                                   CompetitionParameters parameters,
                                   List<SeriesStart> output) {
        // Anchor each offset group on its own longest lane: that lane fires
        // immediately at its offset minute (no delay), guaranteeing every
        // offset minute has a starter going from the very first tick. Other
        // lanes within the same offset get a capped stagger so they don't
        // all fire together at minute zero. Empty minutes at the END of the
        // shorter offset group are OK — the spec prefers a busy start over
        // a busy finish.
        int[] longestPerOffset = new int[gcd];
        for (int i = 0; i < nextFreeSlot.length; i++) {
            if (nextFreeSlot[i] > 0) {
                int offset = i / lanesPerMinute;
                longestPerOffset[offset] = Math.max(longestPerOffset[offset],
                        nextFreeSlot[i] - 1);
            }
        }
        int maxDelaySlots = MAX_LANE_START_DELAY_MINUTES / Math.max(1, gcd);
        int[] shiftByLane = new int[nextFreeSlot.length];
        for (int i = 0; i < nextFreeSlot.length; i++) {
            if (nextFreeSlot[i] > 0) {
                int offset = i / lanesPerMinute;
                int slack = longestPerOffset[offset] - (nextFreeSlot[i] - 1);
                shiftByLane[i] = Math.min(slack, maxDelaySlots);
            }
        }

        for (TempPlacement p : placements) {
            int shift = shiftByLane[p.laneIndex()];
            int offsetMinutes = p.laneIndex() / lanesPerMinute;
            LocalTime firstStart = parameters.firstStart()
                    .plusMinutes((long) offsetMinutes + (long) (p.firstSlot() + shift) * gcd);
            LocalTime lastStart = parameters.firstStart()
                    .plusMinutes((long) offsetMinutes + (long) (p.lastSlot() + shift) * gcd);
            output.add(new SeriesStart(p.series().name(),
                    firstStart, lastStart, p.laneId(),
                    p.series().competitors(),
                    p.series().startIntervalMinutes(),
                    p.series().course().controlCodes()));
        }
    }

    private int gcdInterval(List<Series> seriesAtPlace, int defaultInterval) {
        int g = 0;
        for (Series s : seriesAtPlace) {
            int i = s.startIntervalMinutes();
            if (i > 0) {
                g = (g == 0) ? i : gcd(g, i);
            }
        }
        return g == 0 ? defaultInterval : g;
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    private List<FirstControlGroup> groupByFirstControl(List<Series> seriesAtPlace,
                                                        int gcd,
                                                        int gapSlotsParam) {
        Map<String, List<Series>> byFirstControl = new LinkedHashMap<>();
        for (Series s : seriesAtPlace) {
            String key = firstControlOf(s);
            byFirstControl.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }
        List<FirstControlGroup> result = new ArrayList<>(byFirstControl.size());
        byFirstControl.forEach((firstControl, members) ->
                result.add(new FirstControlGroup(firstControl, members, gcd, gapSlotsParam, null)));
        return result;
    }

    private String firstControlOf(Series s) {
        List<Integer> controls = s.course().controlCodes();
        return controls.isEmpty() ? "__none__:" + s.name() : controls.getFirst().toString();
    }

    /**
     * A pile of series sharing a first control. {@code requiredOffset} is
     * non-null only for split halves: it forces the bucket onto a virtual
     * lane assigned to that offset minute, so split halves of the same
     * bucket land on different physical minutes.
     */
    private record FirstControlGroup(String firstControl,
                                     List<Series> series,
                                     int gcd,
                                     int gapSlotsParam,
                                     Integer requiredOffset) {

        int totalLaneSlots() {
            int total = 0;
            boolean first = true;
            for (Series s : series) {
                int step = s.startIntervalMinutes() / gcd;
                if (!first) {
                    total += gapSlotsParam * step;
                }
                total += Math.max(1, s.competitors()) * step;
                first = false;
            }
            return total;
        }

        int firstStep() {
            return series.getFirst().startIntervalMinutes() / gcd;
        }
    }

    /**
     * Intermediate during BFD packing. A virtual lane is bound to a single
     * offset minute upfront so split halves can target specific minutes;
     * the unbound groups simply land in whichever offset has the least
     * lanes so far.
     */
    private static final class VirtualLane {
        private final int offset;
        private final List<FirstControlGroup> groups = new ArrayList<>();
        private int totalSlots = 0;

        VirtualLane(int offset) {
            this.offset = offset;
        }

        int offset() {
            return offset;
        }

        List<FirstControlGroup> groups() {
            return groups;
        }

        int totalSlotsAfterAdding(FirstControlGroup g, int gapSlotsParam) {
            int leadingGap = groups.isEmpty() ? 0 : gapSlotsParam * g.firstStep();
            return totalSlots + leadingGap + g.totalLaneSlots();
        }

        void add(FirstControlGroup g, int gapSlotsParam) {
            int leadingGap = groups.isEmpty() ? 0 : gapSlotsParam * g.firstStep();
            totalSlots += leadingGap + g.totalLaneSlots();
            groups.add(g);
        }
    }

    private record TempPlacement(Series series, int laneIndex, int firstSlot, int lastSlot,
                                 String laneId) {
    }
}
