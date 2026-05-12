package fi.virit.lahtokaavio.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets the user tweak per-class competitor counts after the initial plan has
 * been generated. Editing only resizes queues (lanes themselves are kept) and
 * triggers a regenerate via {@link CompetitionWorkspace}.
 */
@Route(registerAtStartup = false, layout = TopLayout.class)
public class EditCountsView extends VerticalLayout implements BeforeEnterObserver {

    public static final String ROUTE = "edit-counts";

    private final CountFieldList fields = new CountFieldList();
    private final Button apply = new Button("Apply and re-optimize", e -> apply());
    private final Button cancel = new Button("Back to plan",
            e -> UI.getCurrent().navigate(PlanView.ROUTE));

    public EditCountsView() {
        setSizeFull();
        add(new H2("Edit competitor counts"));
        add(new Paragraph(
                "Changes only resize the existing queues — the lane layout stays the same."));
        add(fields);
        add(new HorizontalLayout(apply, cancel));
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

    private void apply() {
        CompetitionWorkspace workspace = CompetitionWorkspace.current();
        fields.snapshot().forEach(workspace::setCount);
        List<String> missing = workspace.regeneratePlan();
        if (!missing.isEmpty()) {
            Notification.show("Warning: no course found for " + missing);
        }
        UI.getCurrent().navigate(PlanView.ROUTE);
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
