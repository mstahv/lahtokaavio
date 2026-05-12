package fi.virit.lahtokaavio.domain;

import java.time.LocalTime;

/**
 * Top-level knobs that govern start scheduling for one competition day.
 *
 * @param firstStart                       wall-clock time of the first start.
 * @param defaultStartIntervalMinutes      gap between successive starters on
 *                                         the same lane (1–3 minutes typically).
 * @param gapSlotsBetweenSeries            empty slots reserved between two
 *                                         consecutive series on the same lane
 *                                         (defaults to 2, for late-comers /
 *                                         late entries).
 * @param maxStartersPerMinutePerStartPlace upper bound on simultaneous starts
 *                                         at one physical start point.
 * @param allowSplittingFirstControlGroups when {@code false} (the default and
 *                                         the safe rule), all series sharing
 *                                         the same first control are packed
 *                                         onto a single lane. When {@code true}
 *                                         the planner may spread them across
 *                                         multiple lanes as long as their
 *                                         start minutes never collide — useful
 *                                         when one first control draws so
 *                                         many series that strict packing
 *                                         lengthens the competition.
 */
public record CompetitionParameters(
        LocalTime firstStart,
        int defaultStartIntervalMinutes,
        int gapSlotsBetweenSeries,
        int maxStartersPerMinutePerStartPlace,
        boolean allowSplittingFirstControlGroups) {

    public static CompetitionParameters defaults() {
        return new CompetitionParameters(LocalTime.of(11, 0), 2, 2, 6, false);
    }
}
