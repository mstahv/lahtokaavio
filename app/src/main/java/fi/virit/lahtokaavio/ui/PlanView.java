package fi.virit.lahtokaavio.ui;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import fi.virit.lahtokaavio.domain.StartPlan;
import fi.virit.lahtokaavio.io.StartPlanCsvWriter;
import fi.virit.lahtokaavio.io.WorkspaceJsonWriter;
import java.io.ByteArrayInputStream;

/**
 * Renders the generated {@link StartPlan} as a sortable grid plus two download
 * anchors: CSV for downstream results services, JSON for resuming the session
 * later. Registered dynamically (registerAtStartup = false) once the user
 * triggers plan generation from {@link CompetitionView}; the plan itself is
 * held in {@link CompetitionWorkspace}.
 */
@Route(registerAtStartup = false, layout = TopLayout.class)
public class PlanView extends VerticalLayout implements BeforeEnterObserver {

    public static final String ROUTE = "plan";

    private final StartPlanCsvWriter csvWriter;
    private final WorkspaceJsonWriter jsonWriter;
    private final StartPlanGrid grid = new StartPlanGrid();
    private final Anchor csvDownload = new Anchor();
    private final Anchor jsonDownload = new Anchor();

    public PlanView() {
        this(new StartPlanCsvWriter(), new WorkspaceJsonWriter());
    }

    public PlanView(StartPlanCsvWriter csvWriter, WorkspaceJsonWriter jsonWriter) {
        this.csvWriter = csvWriter;
        this.jsonWriter = jsonWriter;
        setSizeFull();
        add(new H2("Plan"));

        csvDownload.setText("Download CSV");
        jsonDownload.setText("Download JSON (resumable)");
        add(new HorizontalLayout(csvDownload, jsonDownload), grid);
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
        grid.setItems(plan.seriesStarts());
        DownloadHandler csvHandler = DownloadHandler.fromInputStream(event ->
                new DownloadResponse(
                        new ByteArrayInputStream(csvWriter.writeBytes(plan)),
                        "lahtokaavio.csv",
                        "text/csv",
                        -1));
        csvDownload.setHref(csvHandler);

        DownloadHandler jsonHandler = DownloadHandler.fromInputStream(event ->
                new DownloadResponse(
                        new ByteArrayInputStream(
                                jsonWriter.writeBytes(CompetitionWorkspace.current().snapshot())),
                        "lahtokaavio.json",
                        "application/json",
                        -1));
        jsonDownload.setHref(jsonHandler);
    }
}
