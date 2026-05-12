package fi.virit.lahtokaavio.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.browserless.BrowserlessExtension;
import com.vaadin.browserless.TesterWrappers;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxTester;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTester;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadTester;
import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.ui.helpers.SampleData;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * End-to-end browserless tests of the vertical slice. Each test starts from a
 * fresh session; shared setup steps are factored into helper methods.
 *
 * Uses the Vaadin 25.2 composition-style {@link BrowserlessExtension} — no
 * superclass inheritance — per the project spec. Without a Spring context the
 * views are constructed via their no-arg fallback constructors, which wire
 * real (not mocked) collaborators.
 */
class CompetitionVerticalSliceTest implements TesterWrappers {

    @RegisterExtension
    BrowserlessExtension ext = new BrowserlessExtension();

    @Test
    void uploadGenerateDownload() {
        loadSampleDataAndGenerate();

        // Generation lands the user on the Gantt view; the grid + download
        // anchors live on PlanView (the "Grid" tab in the top menu).
        ext.navigate(GridAndDownloadView.class);

        @SuppressWarnings("unchecked")
        Grid<SeriesStart> grid = (Grid<SeriesStart>) ext.$(Grid.class).single();
        long rowsInData = grid.getDataProvider().size(new com.vaadin.flow.data.provider.Query<>());
        // 42 classes in the CSV; the sample data has a handful with no matching
        // course (e.g. the well-known L1 typo "D 60" leaves D60 unmapped, and
        // certain RR/TR variants are absent from these particular course files).
        assertThat(rowsInData).isBetween(30L, 42L);

        String csv = downloadCsv();
        assertThat(csv).startsWith("Sarja;");
        assertThat(csv).contains("H21E").contains("H75");
        long dataLines = csv.lines().count() - 1; // minus header
        assertThat(dataLines).isEqualTo(rowsInData);
    }

    @Test
    void postPlanToolbarEditPersistsParametersIntoWorkspace() {
        loadSampleDataAndGenerate();

        // Navigate back to the main entry view — PostPlanToolbar should be active.
        ext.navigate(CompetitionView.class);

        var firstStartPicker = ext.$(com.vaadin.flow.component.timepicker.TimePicker.class).single();
        var newTime = java.time.LocalTime.of(10, 30);
        firstStartPicker.setValue(newTime);

        // The onChange wiring should have pushed the new value straight to workspace.
        assertThat(CompetitionWorkspace.current().parameters().firstStart()).isEqualTo(newTime);

        // And it survives the JSON snapshot round-trip.
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        new tools.jackson.databind.ObjectMapper()
                .writeValue(bout, CompetitionWorkspace.current().snapshot());
        var snapshot = new fi.virit.lahtokaavio.io.WorkspaceJsonReader()
                .read(new java.io.ByteArrayInputStream(bout.toByteArray()));
        assertThat(snapshot.parameters().firstStart()).isEqualTo(newTime);
    }

    @Test
    void planViewOffersJsonDownloadThatRoundTrips() {
        loadSampleDataAndGenerate();
        ext.navigate(GridAndDownloadView.class);

        byte[] json = downloadBytes("Download Backup (JSON)");
        assertThat(json).isNotEmpty();
        String text = new String(json, StandardCharsets.UTF_8);
        assertThat(text).contains("\"seriesStarts\"").contains("\"firstStart\"");

        // Round-trip via the reader the entry view uses on upload.
        var snapshot = new fi.virit.lahtokaavio.io.WorkspaceJsonReader()
                .read(new java.io.ByteArrayInputStream(json));
        assertThat(snapshot.plan()).isNotNull();
        assertThat(snapshot.plan().seriesStarts()).isNotEmpty();
    }

    @Test
    void ganttFirstStartTimesMatchTheWorkspacePlan() {
        // Regression: the Gantt view recomputes positions from its own
        // LanePlan offsets, so the offset minutes it gets passed must
        // already include the planner's soft-start stagger. Otherwise the
        // Gantt would show e.g. STA1-8 starting at 11:01 while the table
        // view (which reads SeriesStart.firstStartTime directly) shows
        // 11:21. This test pins them together.
        loadSampleDataAndGenerate();
        GanttView view = ext.navigate(GanttView.class);
        in.virit.vlp.VisualLanePlanner planner = view.planner();

        for (fi.virit.lahtokaavio.domain.SeriesStart s
                : CompetitionWorkspace.current().plan().seriesStarts()) {
            java.time.LocalTime ganttTime = planner.firstStartOf(s.seriesName());
            assertThat(ganttTime)
                    .as("Gantt first start of %s should match the plan grid", s.seriesName())
                    .isEqualTo(s.firstStartTime());
        }
    }

    @Test
    void ganttMenuActionMovesSeriesToTargetLane() {
        loadSampleDataAndGenerate();

        // Use the registered dynamic route + the add-on's tester API.
        // The "..." MenuBar items are an implementation detail; tests drive
        // moves through the public moveItemToLane(...) method instead.
        GanttView view = ext.navigate(GanttView.class);
        in.virit.vlp.VisualLanePlanner planner = view.planner();

        // Pick any series and any different lane sharing its start place
        // (cross-start-place moves would violate the start-point invariant).
        String series = planner.laneOf("H21E") != null ? "H21E" : planner.itemIds().getFirst();
        String currentLane = planner.laneOf(series);
        String targetLane = planner.laneIds().stream()
                .filter(l -> !l.equals(currentLane))
                .filter(l -> samePlace(l, currentLane))
                .findFirst().orElseThrow();

        planner.moveItemToLane(series, targetLane);

        assertThat(planner.laneOf(series)).isEqualTo(targetLane);
        // The workspace's plan must reflect the move too — the view callback
        // pipes the edited plan back through CompetitionWorkspace.setPlan.
        String laneInWorkspace = CompetitionWorkspace.current().plan().seriesStarts().stream()
                .filter(s -> s.seriesName().equals(series))
                .map(fi.virit.lahtokaavio.domain.SeriesStart::laneId)
                .findFirst().orElseThrow();
        assertThat(laneInWorkspace).isEqualTo(targetLane);
    }

    private static boolean samePlace(String laneA, String laneB) {
        int dashA = laneA.lastIndexOf('-');
        int dashB = laneB.lastIndexOf('-');
        return dashA > 0 && dashB > 0
                && laneA.substring(0, dashA).equals(laneB.substring(0, dashB));
    }

    @Test
    void editingACountReshapesThePlan() {
        loadSampleDataAndGenerate();
        ext.navigate(GridAndDownloadView.class);

        String csvBefore = downloadCsv();
        java.time.LocalTime lastStartBefore = latestLastStart(csvBefore);

        ext.navigate(EditCountsView.class);

        // Bump the first class' count dramatically — the plan must accommodate
        // many more starters on its lane, pushing some queue's last start later.
        PositiveIntegerField firstField = ext.$(PositiveIntegerField.class).first();
        int original = firstField.getValue();
        firstField.setValue(original + 200);

        Button apply = ext.$(Button.class).withText("Apply and re-optimize").single();
        test(apply).click();

        String csvAfter = downloadCsv();
        java.time.LocalTime lastStartAfter = latestLastStart(csvAfter);
        assertThat(lastStartAfter).isAfter(lastStartBefore);
    }

    private void loadSampleDataAndGenerate() {
        ext.navigate(CompetitionView.class);

        // Phase 1 — three options. Use Option A: drop in courses + registrations.
        List<Upload> phase1Uploads = ext.$(Upload.class).all();
        assertThat(phase1Uploads).hasSize(3);
        UploadTester<Upload> coursesUpload = test(phase1Uploads.get(0));
        UploadTester<Upload> registrationsUpload = test(phase1Uploads.get(1));

        coursesUpload.upload("Viking-Line-L1.xml", "text/xml",
                SampleData.read(SampleData.COURSES_L1));
        coursesUpload.upload("Viking-Line-L2.xml", "text/xml",
                SampleData.read(SampleData.COURSES_L2));
        coursesUpload.upload("Viking-Line-RRTR.xml", "text/xml",
                SampleData.read(SampleData.COURSES_RRTR));
        registrationsUpload.upload("ilmoittautumiset.csv", "text/csv",
                SampleData.read(SampleData.REGISTRATIONS));

        // The Continue button moves the wizard from Phase 1 to Phase 2.
        Button continueButton = ext.$(Button.class).withText("Continue").single();
        test(continueButton).click();

        // Phase 2 — pick the competition day and trigger generation.
        ComboBox<String> daySelector = ext.$(ComboBox.class).first();
        ComboBoxTester<ComboBox<String>, String> dayTester = test(daySelector);
        dayTester.selectItem("Päivä #1");

        Button generate = ext.$(Button.class).withText("Generate Initial Start Plan").single();
        test(generate).click();
    }

    private String downloadCsv() {
        return new String(downloadBytes("Download CSV"), StandardCharsets.UTF_8);
    }

    private byte[] downloadBytes(String linkText) {
        Anchor download = ext.$(Anchor.class).withText(linkText).single();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AnchorTester tester = test(download);
        tester.download(out);
        return out.toByteArray();
    }

    private java.time.LocalTime latestLastStart(String csv) {
        // Columns: Sarja;Ensimmäinen lähtö;Viimeinen lähtö;Jono;Rastikoodit
        return csv.lines()
                .skip(1)
                .map(line -> line.split(";"))
                .map(cells -> java.time.LocalTime.parse(cells[2]))
                .max(java.time.LocalTime::compareTo)
                .orElseThrow();
    }
}
