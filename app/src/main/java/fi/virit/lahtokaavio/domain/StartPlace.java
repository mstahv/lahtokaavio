package fi.virit.lahtokaavio.domain;

/**
 * Identifies a physical starting location (IOF XML StartPointCode).
 * Two courses with the same code share the same physical start
 * and therefore the same capacity.
 */
public record StartPlace(String code) {
}
