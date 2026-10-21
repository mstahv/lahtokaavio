package fi.virit.lahtokaavio.domain;

import java.util.List;

/**
 * Result of a start time draw.
 *
 * @param starts             every drawn start, ordered by start time and lane
 * @param overbookedClasses  classes with more entries than planned starts; the
 *                           extra competitors start after the planned last start
 * @param unplannedClasses   classes with entries but no place in the plan; their
 *                           competitors are left out of the draw
 */
public record StartDraw(List<DrawnStart> starts, List<String> overbookedClasses,
        List<String> unplannedClasses) {

    public StartDraw {
        starts = List.copyOf(starts);
        overbookedClasses = List.copyOf(overbookedClasses);
        unplannedClasses = List.copyOf(unplannedClasses);
    }
}
