package fi.virit.lahtokaavio.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;
import fi.virit.lahtokaavio.domain.DrawnStart;
import fi.virit.lahtokaavio.domain.Entry;
import fi.virit.lahtokaavio.domain.StartDraw;
import fi.virit.lahtokaavio.io.EntriesCsvReader;
import fi.virit.lahtokaavio.io.NavisportCsvWriter;
import fi.virit.lahtokaavio.io.NavisportLanguage;
import fi.virit.lahtokaavio.io.StartListCsvWriter;
import fi.virit.lahtokaavio.plan.StartTimeDraw;
import java.io.ByteArrayInputStream;
import java.security.SecureRandom;
import java.util.List;
import org.vaadin.firitin.appframework.MenuItem;

/**
 * Draws the competitors into the start slots of the finished plan: the user
 * uploads the entries exported from IRMA, draws, checks the result and
 * downloads the start list as CSV. Registered dynamically with the other
 * post-plan views once a plan exists.
 */
@PageTitle("Arvonta")
@MenuItem(order = MenuItem.DEFAULT + 100, icon = VaadinIcon.RANDOM)
@Route(registerAtStartup = false, layout = TopLayout.class)
public class DrawView extends VerticalLayout implements BeforeEnterObserver {

    private final EntriesCsvReader entriesReader = new EntriesCsvReader();
    private final StartListCsvWriter startListWriter = new StartListCsvWriter();
    private final NavisportCsvWriter navisportWriter = new NavisportCsvWriter();

    private final Span entriesStatus = new Span();
    private final Button drawButton = new Button("Draw start times", e -> draw());
    private final Anchor download = new StartListDownload();
    private final NavisportFormat navisportFormat = new NavisportFormat();
    private final DrawnStartGrid grid = new DrawnStartGrid();

    public DrawView() {
        setSizeFull();
        add(new H2("Arvonta"));
        add(new Paragraph("Upload the entries exported from IRMA (ilmoittautuneet.csv), unless "
                + "they were already given as the registrations, and draw the start order within each class. Drawing again gives a new order. "
                + "With Navisport format the list can be imported to Navisport's participant data."));
        add(new EntriesUpload(), entriesStatus);
        drawButton.addThemeVariants(ButtonVariant.PRIMARY);
        add(new HorizontalLayout(drawButton, download, navisportFormat), grid);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (CompetitionWorkspace.current().plan() == null) {
            event.forwardTo(CompetitionView.class);
            return;
        }
        refresh();
    }

    private void onEntriesUploaded(byte[] csv, String fileName) {
        try {
            CompetitionWorkspace.current().setEntries(
                    entriesReader.read(new ByteArrayInputStream(csv)));
            Notification.show("Loaded entries from " + fileName);
        } catch (RuntimeException e) {
            ProblemNotification.error("Failed to read " + fileName + ": " + e.getMessage());
        }
        refresh();
    }

    private void draw() {
        CompetitionWorkspace workspace = CompetitionWorkspace.current();
        StartDraw result = new StartTimeDraw(new SecureRandom())
                .draw(workspace.plan(), workspace.entries());
        workspace.setDraw(result);
        refresh();

        StringBuilder problems = new StringBuilder();
        if (!result.overbookedClasses().isEmpty()) {
            problems.append("More entries than planned starts in ")
                    .append(String.join(", ", result.overbookedClasses()))
                    .append(": the extra competitors start after the planned last start. "
                            + "Check the lanes for clashes, or update the counts and re-plan. ");
        }
        if (!result.unplannedClasses().isEmpty()) {
            problems.append("No starts planned for ")
                    .append(String.join(", ", result.unplannedClasses()))
                    .append(": these competitors are not in the start list.");
        }
        if (!problems.isEmpty()) {
            ProblemNotification.warning(problems.toString().trim());
        }
    }

    private void refresh() {
        CompetitionWorkspace workspace = CompetitionWorkspace.current();
        List<Entry> entries = workspace.entries();
        long classes = entries.stream().map(Entry::className).distinct().count();
        entriesStatus.setText(entries.isEmpty()
                ? "No entries loaded"
                : entries.size() + " competitors in " + classes + " classes");
        drawButton.setEnabled(!entries.isEmpty());

        StartDraw draw = workspace.draw();
        List<DrawnStart> starts = draw == null ? List.of() : draw.starts();
        grid.setItems(starts);
        grid.setVisible(draw != null);
        download.setVisible(draw != null);
        navisportFormat.setVisible(draw != null);
        download.setHref(event -> {
            event.setContentType("text/csv");
            if (navisportFormat.isSelected()) {
                event.setFileName("lahtoluettelo-navisport.csv");
                navisportWriter.writeStartList(starts, navisportFormat.language(),
                        event.getOutputStream());
            } else {
                event.setFileName("lahtoluettelo.csv");
                startListWriter.writeBytes(starts, event.getOutputStream());
            }
        });
    }

    private class EntriesUpload extends Upload {
        EntriesUpload() {
            super(UploadHandler.inMemory(
                    (metadata, data) -> onEntriesUploaded(data, metadata.fileName())));
            setAcceptedFileTypes("text/csv", ".csv");
            setDropLabel(new Paragraph("Entries CSV from IRMA"));
        }
    }

    /**
     * Navisport recognises the columns only in the language of its UI, so
     * the user picks the language of their Navisport.
     */
    private class NavisportFormat extends HorizontalLayout {
        private final Checkbox enabled = new Checkbox("Navisport format");
        private final Select<NavisportLanguage> language = new LanguageSelect();

        NavisportFormat() {
            setAlignItems(Alignment.BASELINE);
            language.setVisible(false);
            enabled.addValueChangeListener(e -> language.setVisible(e.getValue()));
            add(enabled, language);
        }

        boolean isSelected() {
            return enabled.getValue();
        }

        NavisportLanguage language() {
            return language.getValue();
        }

        private class LanguageSelect extends Select<NavisportLanguage> {
            LanguageSelect() {
                setAriaLabel("Navisport language");
                setTooltipText("The language of your Navisport: it only recognises "
                        + "the column headers in that language");
                setItems(NavisportLanguage.values());
                setItemLabelGenerator(NavisportLanguage::caption);
                setValue(NavisportLanguage.of(DrawView.this.getLocale()));
            }
        }
    }

    private static class StartListDownload extends Anchor {
        StartListDownload() {
            setText("Download start list (CSV)");
            setDownload(true);
        }
    }

    /** The drawn start list, in the same column order as the downloaded CSV. */
    private static class DrawnStartGrid extends Grid<DrawnStart> {
        DrawnStartGrid() {
            super(DrawnStart.class, false);
            addColumn(s -> s.entry().className()).setHeader("Class").setKey("class")
                    .setSortable(true);
            addColumn(s -> s.startTime().toString()).setHeader("Start").setKey("start")
                    .setSortable(true);
            addColumn(s -> s.entry().name()).setHeader("Name").setKey("name");
            addColumn(s -> s.entry().club()).setHeader("Club").setKey("club");
            addColumn(s -> s.entry().emitCard()).setHeader("Emit").setKey("emit");
            addColumn(s -> s.entry().licence()).setHeader("Licence").setKey("licence");
            addColumn(DrawnStart::laneId).setHeader("Lane").setKey("lane");
        }
    }
}
