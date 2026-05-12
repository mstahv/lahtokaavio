package fi.virit.lahtokaavio.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.server.streams.UploadHandler;
import fi.virit.lahtokaavio.domain.CompetitionParameters;
import fi.virit.lahtokaavio.domain.Course;
import fi.virit.lahtokaavio.domain.WorkspaceSnapshot;
import fi.virit.lahtokaavio.io.CourseXmlReader;
import fi.virit.lahtokaavio.io.RegistrationsCsvReader;
import fi.virit.lahtokaavio.io.WorkspaceJsonReader;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "", layout = TopLayout.class)
@PageTitle("Kilpailu")
public class CompetitionView extends VerticalLayout implements AfterNavigationObserver {

    private final CourseXmlReader courseReader;
    private final RegistrationsCsvReader registrationsReader;
    private final WorkspaceJsonReader jsonReader;

    private SetupWizard wizard;

    public CompetitionView() {
        this(new CourseXmlReader(), new RegistrationsCsvReader(), new WorkspaceJsonReader());
    }

    @Autowired
    public CompetitionView(CourseXmlReader courseReader,
                           RegistrationsCsvReader registrationsReader,
                           WorkspaceJsonReader jsonReader) {
        this.courseReader = courseReader;
        this.registrationsReader = registrationsReader;
        this.jsonReader = jsonReader;
        setSizeFull();
        add(new H1("Lähtökaavio"));
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        renderContent();
    }

    private void renderContent() {
        // Drop everything except the H1 title and re-render based on state.
        while (getComponentCount() > 1) {
            remove(getComponentAt(1));
        }
        if (CompetitionWorkspace.current().plan() == null) {
            wizard = new SetupWizard();
            add(new Paragraph("Suunnistuskilpailun lähtöaikojen suunnitteluun."), wizard);
        } else {
            wizard = null;
            add(new PostPlanToolbar());
        }
    }

    private void onCoursesUploaded(byte[] xml, String fileName) {
        try {
            Map<String, Course> parsed = courseReader.read(new ByteArrayInputStream(xml));
            CompetitionWorkspace.current().addCourses(parsed);
            Notification.show("Loaded " + parsed.size() + " classes from " + fileName);
        } catch (RuntimeException e) {
            Notification.show("Failed to read " + fileName + ": " + e.getMessage());
        }
    }

    private void onRegistrationsUploaded(byte[] csv, String fileName) {
        try {
            var registrations = registrationsReader.read(new ByteArrayInputStream(csv));
            CompetitionWorkspace.current().setRegistrations(registrations);
            if (wizard != null) {
                wizard.parameters.populateDays(registrations.dayLabels());
            }
            Notification.show("Loaded registrations for " + registrations.dayLabels().size() + " day(s)");
        } catch (RuntimeException e) {
            Notification.show("Failed to read " + fileName + ": " + e.getMessage());
        }
    }

    private void onWorkspaceJsonUploaded(byte[] json, String fileName) {
        try {
            WorkspaceSnapshot snapshot = jsonReader.read(new ByteArrayInputStream(json));
            CompetitionWorkspace.current().hydrate(snapshot);
            if (snapshot.plan() != null) {
                Notification.show("Restored workspace from " + fileName);
                registerSessionRoutes();
                UI.getCurrent().navigate(PlanView.ROUTE);
            } else {
                Notification.show("Loaded workspace data — no plan yet, generate one");
                renderContent();
            }
        } catch (RuntimeException e) {
            Notification.show("Failed to read " + fileName + ": " + e.getMessage());
        }
    }

    private void runFirstGeneration() {
        CompetitionWorkspace workspace = CompetitionWorkspace.current();
        if (!workspace.hasRegistrations()) {
            Notification.show("Please upload registrations CSV first");
            return;
        }
        if (workspace.coursesByClass().isEmpty()) {
            Notification.show("Please upload at least one course XML first");
            return;
        }
        String day = wizard.parameters.daySelector.getValue();
        if (day == null) {
            Notification.show("Please select a competition day");
            return;
        }

        workspace.selectDay(day);
        workspace.setParameters(wizard.parameters.form.toParameters());

        regenerateAndNavigate();
    }

    private void regenerateAndNavigate() {
        List<String> missing = CompetitionWorkspace.current().regeneratePlan();
        if (!missing.isEmpty()) {
            Notification.show("Warning: no course found for " + missing);
        }
        registerSessionRoutes();
        UI.getCurrent().navigate(PlanView.ROUTE);
    }

    private void registerSessionRoutes() {
        RouteConfiguration sessionRoutes = RouteConfiguration.forSessionScope();
        if (!sessionRoutes.isRouteRegistered(PlanView.class)) {
            sessionRoutes.setAnnotatedRoute(PlanView.class);
        }
        if (!sessionRoutes.isRouteRegistered(EditCountsView.class)) {
            sessionRoutes.setAnnotatedRoute(EditCountsView.class);
        }
        if (!sessionRoutes.isRouteRegistered(GanttView.class)) {
            sessionRoutes.setAnnotatedRoute(GanttView.class);
        }
        findAncestor(TopLayout.class).buildMenu();
    }

    private class SetupWizard extends VerticalLayout {

        final FileInputSection files = new FileInputSection();
        final ParametersSection parameters = new ParametersSection();

        SetupWizard() {
            setPadding(false);
            add(files, parameters);
        }
    }

    private class FileInputSection extends VerticalLayout {

        FileInputSection() {
            setPadding(false);
            add(new H2("1. Load data"));

            Upload coursesUpload = new Upload(UploadHandler.inMemory(
                    (metadata, data) -> onCoursesUploaded(data, metadata.fileName())));
            coursesUpload.setAcceptedFileTypes("text/xml", "application/xml", ".xml");
            coursesUpload.setMaxFiles(10);
            coursesUpload.setDropLabel(new Paragraph("Course files (IOF XML, one or more)"));

            Upload registrationsUpload = new Upload(UploadHandler.inMemory(
                    (metadata, data) -> onRegistrationsUploaded(data, metadata.fileName())));
            registrationsUpload.setAcceptedFileTypes("text/csv", ".csv");
            registrationsUpload.setDropLabel(new Paragraph("Registrations CSV"));

            Upload jsonUpload = new Upload(UploadHandler.inMemory(
                    (metadata, data) -> onWorkspaceJsonUploaded(data, metadata.fileName())));
            jsonUpload.setAcceptedFileTypes("application/json", ".json");
            jsonUpload.setDropLabel(new Paragraph(
                    "…or resume from a saved Lähtökaavio JSON"));

            HorizontalLayout uploads = new HorizontalLayout(
                    coursesUpload, registrationsUpload, jsonUpload);
            uploads.setWidthFull();
            add(uploads);
        }
    }

    private class ParametersSection extends VerticalLayout {

        final ComboBox<String> daySelector = new ComboBox<>("Competition day");
        final ParametersForm form = new ParametersForm(CompetitionParameters.defaults());
        final Button generate = new Button("Generate start plan", e -> runFirstGeneration());

        ParametersSection() {
            setPadding(false);
            add(new H2("2. Parameters"));
            add(daySelector, form, generate);
        }

        void populateDays(java.util.Collection<String> dayLabels) {
            daySelector.setItems(dayLabels);
            dayLabels.stream().findFirst().ifPresent(daySelector::setValue);
        }
    }

    /**
     * Compact toolbar shown once an initial plan exists. Lets the user add
     * more course files, tweak the planner parameters that influence the
     * next regeneration, and trigger a confirmed re-optimization. Detailed
     * adjustments (counts, manual lane arrangement) live in their own views.
     */
    private class PostPlanToolbar extends VerticalLayout {

        final ParametersForm form;

        PostPlanToolbar() {
            setPadding(false);
            CompetitionWorkspace workspace = CompetitionWorkspace.current();
            form = new ParametersForm(workspace.parameters());
            // Persist edits straight to the workspace so they're in the JSON
            // snapshot even if the user never clicks Re-optimize.
            form.onChange(workspace::setParameters);

            Upload addCourses = new Upload(UploadHandler.inMemory(
                    (metadata, data) -> onCoursesUploaded(data, metadata.fileName())));
            addCourses.setAcceptedFileTypes("text/xml", "application/xml", ".xml");
            addCourses.setMaxFiles(10);
            addCourses.setDropLabel(new Paragraph("Add course files"));

            Button reoptimize = new Button("Re-optimize plan", e -> confirmAndReoptimize());

            add(new Paragraph(
                    "Initial plan generated. Tweak counts in the menu, drop in more "
                    + "course files, or adjust the parameters below, then re-run."));
            add(addCourses, form, reoptimize);
        }

        private void confirmAndReoptimize() {
            new ReoptimizeConfirmDialog(() -> {
                CompetitionWorkspace.current().setParameters(form.toParameters());
                regenerateAndNavigate();
            }).open();
        }
    }

    /**
     * Re-optimization warns the user before throwing away anything they
     * arranged by hand in the Gantt view. The dialog stays generic — it
     * doesn't know whether the user actually edited anything, only that
     * a fresh LPT pass will overwrite whatever is there.
     */
    private static class ReoptimizeConfirmDialog extends ConfirmDialog {
        ReoptimizeConfirmDialog(Runnable onConfirm) {
            setHeader("Re-optimize plan?");
            setText("Re-running the optimization rebuilds the plan from scratch using "
                    + "the current parameters and competitor counts. Any manual lane "
                    + "arrangements or item moves you made in the Gantt view will be lost.");
            setConfirmText("Re-optimize");
            setCancelable(true);
            setCancelText("Cancel");
            addConfirmListener(e -> onConfirm.run());
        }
    }
}
