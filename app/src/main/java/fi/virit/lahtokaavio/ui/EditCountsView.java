package fi.virit.lahtokaavio.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.vaadin.firitin.appframework.MenuItem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lets the user tweak per-class competitor counts after the initial plan has
 * been generated: either keeping the lanes and only re-timing them, or
 * re-optimising the whole plan, via {@link CompetitionWorkspace}.
 */
@PageTitle("Sarjat")
@MenuItem(order = MenuItem.BEGINNING + 10, icon = VaadinIcon.GRID_SMALL)
@Route(registerAtStartup = false, layout = TopLayout.class)
public class EditCountsView extends VerticalLayout implements BeforeEnterObserver {

    public static final String ROUTE = "edit-counts";

    private final CountFieldList fields = new CountFieldList();
    private final Button apply = new Button("Apply", e -> apply(false));
    private final Button reoptimize = new Button("Apply and re-optimize", e -> apply(true));
    private final Button cancel = new Button("Back to plan",
            e -> UI.getCurrent().navigate(GridAndDownloadView.class));

    public EditCountsView() {
        setSizeFull();
        add(new H2("Sarjat"));
        add(new Paragraph("Apply keeps the lanes and the order of the classes, also your "
                + "edits in the Gantt view, and only re-times the classes: the competition gets "
                + "as long as it gets. Apply and re-optimize plans the lanes again from scratch."));
        add(fields);
        apply.addThemeVariants(ButtonVariant.PRIMARY);
        add(new HorizontalLayout(apply, reoptimize, cancel));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        CompetitionWorkspace workspace = CompetitionWorkspace.current();
        if (workspace.plan() == null) {
            event.forwardTo(CompetitionView.class);
            return;
        }
        fields.populate(workspace.counts());
    }

    private void apply(boolean reoptimize) {
        CompetitionWorkspace workspace = CompetitionWorkspace.current();
        fields.snapshot().forEach(workspace::setCount);
        ProblemNotification.showPlanReport(reoptimize
                ? workspace.regeneratePlan()
                : workspace.applyCountsKeepingLanes());
        UI.getCurrent().navigate(GridAndDownloadView.class);
    }

    /**
     * Vertical stack of one {@link PositiveIntegerField} per class, kept in
     * insertion order so the user sees them as the CSV listed them.
     */
    private static class CountFieldList extends VerticalLayout {

        private final Map<String, PositiveIntegerField> byClass = new LinkedHashMap<>();

        CountFieldList() {
            setPadding(false);
            setSpacing(false);
        }

        void populate(Map<String, Integer> counts) {
            removeAll();
            byClass.clear();
            counts.forEach((className, count) -> {
                PositiveIntegerField field = new PositiveIntegerField(className, count, 0);
                byClass.put(className, field);
                add(field);
            });
        }

        Map<String, Integer> snapshot() {
            Map<String, Integer> result = new LinkedHashMap<>();
            byClass.forEach((className, field) -> {
                Integer value = field.getValue();
                result.put(className, value == null ? 0 : value);
            });
            return result;
        }
    }
}
