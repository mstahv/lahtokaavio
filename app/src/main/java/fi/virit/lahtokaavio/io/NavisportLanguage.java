package fi.virit.lahtokaavio.io;

import java.util.Locale;

/**
 * Navisport recognises the participant import columns by their headers in
 * the language of the user's Navisport UI only.
 */
public enum NavisportLanguage {
    ENGLISH("English", "Name,Chip,Club,Class,Start time,License number"),
    FINNISH("Suomi", "Nimi,Kilpailukortti,Seura,Sarja,Lähtöaika,Lisenssinumero"),
    SWEDISH("Svenska", "Namn,Bricka,Klubb,Klass,Starttid,Licensnummer");

    private final String caption;
    private final String startListHeader;

    NavisportLanguage(String caption, String startListHeader) {
        this.caption = caption;
        this.startListHeader = startListHeader;
    }

    public String caption() {
        return caption;
    }

    /** Name, chip, club, class, start time and licence, in this order. */
    public String startListHeader() {
        return startListHeader;
    }

    /** Finnish or Swedish for those locales, English otherwise. */
    public static NavisportLanguage of(Locale locale) {
        return switch (locale.getLanguage()) {
            case "fi" -> FINNISH;
            case "sv" -> SWEDISH;
            default -> ENGLISH;
        };
    }
}
