package fi.virit.lahtokaavio.ui.helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Loads bundled IOF XML and registrations CSV samples for tests. Centralised
 * so individual tests don't repeat resource-loading boilerplate.
 */
public final class SampleData {

    public static final String COURSES_L1 = "/Viking-Line-L1.xml";
    public static final String COURSES_L2 = "/Viking-Line-L2.xml";
    public static final String COURSES_RRTR = "/Viking-Line-RRTR.xml";
    public static final String REGISTRATIONS = "/ilmoittautumiset.csv";

    private SampleData() {
    }

    public static byte[] read(String classpathResource) {
        try (InputStream in = SampleData.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing test resource: " + classpathResource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
