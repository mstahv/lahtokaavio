package fi.virit.lahtokaavio.ui;

import com.vaadin.flow.component.grid.Grid;
import fi.virit.lahtokaavio.domain.SeriesStart;
import java.util.stream.Collectors;

/**
 * Read-only grid presentation of the generated start plan. Columns are kept in
 * the same order they appear in the exported CSV so the user sees what they
 * will download.
 */
public class StartPlanGrid extends Grid<SeriesStart> {

    public StartPlanGrid() {
        super(SeriesStart.class, false);

        addColumn(SeriesStart::laneId).setHeader("Lane").setSortable(true);
        addColumn(SeriesStart::seriesName).setHeader("Class").setSortable(true);
        addColumn(s -> s.firstStartTime().toString()).setHeader("First start").setSortable(true);
        addColumn(s -> s.lastStartTime().toString()).setHeader("Last start").setSortable(true);
        addColumn(s -> s.controlCodes().stream()
                .map(String::valueOf)
                .collect(Collectors.joining("-")))
                .setHeader("Controls");

        setAllRowsVisible(true);
    }
}
