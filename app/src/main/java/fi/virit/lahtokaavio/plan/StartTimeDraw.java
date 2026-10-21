package fi.virit.lahtokaavio.plan;

import fi.virit.lahtokaavio.domain.DrawnStart;
import fi.virit.lahtokaavio.domain.Entry;
import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.domain.StartDraw;
import fi.virit.lahtokaavio.domain.StartPlan;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Draws the start order within each class. The competitors of a class are
 * shuffled into its planned start slots: the first one starts at the class'
 * first start time, the next one a start interval later, and so on.
 *
 * <p>Entries usually change after the plan is made. Late entries in a class
 * get the slots after the planned last start (the class is reported as
 * overbooked, so the organiser can check the lane for clashes), and unused
 * slots at the end are left vacant.
 */
public final class StartTimeDraw {

    private final Random random;

    public StartTimeDraw(Random random) {
        this.random = random;
    }

    public StartDraw draw(StartPlan plan, List<Entry> entries) {
        Map<String, List<Entry>> byClass = new LinkedHashMap<>();
        for (Entry entry : entries) {
            byClass.computeIfAbsent(entry.className(), k -> new ArrayList<>()).add(entry);
        }

        List<DrawnStart> starts = new ArrayList<>();
        List<String> overbooked = new ArrayList<>();
        for (SeriesStart series : plan.seriesStarts()) {
            List<Entry> competitors = byClass.remove(series.seriesName());
            if (competitors == null) {
                continue;
            }
            Collections.shuffle(competitors, random);
            if (competitors.size() > series.competitors()) {
                overbooked.add(series.seriesName());
            }
            LocalTime time = series.firstStartTime();
            for (Entry competitor : competitors) {
                starts.add(new DrawnStart(time, series.laneId(), competitor));
                time = time.plusMinutes(series.startIntervalMinutes());
            }
        }
        starts.sort(Comparator.comparing(DrawnStart::startTime)
                .thenComparing(DrawnStart::laneId, StartPlanGenerator.LANE_ID_NATURAL));
        return new StartDraw(starts, overbooked, new ArrayList<>(byClass.keySet()));
    }
}
