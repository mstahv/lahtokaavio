package fi.virit.lahtokaavio.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Result of parsing the registrations CSV: per-day competitor counts
 * keyed by class name. Each row in the CSV contributes one entry per day
 * column. Day labels are taken from the CSV header (e.g. "Päivä #1").
 * The counts can also be computed from the list of entries.
 */
public final class Registrations {

    private final Map<String, Map<String, Integer>> countsByDayAndClass;

    public Registrations(Map<String, Map<String, Integer>> countsByDayAndClass) {
        LinkedHashMap<String, Map<String, Integer>> copy = new LinkedHashMap<>();
        countsByDayAndClass.forEach((day, perClass) ->
                copy.put(day, Collections.unmodifiableMap(new LinkedHashMap<>(perClass))));
        this.countsByDayAndClass = Collections.unmodifiableMap(copy);
    }

    /** The day label used when the counts are computed from a list of entries. */
    public static final String SINGLE_DAY = "Päivä #1";

    /** Counts the competitors per class, in the order the classes first appear. */
    public static Registrations fromEntries(List<Entry> entries) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Entry entry : entries) {
            counts.merge(entry.className(), 1, Integer::sum);
        }
        return new Registrations(Map.of(SINGLE_DAY, counts));
    }

    public Set<String> dayLabels() {
        return countsByDayAndClass.keySet();
    }

    public Map<String, Integer> countsForDay(String dayLabel) {
        Map<String, Integer> result = countsByDayAndClass.get(dayLabel);
        if (result == null) {
            throw new IllegalArgumentException("Unknown day label: " + dayLabel);
        }
        return result;
    }
}
