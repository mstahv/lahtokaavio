package fi.virit.lahtokaavio.domain;

import java.util.List;

/**
 * A rule a (hand-edited) start plan breaks.
 *
 * @param series  the classes involved, highlighted in the Gantt view
 * @param message what is wrong, where and when, for the user
 */
public record PlanWarning(List<String> series, String message) {

    public PlanWarning {
        series = List.copyOf(series);
    }
}
