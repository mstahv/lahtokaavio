package fi.virit.lahtokaavio.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.browserless.BrowserlessApplicationContext;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.notification.NotificationLocator;
import fi.virit.lahtokaavio.domain.DrawnStart;
import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.io.NavisportLanguage;
import fi.virit.lahtokaavio.ui.helpers.SampleData;
import in.virit.vlp.VisualLanePlanner;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Real-world files from a small sprint event: a Purple Pen IOF XML v3 export
 * (class lists like "D14, H14, 14 Avoin" as course names) and a registrations
 * CSV and the entries list from IRMA (UTF-8, the counts with a byte order
 * mark).
 */
class MetsasprinttiDataTest {

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
    void organiserGeneratesAPlanFromPurplePenAndIrmaFiles() {
        generatePlan();

        // There is no "10 Avoin" course, but it runs the course of D10 and H10.
        // An assumption to check, so the user has to acknowledge it.
        assertThat(problemText())
                .isEqualTo("10 Avoin has no course of its own, using course \"D10, H10\".");
        window.findButton().withText("OK").inside(problem()).click();
        assertThat(problemShown()).isFalse();
        window.navigate(GridAndDownloadView.class);
        assertThat(window.findGrid(SeriesStart.class).size()).isEqualTo(32);

        // Open classes start last
        List<SeriesStart> starts = CompetitionWorkspace.current().plan().seriesStarts();
        LocalTime othersEnd = starts.stream().filter(s -> !s.seriesName().endsWith("Avoin"))
                .map(SeriesStart::lastStartTime).max(LocalTime::compareTo).orElseThrow();
        assertThat(starts).filteredOn(s -> s.seriesName().endsWith("Avoin"))
                .extracting(SeriesStart::seriesName).containsExactly("10 Avoin");
        assertThat(starts).filteredOn(s -> s.seriesName().endsWith("Avoin"))
                .allSatisfy(s -> assertThat(s.firstStartTime()).isAfter(othersEnd));

        // The Gantt view shows the open class at its real time, too
        window.navigate(GanttView.class);
        VisualLanePlanner planner = window.find(VisualLanePlanner.class).single();
        for (SeriesStart s : starts) {
            assertThat(planner.firstStartOf(s.seriesName())).as(s.seriesName())
                    .isEqualTo(s.firstStartTime());
        }
    }

    @Test
    void draggingASeriesIntoAClashShowsAWarningUntilItIsUndone() {
        generatePlan();
        window.findButton().withText("OK").inside(problem()).click();
        window.navigate(GanttView.class);
        assertThat(window.find(PlanWarningsPanel.class).exists()).as("the algorithm's plan is fine")
                .isFalse();
        VisualLanePlanner planner = window.find(VisualLanePlanner.class).single();
        String originalLane = planner.laneOf("H65");
        int originalIndex = planner.itemIds().stream()
                .filter(id -> originalLane.equals(planner.laneOf(id))).toList().indexOf("H65");

        // H65 heads to the first control 161, like D45 and D50 on its own
        // lane; at the head of STA1-2 its runners start a minute from theirs.
        planner.moveItem("H65", "STA1-2", 0);

        PlanWarningsPanel warnings = window.find(PlanWarningsPanel.class).single();
        assertThat(window.findListItem().inside(warnings).components())
                .extracting(ListItem::getText)
                .contains("D45 and H65 start from STA1 to the same first control 161 only 1 min "
                        + "apart, 2 times between 11:22 and 11:23.");
        assertThat(planner.warningOf("H65")).contains("D45 and H65");
        assertThat(planner.warningOf("H21")).isNull();

        // Shown in the other plan views as well
        window.navigate(GridAndDownloadView.class);
        assertThat(window.find(PlanWarningsPanel.class).exists()).isTrue();

        // Dragging it back fixes the plan
        window.navigate(GanttView.class);
        VisualLanePlanner again = window.find(VisualLanePlanner.class).single();
        again.moveItem("H65", originalLane, originalIndex);
        assertThat(window.find(PlanWarningsPanel.class).exists()).isFalse();
        assertThat(again.warningOf("H65")).isNull();
    }

    @Test
    void organiserRenamesALaneAndMovesItsStartIntoAClash() {
        generatePlan();
        window.findButton().withText("OK").inside(problem()).click();
        window.navigate(GanttView.class);

        // 10 Avoin runs alone on STA1-8 after everyone else. Starting it at
        // 12:01 puts its runners a minute from D10 and H10: same first control.
        editLane("STA1-8", "Avoimet", LocalTime.of(12, 1));

        assertThat(laneOf("10 Avoin")).isEqualTo("Avoimet");
        assertThat(firstStartOf("10 Avoin")).isEqualTo(LocalTime.of(12, 1));
        // Still known to start from STA1, though the lane name no longer says so
        assertThat(window.findListItem().inside(window.find(PlanWarningsPanel.class).single())
                .components()).extracting(ListItem::getText)
                .anyMatch(text -> text.startsWith("10 Avoin and D10 start from STA1 to the "
                        + "same first control 79"));

        editLane("Avoimet", "Avoimet", LocalTime.of(13, 49));
        assertThat(window.find(PlanWarningsPanel.class).exists()).isFalse();
        assertThat(laneOf("10 Avoin")).as("the name stays").isEqualTo("Avoimet");
    }

    private void editLane(String lane, String newName, LocalTime start) {
        window.findButton().withAriaLabel("Edit lane " + lane).click();
        window.findTextField().withLabel("Lane name").setValue(newName);
        window.findTimePicker().withLabel("First start").setValue(start);
        window.findButton().withText("Save").click();
    }

    @Test
    void applyKeepsTheLanesAndTheGanttEditsWhileReoptimizeStartsOver() {
        generatePlan();
        window.findButton().withText("OK").inside(problem()).click();
        window.navigate(GanttView.class);
        window.find(VisualLanePlanner.class).single().moveItem("H65", "STA1-2", 0);
        LocalTime h21Before = firstStartOf("H21");

        // Two late entries in H65, now at the head of STA1-2
        window.navigate(EditCountsView.class);
        var h65 = window.findIntegerField().withLabel("H65");
        h65.setValue(h65.component().getValue() + 2);
        window.findButton().withText("Apply").click();

        assertThat(laneOf("H65")).as("the Gantt edit is kept").isEqualTo("STA1-2");
        assertThat(firstStartOf("H21")).as("pushed by two H65 starters")
                .isEqualTo(h21Before.plusMinutes(4));

        window.navigate(EditCountsView.class);
        window.findButton().withText("Apply and re-optimize").click();
        assertThat(laneOf("H65")).as("the algorithm's choice again").isEqualTo("STA1-7");
    }

    private static String laneOf(String series) {
        return startOf(series).laneId();
    }

    private static LocalTime firstStartOf(String series) {
        return startOf(series).firstStartTime();
    }

    private static SeriesStart startOf(String series) {
        return CompetitionWorkspace.current().plan().seriesStarts().stream()
                .filter(s -> s.seriesName().equals(series)).findFirst().orElseThrow();
    }

    @Test
    void organiserDrawsStartTimesAndDownloadsTheStartList() {
        generatePlan();
        window.findButton().withText("OK").inside(problem()).click();

        window.navigate(DrawView.class);
        assertThat(window.findButton().withText("Draw start times").component().isEnabled()).isFalse();
        window.findUpload().upload("ilmoittautuneet.csv", "text/csv",
                SampleData.read("/metsasprintti/ilmoittautuneet.csv"));
        assertThat(window.findSpan().withText("143 competitors in 32 classes").exists()).isTrue();

        window.findButton().withText("Draw start times").click();

        // Late entries after the counts were exported
        String warning = problemText();
        assertThat(warning).contains("More entries than planned starts in")
                .contains("D12").contains("D14").contains("H10")
                .doesNotContain("No starts planned");
        window.findButton().withText("OK").inside(problem()).click();

        // Everyone is in the list
        assertThat(window.findGrid(DrawnStart.class).size()).isEqualTo(143);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        window.findAnchor().withText("Download start list (CSV)").download(out);
        List<String> lines = out.toString(StandardCharsets.UTF_8).lines().toList();
        assertThat(lines.getFirst()).isEqualTo("Sarja;Lähtöaika;Nimi;Seura;Emit;Lisenssi;Jono");
        assertThat(lines).hasSize(1 + 143);
        assertThat(lines).anyMatch(line -> line.startsWith("H21;")
                && line.contains(";Arola Topias;Sweden;536400;3094;"));

        // The same draw for Navisport's participant import. Navisport only
        // recognises the headers in the language of its UI.
        window.findCheckbox().withLabel("Navisport format").check();
        window.findSelect(NavisportLanguage.class).withAriaLabel("Navisport language")
                .selectItem("English");
        out = new ByteArrayOutputStream();
        window.findAnchor().withText("Download start list (CSV)").download(out);
        lines = out.toString(StandardCharsets.UTF_8).lines().toList();
        assertThat(lines.getFirst())
                .isEqualTo("Name,Chip,Club,Class,Start time,License number");
        assertThat(lines).hasSize(1 + 143);
        assertThat(lines).anyMatch(line -> line.matches(
                "Topias Arola,536400,Sweden,H21,\\d\\d:\\d\\d:00,3094"));
    }

    @Test
    void organiserLetsNavisportDrawFromTheStartScheme() {
        generatePlan();
        window.findButton().withText("OK").inside(problem()).click();
        window.navigate(GridAndDownloadView.class);

        window.findCheckbox().withLabel("Navisport format").check();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        window.findAnchor().withText("Download CSV").download(out);

        // One row per class, no header: class, first start, interval in seconds
        List<String> lines = out.toString(StandardCharsets.UTF_8).lines().toList();
        List<SeriesStart> starts = CompetitionWorkspace.current().plan().seriesStarts();
        assertThat(lines).hasSize(starts.size());
        SeriesStart open = starts.stream()
                .filter(s -> s.seriesName().equals("10 Avoin")).findFirst().orElseThrow();
        assertThat(lines).contains("10 Avoin," + open.firstStartTime()
                + "," + open.startIntervalMinutes() * 60);
        assertThat(lines).allMatch(line -> line.matches("[^,]+,\\d\\d:\\d\\d,\\d+"));
    }

    @Test
    void organiserPlansFromTheListOfEntriesAndDrawsWithoutUploadingAgain() {
        window.navigate(CompetitionView.class);
        window.findUpload().atIndex(1).upload("Metsäsprintti-26.xml", "text/xml",
                SampleData.read("/metsasprintti/Metsasprintti-26.xml"));
        // The list of entries instead of the count summary
        window.findUpload().atIndex(2).upload("ilmoittautuneet.csv", "text/csv",
                SampleData.read("/metsasprintti/ilmoittautuneet.csv"));
        assertThat(problemShown()).isFalse();
        window.findButton().withText("Continue").click();
        window.findButton().withText("Generate Initial Start Plan").click();
        window.findButton().withText("OK").inside(problem()).click(); // 10 Avoin borrows

        // The late entries are counted in: D12 has 7, the summary said 6
        assertThat(CompetitionWorkspace.current().plan().seriesStarts())
                .filteredOn(s -> s.seriesName().equals("D12"))
                .extracting(SeriesStart::competitors).containsExactly(7);

        // The entries are already there for the draw, and everyone has a slot
        window.navigate(DrawView.class);
        assertThat(window.findSpan().withText("143 competitors in 32 classes").exists()).isTrue();
        window.findButton().withText("Draw start times").click();
        assertThat(problemShown()).as("no overbooked classes").isFalse();
        assertThat(window.findGrid(DrawnStart.class).size()).isEqualTo(143);
    }

    private void generatePlan() {
        window.navigate(CompetitionView.class);

        window.findUpload().atIndex(1).upload("Metsäsprintti-26.xml", "text/xml",
                SampleData.read("/metsasprintti/Metsasprintti-26.xml"));
        window.findUpload().atIndex(2).upload("ilmoittautumiset-irmasta-mp.csv", "text/csv",
                SampleData.read("/metsasprintti/ilmoittautumiset-irmasta-mp.csv"));
        assertThat(problemShown()).isFalse();

        window.findButton().withText("Continue").click();
        window.findButton().withText("Generate Initial Start Plan").click();
    }

    @Test
    void unreadableFileShowsAnErrorUntilTheUserClicksOk() {
        window.navigate(CompetitionView.class);

        window.findUpload().atIndex(2).upload("courses.xml", "text/csv",
                "<CourseData/>".getBytes(StandardCharsets.UTF_8));

        assertThat(problemText()).startsWith("Failed to read courses.xml: Expected IRMA's summary by class");
        // No time passes in a browserless test, so check the setting itself
        assertThat(problem().component().getDuration()).as("stays open until dismissed").isZero();

        window.findButton().withText("OK").inside(problem()).click();
        assertThat(problemShown()).isFalse();
    }

    /** The OK-to-dismiss notification, not the "Loaded …" toasts. */
    private NotificationLocator problem() {
        return window.findNotification().withCondition(n -> n instanceof ProblemNotification);
    }

    private boolean problemShown() {
        return problem().exists();
    }

    private String problemText() {
        return window.findParagraph().inside(problem()).getText();
    }
}
