package fi.virit.lahtokaavio.ui;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import fi.virit.lahtokaavio.domain.StartPlan;
import fi.virit.lahtokaavio.domain.WorkspaceSnapshot;
import fi.virit.lahtokaavio.io.NavisportCsvWriter;
import fi.virit.lahtokaavio.io.StartPlanCsvWriter;
import fi.virit.lahtokaavio.plan.StartPlanValidator;
import org.vaadin.firitin.appframework.MenuItem;
import tools.jackson.databind.ObjectMapper;

/**
 * Renders the generated {@link StartPlan} as a sortable grid plus two download
 * anchors: CSV for downstream results services, JSON for resuming the session
 * later. Registered dynamically (registerAtStartup = false) once the user
 * triggers plan generation from {@link CompetitionView}; the plan itself is
 * held in {@link CompetitionWorkspace}.
 */
@Route(registerAtStartup = false, layout = TopLayout.class)
@MenuItem(order = MenuItem.END, icon = VaadinIcon.GRID)
public class GridAndDownloadView extends VerticalLayout implements BeforeEnterObserver {

    private final StartPlanCsvWriter csvWriter = new StartPlanCsvWriter();
    private final NavisportCsvWriter navisportWriter = new NavisportCsvWriter();
    private final StartPlanGrid grid = new StartPlanGrid();
    private final PlanWarningsPanel warnings = new PlanWarningsPanel();
    private final Anchor csvDownload = new Anchor();
    private final Anchor jsonDownload = new Anchor();
    private final Checkbox navisportFormat = new NavisportFormatCheckbox();

    public GridAndDownloadView() {
        setSizeFull();
        add(new H2("Grid"));

        csvDownload.setText("Download CSV");
        jsonDownload.setText("Download Backup (JSON)");
        add(warnings, new HorizontalLayout(csvDownload, navisportFormat, jsonDownload), grid);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        StartPlan plan = CompetitionWorkspace.current().plan();
        if (plan == null) {
            event.forwardTo(CompetitionView.class);
            return;
        }
        show(plan);
    }

    private void show(StartPlan plan) {
        WorkspaceSnapshot snapshot = CompetitionWorkspace.current().snapshot();
        grid.setItems(plan.seriesStarts());
        warnings.show(new StartPlanValidator().validate(plan));
        csvDownload.setDownload(true);
        csvDownload.setHref(event -> {
            event.setContentType("text/csv");
            if (navisportFormat.getValue()) {
                event.setFileName("lahtoaikataulu-navisport.csv");
                navisportWriter.writeStartScheme(plan, event.getOutputStream());
            } else {
                event.setFileName("lahtokaavio.csv");
                csvWriter.writeBytes(plan, event.getOutputStream());
            }
        });

        jsonDownload.setDownload(true);
        jsonDownload.setHref(event -> {
            event.setContentType("application/json");
            new ObjectMapper().writeValue(event.getOutputStream(), snapshot);
        });
    }

    /** Navisport's start scheme: Navisport then draws the start times itself. */
    private static class NavisportFormatCheckbox extends Checkbox {
        NavisportFormatCheckbox() {
            super("Navisport format");
            setTooltipText("Class, first start and interval, for Navisport's start scheme "
                    + "import. Navisport can then draw the start times.");
        }
    }
}
