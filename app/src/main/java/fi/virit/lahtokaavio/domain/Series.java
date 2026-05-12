package fi.virit.lahtokaavio.domain;

/**
 * A competition class with its assigned course and entry count.
 * The start interval defaults to the competition-wide value but
 * can be overridden per series.
 */
public record Series(String name, Course course, int competitors, int startIntervalMinutes) {
}
