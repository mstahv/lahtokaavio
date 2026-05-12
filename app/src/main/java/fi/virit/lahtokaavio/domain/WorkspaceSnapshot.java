package fi.virit.lahtokaavio.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serialisable snapshot of the full planning workspace — courses,
 * editable competitor counts, parameters and the current plan.
 * Used as the on-disk JSON format so the user can save a session
 * mid-flight and pick it up later (the spec's primary persistence
 * format; CSV remains an export-only target for results services).
 *
 * <p>Records and {@code Map<String,?>} serialise out of the box with
 * Jackson once JSR310 (LocalTime) is registered.
 */
public record WorkspaceSnapshot(
        Map<String, Course> coursesByClass,
        Map<String, Integer> counts,
        String selectedDay,
        CompetitionParameters parameters,
        StartPlan plan) {

    public WorkspaceSnapshot {
        coursesByClass = unmodifiableOrdered(coursesByClass);
        counts = unmodifiableOrdered(counts);
    }

    private static <K, V> Map<K, V> unmodifiableOrdered(Map<K, V> in) {
        return in == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(in));
    }
}
