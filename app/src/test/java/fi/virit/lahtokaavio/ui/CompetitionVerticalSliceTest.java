package fi.virit.lahtokaavio.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.browserless.BrowserlessApplicationContext;
import com.vaadin.browserless.BrowserlessUIContext;
import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.io.WorkspaceJsonReader;
import fi.virit.lahtokaavio.ui.helpers.SampleData;
import in.virit.vlp.VisualLanePlanner;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end browserless tests of the vertical slice. Each test starts from a
 * fresh user; shared setup steps are factored into helper methods.
 *
 * Without a Spring context the views are constructed via their no-arg
 * fallback constructors, which wire real (not mocked) collaborators.
 */
class CompetitionVerticalSliceTest {

    private BrowserlessApplicationContext app;
    private BrowserlessUIContext window;

    @BeforeEach
    void setUp() {
        app = BrowserlessApplicationContext.create(CompetitionView.class);
        window = app.newUser().newWindow();
    }

    @AfterEach
    void tearDown() {
        app.close();
    }

    @Test
    void uploadGenerateDownload() {
        loadSampleDataAndGenerate();

        // Generation lands the user on the Gantt view; the grid + download
        // anchors live on GridAndDownloadView (the "Grid" tab in the top menu).
        window.navigate(GridAndDownloadView.class);

        int rows = window.findGrid(SeriesStart.class).size();
        // 42 classes in the CSV; the sample data has a handful with no matching
        // course (e.g. the well-known L1 typo "D 60" leaves D60 unmapped, and
        // certain RR/TR variants are absent from these particular course files).
        assertThat(rows).isBetween(30, 42);

        String csv = downloadCsv();
        assertThat(csv).startsWith("Sarja;");
        assertThat(csv).contains("H21E").contains("H75");
        long dataLines = csv.lines().count() - 1; // minus header
        assertThat(dataLines).isEqualTo(rows);
    }

    @Test
    void postPlanToolbarEditPersistsParametersIntoWorkspace() {
        loadSampleDataAndGenerate();

        // Navigate back to the main entry view — PostPlanToolbar should be active.
        window.navigate(CompetitionView.class);

        LocalTime newTime = LocalTime.of(10, 30);
        window.findTimePicker().withLabel("First start").setValue(newTime);

        // The onChange wiring should have pushed the new value straight to workspace.
        assertThat(CompetitionWorkspace.current().parameters().firstStart()).isEqualTo(newTime);

        // And it survives the JSON snapshot round-trip.
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        new ObjectMapper().writeValue(bout, CompetitionWorkspace.current().snapshot());
        var snapshot = new WorkspaceJsonReader().read(new ByteArrayInputStream(bout.toByteArray()));
        assertThat(snapshot.parameters().firstStart()).isEqualTo(newTime);
    }

    @Test
    void planViewOffersJsonDownloadThatRoundTrips() {
        loadSampleDataAndGenerate();
        window.navigate(GridAndDownloadView.class);

        byte[] json = download("Download Backup (JSON)");
        assertThat(json).isNotEmpty();
        String text = new String(json, StandardCharsets.UTF_8);
        assertThat(text).contains("\"seriesStarts\"").contains("\"firstStart\"");

        // Round-trip via the reader the entry view uses on upload.
        var snapshot = new WorkspaceJsonReader().read(new ByteArrayInputStream(json));
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
        VisualLanePlanner planner = window.find(VisualLanePlanner.class).single();

        for (SeriesStart s : CompetitionWorkspace.current().plan().seriesStarts()) {
            assertThat(planner.firstStartOf(s.seriesName()))
                    .as("Gantt first start of %s should match the plan grid", s.seriesName())
                    .isEqualTo(s.firstStartTime());
        }
    }

    @Test
    void ganttMoveUpdatesTheWorkspacePlan() {
        loadSampleDataAndGenerate();

        // Users drag series between lanes (or use the box's context menu).
        // Neither can be simulated here: drag and drop needs a browser, and
        // a closed context menu can't be found with a locator. Shortcut: the
        // add-on's own Java API, the same code path the drop listener uses.
        VisualLanePlanner planner = window.find(VisualLanePlanner.class).single();

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
                .map(SeriesStart::laneId)
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
        window.navigate(GridAndDownloadView.class);

        LocalTime lastStartBefore = latestLastStart(downloadCsv());

        window.navigate(EditCountsView.class);

        // Bump the first class' count dramatically — the plan must accommodate
        // many more starters on its lane, pushing some queue's last start later.
        var firstCount = window.findIntegerField().atIndex(1);
        firstCount.setValue(firstCount.component().getValue() + 200);

        window.findButton().withText("Apply and re-optimize").click();

        LocalTime lastStartAfter = latestLastStart(downloadCsv());
        assertThat(lastStartAfter).isAfter(lastStartBefore);
    }

    private void loadSampleDataAndGenerate() {
        window.navigate(CompetitionView.class);

        // Phase 1 — three options. Use Option A: drop in courses + registrations.
        // The uploads have no label, only a drop label, so they go by order.
        var coursesUpload = window.findUpload().atIndex(1);
        coursesUpload.upload("Viking-Line-L1.xml", "text/xml",
                SampleData.read(SampleData.COURSES_L1));
        coursesUpload.upload("Viking-Line-L2.xml", "text/xml",
                SampleData.read(SampleData.COURSES_L2));
        coursesUpload.upload("Viking-Line-RRTR.xml", "text/xml",
                SampleData.read(SampleData.COURSES_RRTR));
        window.findUpload().atIndex(2).upload("ilmoittautumiset.csv", "text/csv",
                SampleData.read(SampleData.REGISTRATIONS));

        // The Continue button moves the wizard from Phase 1 to Phase 2.
        window.findButton().withText("Continue").click();

        // Phase 2 — pick the competition day and trigger generation.
        window.findComboBox(String.class).withLabel("Competition day").selectItem("Päivä #1");
        window.findButton().withText("Generate Initial Start Plan").click();
    }

    private String downloadCsv() {
        return new String(download("Download CSV"), StandardCharsets.UTF_8);
    }

    private byte[] download(String linkText) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        window.findAnchor().withText(linkText).download(out);
        return out.toByteArray();
    }

    private LocalTime latestLastStart(String csv) {
        // Columns: Sarja;Ensimmäinen lähtö;Viimeinen lähtö;Jono;Rastikoodit
        return csv.lines()
                .skip(1)
                .map(line -> line.split(";"))
                .map(cells -> LocalTime.parse(cells[2]))
                .max(LocalTime::compareTo)
                .orElseThrow();
    }
}
