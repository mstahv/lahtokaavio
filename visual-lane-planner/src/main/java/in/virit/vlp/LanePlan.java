package in.virit.vlp;

import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable input to {@link VisualLanePlanner}. The list of {@link LaneItem}s
 * is in render order within each lane; lanes are rendered in {@code laneIds}
 * order regardless of whether they currently host items (empty lanes still
 * appear as drop targets).
 *
 * <p>Times for each item are computed from this snapshot rather than stored
 * per-item, so manual moves remain trivially correct: the planner just
 * re-derives positions from the new ordering.
 */
public record LanePlan(
        LocalTime origin,
        int gapBetweenItemsInSteps,
        List<String> laneIds,
        Map<String, Integer> laneOffsetMinutes,
        List<LaneItem> items) {

    public LanePlan {
        laneIds = List.copyOf(laneIds);
        laneOffsetMinutes = Collections.unmodifiableMap(new LinkedHashMap<>(laneOffsetMinutes));
        items = List.copyOf(items);
    }
}
