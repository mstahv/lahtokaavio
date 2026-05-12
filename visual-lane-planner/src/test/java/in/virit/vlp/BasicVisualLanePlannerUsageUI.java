package in.virit.vlp;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Smallest possible demo of {@link VisualLanePlanner}: three lanes, a handful
 * of items, the planner wired so any drag/menu move surfaces as a Notification.
 * Used by Playwright IT and dev-mode visual inspection.
 */
@Route("basic")
public class BasicVisualLanePlannerUsageUI extends VerticalLayout {

    private final VisualLanePlanner planner = new VisualLanePlanner();

    public BasicVisualLanePlannerUsageUI() {
        add(new H2("Visual Lane Planner — basic"));
        add(new Paragraph("Three lanes, four items. Drag, or use the … menu."));

        planner.setPlan(samplePlan(), edited ->
                Notification.show("Plan changed: " + edited.items().size() + " items"));
        add(planner);
    }

    public VisualLanePlanner getPlanner() {
        return planner;
    }

    private static LanePlan samplePlan() {
        return new LanePlan(
                LocalTime.of(11, 0),
                2,
                List.of("LANE-1", "LANE-2", "LANE-3"),
                Map.of("LANE-1", 0, "LANE-2", 0, "LANE-3", 1),
                List.of(
                        new LaneItem("alpha", "LANE-1", "Alpha", 2, 5),
                        new LaneItem("beta", "LANE-1", "Beta", 2, 3),
                        new LaneItem("gamma", "LANE-2", "Gamma", 2, 4),
                        new LaneItem("delta", "LANE-3", "Delta", 1, 6)));
    }
}
