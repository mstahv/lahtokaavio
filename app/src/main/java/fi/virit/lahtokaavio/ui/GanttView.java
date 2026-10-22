package fi.virit.lahtokaavio.ui;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import fi.virit.lahtokaavio.domain.CompetitionParameters;
import fi.virit.lahtokaavio.domain.PlanWarning;
import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.domain.StartPlan;
import in.virit.vlp.LaneItem;
import in.virit.vlp.LanePlan;
import in.virit.vlp.VisualLanePlanner;
import org.vaadin.firitin.appframework.MenuItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Hosts the {@link VisualLanePlanner} add-on against the orienteering plan in
 * {@link CompetitionWorkspace}. Translates {@link SeriesStart} ↔ {@link LaneItem}
 * at the view boundary so the add-on stays domain-agnostic.
 */
@PageTitle("Lähtökaavio")
@MenuItem(icon = VaadinIcon.RECYCLE)
@Route(registerAtStartup = false, layout = TopLayout.class)
public class GanttView extends VerticalLayout implements BeforeEnterObserver {

    public static final String ROUTE = "gantt";

    private final VisualLanePlanner planner = new VisualLanePlanner();
    private final PlanWarningsPanel warnings = new PlanWarningsPanel();
    /** Side-map: series name → its course controls, used when we rebuild a StartPlan. */
    private final Map<String, List<Integer>> controlsBySeries = new LinkedHashMap<>();

    public GanttView() {
        setSizeFull();
        add(new H2("Lähtökaavio"));
        add(new Paragraph(
                "Drag a series onto another lane, or right-click (long-press) the series box. "
                + "The pencil next to a lane renames it or sets its start by hand. "
                + "Lanes are re-timed automatically, and the plan is checked after every "
                + "change. Use Re-optimize from the entry view to reset to the algorithm's "
                + "choice."));
        add(warnings, planner);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        StartPlan plan = CompetitionWorkspace.current().plan();
        if (plan == null) {
            event.forwardTo(CompetitionView.class);
            return;
        }
        LanePlan lanePlan = toLanePlan(plan);
        planner.setPlan(lanePlan, this::onPlanEdited);
        check();
    }

    private void onPlanEdited(LanePlan edited) {
        StartPlan rebuilt = toStartPlan(edited);
        CompetitionWorkspace.current().setPlan(rebuilt);
        check();
    }

    /** Lists the broken rules above the chart and outlines the classes involved. */
    private void check() {
        List<PlanWarning> found = CompetitionWorkspace.current().planWarnings();
        warnings.show(found);
        Map<String, String> byClass = new LinkedHashMap<>();
        for (PlanWarning w : found) {
            w.series().forEach(series -> byClass.merge(series, w.message(), (a, b) -> a + "\n" + b));
        }
        planner.setItemWarnings(byClass);
    }

    // --- Mapping ---------------------------------------------------------

    private LanePlan toLanePlan(StartPlan plan) {
        controlsBySeries.clear();
        CompetitionParameters params = plan.parameters();
        TreeSet<String> laneIdsSorted =
                new TreeSet<>(fi.virit.lahtokaavio.plan.StartPlanGenerator.LANE_ID_NATURAL);

        for (SeriesStart s : plan.seriesStarts()) {
            laneIdsSorted.add(s.laneId());
            controlsBySeries.put(s.seriesName(), s.controlCodes());
        }

        // Per-lane offset: take the actual first start time of the lane's
        // earliest series. This bakes in the soft-start delay the planner
        // already applied, so the Gantt visualisation matches the plan grid
        // exactly. Pure laneIndex/maxPerMin would only show the rhythmic
        // offset (0 or 1 min) and miss the staggered start.
        Map<String, Integer> laneOffsets = new LinkedHashMap<>();
        for (String laneId : laneIdsSorted) {
            java.time.LocalTime earliestInLane = plan.seriesStarts().stream()
                    .filter(s -> s.laneId().equals(laneId))
                    .map(SeriesStart::firstStartTime)
                    .min(java.time.LocalTime::compareTo)
                    .orElse(params.firstStart());
            laneOffsets.put(laneId,
                    (int) java.time.Duration.between(params.firstStart(), earliestInLane).toMinutes());
        }

        Map<String, List<LaneItem>> grouped = new LinkedHashMap<>();
        for (String laneId : laneIdsSorted) {
            grouped.put(laneId, new ArrayList<>());
        }
        plan.seriesStarts().stream()
                .sorted(Comparator.comparing(SeriesStart::firstStartTime))
                .forEach(s -> grouped.get(s.laneId()).add(toLaneItem(s)));

        List<LaneItem> ordered = new ArrayList<>();
        grouped.values().forEach(ordered::addAll);

        return new LanePlan(
                params.firstStart(),
                params.gapSlotsBetweenSeries(),
                new ArrayList<>(laneIdsSorted),
                laneOffsets,
                ordered);
    }

    private StartPlan toStartPlan(LanePlan lanePlan) {
        List<SeriesStart> placements = new ArrayList<>(lanePlan.items().size());
        for (LaneItem item : lanePlan.items()) {
            placements.add(new SeriesStart(
                    item.id(),
                    planner.firstStartOf(item.id()),
                    planner.lastStartOf(item.id()),
                    item.laneId(),
                    item.unitCount(),
                    item.stepMinutes(),
                    controlsBySeries.getOrDefault(item.id(), List.of())));
        }
        placements.sort(Comparator.comparing(SeriesStart::laneId,
                        fi.virit.lahtokaavio.plan.StartPlanGenerator.LANE_ID_NATURAL)
                .thenComparing(SeriesStart::firstStartTime));
        return new StartPlan(CompetitionWorkspace.current().parameters(), placements);
    }

    /**
     * Builds a {@link LaneItem} from a {@link SeriesStart}: the first control
     * code doubles as the colour group key (same control → same colour) and
     * also shows up in the tooltip so the user can confirm at a glance which
     * physical start point a series uses.
     */
    private LaneItem toLaneItem(SeriesStart s) {
        Integer firstControl = s.controlCodes().isEmpty() ? null : s.controlCodes().getFirst();
        String tooltipExtra = firstControl != null ? "first control: " + firstControl : null;
        return new LaneItem(
                s.seriesName(), s.laneId(), s.seriesName(),
                s.startIntervalMinutes(), s.competitors(),
                firstControl != null ? firstControl : 0, tooltipExtra);
    }

}
