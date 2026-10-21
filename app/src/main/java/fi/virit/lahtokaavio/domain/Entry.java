package fi.virit.lahtokaavio.domain;

/**
 * One competitor entered in a class, as exported from IRMA.
 *
 * @param className the class (sarja) the competitor runs in
 * @param licence   the competitor's licence or member number, may be empty
 * @param emitCard  the Emit card number, may be empty
 * @param name      "Lastname Firstname", as IRMA writes it
 * @param club      club name, may be empty
 */
public record Entry(String className, String licence, String emitCard, String name, String club) {
}
