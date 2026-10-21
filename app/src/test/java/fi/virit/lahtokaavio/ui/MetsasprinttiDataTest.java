package fi.virit.lahtokaavio.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.browserless.BrowserlessApplicationContext;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.flow.component.notification.NotificationLocator;
import fi.virit.lahtokaavio.domain.DrawnStart;
import fi.virit.lahtokaavio.domain.SeriesStart;
import fi.virit.lahtokaavio.ui.helpers.SampleData;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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

        // The registrations have "10 Avoin", the courses only "14 Avoin".
        // Every other class is found.
        assertThat(problemText()).isEqualTo("Warning: no course found for [10 Avoin]");
        window.findButton().withText("OK").inside(problem()).click();
        assertThat(problemShown()).isFalse();

        window.navigate(GridAndDownloadView.class);
        // 32 classes in the CSV, one of them without a course
        assertThat(window.findGrid(SeriesStart.class).size()).isEqualTo(31);
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

        // Late entries after the counts were exported, and the class without a course
        String warning = problemText();
        assertThat(warning).contains("More entries than planned starts in")
                .contains("D12").contains("D14").contains("H10")
                .contains("No starts planned for 10 Avoin");
        window.findButton().withText("OK").inside(problem()).click();

        // Everyone but the three "10 Avoin" competitors is in the list
        assertThat(window.findGrid(DrawnStart.class).size()).isEqualTo(140);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        window.findAnchor().withText("Download start list (CSV)").download(out);
        List<String> lines = out.toString(StandardCharsets.UTF_8).lines().toList();
        assertThat(lines.getFirst()).isEqualTo("Sarja;Lähtöaika;Nimi;Seura;Emit;Lisenssi;Jono");
        assertThat(lines).hasSize(1 + 140);
        assertThat(lines).anyMatch(line -> line.startsWith("H21;")
                && line.contains(";Arola Topias;Sweden;536400;3094;"));
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

        assertThat(problemText()).startsWith("Failed to read courses.xml: Expected header");
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
