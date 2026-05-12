package fi.virit.lahtokaavio.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
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
import fi.virit.lahtokaavio.domain.Course;
import fi.virit.lahtokaavio.domain.WorkspaceSnapshot;
import fi.virit.lahtokaavio.io.CourseXmlReader;
import fi.virit.lahtokaavio.io.DemoFiles;
import fi.virit.lahtokaavio.io.RegistrationsCsvReader;
import fi.virit.lahtokaavio.io.WorkspaceJsonReader;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.vaadin.firitin.appframework.MenuItem;

@Route(value = "", layout = TopLayout.class)
@PageTitle("Kilpailu")
@MenuItem(order = MenuItem.BEGINNING)
public class CompetitionView extends VerticalLayout implements AfterNavigationObserver {

    private final CourseXmlReader courseReader;
    private final RegistrationsCsvReader registrationsReader;
    private final WorkspaceJsonReader jsonReader;

    private InitialWizard phase1;
    private ParametersPhase phase2;

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

    /**
     * Three terminal screens picked by current workspace state:
     * <ol>
     *   <li>Plan exists → {@link PostPlanToolbar} (add courses, re-optimize, reset).</li>
     *   <li>Courses + registrations loaded → {@link ParametersPhase} (parameters, generate).</li>
     *   <li>Otherwise → {@link InitialWizard} with the three load-data options.</li>
     * </ol>
     */
    private void renderContent() {
        while (getComponentCount() > 1) {
            remove(getComponentAt(1));
        }
        phase1 = null;
        phase2 = null;
        CompetitionWorkspace workspace = CompetitionWorkspace.current();
        if (workspace.plan() != null) {
            add(new PostPlanToolbar());
        } else if (hasMinimumData(workspace)) {
            phase2 = new ParametersPhase();
            add(phase2);
        } else {
            phase1 = new InitialWizard();
            add(new Paragraph("Suunnistuskilpailun lähtöaikojen suunnitteluun."), phase1);
        }
    }

    /**
     * A hydrated-but-planless workspace (Option B without a plan) has counts
     * populated even when the live {@code Registrations} object is null, so
     * we admit that case to Phase 2 as well.
     */
    private boolean hasMinimumData(CompetitionWorkspace workspace) {
        if (workspace.coursesByClass().isEmpty()) {
            return false;
        }
        return workspace.hasRegistrations() || !workspace.counts().isEmpty();
    }

    private void onCoursesUploaded(byte[] xml, String fileName) {
        try {
            Map<String, Course> parsed = courseReader.read(new ByteArrayInputStream(xml));
            CompetitionWorkspace.current().addCourses(parsed);
            Notification.show("Loaded " + parsed.size() + " classes from " + fileName);
            if (phase1 != null) {
                phase1.uploadOption.refreshContinueEnabled();
            }
        } catch (RuntimeException e) {
            Notification.show("Failed to read " + fileName + ": " + e.getMessage());
        }
    }

    private void onRegistrationsUploaded(byte[] csv, String fileName) {
        try {
            var registrations = registrationsReader.read(new ByteArrayInputStream(csv));
            CompetitionWorkspace.current().setRegistrations(registrations);
            Notification.show("Loaded registrations for " + registrations.dayLabels().size() + " day(s)");
            if (phase1 != null) {
                phase1.uploadOption.refreshContinueEnabled();
            }
            if (phase2 != null) {
                phase2.populateDays(registrations.dayLabels());
            }
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
                UI.getCurrent().navigate(GanttView.class);
            } else {
                Notification.show("Loaded workspace data — no plan yet, generate one");
                renderContent();
            }
        } catch (RuntimeException e) {
            Notification.show("Failed to read " + fileName + ": " + e.getMessage());
        }
    }

    private void loadDemoFiles() {
        onCoursesUploaded(DemoFiles.read(DemoFiles.COURSES_L1), "Viking-Line-L1.xml");
        onCoursesUploaded(DemoFiles.read(DemoFiles.COURSES_L2), "Viking-Line-L2.xml");
        onCoursesUploaded(DemoFiles.read(DemoFiles.COURSES_RRTR), "Viking-Line-RRTR.xml");
        onRegistrationsUploaded(DemoFiles.read(DemoFiles.REGISTRATIONS), "ilmoittautumiset.csv");
        renderContent();
    }

    private void regenerateAndNavigate() {
        List<String> missing = CompetitionWorkspace.current().regeneratePlan();
        if (!missing.isEmpty()) {
            Notification.show("Warning: no course found for " + missing);
        }
        registerSessionRoutes();
        UI.getCurrent().navigate(GanttView.class);
    }

    private void registerSessionRoutes() {
        RouteConfiguration sessionRoutes = RouteConfiguration.forSessionScope();
        if (!sessionRoutes.isRouteRegistered(GridAndDownloadView.class)) {
            sessionRoutes.setAnnotatedRoute(GridAndDownloadView.class);
        }
        if (!sessionRoutes.isRouteRegistered(EditCountsView.class)) {
            sessionRoutes.setAnnotatedRoute(EditCountsView.class);
        }
        if (!sessionRoutes.isRouteRegistered(GanttView.class)) {
            sessionRoutes.setAnnotatedRoute(GanttView.class);
        }
        findAncestor(TopLayout.class).buildMenu();
    }

    // --- Phase 1 ---------------------------------------------------------

    /**
     * Phase 1: three mutually exclusive ways to bring data into the
     * workspace. Options B and C auto-advance to Phase 2 once they finish;
     * Option A asks the user to click "Continue" so they can drop in
     * multiple course files first.
     */
    private class InitialWizard extends VerticalLayout {

        final UploadOption uploadOption = new UploadOption();

        InitialWizard() {
            setPadding(false);
            add(new H2("Load data"));
            add(uploadOption,
                    new OrSeparator(),
                    new ResumeOption(),
                    new OrSeparator(),
                    new DemoOption());
        }
    }

    /** Option A: upload course XMLs plus a registrations CSV from scratch. */
    private class UploadOption extends VerticalLayout {

        private final Button continueButton =
                new Button("Continue", e -> renderContent());

        UploadOption() {
            setPadding(false);
            setSpacing(false);
            add(new H3("Option A — Upload data files"));
            add(new Paragraph(
                    "Drop in the course XML (one or more files in IOF v2 or v3 format) "
                    + "and the registrations CSV exported from Pirilä, then click Continue."));

            Upload coursesUpload = new Upload(UploadHandler.inMemory(
                    (metadata, data) -> onCoursesUploaded(data, metadata.fileName())));
            coursesUpload.setAcceptedFileTypes("text/xml", "application/xml", ".xml");
            coursesUpload.setMaxFiles(10);
            coursesUpload.setDropLabel(new Paragraph("Course files (IOF XML, one or more)"));

            Upload registrationsUpload = new Upload(UploadHandler.inMemory(
                    (metadata, data) -> onRegistrationsUploaded(data, metadata.fileName())));
            registrationsUpload.setAcceptedFileTypes("text/csv", ".csv");
            registrationsUpload.setDropLabel(new Paragraph("Registrations CSV"));

            HorizontalLayout uploads = new HorizontalLayout(coursesUpload, registrationsUpload);
            uploads.setWidthFull();
            add(uploads, continueButton);
            refreshContinueEnabled();
        }

        void refreshContinueEnabled() {
            CompetitionWorkspace workspace = CompetitionWorkspace.current();
            continueButton.setEnabled(workspace.hasRegistrations()
                    && !workspace.coursesByClass().isEmpty());
        }
    }

    /** Option B: resume an earlier session from its saved JSON snapshot. */
    private class ResumeOption extends VerticalLayout {
        ResumeOption() {
            setPadding(false);
            setSpacing(false);
            add(new H3("Option B — Resume a saved session"));
            add(new Paragraph(
                    "Re-loads everything (courses, counts, parameters and the plan itself) "
                    + "from a Lähtökaavio JSON download."));

            Upload jsonUpload = new Upload(UploadHandler.inMemory(
                    (metadata, data) -> onWorkspaceJsonUploaded(data, metadata.fileName())));
            jsonUpload.setAcceptedFileTypes("application/json", ".json");
            jsonUpload.setDropLabel(new Paragraph("Saved Lähtökaavio JSON"));
            add(jsonUpload);
        }
    }

    /**
     * Option C: skip the file dance entirely and load the bundled
     * Viking-Line sample data. Auto-advances to Phase 2 — there's no
     * "Continue" step because the demo loads everything at once.
     */
    private class DemoOption extends VerticalLayout {
        DemoOption() {
            setPadding(false);
            setSpacing(false);
            add(new H3("Option C — Try the demo data"));
            add(new Paragraph(
                    "Loads the bundled Viking-Line course files and registrations so you "
                    + "can see the whole workflow without any of your own data."));
            Button useDemo = new Button("Use demo files", e -> loadDemoFiles());
            add(useDemo);
        }
    }

    /** Visual "or" divider between Phase 1's three option cards. */
    private static class OrSeparator extends HorizontalLayout {
        OrSeparator() {
            setWidthFull();
            setAlignItems(Alignment.CENTER);
            Hr left = new Hr();
            Hr right = new Hr();
            left.getStyle().setFlexGrow("1");
            right.getStyle().setFlexGrow("1");
            Span label = new Span("or");
            label.getStyle()
                    .setMargin("0 var(--lumo-space-m)")
                    .setColor("var(--lumo-secondary-text-color)");
            add(left, label, right);
        }
    }

    // --- Phase 2 ---------------------------------------------------------

    /**
     * Phase 2: parameters + generate. Reached after Phase 1 completes (or
     * after the user clicks "Aloita alusta" on the post-plan toolbar and
     * still has data loaded). Mirrors {@link PostPlanToolbar} but with a
     * day picker and the initial Generate button instead of Re-optimize.
     */
    private class ParametersPhase extends VerticalLayout {

        private final ComboBox<String> daySelector = new ComboBox<>("Competition day");
        private final ParametersForm form;
        private final Button generate = new Button("Generate Initial Start Plan",
                e -> runFirstGeneration());

        ParametersPhase() {
            setPadding(false);
            CompetitionWorkspace workspace = CompetitionWorkspace.current();
            form = new ParametersForm(workspace.parameters());
            form.onChange(workspace::setParameters);

            Upload addCourses = new Upload(UploadHandler.inMemory(
                    (metadata, data) -> onCoursesUploaded(data, metadata.fileName())));
            addCourses.setAcceptedFileTypes("text/xml", "application/xml", ".xml");
            addCourses.setMaxFiles(10);
            addCourses.setDropLabel(new Paragraph("Add more course files"));

            Button startOver = new Button("Aloita alusta", e -> confirmAndReset());

            daySelector.addValueChangeListener(e -> refreshGenerateEnabled());
            if (workspace.registrations() != null) {
                populateDays(workspace.registrations().dayLabels());
            } else if (workspace.selectedDay() != null) {
                // Resumed session: keep the day from the snapshot even
                // though we no longer have the full day list.
                daySelector.setItems(workspace.selectedDay());
                daySelector.setValue(workspace.selectedDay());
            }

            add(new Paragraph(
                    "Pick the competition day, fine-tune the optimiser knobs, then run "
                    + "the planner. You can still come back and re-optimize, change "
                    + "competitor counts or rearrange lanes from the top menu after the "
                    + "first plan is out."));
            add(addCourses, daySelector, form,
                    new HorizontalLayout(generate, startOver));
            refreshGenerateEnabled();
        }

        void populateDays(java.util.Collection<String> dayLabels) {
            daySelector.setItems(dayLabels);
            dayLabels.stream().findFirst().ifPresent(daySelector::setValue);
            refreshGenerateEnabled();
        }

        private void refreshGenerateEnabled() {
            CompetitionWorkspace workspace = CompetitionWorkspace.current();
            boolean ready = !workspace.coursesByClass().isEmpty()
                    && daySelector.getValue() != null
                    && (workspace.hasRegistrations() || !workspace.counts().isEmpty());
            generate.setEnabled(ready);
        }

        private void runFirstGeneration() {
            CompetitionWorkspace workspace = CompetitionWorkspace.current();
            String day = daySelector.getValue();
            if (workspace.hasRegistrations()) {
                workspace.selectDay(day);
            }
            workspace.setParameters(form.toParameters());
            regenerateAndNavigate();
        }

        private void confirmAndReset() {
            new ResetConfirmDialog(() -> {
                CompetitionWorkspace.current().reset();
                renderContent();
            }).open();
        }
    }

    // --- Post-plan -------------------------------------------------------

    /**
     * Toolbar shown once an initial plan exists. Lets the user add more
     * course files, tweak the planner parameters that influence the next
     * regeneration, and trigger a confirmed re-optimization. Detailed
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
            Button startOver = new Button("Aloita alusta", e -> confirmAndReset());

            add(new Paragraph(
                    "Initial plan generated. Tweak counts in the menu, drop in more "
                    + "course files, or adjust the parameters below, then re-run."));
            add(addCourses, form, new HorizontalLayout(reoptimize, startOver));
        }

        private void confirmAndReoptimize() {
            new ReoptimizeConfirmDialog(() -> {
                CompetitionWorkspace.current().setParameters(form.toParameters());
                regenerateAndNavigate();
            }).open();
        }

        private void confirmAndReset() {
            new ResetConfirmDialog(() -> {
                CompetitionWorkspace.current().reset();
                renderContent();
            }).open();
        }
    }

    /**
     * Drops every loaded course, registration list and generated plan so the
     * user can plan a fresh competition. Phrased as a hard reset because
     * there's no undo once confirmed — the JSON download from the plan view
     * is the user's only escape hatch.
     */
    private static class ResetConfirmDialog extends ConfirmDialog {
        ResetConfirmDialog(Runnable onConfirm) {
            setHeader("Aloita alusta?");
            setText("Tyhjentää kaikki ladatut sarjat, ilmoittautumiset ja luodun "
                    + "lähtökaavion. Tämän jälkeen voit ladata uudet tiedostot "
                    + "puhtaalta pöydältä. Lataa lähtökaavio JSON-tiedostona "
                    + "ennen kuin jatkat, jos haluat säilyttää nykyisen suunnitelman.");
            setConfirmText("Aloita alusta");
            setCancelable(true);
            setCancelText("Peruuta");
            addConfirmListener(e -> onConfirm.run());
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
