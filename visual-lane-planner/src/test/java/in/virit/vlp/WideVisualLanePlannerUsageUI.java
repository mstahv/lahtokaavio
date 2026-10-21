package in.virit.vlp;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A plan about the size of a real competition (eight lanes, a bit over an
 * hour of starts), for checking how the planner uses the available width.
 */
@Route("wide")
public class WideVisualLanePlannerUsageUI extends VerticalLayout {

    private final VisualLanePlanner planner = new VisualLanePlanner();

    public WideVisualLanePlannerUsageUI() {
        setSizeFull();
        add(new H2("Visual Lane Planner — wide"));
        planner.setPlan(widePlan(), edited -> { });
        add(planner);
    }

    public VisualLanePlanner getPlanner() {
        return planner;
    }

    static LanePlan widePlan() {
        List<String> lanes = new ArrayList<>();
        Map<String, Integer> offsets = new LinkedHashMap<>();
        List<LaneItem> items = new ArrayList<>();
        for (int lane = 1; lane <= 8; lane++) {
            String laneId = "STA1-" + lane;
            lanes.add(laneId);
            offsets.put(laneId, lane % 2);
            for (int i = 0; i < 4; i++) {
                String id = "S" + lane + "-" + i;
                items.add(new LaneItem(id, laneId, id, 2, 6 + (lane + i) % 4, 100 + lane * 4 + i, null));
            }
        }
        return new LanePlan(LocalTime.of(11, 0), 2, lanes, offsets, items);
    }
}
