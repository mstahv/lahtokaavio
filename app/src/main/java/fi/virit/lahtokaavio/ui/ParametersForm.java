package fi.virit.lahtokaavio.ui;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.timepicker.TimePicker;
import fi.virit.lahtokaavio.domain.CompetitionParameters;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Common parameter editor — the five planner knobs the user can tune.
 * Reused by the initial setup wizard and the post-plan toolbar so the same
 * fields appear (and are pre-populated) in both contexts.
 */
public class ParametersForm extends VerticalLayout {

    private final TimePicker firstStart;
    private final PositiveIntegerField startInterval;
    private final PositiveIntegerField gapSlots;
    private final PositiveIntegerField maxPerMinute;
    private final Checkbox allowSplitting;

    public ParametersForm(CompetitionParameters initial) {
        setPadding(false);
        setSpacing(false);

        firstStart = new TimePicker("First start");
        firstStart.setValue(initial.firstStart());
        firstStart.setStep(Duration.ofMinutes(1));

        startInterval = new PositiveIntegerField(
                "Start interval (min)", initial.defaultStartIntervalMinutes(), 1);
        gapSlots = new PositiveIntegerField(
                "Gap slots between series", initial.gapSlotsBetweenSeries(), 0);
        maxPerMinute = new PositiveIntegerField(
                "Max starters / minute / start", initial.maxStartersPerMinutePerStartPlace(), 1);

        allowSplitting = new Checkbox(
                "Allow same first control on parallel lanes (different minutes only)");
        allowSplitting.setValue(initial.allowSplittingFirstControlGroups());

        HorizontalLayout row = new HorizontalLayout(
                firstStart, startInterval, gapSlots, maxPerMinute);
        row.setAlignItems(Alignment.END);
        add(row, allowSplitting);
    }

    /** Builds a {@link CompetitionParameters} from the current field values. */
    public CompetitionParameters toParameters() {
        return new CompetitionParameters(
                firstStart.getValue(),
                startInterval.getValue(),
                gapSlots.getValue(),
                maxPerMinute.getValue(),
                allowSplitting.getValue());
    }

    /**
     * Wires every field so any edit reports the latest {@link CompetitionParameters}
     * to {@code listener} — used to persist post-plan parameter tweaks
     * immediately (so they're in the JSON snapshot and survive navigation
     * even without a Re-optimize click).
     */
    public void onChange(Consumer<CompetitionParameters> listener) {
        firstStart.addValueChangeListener(e -> listener.accept(toParameters()));
        startInterval.addValueChangeListener(e -> listener.accept(toParameters()));
        gapSlots.addValueChangeListener(e -> listener.accept(toParameters()));
        maxPerMinute.addValueChangeListener(e -> listener.accept(toParameters()));
        allowSplitting.addValueChangeListener(e -> listener.accept(toParameters()));
    }
}
