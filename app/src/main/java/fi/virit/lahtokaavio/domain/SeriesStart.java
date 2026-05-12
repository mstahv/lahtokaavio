package fi.virit.lahtokaavio.domain;

import java.time.LocalTime;
import java.util.List;

/**
 * Result of planning: a single series' start window on its assigned lane.
 * laneId is a running integer derived from the start place name + lane index.
 * {@code competitors} and {@code startIntervalMinutes} are carried alongside
 * so downstream views (Gantt, CSV writer, JSON) can rebuild the schedule
 * without consulting the original {@link Series} collection.
 */
public record SeriesStart(
        String seriesName,
        LocalTime firstStartTime,
        LocalTime lastStartTime,
        String laneId,
        int competitors,
        int startIntervalMinutes,
        List<String> controlCodes) {

    public SeriesStart {
        controlCodes = List.copyOf(controlCodes);
    }
}
